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
import com.platinum20.dashboard.net.AutoLogin
import com.platinum20.dashboard.net.WebSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    /** Se nel frattempo ha dovuto rifare l'accesso: altro tempo per entrare e caricare. */
    private const val RELOGIN_MS = 90_000L

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

        @JavascriptInterface
        fun typed(user: String, pass: String) = WindTreAuto.typed(user, pass)

        @JavascriptInterface
        fun loginSeen() = WindTreAuto.seen()

        @JavascriptInterface
        fun mayFill(): Boolean = WindTreAuto.mayFill()

        @JavascriptInterface
        fun codeSeen() {
            if (!WindTreAuto.codeSeen()) return
            val app = appContext ?: return
            val since = WindTreAuto.codeAskedAt
            mailScope.launch { WindTreCode.await(app, since)?.let { WindTreAuto.code = it } }
        }

        @JavascriptInterface
        fun code(): String = WindTreAuto.code.orEmpty()
    }

    @Volatile private var appContext: Context? = null
    private val mailScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Prepara una WebView (quella dell'accesso o quella nascosta) a passare i dati a Canvas. */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun prepare(view: WebView, context: Context, login: Boolean = false, onPage: (String) -> Unit = {}) {
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
        CookieManager.getInstance().setAcceptCookie(true)
        // Come per Creators Pro: cookie di sessione e sessionStorage dell'ultima lettura buona tornano al loro posto.
        if (SimStore.linked.value) WindTreSession.restore(context)
        val keep = if (SimStore.linked.value) WindTreSession.storageScript(context) else null
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(Bridge, "CanvasSim")
        // Accesso visibile: si tiene da parte quello che scrivi. Rilettura nascosta: si rientra da soli se serve.
        appContext = context.applicationContext
        val auto = (if (login) AutoLogin.captureScript("CanvasSim")
            else WindTreAuto.saved(context).let { AutoLogin.fillScript("CanvasSim", it?.first.orEmpty(), it?.second.orEmpty()) }) +
            // Il codice di conferma per mail: lo scrive Canvas, anche nell'accesso visibile.
            "\n" + AutoLogin.codeScript("CanvasSim")
        val early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (early) {
            keep?.let { WebViewCompat.addDocumentStartJavaScript(view, it, setOf("https://*.windtre.it")) }
            WebViewCompat.addDocumentStartJavaScript(view, script, setOf("https://*.windtre.it"))
            WebViewCompat.addDocumentStartJavaScript(view, auto, setOf("https://*.windtre.it"))
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
                if (!early) { view.evaluateJavascript(script, null); view.evaluateJavascript(auto, null) }
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
        val asked = System.currentTimeMillis()
        var lastUrl: String? = null
        WindTreAuto.begin()
        try {
            withContext(Dispatchers.Main) {
                val view = WebView(context.applicationContext)
                prepare(view, context) { lastUrl = it }
                // Nessuno la guarda: niente immagini da scaricare (meno rete e batteria a ogni giro).
                view.settings.blockNetworkImage = true
                SimStore.beginSession()
                view.loadUrl(HOME)
                val read = withTimeoutOrNull(READ_MS) { waitData(before) }
                // Ha trovato il modulo d'accesso e l'ha compilato: si aspetta che entri e carichi.
                if (read == null && (WindTreAuto.loginSeenAt >= asked && WindTreAuto.hasSaved(context) || WindTreAuto.codeAskedAt >= asked)) {
                    withTimeoutOrNull(RELOGIN_MS) { waitData(before) }
                }
                readText(view)
                delay(800)
                view.stopLoading()
                view.destroy()
            }
        } finally {
            SimStore.setRefreshing(false)
        }
        val got = (SimStore.status.value?.at ?: 0) > before
        if (!got && sessionGone(WindTreAuto.loginSeenAt >= asked || WindTreAuto.codeAskedAt >= asked, lastUrl)) SimStore.setExpired(true)
        return got
    }

    private suspend fun waitData(before: Long) {
        while ((SimStore.status.value?.at ?: 0) == before) delay(500)
        // Arrivato il primo dato, ancora un attimo per gli altri contatori e il testo.
        delay(4_000)
    }

    /** Fuori dall'area clienti: il modulo d'accesso si è visto, o la pagina è finita su un indirizzo d'accesso. */
    internal fun sessionGone(loginSeen: Boolean, lastUrl: String?) =
        loginSeen || (lastUrl != null && Regex("(?i)login|/accedi|signin|/auth").containsMatchIn(lastUrl))

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
        WindTreAuto.clear(context)
        SimStore.unlink()
    }
}
