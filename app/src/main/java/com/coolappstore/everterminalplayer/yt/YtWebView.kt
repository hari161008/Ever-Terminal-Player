package com.coolappstore.everterminalplayer.yt

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.innertube.BotGuard
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

private class DmtYtBridge(
    private val decipherRequests: ConcurrentHashMap<String, CompletableDeferred<String>>
) {
    @JavascriptInterface
    fun onDecipherResult(requestId: String, result: String) {
        decipherRequests.remove(requestId)?.complete(result)
    }
}

/**
 * A near-invisible WebView that loads music.youtube.com purely to extract a
 * visitor id and stand up an n-parameter decipher function, exactly like
 * soundpod does. This is what lets [YtRepository] search and resolve real,
 * playable audio URLs from YouTube.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtWebView() {
    val decipherRequests = remember { ConcurrentHashMap<String, CompletableDeferred<String>>() }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"

                addJavascriptInterface(DmtYtBridge(decipherRequests), "DmtYtBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        CookieManager.getInstance().flush()
                        val cookies = CookieManager.getInstance().getCookie(url)

                        val extractVisitorDataJs = """
                            (function() {
                                return window.yt?.config_?.VISITOR_DATA ||
                                       (window.ytcfg && ytcfg.get ? ytcfg.get('VISITOR_DATA') : null);
                            })();
                        """.trimIndent()

                        view.evaluateJavascript(extractVisitorDataJs) { visitorData ->
                            val clean = visitorData?.replace("\"", "")
                            if (!clean.isNullOrBlank() && clean != "null") {
                                YtSession.update(
                                    visitorData = clean,
                                    cookies = cookies,
                                    decipher = { nParam ->
                                        val deferred = CompletableDeferred<String>()
                                        val requestId = "${System.currentTimeMillis()}_$nParam"
                                        decipherRequests[requestId] = deferred

                                        val invokeJs = """
                                            if (typeof decipherNParam === 'function') {
                                                decipherNParam('$nParam', '$requestId');
                                            } else {
                                                DmtYtBridge.onDecipherResult('$requestId', '$nParam');
                                            }
                                        """.trimIndent()

                                        view.post { view.evaluateJavascript(invokeJs, null) }
                                        deferred.await()
                                    }
                                )
                            }
                        }

                        runCatching { view.evaluateJavascript(BotGuard.HTML, null) }
                            .onFailure { Log.e("DmtYtWebView", "botguard inject failed", it) }

                        injectDecipherScript(view)
                    }
                }

                loadUrl("https://music.youtube.com")
            }
        },
        onRelease = { webView ->
            decipherRequests.values.forEach { it.cancel() }
            decipherRequests.clear()
            webView.removeJavascriptInterface("DmtYtBridge")
            webView.stopLoading()
            webView.destroy()
        }
    )
}

private fun injectDecipherScript(webView: WebView) {
    val script = """
        function decipherNParam(n, requestId) {
            let result = n;
            if (window.decipherFunction) {
                try { result = window.decipherFunction(n); } catch (e) { console.error(e); }
            }
            DmtYtBridge.onDecipherResult(requestId, result);
        }
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}
