package `in`.dragonbra.javasteam.steam.handlers.steamworkshop

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request

/**
 * Details for querying a specific user's published workshop files — mirrors
 * ISteamPublishedFile's GetUserFiles RPC parameters.
 *
 * Used for browsing a specific creator's workshop (replacing the HTML scraping
 * of steamcommunity.com/profiles/<steamId>/myworkshop/ which gets rate-limited
 * with HTTP 429 after just a few requests).
 *
 * @property steamId The Steam ID64 of the creator whose files to query.
 * @property appId The Steam appid whose workshop to query (431960 for Wallpaper Engine).
 * @property page 1-indexed page number.
 * @property numPerPage Items per page (up to 100).
 * @property type The type of files to return. Default "myfiles" = files published by this user.
 * @property sortMethod Sort method. Default "lastupdated". Other values: "published", "name", "trend", "subscriptions", "voted".
 * @property requiredTags Tags that must be present.
 * @property excludedTags Tags that must NOT be present.
 * @property requiredKvTags Key-value tags to match on.
 * @property filetype Filter by file type; 0 = items.
 * @property returnVoteData Return vote data.
 * @property returnTags Return tags.
 * @property returnKvTags Return key-value tags.
 * @property returnPreviews Return preview details.
 * @property returnShortDescription Populate short_description instead of file_description.
 * @property returnForSaleData Return pricing information.
 * @property returnMetadata Populate the metadata field.
 * @property returnChildren Return child item ids.
 * @property idsOnly If true, only return published file ids.
 * @property totalOnly If true, only return the total count.
 */
@Suppress("DataClassShouldBeImmutable")
data class GetUserFilesDetails(
    val steamId: ULong,
    val appId: Int,
    val page: Int = 1,
    val numPerPage: Int = 30,
    val type: String = "myfiles",
    val sortMethod: String = "lastupdated",
    val requiredTags: List<String> = emptyList(),
    val excludedTags: List<String> = emptyList(),
    val requiredKvTags: List<Pair<String, String>> = emptyList(),
    val filetype: Int? = null,
    val returnVoteData: Boolean = true,
    val returnTags: Boolean = true,
    val returnKvTags: Boolean = true,
    val returnPreviews: Boolean = true,
    val returnShortDescription: Boolean = true,
    val returnForSaleData: Boolean = false,
    val returnMetadata: Boolean = false,
    val returnChildren: Boolean = false,
    val idsOnly: Boolean = false,
    val totalOnly: Boolean = false,
)
