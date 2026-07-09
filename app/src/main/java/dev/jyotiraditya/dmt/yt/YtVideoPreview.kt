package dev.jyotiraditya.dmt.yt

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.jyotiraditya.dmt.ui.YtVideoKey
import java.net.URLEncoder

/**
 * Video mode for a track that was played from YouTube search. Loads the
 * real youtube.com site inside a WebView, auto-searches for
 * "<track title> video song", forces a real navigation into the first
 * result's watch page (so the WebView actually fires a page-load callback
 * instead of silently soft-navigating), and spoofs a viewport matching the
 * preview box's own 1095x657 ratio so YouTube — including its own
 * fullscreen player — lays itself out against that exact shape, matching
 * the container pixel-for-ratio so the fullscreen video fills it cleanly
 * with no leftover empty space.
 *
 * When the person taps YouTube's own fullscreen/expand control, the
 * resulting custom view is added into this composable's own container
 * (sized to the box, never the device's actual screen), so "fullscreen"
 * here means filling the preview container only, not the whole phone
 * display.
 *
 * dmt's own audio pipeline is muted by the view model while this is
 * active (see [dev.jyotiraditya.dmt.ui.PlayerViewModel.toggleYtVideoMode]),
 * so all audio and video the person sees/hears here comes straight from
 * YouTube itself. Play/pause and next/previous from dmt's own transport are
 * relayed in as a simulated spacebar / arrow-key press via [keyEvent].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtVideoPreview(
    videoId: String,
    title: String,
    isPlaying: Boolean,
    keyEvent: YtVideoKey?,
    modifier: Modifier = Modifier,
) {
    // Keying on the id forces a fresh WebView + fresh search whenever the
    // currently playing track changes, so the preview never gets stuck on a
    // stale video.
    key(videoId) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        var watchPageReady by remember { mutableStateOf(false) }

        AndroidView(
            modifier = modifier.aspectRatio(1095f / 657f),
            factory = { context ->
                // A local container the size of the preview box itself.
                // YouTube's own fullscreen/"expand" control hands us a
                // custom view via onShowCustomView — adding that view here,
                // instead of into the Activity's root decor view, is what
                // confines "fullscreen" to this box instead of letting it
                // cover the whole device screen.
                val container = FrameLayout(context)

                val wv = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.userAgentString = DESKTOP_UA
                    setBackgroundColor(android.graphics.Color.BLACK)

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view == null) return
                            visibility = View.GONE
                            container.addView(
                                view,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            )
                        }

                        override fun onHideCustomView() {
                            if (container.childCount > 1) {
                                container.removeViewAt(container.childCount - 1)
                            }
                            visibility = View.VISIBLE
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view.evaluateJavascript(SPOOF_VIEWPORT_JS, null)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(SPOOF_VIEWPORT_JS, null)
                            when {
                                url.contains("/watch") -> {
                                    view.evaluateJavascript(PLAY_AND_FULLSCREEN_JS, null)
                                    watchPageReady = true
                                }
                                url.contains("/results") -> {
                                    watchPageReady = false
                                    view.evaluateJavascript(NAVIGATE_TO_FIRST_RESULT_JS, null)
                                }
                            }
                        }
                    }

                    val query = URLEncoder.encode("$title video song", "UTF-8")
                    loadUrl("https://www.youtube.com/results?search_query=$query")
                }

                container.addView(
                    wv,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                )
                webView = wv
                container
            },
            update = { },
            onRelease = { container ->
                (container as? FrameLayout)?.let { fl ->
                    for (i in fl.childCount - 1 downTo 0) fl.removeViewAt(i)
                }
                webView?.stopLoading()
                webView?.destroy()
            }
        )

        // dmt's transport controls relay in here as a one-shot simulated
        // keypress, rather than a continuous play/pause push — this keeps
        // a single toggle from a button tap mapping to a single toggle on
        // the YouTube page instead of racing with it.
        LaunchedEffect(keyEvent, watchPageReady) {
            if (watchPageReady && keyEvent != null) {
                webView?.evaluateJavascript(pressKeyJs(keyEvent.key), null)
            }
        }
    }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

/** Reports a viewport matching the preview box's own 1095x657 ratio to the
 * page's own JS, so YouTube (and its fullscreen player) lays itself out
 * against that exact size instead of the device's real, differently-shaped
 * screen. Wrapped defensively since some of these properties may already
 * be non-configurable on a given WebView build. */
