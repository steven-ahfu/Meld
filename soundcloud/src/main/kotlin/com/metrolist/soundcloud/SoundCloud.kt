package com.metrolist.soundcloud

import com.metrolist.soundcloud.auth.SoundCloudAuth
import com.metrolist.soundcloud.models.SoundCloudActivityDto
import com.metrolist.soundcloud.models.SoundCloudCollectionPage
import com.metrolist.soundcloud.models.SoundCloudLikeDto
import com.metrolist.soundcloud.models.SoundCloudPlaylistDto
import com.metrolist.soundcloud.models.SoundCloudSearchSummary
import com.metrolist.soundcloud.models.SoundCloudStreamResponse
import com.metrolist.soundcloud.models.SoundCloudStreamUrlsDto
import com.metrolist.soundcloud.models.SoundCloudTokenResponse
import com.metrolist.soundcloud.models.SoundCloudTrackDto
import com.metrolist.soundcloud.models.SoundCloudUserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object SoundCloud {
    private const val API_BASE_URL = "https://api-v2.soundcloud.com/"

    @Volatile
    var accessToken: String? = null

    @Volatile
    var refreshToken: String? = null

    @Volatile
    var clientId: String? = null

    @Volatile
    var userId: String? = null

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                exponentialDelay()
            }
            defaultRequest {
                url(API_BASE_URL)
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                header("Accept", "application/json, text/javascript, */*; q=0.01")
                accessToken?.let { header("Authorization", "OAuth $it") }
                clientId?.let { url.parameters.append("client_id", it) }
            }
            expectSuccess = false
        }
    }

    private fun log(level: String, message: String) {
        logger?.invoke(level, message)
    }

    suspend fun exchangeCode(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        codeVerifier: String,
        code: String,
    ): SoundCloudTokenResponse =
        SoundCloudAuth.exchangeCode(
            httpClient = httpClient,
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
            code = code,
        ).also { token ->
            accessToken = token.accessToken
            refreshToken = token.refreshToken
        }

    suspend fun refreshAccessToken(
        clientId: String,
        clientSecret: String,
        token: String = refreshToken ?: error("No refresh token available"),
    ): SoundCloudTokenResponse =
        SoundCloudAuth.refreshToken(
            httpClient = httpClient,
            clientId = clientId,
            clientSecret = clientSecret,
            refreshToken = token,
        ).also { refreshed ->
            accessToken = refreshed.accessToken
            refreshToken = refreshed.refreshToken ?: token
        }

    suspend fun me(): Result<SoundCloudUserDto> = runCatching {
        log("D", "GET me")
        val response = httpClient.get("me")
        require(response.status == HttpStatusCode.OK) {
            "SoundCloud me failed: ${response.status.value}"
        }
        response.body()
    }

    private fun requireUserId(): String =
        userId ?: error("SoundCloud userId not set")

    suspend fun stream(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudActivityDto>> =
        fetchCollection("stream", continuation)

    suspend fun likes(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudLikeDto>> =
        fetchCollection("users/${requireUserId()}/track_likes", continuation)

    suspend fun playlists(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudPlaylistDto>> =
        fetchCollection("users/${requireUserId()}/playlists", continuation)

    suspend fun selfTracks(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudTrackDto>> =
        fetchCollection("users/${requireUserId()}/tracks", continuation)

    suspend fun selfPlaylists(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudPlaylistDto>> =
        fetchCollection("users/${requireUserId()}/playlists", continuation)

    suspend fun followings(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudUserDto>> =
        fetchCollection("users/${requireUserId()}/followings", continuation)

    suspend fun followers(continuation: String? = null): Result<SoundCloudCollectionPage<SoundCloudUserDto>> =
        fetchCollection("users/${requireUserId()}/followers", continuation)

    suspend fun searchTracks(
        query: String,
        continuation: String? = null,
    ): Result<SoundCloudCollectionPage<SoundCloudTrackDto>> = runCatching {
        if (continuation != null) {
            val response = httpClient.get(continuation)
            require(response.status == HttpStatusCode.OK) {
                "SoundCloud track search continuation failed: ${response.status.value}"
            }
            response.body()
        } else {
            val response = httpClient.get("search/tracks") {
                parameter("q", query)
                parameter("linked_partitioning", 1)
                parameter("limit", 50)
            }
            require(response.status == HttpStatusCode.OK) {
                "SoundCloud track search failed: ${response.status.value}"
            }
            response.body()
        }
    }

    suspend fun searchUsers(
        query: String,
        continuation: String? = null,
    ): Result<SoundCloudCollectionPage<SoundCloudUserDto>> = runCatching {
        if (continuation != null) {
            val response = httpClient.get(continuation)
            require(response.status == HttpStatusCode.OK) {
                "SoundCloud user search continuation failed: ${response.status.value}"
            }
            response.body()
        } else {
            val response = httpClient.get("search/users") {
                parameter("q", query)
                parameter("linked_partitioning", 1)
                parameter("limit", 50)
            }
            require(response.status == HttpStatusCode.OK) {
                "SoundCloud user search failed: ${response.status.value}"
            }
            response.body()
        }
    }

    suspend fun searchPlaylists(
        query: String,
        continuation: String? = null,
    ): Result<SoundCloudCollectionPage<SoundCloudPlaylistDto>> = runCatching {
        if (continuation != null) {
            val response = httpClient.get(continuation)
            require(response.status == HttpStatusCode.OK) {
                "SoundCloud playlist search continuation failed: ${response.status.value}"
            }
            response.body()
        } else {
            val response = httpClient.get("search/playlists") {
                parameter("q", query)
                parameter("linked_partitioning", 1)
                parameter("limit", 50)
            }
            require(response.status == HttpStatusCode.OK) {
                "SoundCloud playlist search failed: ${response.status.value}"
            }
            response.body()
        }
    }

    suspend fun searchSummary(query: String): Result<SoundCloudSearchSummary> = runCatching {
        val tracks = searchTracks(query).getOrThrow().collection
        val users = searchUsers(query).getOrThrow().collection
        val playlists = searchPlaylists(query).getOrThrow().collection
        SoundCloudSearchSummary(
            tracks = tracks,
            users = users,
            playlists = playlists,
        )
    }

    suspend fun user(id: String): Result<SoundCloudUserDto> = runCatching {
        val response = httpClient.get("users/$id")
        require(response.status == HttpStatusCode.OK) {
            "SoundCloud user failed: ${response.status.value}"
        }
        response.body()
    }

    suspend fun playlist(id: String): Result<SoundCloudPlaylistDto> = runCatching {
        val response = httpClient.get("playlists/$id")
        require(response.status == HttpStatusCode.OK) {
            "SoundCloud playlist failed: ${response.status.value}"
        }
        response.body()
    }

    suspend fun userPlaylists(
        userId: String,
        continuation: String? = null,
    ): Result<SoundCloudCollectionPage<SoundCloudPlaylistDto>> =
        fetchCollection("users/$userId/playlists", continuation)

    suspend fun userTracks(
        userId: String,
        continuation: String? = null,
    ): Result<SoundCloudCollectionPage<SoundCloudTrackDto>> =
        fetchCollection("users/$userId/tracks", continuation)

    /**
     * Resolves a playable stream URL for the given track.
     *
     * Strategy:
     * 1. Fetch track info to get `access` and `media.transcodings`
     * 2. Use the official `/tracks/{id}/stream` endpoint (redirects to stream)
     * 3. Try resolving individual transcodings
     */
    suspend fun preferredStreamUrl(trackId: String): Result<String> = runCatching {
        // Fetch track to get access level and transcodings
        val track = runCatching {
            val resp = httpClient.get("tracks/$trackId")
            log("D", "preferredStreamUrl($trackId): track fetch status=${resp.status.value}")
            require(resp.status == HttpStatusCode.OK) {
                "SoundCloud track fetch failed: ${resp.status.value}"
            }
            resp.body<SoundCloudTrackDto>()
        }.getOrElse { e ->
            log("E", "preferredStreamUrl($trackId): track fetch error: ${e.message}")
            null
        }

        // Check access level
        val access = track?.access
        log("D", "preferredStreamUrl($trackId): access=$access streamable=${track?.streamable}")
        if (access == "blocked") {
            error("Track $trackId is blocked (geo-restricted or paywall)")
        }

        // --- Try transcodings ---
        val transcodings = track?.media?.transcodings.orEmpty()
            .filter { !it.snipped && it.url != null }
        log("D", "preferredStreamUrl($trackId): ${transcodings.size} transcodings")

        if (transcodings.isNotEmpty()) {
            // Order: progressive first (most reliable), then HLS AAC, then HLS MP3
            val ordered = buildList {
                addAll(transcodings.filter { it.format?.protocol == "progressive" })
                addAll(transcodings.filter { it.format?.protocol == "hls" && it.format.mimeType?.contains("mp4") == true })
                addAll(transcodings.filter { it.format?.protocol == "hls" && it.format.mimeType?.contains("mpeg") == true })
                addAll(transcodings.filter { it.format?.protocol == "hls" })
            }.distinctBy { it.url }

            for (transcoding in ordered) {
                val transcodingUrl = transcoding.url ?: continue
                log("D", "preferredStreamUrl($trackId): trying ${transcoding.format?.protocol}/${transcoding.format?.mimeType}")
                try {
                    val streamResp = httpClient.get(transcodingUrl)
                    log("D", "preferredStreamUrl($trackId): resolve status=${streamResp.status.value}")
                    if (streamResp.status == HttpStatusCode.OK) {
                        val streamData = streamResp.body<SoundCloudStreamResponse>()
                        val resolvedUrl = streamData.url
                        if (!resolvedUrl.isNullOrBlank()) {
                            log("D", "preferredStreamUrl($trackId) OK via transcoding: ${resolvedUrl.take(120)}")
                            return@runCatching resolvedUrl
                        }
                    }
                } catch (e: Exception) {
                    log("W", "preferredStreamUrl($trackId): transcoding failed: ${e.message}")
                }
            }
        }

        // --- Fallback: /tracks/{urn}/streams (uses track URN format) ---
        val trackUrn = "soundcloud:tracks:$trackId"
        log("D", "preferredStreamUrl($trackId): trying /streams with URN")
        val streamsResp = httpClient.get("tracks/$trackUrn/streams")
        log("D", "preferredStreamUrl($trackId): /streams status=${streamsResp.status.value}")
        if (streamsResp.status == HttpStatusCode.OK) {
            val streams = streamsResp.body<SoundCloudStreamUrlsDto>()
            val url = streams.hlsAac160Url
                ?: streams.hlsMp3128Url
                ?: streams.httpMp3128Url
            if (!url.isNullOrBlank()) {
                log("D", "preferredStreamUrl($trackId) OK via /streams: ${url.take(120)}")
                return@runCatching url
            }
        }

        error("No playable SoundCloud stream URL found for track $trackId (access=$access)")
    }

    private suspend inline fun <reified T> fetchCollection(
        path: String,
        continuation: String?,
    ): Result<SoundCloudCollectionPage<T>> = runCatching {
        val response = if (continuation != null) {
            httpClient.get(continuation)
        } else {
            httpClient.get(path) {
                parameter("linked_partitioning", 1)
                parameter("limit", 50)
            }
        }
        require(response.status == HttpStatusCode.OK) {
            "SoundCloud request failed: ${response.status.value}"
        }
        response.body()
    }
}
