package com.coolappstore.everterminalplayer.yt

import com.github.innertube.Innertube
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the bits of state that Innertube (soundpod's YouTube Music backend)
 * needs in order to search and resolve playable audio: a visitor id, YT
 * cookies, and a "decipher" callback that runs in the hidden [YtWebView] to
 * un-throttle signed stream URLs.
 */
object YtSession {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    fun update(
        visitorData: String? = null,
        cookies: String? = null,
        decipher: (suspend (String) -> String)? = null,
    ) {
        visitorData?.let { Innertube.visitorData = it }
        cookies?.let { Innertube.cookies = it }
        decipher?.let { Innertube.decipher = it }
        if (Innertube.visitorData != null) _ready.value = true
    }
}
