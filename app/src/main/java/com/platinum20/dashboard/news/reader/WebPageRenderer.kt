package com.platinum20.dashboard.news.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Carica la pagina in un WebView invisibile, come un browser vero, e restituisce l'HTML
 * dopo che il JavaScript del sito ha composto l'articolo. Serve per i siti che nell'HTML
 * scaricato direttamente non hanno il testo. Usa gli stessi cookie della pagina originale.
 */
object WebPageRenderer {

    private const val TIMEOUT_MS = 20_000L
    /** Dopo il caricamento lasciamo un attimo al sito per finire di comporre la pagina. */
    private const val SETTLE_MS = 1_500L

    suspend fun render(context: Context, url: String): FetchedPage? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<FetchedPage?> { cont ->
                    val view = createWebView(context.applicationContext)
                    webView = view
                    val handler = Handler(Looper.getMainLooper())
                    var done = false
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, finishedUrl: String) {
                            if (done) return
                            handler.postDelayed({
                                if (done) return@postDelayed
                                done = true
                                // Scorre la pagina per far caricare le immagini "pigre", poi legge il DOM.
                                view.evaluateJavascript(
                                    "window.scrollTo(0, document.body.scrollHeight); document.documentElement.outerHTML",
                                ) { result ->
                                    val html = decodeJsString(result)
                                    if (cont.isActive) {
                                        cont.resume(html?.let { FetchedPage(view.url ?: finishedUrl, it) })
                                    }
                                }
                            }, SETTLE_MS)
                        }
                    }
                    view.loadUrl(url)
                    cont.invokeOnCancellation { done = true }
                }
            }
        } finally {
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptCookie(true)
        // Senza finestra il WebView ha dimensione zero: gli diamo quella di un telefono.
        measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, 1080, 2200)
    }

    /** evaluateJavascript restituisce la stringa in formato JSON (tra virgolette, con escape). */
    private fun decodeJsString(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        return runCatching { JSONArray("[$result]").getString(0) }.getOrNull()
    }

    /** Cookie del WebView per un indirizzo, da riusare nei download diretti. */
    fun cookiesFor(url: String): String? = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
}
