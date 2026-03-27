/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.SOUNDCLOUD_ID_PREFIX
import com.metrolist.music.utils.SoundCloudTokenManager
import com.metrolist.music.utils.toMediaItem
import com.metrolist.soundcloud.SoundCloud
import com.metrolist.soundcloud.models.SoundCloudTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber

class SoundCloudQueue(
    private val tracks: List<SoundCloudTrackDto>,
    private val title: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {

    companion object {
        private const val RESOLVE_BATCH_SIZE = 12
        private const val INITIAL_WINDOW_BEFORE = 4
        private const val INITIAL_WINDOW_AFTER = 10
    }

    private val playableTracks = tracks
    private var resolveOffset = 0

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        if (playableTracks.isEmpty()) {
            return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
        }

        val targetIndex = startIndex.coerceIn(0, playableTracks.lastIndex)
        val windowStart = (targetIndex - INITIAL_WINDOW_BEFORE).coerceAtLeast(0)
        val windowEnd = (targetIndex + INITIAL_WINDOW_AFTER + 1).coerceAtMost(playableTracks.size)
        val resolvedItems = resolveRange(windowStart, windowEnd)
        resolveOffset = windowEnd

        val mediaItemIndex = resolvedItems.indexOfFirst { it.mediaId == "$SOUNDCLOUD_ID_PREFIX${playableTracks[targetIndex].id}" }
            .takeIf { it >= 0 }
            ?: resolvedItems.indices.firstOrNull()
            ?: 0

        Queue.Status(
            title = title ?: playableTracks[targetIndex].title,
            items = resolvedItems,
            mediaItemIndex = mediaItemIndex,
        )
    }

    override fun hasNextPage(): Boolean = resolveOffset < playableTracks.size

    override suspend fun nextPage(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (resolveOffset >= playableTracks.size) {
            return@withContext emptyList()
        }

        val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(playableTracks.size)
        val nextItems = resolveRange(resolveOffset, end)
        resolveOffset = end
        nextItems
    }

    override suspend fun getFullStatus(): Queue.Status? = withContext(Dispatchers.IO) {
        if (playableTracks.isEmpty()) return@withContext null

        val resolvedItems = resolveRange(0, playableTracks.size)
        if (resolvedItems.isEmpty()) return@withContext null

        val targetIndex = startIndex.coerceIn(0, playableTracks.lastIndex)
        val mediaItemIndex = resolvedItems.indexOfFirst { it.mediaId == "$SOUNDCLOUD_ID_PREFIX${playableTracks[targetIndex].id}" }
            .takeIf { it >= 0 }
            ?: 0

        resolveOffset = playableTracks.size
        Queue.Status(
            title = title ?: playableTracks[targetIndex].title,
            items = resolvedItems,
            mediaItemIndex = mediaItemIndex,
        )
    }

    private suspend fun resolveRange(start: Int, end: Int): List<MediaItem> = coroutineScope {
        if (!SoundCloudTokenManager.ensureAuthenticated()) {
            Timber.w("SoundCloudQueue: auth failed, cannot resolve tracks")
            return@coroutineScope emptyList()
        }
        playableTracks.subList(start, end).map { track ->
            async {
                resolveTrack(track)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun resolveTrack(track: SoundCloudTrackDto): MediaItem? {
        val result = SoundCloud.preferredStreamUrl(track.id.toString())
        val streamUrl = result.getOrElse { e ->
            Timber.e(e, "SoundCloudQueue: failed to resolve stream for track ${track.id} (${track.title})")
            return null
        }
        Timber.d("SoundCloudQueue: resolved track ${track.id} → ${streamUrl.take(80)}…")
        return track.toMediaItem(streamUrl)
    }
}
