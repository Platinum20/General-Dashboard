package com.platinum20.dashboard.sim

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.platinum20.dashboard.media.browserUserAgent
import com.platinum20.dashboard.net.WebSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Il collegamento con l'area clienti WindTre. Non c'è un'API pubblica: Canvas apre l'area clienti
 * (visibile solo per l'accesso, poi nascosta) e legge le risposte che la pagina stessa riceve dal
 * server di WindTre, più il testo della pagina. La password non passa mai da Canvas.
 */
object WindTreLink {
    const val HOME = "https://ac.windtre.it/"
    /** Il tempo lasciato all'area clienti per caricare i contatori (è un'app web pesante). */
    private const val READ_MS = 25_000L

    /** Intercetta fetch e XMLHttpRequest della pagina e passa a Canvas le risposte JSON di WindTre. */
    private val script = """
        (function () {
          if (window.__canvasSim) return; window.__canvasSim = true;
          function mine(u) { u = String(u || ''); return u.indexOf('://') < 0 || /windtre\.it/.test(u); }
          function send(u, t) {
            try { if (t && t.length < 2000000 && /^\s*[\[{]/.test(t)) CanvasSim.capture(String(u), t); } catch (e) {}
          }
          var f = window.fetch;
          if (f) window.fetch = function () {
            var a = arguments;
            return f.apply(this, a).then(function (r) {
              try { var u = r.url || (a[0] && a[0].url) || a[0]; if (mine(u)) r.clone().text().then(function (t) { send(u, t); }); } catch (e) {}
              return r;
            });
          };
          var open = XMLHttpRequest.prototype.open, sendX = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function (m, u) { this.__u = u; return open.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function () {
            var x = this;
            x.addEventListener('load', function () {
              try {
                var u = x.responseURL || x.__u;
                if (!mine(u)) return;
                if (x.responseType === '' || x.responseType === 'text') send(u, x.responseText);
                else if (x.responseType === 'json') send(u, JSON.stringify(x.response));
              } catch (e) {}
            });
            return sendX.apply(this, arguments);
          };
        })();
    """.trimIndent() + "\n" + WebSession.snapshotScript("CanvasSim")

    private const val READ_TEXT = "(function(){try{CanvasSim.text(document.body?document.body.innerText:'')}catch(e){}})()"

    private object Bridge {
        @JavascriptInterface
        fun capture(url: String, body: String) = SimStore.capture(url, body)

        @JavascriptInterface
        fun text(text: String) = SimStore.pageText(text)

        @JavascriptInterface
        fun storage(origin: String, json: String) = WindTreSession.offer(origin, json)
    }

    /** Prepara una WebView (quella dell'accesso o quella nascosta) a passare i dati a Canvas. */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun prepare(view: WebView, context: Context, onPage: (String) -> Unit = {}) {
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
        CookieManager.getInstance().setAcceptCookie(true)
        // Come per Revolut: cookie di sessione e sessionStorage dell'ultima lettura buona tornano al loro posto.
        if (SimStore.linked.value) WindTreSession.restore(context)
        val keep = if (SimStore.linked.value) WindTreSession.storageScript(context) else null
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(Bridge, "CanvasSim")
        val early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (early) {
            keep?.let { WebViewCompat.addDocumentStartJavaScript(view, it, setOf("https://*.windtre.it")) }
            WebViewCompat.addDocumentStartJavaScript(view, script, setOf("https://*.windtre.it"))
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                // Solo l'indirizzo, mai il contenuto: dice quali chiamate fa la pagina.
                if (request.url.host?.endsWith("windtre.it") == true) SimStore.seen(request.url.toString())
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                // Senza lo script d'inizio pagina si aggancia appena può: perde solo le primissime richieste.
                if (!early) { keep?.let { view.evaluateJavascript(it, null) }; view.evaluateJavascript(script, null) }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!early) view.evaluateJavascript(script, null)
                onPage(url)
            }
        }
    }

    /** Legge il testo visibile della pagina (dopo che i contatori sono apparsi). */
    fun readText(view: WebView) = view.evaluateJavascript(READ_TEXT, null)

    /**
     * Aggiornamento in silenzio: apre l'area clienti in una WebView nascosta con la sessione già
     * fatta, aspetta che carichi i contatori e la chiude. False se la sessione è scaduta.
     */
    suspend fun refresh(context: Context): Boolean {
        if (!SimStore.linked.value || SimStore.refreshing.value) return false
        SimStore.setRefreshing(true)
        val before = SimStore.status.value?.at ?: 0
        try {
            withContext(Dispatchers.Main) {
                val view = WebView(context.applicationContext)
                prepare(view, context)
                SimStore.beginSession()
                view.loadUrl(HOME)
                withTimeoutOrNull(READ_MS) {
                    while ((SimStore.status.value?.at ?: 0) == before) delay(500)
                    // Arrivato il primo dato, ancora un attimo per gli altri contatori e il testo.
                    delay(4_000)
                }
                readText(view)
                delay(800)
                view.stopLoading()
                view.destroy()
            }
        } finally {
            SimStore.setRefreshing(false)
        }
        return (SimStore.status.value?.at ?: 0) > before
    }

    /** Toglie la sessione WindTre dal telefono. */
    fun forget(context: Context) {
        val cookies = CookieManager.getInstance()
        listOf("https://ac.windtre.it", "https://www.windtre.it", "https://apigw.windtre.it").forEach { url ->
            cookies.getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                cookies.setCookie(url, "$name=; Max-Age=0")
                cookies.setCookie(url, "$name=; Max-Age=0; Domain=.windtre.it; Path=/")
            }
        }
        cookies.flush()
        WindTreSession.clear(context)
        SimStore.unlink()
    }
}
