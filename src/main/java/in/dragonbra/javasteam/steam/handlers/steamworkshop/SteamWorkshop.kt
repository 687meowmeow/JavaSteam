package `in`.dragonbra.javasteam.steam.handlers.steamworkshop

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUcm.CMsgClientUCMEnumeratePublishedFilesByUserAction
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_QueryFiles_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_QueryFiles_Response
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.callback.UserActionPublishedFilesCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.AsyncJobSingle

/**
 * This handler is used for requesting files published on the Steam Workshop.
 *
 * Provides:
 *  - [enumeratePublishedFilesByUserAction] — list files the current user has acted on (subscribed, favorited).
 *  - [queryFiles] — query the workshop for files matching the given criteria (browse/sort/search).
 *    This is the same call WE PC's `wallpaperui.exe` makes via `steam_api64.dll`'s
 *    `ISteamUGC::SendQueryUGCRequest`. Bypasses the IPublishedFileService HTTP API's
 *    [1,7] days limit for RankedByTrend — pass any positive integer (1, 7, 30, 90, 365, ...)
 *    for arbitrary time ranges.
 */
class SteamWorkshop : ClientMsgHandler() {

    /**
     * Lazy-initialized handle to the auto-generated [PublishedFile] RPC service.
     *
     * The service class is generated at build time by the RPC code generator
     * (buildSrc `RpcGenTask`) from the `service PublishedFile { ... }` declaration
     * in `steammessages_publishedfile.steamclient.proto`. It dispatches calls
     * through the [SteamUnifiedMessages] handler.
     */
    private val publishedFileService: PublishedFile by lazy {
        val unifiedMessages = client.getHandler(SteamUnifiedMessages::class.java)
            ?: throw NullPointerException("Unable to get SteamUnifiedMessages handler")
        unifiedMessages.createService<PublishedFile>()
    }

    /**
     * Enumerates the list of published files for the current logged-in user based on user action.
     * Results are returned in a [UserActionPublishedFilesCallback].
     * The returned [AsyncJobSingle] can also be awaited to retrieve the callback result.
     *
     * @param details The specific details of the request.
     * @return The Job ID of the request. This can be used to find the appropriate [UserActionPublishedFilesCallback].
     */
    fun enumeratePublishedFilesByUserAction(details: EnumerationUserDetails): AsyncJobSingle<UserActionPublishedFilesCallback> {
        val enumRequest = ClientMsgProtobuf<CMsgClientUCMEnumeratePublishedFilesByUserAction.Builder>(
            CMsgClientUCMEnumeratePublishedFilesByUserAction::class.java,
            EMsg.ClientUCMEnumeratePublishedFilesByUserAction
        ).apply {
            sourceJobID = client.getNextJobID()

            body.action = details.userAction.code()
            body.appId = details.appID
            body.startIndex = details.startIndex
        }

        client.send(enumRequest)

        return AsyncJobSingle(this.client, enumRequest.sourceJobID)
    }

