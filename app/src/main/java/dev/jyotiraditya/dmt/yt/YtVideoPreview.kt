package dev.jyotiraditya.dmt.yt

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Shows a real, unmuted YouTube page in a WebView: it searches YouTube for
 * [query] (the currently playing track's title + artist), auto-clicks the
 * first search result, and lets that watch page's own player provide both
 * video and audio. dmt's own audio pipeline is muted by the view model while
 * this is active, so there's only one audio source at a time. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtVideoPreview(
    query: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    // Keying on the query forces a fresh WebView (and a fresh search) whenever
    // the currently playing track changes, so the preview never gets stuck on
    // a stale video.
    key(query) {
        var webView by remember { mutableStateOf<WebView?>(null) }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            when {
                                // Search results: auto-click the first video.
                                url.contains("/results") ->
                                    view.evaluateJavascript(AUTO_CLICK_FIRST_RESULT_JS, null)

                                // Watch page: dismiss consent, unmute, play.
                                url.contains("/watch") ->
                                    view.evaluateJavascript(START_PLAYBACK_JS, null)
                            }
                        }
                    }
                    val searchUrl = "https://m.youtube.com/results?search_query=" +
                        Uri.encode(query)
                    loadUrl(searchUrl)
                    webView = this
                }
            },
            update = { },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            }
        )

        LaunchedEffect(isPlaying) {
            webView?.evaluateJavascript(
                if (isPlaying) PLAY_JS else PAUSE_JS,
                null
            )
        }
    }
}

private const val AUTO_CLICK_FIRST_RESULT_JS = """
(function() {
  function dismissConsent() {
    var candidates = document.querySelectorAll('button, tp-yt-paper-button, yt-button-renderer');
    for (var i = 0; i < candidates.length; i++) {
      var t = (candidates[i].innerText || '').trim().toLowerCase();
      if (t === 'accept all' || t === 'i agree' || t === 'agree') {
        candidates[i].click();
        return true;
      }
    }
    return false;
  }
  dismissConsent();
  var tries = 0;
  var timer = setInterval(function() {
    tries++;
    dismissConsent();
    var link = document.querySelector('a[href^="/watch?v="]');
    if (link) {
      clearInterval(timer);
      window.location.href = link.href;
    } else if (tries > 20) {
      clearInterval(timer);
    }
  }, 250);
})();
"""

private const val START_PLAYBACK_JS = """
(function() {
  function dismissConsent() {
    var candidates = document.querySelectorAll('button, tp-yt-paper-button, yt-button-renderer');
    for (var i = 0; i < candidates.length; i++) {
      var t = (candidates[i].innerText || '').trim().toLowerCase();
      if (t === 'accept all' || t === 'i agree' || t === 'agree') {
        candidates[i].click();
        return true;
      }
    }
    return false;
  }
  var tries = 0;
  var timer = setInterval(function() {
    tries++;
    dismissConsent();
    var v = document.querySelector('video');
    if (v) {
      clearInterval(timer);
      v.muted = false;
      v.volume = 1.0;
      v.play().catch(function() {});
    } else if (tries > 20) {
      clearInterval(timer);
    }
  }, 250);
})();
"""

private const val PLAY_JS = """
(function() {
  var v = document.querySelector('video');
  if (v) { v.muted = false; v.play().catch(function() {}); }
})();
"""

private const val PAUSE_JS = """
(function() {
  var v = document.querySelector('video');
  if (v) { v.pause(); }
})();
"""
