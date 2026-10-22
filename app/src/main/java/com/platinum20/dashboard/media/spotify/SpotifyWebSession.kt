package com.platinum20.dashboard.media.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.desktopUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Spotify senza creare un'app per sviluppatori: con l'accesso fatto nell'app, il web player
 * ottiene da sé un token per le API. Lo leggiamo da una WebView invisibile del web player e lo
 * usiamo per le stesse chiamate del collegamento ufficiale. Dura circa un'ora, poi si rinnova.
 */
object SpotifyWebSession {
    private const val LIFETIME = 45 * 60_000L
    private val lock = Mutex()
    private var token: String? = null
    private var obtainedAt = 0L

    fun available(): Boolean = Accounts.isLoggedIn(MediaService.SPOTIFY)

    fun clear() {
        token = null
    }

    suspend fun token(context: Context, forceRefresh: Boolean = false): String? = lock.withLock {
        val cached = token
        if (!forceRefresh && cached != null && System.currentTimeMillis() - obtainedAt < LIFETIME) return cached
        if (!available()) return null
        val fresh = withTimeoutOrNull(30_000) { withContext(Dispatchers.Main) { capture(context.applicationContext) } }
        token = fresh
        obtainedAt = System.currentTimeMillis()
        fresh
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun capture(context: Context): String? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val webView = WebView(context)
        var done = false
        fun finish(result: String?) {
            if (done) return
            done = true
            webView.stopLoading()
            webView.destroy()
            if (cont.isActive) cont.resume(result)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            userAgentString = desktopUserAgent(userAgentString)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            // Il web player manda il suo token nelle richieste ai server di Spotify: è quello che ci serve.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host.orEmpty()
                if (host.endsWith("spotify.com")) {
                    val auth = request.requestHeaders.entries
                        .firstOrNull { it.key.equals("authorization", ignoreCase = true) }?.value
                    if (auth != null && auth.startsWith("Bearer ", ignoreCase = true)) {
                        val value = auth.substring(7).trim()
                        if (value.isNotEmpty()) handler.post { finish(value) }
                    }
                }
                return null
            }
        }
        cont.invokeOnCancellation { handler.post { finish(null) } }
        webView.loadUrl("https://open.spotify.com/")
    }
}
