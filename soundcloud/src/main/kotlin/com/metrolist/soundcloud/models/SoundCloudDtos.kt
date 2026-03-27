package com.metrolist.soundcloud.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SoundCloudTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    val scope: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
)

@Serializable
data class SoundCloudCollectionPage<T>(
    val collection: List<T> = emptyList(),
    @SerialName("next_href") val nextHref: String? = null,
)

@Serializable
data class SoundCloudUserDto(
    val id: Long,
    val username: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    val description: String? = null,
)

@Serializable
data class SoundCloudTranscodingFormat(
    val protocol: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
)

@Serializable
data class SoundCloudTranscoding(
    val url: String? = null,
    val preset: String? = null,
    val duration: Long? = null,
    val snipped: Boolean = false,
    val format: SoundCloudTranscodingFormat? = null,
    val quality: String? = null,
)

@Serializable
data class SoundCloudMedia(
    val transcodings: List<SoundCloudTranscoding> = emptyList(),
)

@Serializable
data class SoundCloudStreamResponse(
    val url: String? = null,
)

@Serializable
data class SoundCloudTrackDto(
    val id: Long,
    val title: String,
    val duration: Long? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("waveform_url") val waveformUrl: String? = null,
    val streamable: Boolean = false,
    @SerialName("user_favorite") val userFavorite: Boolean? = null,
    val user: SoundCloudUserDto? = null,
    val media: SoundCloudMedia? = null,
    val access: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
)

@Serializable
data class SoundCloudPlaylistDto(
    val id: Long,
    val title: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    val description: String? = null,
    val user: SoundCloudUserDto? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    val tracks: List<SoundCloudTrackDto> = emptyList(),
)

@Serializable
data class SoundCloudLikeOriginDto(
    val kind: String? = null,
    val id: Long? = null,
    val title: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    val streamable: Boolean? = null,
    @SerialName("user_favorite") val userFavorite: Boolean? = null,
    val duration: Long? = null,
    val user: SoundCloudUserDto? = null,
)

@Serializable
data class SoundCloudActivityDto(
    val kind: String? = null,
    val track: SoundCloudTrackDto? = null,
    val user: SoundCloudUserDto? = null,
    val origin: SoundCloudLikeOriginDto? = null,
)

@Serializable
data class SoundCloudLikeDto(
    val kind: String? = null,
    val track: SoundCloudTrackDto? = null,
    val playlist: SoundCloudPlaylistDto? = null,
    val user: SoundCloudUserDto? = null,
    val origin: SoundCloudLikeOriginDto? = null,
)

@Serializable
data class SoundCloudStreamUrlsDto(
    @SerialName("hls_aac_160_url") val hlsAac160Url: String? = null,
    @SerialName("hls_mp3_128_url") val hlsMp3128Url: String? = null,
    @SerialName("http_mp3_128_url") val httpMp3128Url: String? = null,
)

@Serializable
data class SoundCloudSearchSummary(
    val tracks: List<SoundCloudTrackDto> = emptyList(),
    val users: List<SoundCloudUserDto> = emptyList(),
    val playlists: List<SoundCloudPlaylistDto> = emptyList(),
)
