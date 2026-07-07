package dev.jyotiraditya.dmt.yt

import com.github.innertube.Innertube
import com.github.innertube.requests.lyrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
private data class LrcLibResult(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)

/** Fetches lyrics for a YouTube-sourced track: LRCLIB first for real, synced
 * (time-tagged) lyrics, falling back to YouTube Music's own plain lyrics tab
 * via Innertube when LRCLIB has no match. Both are handed to the same
 * [dev.jyotiraditya.dmt.data.lyrics.LyricsParser] used for local files, so
 * whichever one succeeds renders through the existing lyrics UI unchanged. */
object YtLyrics {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(videoId: String, title: String, artist: String): String? {
        if (title.isNotBlank()) {
            fetchFromLrcLib(title, artist)?.let { return it }
        }
        if (videoId.isBlank()) return null
        return runCatching { Innertube.lyrics(videoId)?.getOrNull() }.getOrNull()
    }

    private fun fetchFromLrcLib(title: String, artist: String): String? = runCatching {
        val query = buildString {
            append("track_name=").append(URLEncoder.encode(title, "UTF-8"))
            if (artist.isNotBlank() && artist != "unknown artist") {
                append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            }
        }
        val connection = URL("https://lrclib.net/api/get?$query")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "dmt/1.0 (+https://github.com/jyotiraditya/dmt)")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        if (connection.responseCode != 200) return@runCatching null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val result = json.decodeFromString<LrcLibResult>(body)
        result.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: result.plainLyrics?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