private const val SPOOF_VIEWPORT_JS = """
(function() {
  try {
    var w = 1095, h = 657;
    Object.defineProperty(window.screen, 'width', { get: function() { return w; }, configurable: true });
    Object.defineProperty(window.screen, 'height', { get: function() { return h; }, configurable: true });
    Object.defineProperty(window, 'innerWidth', { get: function() { return w; }, configurable: true });
    Object.defineProperty(window, 'innerHeight', { get: function() { return h; }, configurable: true });
    Object.defineProperty(window, 'outerWidth', { get: function() { return w; }, configurable: true });
    Object.defineProperty(window, 'outerHeight', { get: function() { return h; }, configurable: true });
  } catch (e) {}
})();
"""

/** Runs on the search results page. Finds the first real video result
 * (skipping ads/shelves) and forces a real navigation to it via
 * `window.location.href` rather than a synthetic `.click()`. YouTube's own
 * click handler just soft-navigates via history.pushState, which the
 * WebView never sees as a page load — a real location change is what makes
 * [WebViewClient.onPageFinished] fire again so the watch-page script below
 * actually runs. Results load in asynchronously, so this polls briefly
 * rather than firing once. */
private const val NAVIGATE_TO_FIRST_RESULT_JS = """
(function() {
  var tries = 0;
  var timer = setInterval(function() {
    tries++;
    var link = document.querySelector('a#thumbnail[href^="/watch"]') ||
               document.querySelector('a#video-title[href^="/watch"]') ||
               document.querySelector('ytd-video-renderer a#thumbnail');
    if (link) {
      clearInterval(timer);
      var href = link.getAttribute('href');
      window.location.href = href.indexOf('http') === 0
        ? href
        : 'https://www.youtube.com' + href;
    } else if (tries > 30) {
      clearInterval(timer);
    }
  }, 300);
})();
"""

/** Runs on the watch page once it has actually finished loading. Starts
 * playback, and both simulates the "f" fullscreen shortcut / clicks the
 * fullscreen button so YouTube's own fullscreen (contained to this box via
 * onShowCustomView above) engages automatically. */
private const val PLAY_AND_FULLSCREEN_JS = """
(function() {
  var tries = 0;
  var timer = setInterval(function() {
    tries++;
    var player = document.querySelector('#movie_player');
    var video = document.querySelector('video');
    if (player && video) {
      clearInterval(timer);
      video.muted = false;
      video.play().catch(function() {});
      if (player.focus) { player.focus(); }
      ['keydown', 'keyup'].forEach(function(type) {
        var evt = new KeyboardEvent(type, {
          key: 'f', code: 'KeyF', keyCode: 70, which: 70, bubbles: true
        });
        document.dispatchEvent(evt);
        player.dispatchEvent(evt);
      });
      setTimeout(function() {
        if (!document.fullscreenElement) {
          var btn = document.querySelector('.ytp-fullscreen-button');
          if (btn) { btn.click(); }
        }
        video.play().catch(function() {});
      }, 500);
    } else if (tries > 30) {
      clearInterval(timer);
    }
  }, 300);
})();
"""

/** Builds the JS for a one-shot simulated keypress (space for play/pause,
 * left/right arrow for seek) sent to the YouTube page, matching the actual
 * keyboard shortcut a person would use on youtube.com itself. */
private fun pressKeyJs(key: String): String {
    val (code, keyCode) = when (key) {
        "ArrowRight" -> "ArrowRight" to 39
        "ArrowLeft" -> "ArrowLeft" to 37
        else -> "Space" to 32
    }
    val keyValue = if (key == "Space") " " else key
    return """
(function() {
  var player = document.querySelector('#movie_player');
  if (player && player.focus) { player.focus(); }
  ['keydown', 'keyup'].forEach(function(type) {
    var evt = new KeyboardEvent(type, {
      key: '$keyValue', code: '$code', keyCode: $keyCode, which: $keyCode, bubbles: true
    });
    document.dispatchEvent(evt);
    if (player) { player.dispatchEvent(evt); }
  });
})();
"""
}
