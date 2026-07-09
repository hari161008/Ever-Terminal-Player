package dev.jyotiraditya.dmt.yt

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView

/** Shows a real, unmuted YouTube page in a small preview box: it searches
 * YouTube for [query] (the currently playing track's title + artist),
 * auto-clicks the first search result, and lets that watch page's own
 * player provide both video and audio. dmt's own audio pipeline is muted by
 * the view model while this is active, so there's only one audio source at
 * a time.
 *
 * The YouTube page's own fullscreen control (tap the fullscreen icon, or
 * press "f") works normally here: a [WebChromeClient] hands the fullscreen
 * `<video>` element off to the activity's root view so it can cover the
 * whole screen, then hands it back to this preview box when fullscreen is
 * exited. */
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
        val composeView = LocalView.current
        val activity = composeView.context as? Activity

        AndroidView(
            modifier = modifier.aspectRatio(16f / 9f),
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
                    webChromeClient = if (activity != null) {
                        FullscreenVideoChromeClient(activity)
                    } else {
                        WebChromeClient()
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

        DisposableEffect(Unit) {
            onDispose {
                (webView?.webChromeClient as? FullscreenVideoChromeClient)?.exitFullscreenIfNeeded()
            }
        }

        LaunchedEffect(isPlaying) {
            webView?.evaluateJavascript(
                if (isPlaying) PLAY_JS else PAUSE_JS,
                null
            )
        }
    }
}

/** Lets the WebView's HTML5 fullscreen video (the YouTube page's own
 * fullscreen button / "f" shortcut) actually cover the whole screen, by
 * moving the browser-supplied custom view into the activity's root content
 * view for the duration of fullscreen, then removing it again. */
private class FullscreenVideoChromeClient(private val activity: Activity) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var originalSystemUiVisibility = 0

    private val rootView: ViewGroup?
        get() = activity.window?.decorView as? ViewGroup

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        val decor = rootView ?: return

        @Suppress("DEPRECATION")
        originalSystemUiVisibility = decor.systemUiVisibility
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        decor.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        val decor = rootView
        decor?.removeView(view)
        @Suppress("DEPRECATION")
        decor?.systemUiVisibility = originalSystemUiVisibility
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    fun exitFullscreenIfNeeded() {
        if (customView != null) onHideCustomView()
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
