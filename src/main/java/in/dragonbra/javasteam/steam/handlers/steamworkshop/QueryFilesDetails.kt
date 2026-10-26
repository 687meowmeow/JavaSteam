package `in`.dragonbra.javasteam.steam.handlers.steamworkshop

import `in`.dragonbra.javasteam.protobufs.steamclient.Enums.EPublishedFileQueryType
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.EPublishedFileRevision

/**
 * Details for a workshop browse query — mirrors ISteamUGC's SendQueryUGCRequest parameters.
 *
 * This is the SAME call that `steam_api64.dll` (the native Steamworks SDK) makes when WE PC's
 * BrowseWallpapersWindow sends a "queryMostPopular" action to its wpx.internal backend.
 *
 * Unlike the IPublishedFileService HTTP API (which hard-limits `days` to [1,7] for
 * RankedByTrend), the unified-message service path honors ANY positive integer value —
 * so we can pass days=365 for "Most Popular (Year)" and Steam will return the correct
 * trending-across-the-year results.
 *
 * @property appId The Steam appid whose workshop to query (431960 for Wallpaper Engine).
 * @property queryType The sort mode — see [EPublishedFileQueryType]. Use [EPublishedFileQueryType.k_PublishedFileQueryType_RankedByTrend] for "Most Popular".
 * @property page 1-indexed page number (Steam caps at 1000).
 * @property cursor Pagination cursor — pass `"*"` for the first request; for subsequent pages use the `nextCursor` value from the previous response. When set, `page` is ignored.
 * @property numPerPage Items per page (Steam accepts up to 100; typical: 30-50).
 * @property creatorAppId The appid that PUBLISHED the files (usually the same as appId for self-published workshops).
 * @property requiredTags Tags that must be present on returned items.
 * @property excludedTags Tags that must NOT be present on returned items.
 * @property matchAllTags If true, items must have ALL required tags; if false, items must have at LEAST ONE.
 * @property requiredFlags Required flags that must be set on returned items.
 * @property omittedFlags Flags that must not be set on returned items.
 * @property searchText Text to search for in title/description (use with [EPublishedFileQueryType.k_PublishedFileQueryType_RankedByTextSearch]).
 * @property filetype Filter by file type; 0 = items.
 * @property childPublishedFileId Find all items that reference the given item.
 * @property days For RankedByTrend queries — the number of days to get votes for. **No [1,7] limit on this path** — pass 1, 7, 30, 90, 365, etc. for arbitrary time ranges.
 * @property includeRecentVotesOnly If true, limit result set to items that have votes within the day range given.
 * @property cacheMaxAgeSeconds Allow stale data to be returned for the specified number of seconds.
 * @property language Language to search in and return. Defaults to 0 (English).
 * @property requiredKvTags Key-value tags to match on (e.g., `app_workshop_eula_version=3` for mobile-compat items).
 * @property tagGroups At least one of the tags in each group must be present on a published file to satisfy the query.
 * @property dateRangeCreated Filter to items created within this range (pair of start/end unix timestamps).
 * @property dateRangeUpdated Filter to items updated within this range.
 * @property adminQuery Admin tool is doing a query, return hidden items.
 * @property totalOnly If true, only return the total number of files that satisfy the query.
 * @property idsOnly If true, only return the published file ids of files that satisfy the query.
 * @property returnVoteData Return vote data (score, votes_up, votes_down) in the file details.
 * @property returnTags Return tags in the file details.
 * @property returnKvTags Return key-value tags in the file details.
 * @property returnPreviews Return preview image and video details in the file details.
 * @property returnChildren Return child item ids in the file details.
 * @property returnShortDescription Populate the short_description field instead of file_description.
 * @property returnForSaleData Return pricing information, if applicable.
 * @property returnMetadata Populate the metadata field.
 * @property returnPlaytimeStats Return playtime stats for the specified number of days before today.
 * @property returnDetails Return the full PublishedFileDetails for each item (always true on the service path).
 * @property stripDescriptionBbcode If true, strip BBCode from the description.
 * @property desiredRevision The desired revision of the published file (e.g., latest, approved snapshot, author snapshot).
 * @property returnReactions Return reaction data.
 */
@Suppress("DataClassShouldBeImmutable")
data class QueryFilesDetails(
    val appId: Int,
    val queryType: EPublishedFileQueryType,
    val page: Int = 1,
    val cursor: String? = null,
    val numPerPage: Int = 30,
    val creatorAppId: Int? = null,
    val requiredTags: List<String> = emptyList(),
    val excludedTags: List<String> = emptyList(),
    val matchAllTags: Boolean = true,
    val requiredFlags: List<String> = emptyList(),
    val omittedFlags: List<String> = emptyList(),
    val searchText: String? = null,
    val filetype: Int? = null,
    val childPublishedFileId: ULong? = null,
    val days: UInt? = null,
    val includeRecentVotesOnly: Boolean = false,
    val cacheMaxAgeSeconds: UInt = 0u,
    val language: Int = 0,
    val requiredKvTags: List<Pair<String, String>> = emptyList(),
    val tagGroups: List<List<String>> = emptyList(),
    val dateRangeCreated: Pair<UInt, UInt>? = null,
    val dateRangeUpdated: Pair<UInt, UInt>? = null,
    val adminQuery: Boolean = false,
    val totalOnly: Boolean = false,
    val idsOnly: Boolean = false,
    val returnVoteData: Boolean = true,
    val returnTags: Boolean = true,
    val returnKvTags: Boolean = true,
    val returnPreviews: Boolean = true,
    val returnChildren: Boolean = false,
    val returnShortDescription: Boolean = true,
    val returnForSaleData: Boolean = false,
    val returnMetadata: Boolean = false,
    val returnPlaytimeStats: UInt? = null,
    val returnDetails: Boolean = true,
    val stripDescriptionBbcode: Boolean = false,
    val desiredRevision: EPublishedFileRevision = EPublishedFileRevision.k_EPublishedFileRevision_Default,
    val returnReactions: Boolean = false,
)