    /**
     * Queries the Steam Workshop for files matching the given criteria. This is the
     * `ISteamPublishedFile.QueryFiles` service RPC — the same call that
     * `steam_api64.dll` (the native Steamworks SDK) makes when WE PC browses the
     * workshop with a "Most Popular (Year)" sort.
     *
     * Unlike the IPublishedFileService HTTP API (which hard-limits `days` to [1,7]
     * for RankedByTrend), this service path honors ANY positive integer value:
     *   - days = 1   → "Most Popular (Today)"
     *   - days = 7   → "Most Popular (Week)"
     *   - days = 30  → "Most Popular (Month)"
     *   - days = 365 → "Most Popular (Year)"
     *   - days = 90  → custom 90-day trend window
     * Steam's backend honors any value and returns distinct, correctly-ordered results
     * for each.
     *
     * Usage:
     * ```kotlin
     * val job = steamWorkshop.queryFiles(details)
     * callbackManager.subscribe(job) { response: ServiceMethodResponse<CPublishedFile_QueryFiles_Response.Builder> ->
     *     val body = response.body.build()
     *     val total = body.total
     *     val items = body.publishedfiledetailsList
     *     val nextCursor = body.nextCursor
     *     // ...
     * }
     * ```
     *
     * Or with coroutines:
     * ```kotlin
     * val response = steamWorkshop.queryFiles(details).await()
     * val body = response.body.build()
     * ```
     *
     * @param details The query parameters.
     * @return The Job ID of the request. The corresponding
     *   [ServiceMethodResponse]<[CPublishedFile_QueryFiles_Response.Builder]> callback
     *   can be awaited or subscribed to via the callback manager.
     */
    fun queryFiles(
        details: QueryFilesDetails,
    ): AsyncJobSingle<ServiceMethodResponse<CPublishedFile_QueryFiles_Response.Builder>> {
        val request = CPublishedFile_QueryFiles_Request.newBuilder().apply {
            queryType = details.queryType.number
            page = details.page
            details.cursor?.let { cursor = it }
            numperpage = details.numPerPage
            details.creatorAppId?.let { creatorAppid = it }
            appid = details.appId
            addAllRequiredtags(details.requiredTags)
            addAllExcludedtags(details.excludedTags)
            matchAllTags = details.matchAllTags
            addAllRequiredFlags(details.requiredFlags)
            addAllOmittedFlags(details.omittedFlags)
            details.searchText?.let { searchText = it }
            details.filetype?.let { filetype = it }
            details.childPublishedFileId?.let { childPublishedfileid = it.toLong() }
            details.days?.let { days = it.toInt() }
            includeRecentVotesOnly = details.includeRecentVotesOnly
            cacheMaxAgeSeconds = details.cacheMaxAgeSeconds.toInt()
            language = details.language
            // required_kv_tags (KVTag submessages)
            details.requiredKvTags.forEach { (key, value) ->
                addRequiredKvTags(
                    CPublishedFile_QueryFiles_Request.KVTag.newBuilder()
                        .setKey(key)
                        .setValue(value)
                        .build()
                )
            }
            // taggroups (TagGroup submessages)
            details.tagGroups.forEach { tags ->
                addTaggroups(
                    CPublishedFile_QueryFiles_Request.TagGroup.newBuilder()
                        .addAllTags(tags)
                        .build()
                )
            }
            details.dateRangeCreated?.let { (start, end) ->
                dateRangeCreated = CPublishedFile_QueryFiles_Request.DateRange.newBuilder()
                    .setTimestampStart(start.toInt())
                    .setTimestampEnd(end.toInt())
                    .build()
            }
            details.dateRangeUpdated?.let { (start, end) ->
                dateRangeUpdated = CPublishedFile_QueryFiles_Request.DateRange.newBuilder()
                    .setTimestampStart(start.toInt())
                    .setTimestampEnd(end.toInt())
                    .build()
            }
            adminQuery = details.adminQuery
            totalonly = details.totalOnly
            idsOnly = details.idsOnly
            returnVoteData = details.returnVoteData
            returnTags = details.returnTags
            returnKvTags = details.returnKvTags
            returnPreviews = details.returnPreviews
            returnChildren = details.returnChildren
            returnShortDescription = details.returnShortDescription
            returnForSaleData = details.returnForSaleData
            returnMetadata = details.returnMetadata
            details.returnPlaytimeStats?.let { returnPlaytimeStats = it.toInt() }
            returnDetails = details.returnDetails
            stripDescriptionBbcode = details.stripDescriptionBbcode
            // For enum-typed proto fields, Java protobuf generates setXxx(Enum) overload.
            // Pass the enum directly (not .number).
            desiredRevision = details.desiredRevision
            returnReactions = details.returnReactions
        }.build()

        return publishedFileService.queryFiles(request)
    }

    /**
     * Handles a client message. This should not be called directly.
     * @param packetMsg The packet message that contains the data.
     */
    override fun handleMsg(packetMsg: IPacketMsg) {
        // ignore messages that we don't have a handler function for
        val callback: CallbackMsg = getCallback(packetMsg) ?: return

        client.postCallback(callback)
    }

    companion object {
        private fun getCallback(packetMsg: IPacketMsg): CallbackMsg? = when (packetMsg.msgType) {
            EMsg.ClientUCMEnumeratePublishedFilesByUserActionResponse -> UserActionPublishedFilesCallback(packetMsg)
            // Note: ServiceMethodResponse messages (incl. QueryFiles responses) are dispatched
            // automatically by SteamUnifiedMessages via the PublishedFile service class — no
            // extra handling needed here.
            else -> null
        }
    }
}
