package com.platinum20.dashboard.bank

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Il collegamento col conto Revolut. Non c'è un'API per i privati: Canvas apre l'app web ufficiale
 * (visibile solo per l'accesso, che Antonio conferma nell'app Revolut) e legge le risposte che la
 * pagina riceve dal server, più una lettura dei movimenti fatta con la stessa sessione. Solo
 * lettura: nessuna richiesta che non sia GET, nessun pagamento, il codice non passa da Canvas.
 */
object RevolutLink {
    const val HOME = "https://app.revolut.com/home"
    private const val READ_MS = 30_000L

    /** Intercetta fetch e XMLHttpRequest dell'app web; tiene le intestazioni delle sue chiamate API per rileggerle. */
    private val script = """
        (function () {
          if (window.__canvasBank) return; window.__canvasBank = true;
          function mine(u) { u = String(u || ''); return u.indexOf('://') < 0 || /revolut\.com/.test(u); }
          function send(u, s, t) {
            try { if (t && t.length < 3000000 && /^\s*[\[{]/.test(t)) CanvasBank.capture(String(u), s, t); else if (s == 401) CanvasBank.capture(String(u), s, ''); } catch (e) {}
          }
          function keep(u, h) {
            try {
              if (!/\/api\//.test(String(u)) || !h) return;
              var o = {};
              if (typeof h.forEach === 'function' && !Array.isArray(h)) h.forEach(function (v, k) { o[k] = v; });
              else if (Array.isArray(h)) h.forEach(function (p) { o[p[0]] = p[1]; });
              else for (var k in h) o[k] = h[k];
              delete o['content-type']; delete o['Content-Type'];
              if (Object.keys(o).length) window.__cvHeaders = o;
            } catch (e) {}
          }
          var f = window.fetch;
          if (f) window.fetch = function () {
            var a = arguments;
            try { var req = a[0]; keep(req && req.url ? req.url : req, (a[1] && a[1].headers) || (req && req.headers)); } catch (e) {}
            return f.apply(this, a).then(function (r) {
              try { var u = r.url || (a[0] && a[0].url) || a[0]; if (mine(u)) r.clone().text().then(function (t) { send(u, r.status, t); }); } catch (e) {}
              return r;
            });
          };
          var open = XMLHttpRequest.prototype.open, sendX = XMLHttpRequest.prototype.send, setH = XMLHttpRequest.prototype.setRequestHeader;
          XMLHttpRequest.prototype.open = function (m, u) { this.__u = u; this.__h = {}; return open.apply(this, arguments); };
          XMLHttpRequest.prototype.setRequestHeader = function (k, v) { try { this.__h[k] = v; } catch (e) {} return setH.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function () {
            var x = this;
            keep(x.__u, x.__h);
            x.addEventListener('load', function () {
              try {
                var u = x.responseURL || x.__u;
                if (!mine(u)) return;
                if (x.responseType === '' || x.responseType === 'text') send(u, x.status, x.responseText);
                else if (x.responseType === 'json') send(u, x.status, JSON.stringify(x.response));
              } catch (e) {}
            });
            return sendX.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /**
     * Rilegge saldo e ultimi movimenti con la sessione della pagina: la home dell'app web ne carica
     * pochi, così il calendario ha anche quelli di inizio mese. Le risposte passano dal gancio sopra.
     */
    private const val PULL = """
        (function () {
          var h = window.__cvHeaders || {};
          ['/api/retail/user/current/wallet', '/api/retail/user/current/transactions/last?count=300'].forEach(function (p) {
            try { fetch(p, { method: 'GET', credentials: 'include', headers: h }); } catch (e) {}
          });
        })()
    """

    private object Bridge {
        @JavascriptInterface
        fun capture(url: String, status: Int, body: String) = RevolutStore.capture(url, status, body)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun prepare(view: WebView, context: Context, onPage: (String) -> Unit = {}) {
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(Bridge, "CanvasBank")
        val early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (early) WebViewCompat.addDocumentStartJavaScript(view, script, setOf("https://*.revolut.com"))
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                if (request.url.host?.endsWith("revolut.com") == true) RevolutStore.seen(request.url.toString())
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (!early) view.evaluateJavascript(script, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!early) view.evaluateJavascript(script, null)
                onPage(url)
            }

            // L'app web cambia pagina senza ricaricare (es. torna all'accesso): anche questo conta.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                onPage(url)
            }
        }
    }

    /** Chiede alla pagina (già entrata nel conto) saldo e movimenti. */
    fun pull(view: WebView) = view.evaluateJavascript(PULL, null)

    /** La pagina è tornata all'accesso: la sessione web non c'è più. */
    fun isLoginPage(url: String?) = url != null && Regex("""(?i)app\.revolut\.com/(start|login|signin|sign-in|auth)""").containsMatchIn(url)

    /**
     * Aggiornamento in silenzio: apre l'app web in una WebView nascosta con la sessione già fatta,
     * rilegge saldo e movimenti e la chiude. False se non è arrivato niente (sessione scaduta).
     */
    suspend fun refresh(context: Context): Boolean {
        RevolutStore.init(context)
        if (!RevolutStore.linked.value || RevolutStore.refreshing.value) return false
        RevolutStore.setRefreshing(true)
        val before = RevolutStore.readAt.value
        try {
            withContext(Dispatchers.Main) {
                val view = WebView(context.applicationContext)
                var lastUrl: String? = null
                prepare(view, context) { lastUrl = it }
                view.loadUrl(HOME)
                withTimeoutOrNull(READ_MS) {
                    // Prima la home della pagina (che porta con sé le intestazioni giuste), poi la rilettura.
                    while (lastUrl == null && RevolutStore.readAt.value == before) delay(300)
                    delay(4_000)
                    if (!isLoginPage(lastUrl ?: view.url)) {
                        pull(view)
                        delay(5_000)
                    }
                }
                if (RevolutStore.readAt.value == before && isLoginPage(lastUrl ?: view.url)) RevolutStore.setExpired(true)
                view.stopLoading()
                view.destroy()
            }
        } finally {
            RevolutStore.setRefreshing(false)
        }
        return RevolutStore.readAt.value > before
    }

    /** Toglie la sessione Revolut dal telefono. */
    fun forget() {
        val cookies = CookieManager.getInstance()
        listOf("https://app.revolut.com", "https://www.revolut.com", "https://revolut.com").forEach { url ->
            cookies.getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                cookies.setCookie(url, "$name=; Max-Age=0")
                cookies.setCookie(url, "$name=; Max-Age=0; Domain=.revolut.com; Path=/")
            }
        }
        cookies.flush()
        // Anche lo storage della pagina: l'app web tiene lì parte della sessione.
        runCatching { android.webkit.WebStorage.getInstance().deleteOrigin("https://app.revolut.com") }
        RevolutStore.unlink()
    }
}
