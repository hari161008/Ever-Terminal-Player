package dev.jyotiraditya.dmt.yt

import com.github.innertube.Innertube
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Persists the user's pinned YouTube search results as JSON so they survive
 * app restarts, without needing a database just for a short favorites list. */
object YtPinStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Innertube.SongItem.serializer())

    fun encode(songs: List<Innertube.SongItem>): String =
        runCatching { json.encodeToString(serializer, songs) }.getOrDefault("[]")

    fun decode(raw: String?): List<Innertube.SongItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
