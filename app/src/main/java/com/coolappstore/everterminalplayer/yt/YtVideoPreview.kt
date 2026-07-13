package com.coolappstore.everterminalplayer.yt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.coolappstore.everterminalplayer.ui.KEY_SEEK_PREFIX
import com.coolappstore.everterminalplayer.ui.YtVideoKey
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Video mode for a track that was played from YouTube search. Loads the
 * real youtube.com site inside a WebView, auto-searches for
 * "<track title> video song", forces a real navigation into the first
 * result's watch page (so the WebView actually fires a page-load callback
 * instead of silently soft-navigating), spoofs a viewport matching the
 * preview box's own 1095x657 ratio, and auto-engages fullscreen directly
 * through the page's own Fullscreen API — calling requestFullscreen() (or
 * the WebView-native webkitEnterFullscreen()) on the video/player element
 * itself, the same mechanism the browser invokes when a real tap on the
 * player triggers it, without simulating any click or touch.
 *
 * The resulting fullscreen custom view is added into this composable's own
 * container (sized to the box, never the device's actual screen), so
 * "fullscreen" here means filling the preview container only — while the
 * device's own navigation bar is hidden for the duration so it doesn't
 * poke into that view.
 *
 * dmt's own audio pipeline is muted by the view model while this is
 * active (see [com.coolappstore.everterminalplayer.ui.PlayerViewModel.toggleYtVideoMode]),
 * so all audio and video the person sees/hears here comes straight from
 * YouTube itself. Play/pause and next/previous from dmt's own transport are
 * relayed in via [keyEvent] as a direct, deterministic play()/pause()/seek
 * call on the page's <video> element (paired with a simulated keypress for
 * YouTube's own on-screen feedback), so a tap always lands in the state dmt
 * asked for instead of racing a toggle. The real video's own current time
 * and duration are polled back out and reported via [onProgress] so dmt's
 * own time slider tracks the actual YouTube video instead of the muted
 * internal track running behind it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtVideoPreview(
    videoId: String,
    title: String,
    isPlaying: Boolean,
    keyEvent: YtVideoKey?,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keying on the id forces a fresh WebView + fresh search whenever the
    // currently playing track changes, so the preview never gets stuck on a
    // stale video.
    key(videoId) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        var watchPageReady by remember { mutableStateOf(false) }
        val activity = LocalContext.current.findActivity()

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

                    // Without this, an outer scrollable parent (e.g. a
                    // Column/LazyColumn hosting this preview) steals touch
                    // gestures meant for the WebView itself, so it can
                    // never process its own scrolling/taps — which in turn
                    // was also throwing off the page's own layout timing
                    // for revealing its controls.
                    setOnTouchListener { v, _ ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }

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
                            activity?.let { act ->
                                val controller = WindowCompat.getInsetsController(act.window, view)
                                controller.hide(WindowInsetsCompat.Type.navigationBars())
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        }

                        override fun onHideCustomView() {
                            if (container.childCount > 1) {
                                container.removeViewAt(container.childCount - 1)
                            }
                            visibility = View.VISIBLE
                            activity?.let { act ->
                                WindowCompat.getInsetsController(act.window, act.window.decorView)
                                    .show(WindowInsetsCompat.Type.navigationBars())
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view.evaluateJavascript(SPOOF_VISIBILITY_JS, null)
                            view.evaluateJavascript(SPOOF_VIEWPORT_JS, null)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(SPOOF_VISIBILITY_JS, null)
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
                activity?.let { act ->
                    WindowCompat.getInsetsController(act.window, act.window.decorView)
                        .show(WindowInsetsCompat.Type.navigationBars())
                }
                (container as? FrameLayout)?.let { fl ->
                    for (i in fl.childCount - 1 downTo 0) fl.removeViewAt(i)
                }
                webView?.stopLoading()
                webView?.destroy()
            }
        )

        // dmt's transport controls relay in here as a one-shot, deterministic
        // command — always "make it Play" / "make it Pause" / "seek to this
        // fraction" rather than an ambiguous toggle — so a tap always lands
        // exactly where dmt asked instead of racing the page's own state.
        LaunchedEffect(keyEvent, watchPageReady) {
            if (watchPageReady && keyEvent != null) {
                webView?.evaluateJavascript(commandJs(keyEvent.key), null)
            }
        }

        // Polls the real YouTube video's current time/duration back out so
        // dmt's own slider tracks what's actually on screen, instead of the
        // muted internal track running behind it. Also keeps nudging the
        // WebView to stay resumed and keeps re-asserting the visibility
        // spoof, since YouTube's player can re-attach its own
        // visibility/focus listeners after internal state changes (e.g. an
        // ad transition or a quality/player reload).
        LaunchedEffect(watchPageReady) {
            while (watchPageReady) {
                webView?.let { wv ->
                    wv.onResume()
                    wv.resumeTimers()
                    wv.evaluateJavascript(SPOOF_VISIBILITY_JS, null)
                    wv.evaluateJavascript(GET_PROGRESS_JS) { raw ->
                        val cleaned = raw?.trim('"')?.takeIf { it.isNotEmpty() && it != "null" }
                        val parts = cleaned?.split("|")
                        val posSec = parts?.getOrNull(0)?.toDoubleOrNull()
                        val durSec = parts?.getOrNull(1)?.toDoubleOrNull()
                        if (posSec != null && durSec != null && durSec > 0) {
                            onProgress((posSec * 1000).toLong(), (durSec * 1000).toLong())
                        }
                    }
                }
                delay(500)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}


private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

/** Reports a viewport matching the preview box's own 1095x657 ratio to the
 * page's own JS, so YouTube (and its fullscreen player) lays itself out
 * against that exact size instead of the device's real, differently-shaped
 * screen. Wrapped defensively since some of these properties may already
 * be non-configurable on a given WebView build. */
/** Makes the page believe it's always visible and focused, so YouTube's own
 * player JS — which listens for the Page Visibility API and focus/blur to
 * decide whether to pause playback — never sees a reason to pause on its
 * own. This is what lets the video keep playing once the WebView's window
 * itself is genuinely backgrounded/off-screen (dmt's own audio pipeline
 * takes over for actual sound at that point, but the underlying page never
 * needs to know that to keep ticking along). Installed at document-start
 * (before YouTube's own scripts run) so the very first listener it attaches
 * already reads the spoofed values, and reapplied on every poll tick in
 * case the player re-attaches its own listeners later (e.g. after an ad
 * break or a quality change). */
private const val SPOOF_VISIBILITY_JS = """
(function() {
  try {
    if (!window.__dmtVisibilitySpoofed) {
      window.__dmtVisibilitySpoofed = true;
      Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
      Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
      Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
      Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
      document.hasFocus = function() { return true; };

      // Swallow the events YouTube's player listens to for pausing on
      // "hide"/"blur" before they reach any of its own listeners, by
      // registering ours first and in the capture phase.
      ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide', 'freeze'].forEach(function(type) {
        window.addEventListener(type, function(e) {
          e.stopImmediatePropagation();
        }, true);
        document.addEventListener(type, function(e) {
          e.stopImmediatePropagation();
        }, true);
      });

      // Some player code checks window focus directly rather than
      // document.hasFocus(); report a permanently-focused window too.
      window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
    }
  } catch (e) {}
})();
"""

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
    // YouTube's player only recalculates its own control layout (e.g.
    // whether the fullscreen button fits directly or gets collapsed
    // behind the overflow chevron) in response to an actual resize
    // event — without dispatching one here, it can keep using whatever
    // layout it assumed on initial load instead of the spoofed size.
    window.dispatchEvent(new Event('resize'));
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
 * playback, then engages fullscreen the same way a real tap on the button
 * would. YouTube only renders/shows its `.ytp-chrome-bottom` control bar
 * (and lays out the buttons inside it) once it sees mouse/hover activity
 * over the player — without that, `.ytp-right-controls`, the overflow
 * chevron, and the fullscreen button are either absent or not yet laid
 * out, which is exactly why a manual tap was "needed" before: a tap
 * doesn't just click, it also wakes this hover state as a side effect.
 * [wakeControls] reproduces that half — a synthetic mouseenter/mousemove
 * over the player — without the click that pauses playback, so it's
 * dispatched on every pass to keep the control bar awake for as long as
 * the search for the overflow/fullscreen buttons takes. On a narrow
 * player like this preview box, YouTube also reports fullscreen as
 * unavailable and collapses the right-side controls behind that
 * left-pointing chevron ("more controls") until it's expanded at least
 * once, so the overflow toggle is clicked first, then the now-revealed
 * fullscreen button itself is clicked — calling requestFullscreen()
 * directly from a timer doesn't carry the same real user-gesture a
 * button click does, so the Fullscreen API silently no-ops there. */
private const val PLAY_AND_FULLSCREEN_JS = """
(function() {
  function visible(el) {
    return !!el && !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  }
  function findOverflowToggle(root) {
    var candidates = root.querySelectorAll('button, .ytp-button');
    for (var i = 0; i < candidates.length; i++) {
      var b = candidates[i];
      var label = ((b.getAttribute('aria-label') || '') + ' ' + (b.getAttribute('title') || '') + ' ' + (b.className || '')).toLowerCase();
      if (label.indexOf('more') !== -1 || label.indexOf('expand') !== -1 ||
          label.indexOf('overflow') !== -1 || label.indexOf('chevron') !== -1 ||
          label.indexOf('arrow') !== -1) {
        return b;
      }
    }
    return null;
  }
  function isFullscreen(video) {
    return !!document.fullscreenElement || !!video.webkitDisplayingFullscreen;
  }
  function wakeControls(player) {
    try { window.dispatchEvent(new Event('resize')); } catch (e) {}
    ['mouseenter', 'mouseover', 'mousemove'].forEach(function(type) {
      try {
        var evt = new MouseEvent(type, {
          bubbles: true, cancelable: true, view: window, clientX: 1, clientY: 1
        });
        player.dispatchEvent(evt);
      } catch (e) {}
    });
    player.classList.remove('ytp-autohide');
    var bottom = player.querySelector('.ytp-chrome-bottom');
    if (bottom) { bottom.classList.remove('ytp-autohide'); }
  }

  var readyTries = 0;
  var readyTimer = setInterval(function() {
    readyTries++;
    var player = document.querySelector('#movie_player');
    var video = document.querySelector('video');
    if (player && video) {
      clearInterval(readyTimer);
      video.muted = false;
      video.play().catch(function() {});

      var overflowClicked = false;
      var fsTries = 0;
      var fsTimer = setInterval(function() {
        fsTries++;
        wakeControls(player);

        if (isFullscreen(video)) {
          clearInterval(fsTimer);
          return;
        }

        var rightControls = player.querySelector('.ytp-right-controls') || player;
        var fsBtn = rightControls.querySelector('.ytp-fullscreen-button');

        if (fsBtn && visible(fsBtn)) {
          fsBtn.click();
        } else if (!overflowClicked) {
          var overflow = findOverflowToggle(rightControls) || findOverflowToggle(player);
          if (overflow && visible(overflow)) {
            overflow.click();
            overflowClicked = true;
          }
        } else {
          // Overflow was already clicked but the fullscreen button still
          // isn't visible/found yet — allow another overflow click in
          // case the first one toggled it back closed instead of open.
          overflowClicked = false;
        }
        // Keeps going indefinitely until fullscreen actually engages —
        // no try cap, so it always keeps clicking whatever's needed
        // (overflow or fullscreen button) no matter how long the page
        // takes to settle.
      }, 400);
    } else if (readyTries > 30) {
      clearInterval(readyTimer);
    }
  }, 300);
})();
"""

/** Reads back the real video's current time and duration (in seconds,
 * `currentTime|duration`) so dmt's own slider can be kept in sync with
 * what's actually playing on screen. */
private const val GET_PROGRESS_JS = """
(function() {
  var video = document.querySelector('video');
  if (!video || !isFinite(video.duration) || video.duration <= 0) { return ''; }
  return video.currentTime + '|' + video.duration;
})();
"""

/** Builds the JS for a one-shot transport command relayed in from dmt's own
 * controls. Play/Pause/Seek act directly on the <video> element so the
 * result is deterministic no matter what state the page thinks it's in —
 * retried briefly in case the element isn't available at the exact instant
 * the command arrives — and a simulated keypress is dispatched alongside
 * each so YouTube's own on-screen feedback (the seek flash, the pause
 * icon) still shows, but it's never load-bearing for the actual change. */
private fun commandJs(key: String): String {
    val isSeek = key.startsWith(KEY_SEEK_PREFIX)
    val fraction = if (isSeek) key.removePrefix(KEY_SEEK_PREFIX).toFloatOrNull() ?: 0f else 0f

    val (code, keyCode, keyValue) = when {
        isSeek -> Triple("ArrowRight", 39, "ArrowRight")
        key == "ArrowRight" -> Triple("ArrowRight", 39, "ArrowRight")
        key == "ArrowLeft" -> Triple("ArrowLeft", 37, "ArrowLeft")
        else -> Triple("Space", 32, " ")
    }

    val action = when {
        isSeek -> "if (isFinite(video.duration) && video.duration > 0) { video.currentTime = $fraction * video.duration; }"
        key == "Play" -> "video.play().catch(function() {});"
        key == "Pause" -> "video.pause();"
        key == "ArrowRight" -> "video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 5);"
        key == "ArrowLeft" -> "video.currentTime = Math.max(0, video.currentTime - 5);"
        else -> ""
    }

    return """
(function() {
  var tries = 0;
  var timer = setInterval(function() {
    tries++;
    var player = document.querySelector('#movie_player');
    var video = document.querySelector('video');
    if (video) {
      clearInterval(timer);
      if (player && player.focus) { player.focus(); }
      ['keydown', 'keyup'].forEach(function(type) {
        var evt = new KeyboardEvent(type, {
          key: '$keyValue', code: '$code', keyCode: $keyCode, which: $keyCode, bubbles: true
        });
        document.dispatchEvent(evt);
        if (player) { player.dispatchEvent(evt); }
      });
      $action
    } else if (tries > 10) {
      clearInterval(timer);
    }
  }, 150);
})();
"""
}
