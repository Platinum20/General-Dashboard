package com.platinum20.dashboard.net

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scarica un indirizzo passando da un WebView invisibile, cioè con lo stesso motore di Chrome.
 *
 * Alcuni siti (per esempio Il Post) rispondono 403 alle richieste dirette dell'app perché le
 * scambiano per un bot, ma lasciano passare un browser vero. Qui apriamo la home del sito nel
 * WebView e poi chiediamo il feed o l'articolo con fetch() dalla pagina stessa: stessi cookie,
 * stesso browser. Un solo WebView per tutta l'app, una richiesta alla volta.
 */
object WebFetcher {

    private const val TIMEOUT_MS = 30_000L
    /** Tempo lasciato al sito dopo il caricamento della home, per eventuali controlli anti-bot. */
    private const val SETTLE_MS = 2_000L

    private var appContext: Context? = null
    private val mutex = Mutex()
    private var webView: WebView? = null
    private var readyOrigin: String? = null
    private var pageLoaded: CompletableDeferred<Unit>? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Result<String>>>()
    private val ids = AtomicInteger()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Il testo della risposta, oppure null se anche il browser non riesce a scaricarlo. */
    suspend fun fetchText(url: String): String? {
        val context = appContext ?: return null
        val uri = Uri.parse(url)
        val origin = "${uri.scheme}://${uri.host}"
        return mutex.withLock {
            withTimeoutOrNull(TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    val view = webView ?: createWebView(context).also { webView = it }
                    if (readyOrigin != origin) {
                        openHome(view, origin)
                        readyOrigin = origin
                    }
                    var result = jsFetch(view, url)
                    if (result.isFailure) {
                        // Prima volta su questo sito: il controllo anti-bot può richiedere qualche secondo in più.
                        delay(SETTLE_MS * 2)
                        result = jsFetch(view, url)
                    }
                    result.getOrNull()
                }
            }
        }
    }

    private suspend fun openHome(view: WebView, origin: String) {
        val loaded = CompletableDeferred<Unit>()
        pageLoaded = loaded
        view.loadUrl("$origin/")
        withTimeoutOrNull(TIMEOUT_MS / 2) { loaded.await() }
        delay(SETTLE_MS)
    }

    private suspend fun jsFetch(view: WebView, url: String): Result<String> {
        val id = ids.incrementAndGet()
        val deferred = CompletableDeferred<Result<String>>()
        pending[id] = deferred
        val script = """
            fetch(${JSONObject.quote(url)}, { credentials: 'include' })
              .then(function (r) { return r.ok ? r.text() : Promise.reject('HTTP ' + r.status); })
              .then(function (t) { GDFetch.done($id, true, t); })
              .catch(function (e) { GDFetch.done($id, false, String(e)); });
        """.trimIndent()
        view.evaluateJavascript(script, null)
        return try {
            withTimeoutOrNull(TIMEOUT_MS / 2) { deferred.await() } ?: Result.failure(Exception("timeout"))
        } finally {
            pending.remove(id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = true
        addJavascriptInterface(Bridge, "GDFetch")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded?.complete(Unit)
            }
        }
    }

    private object Bridge {
        @JavascriptInterface
        fun done(id: Int, ok: Boolean, body: String) {
            pending[id]?.complete(if (ok) Result.success(body) else Result.failure(Exception(body)))
        }
    }
}
