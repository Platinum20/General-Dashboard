package com.platinum20.dashboard.shifts

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
 * Il collegamento con Creators Pro, dove l'agenzia mette i turni. Nessuna API per i turni: Canvas
 * apre il sito (visibile solo per l'accesso, poi nascosto sulla timeline) e legge le risposte che la
 * pagina riceve, come per WindTre e Revolut. Solo lettura; la password non passa da Canvas.
 */
object CreatorsLink {
    const val HOME = "https://creatorspro.com/"
    /** La timeline è un'app web: il tempo per caricare i turni della settimana. */
    private const val READ_MS = 30_000L

    /** Intercetta fetch e XMLHttpRequest della pagina e passa a Canvas le risposte JSON (anche da altri domini: l'API può stare altrove). */
    private val script = """
        (function () {
          if (window.__canvasShift) return; window.__canvasShift = true;
          function send(u, s, t) {
            try { if (t && t.length < 3000000 && /^\s*[\[{]/.test(t)) CanvasShift.capture(String(u), s, t); else if (s == 401) CanvasShift.capture(String(u), s, ''); } catch (e) {}
          }
          var f = window.fetch;
          if (f) window.fetch = function () {
            var a = arguments;
            return f.apply(this, a).then(function (r) {
              try { var u = r.url || (a[0] && a[0].url) || a[0]; r.clone().text().then(function (t) { send(u, r.status, t); }); } catch (e) {}
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
                if (x.responseType === '' || x.responseType === 'text') send(u, x.status, x.responseText);
                else if (x.responseType === 'json') send(u, x.status, JSON.stringify(x.response));
              } catch (e) {}
            });
            return sendX.apply(this, arguments);
          };
          // Le app che parlano in tempo reale (WebSocket): un turno messo adesso arriva anche da qui.
          var WS = window.WebSocket;
          if (WS) {
            var Hooked = function (u, p) {
              var w = p === undefined ? new WS(u) : new WS(u, p);
              w.addEventListener('message', function (ev) { if (typeof ev.data === 'string') send(u, 200, ev.data.replace(/^\d+/, '')); });
              return w;
            };
            Hooked.prototype = WS.prototype;
            Hooked.CONNECTING = 0; Hooked.OPEN = 1; Hooked.CLOSING = 2; Hooked.CLOSED = 3;
            window.WebSocket = Hooked;
          }
        })();
    """.trimIndent()

    private object Bridge {
        @JavascriptInterface
        fun capture(url: String, status: Int, body: String) = ShiftStore.capture(url, status, body)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun prepare(view: WebView, context: Context, onPage: (String) -> Unit = {}) {
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(Bridge, "CanvasShift")
        val early = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (early) WebViewCompat.addDocumentStartJavaScript(view, script, setOf("https://*.creatorspro.com", "https://creatorspro.com"))
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                ShiftStore.seen(request.url.toString())
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (!early) view.evaluateJavascript(script, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!early) view.evaluateJavascript(script, null)
                ShiftStore.visited(url)
                onPage(url)
            }

            // App web a pagina unica: la timeline si apre senza ricaricare.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                ShiftStore.visited(url)
                onPage(url)
            }
        }
    }

    fun isLoginPage(url: String?) = url != null && Regex("(?i)/(login|signin|sign-in|auth|register)").containsMatchIn(url)

    /** Rilettura nascosta: riapre la timeline con la sessione già fatta. False se non è arrivato niente. */
    suspend fun refresh(context: Context): Boolean {
        ShiftStore.init(context)
        if (!ShiftStore.linked.value || ShiftStore.refreshing.value) return false
        ShiftStore.setRefreshing(true)
        val before = ShiftStore.readAt.value
        try {
            withContext(Dispatchers.Main) {
                val view = WebView(context.applicationContext)
                var lastUrl: String? = null
                prepare(view, context) { lastUrl = it }
                view.loadUrl(ShiftStore.timelineUrl ?: HOME)
                withTimeoutOrNull(READ_MS) {
                    while (ShiftStore.readAt.value == before && !isLoginPage(lastUrl)) delay(400)
                    // Arrivata la prima risposta coi turni: ancora un attimo per le altre (altre settimane, righe).
                    if (ShiftStore.readAt.value != before) delay(3_000)
                }
                if (ShiftStore.readAt.value == before && isLoginPage(lastUrl ?: view.url)) ShiftStore.setExpired(true)
                CookieManager.getInstance().flush()
                view.stopLoading()
                view.destroy()
            }
        } finally {
            ShiftStore.setRefreshing(false)
        }
        return ShiftStore.readAt.value > before
    }

    fun forget() {
        val cookies = CookieManager.getInstance()
        listOf("https://creatorspro.com", "https://app.creatorspro.com", "https://www.creatorspro.com").forEach { url ->
            cookies.getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                cookies.setCookie(url, "$name=; Max-Age=0")
                cookies.setCookie(url, "$name=; Max-Age=0; Domain=.creatorspro.com; Path=/")
            }
        }
        cookies.flush()
        runCatching { android.webkit.WebStorage.getInstance().deleteOrigin("https://creatorspro.com") }
        ShiftStore.unlink()
    }
}
