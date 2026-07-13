package com.coolappstore.everterminalplayer.yt

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.innertube.Innertube
import com.github.innertube.requests.player
import com.github.innertube.requests.searchPage
import com.github.innertube.utils.from
import kotlinx.coroutines.withTimeoutOrNull

/** Prefix used on media ids for tracks that come from YouTube search, so the
 * rest of the app can tell them apart from local library tracks (whose ids
 * are plain numeric MediaStore ids). */
const val YT_ID_PREFIX = "yt:"

object YtRepository {

    private suspend fun ensureSession() {
        if (Innertube.visitorData != null) return
        withTimeoutOrNull(4_000) {
            while (Innertube.visitorData == null) {
                kotlinx.coroutines.delay(100)
            }
        }
        if (Innertube.visitorData == null) {
            runCatching { Innertube.fetchVisitorData() }
        }
    }

    suspend fun search(query: String): Result<List<Innertube.SongItem>> = runCatching {
        ensureSession()
        Innertube.searchPage(
            query = query,
            params = Innertube.SearchFilter.Song.value,
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )?.getOrThrow()?.items.orEmpty()
    }

    suspend fun resolveStreamUrl(videoId: String): String? {
        ensureSession()
        val response = Innertube.player(videoId)?.getOrNull() ?: return null
        return response.streamingData?.highestQualityFormat?.url
    }
}

fun Innertube.SongItem.title(): String = info?.name ?: "unknown"

fun Innertube.SongItem.artistLine(): String =
    authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
        ?.takeIf { it.isNotBlank() } ?: "unknown artist"

fun Innertube.SongItem.albumName(): String = album?.name.orEmpty()

fun Innertube.SongItem.thumbnailUrl(): String? = thumbnail?.size(544)

fun Innertube.SongItem.toMediaItem(streamUrl: String): MediaItem {
    val videoId = key
    val artwork = thumbnailUrl()
    return MediaItem.Builder()
        .setMediaId(YT_ID_PREFIX + videoId)
        .setUri(Uri.parse(streamUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title())
                .setArtist(artistLine())
                .setAlbumTitle(albumName())
                .apply { if (artwork != null) setArtworkUri(Uri.parse(artwork)) }
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()
}
