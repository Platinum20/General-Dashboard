package com.platinum20.dashboard.shifts

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.os.Message
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
 * pagina riceve, come per WindTre. Solo lettura; la password non passa da Canvas.
 */
object CreatorsLink {
    const val HOME = "https://app.creatorspro.com/"
    /** La timeline è un'app web: il tempo per caricare i turni della settimana. */
    private const val READ_MS = 60_000L

    /** Intercetta fetch e XMLHttpRequest della pagina e passa a Canvas le risposte JSON (anche da altri domini: l'API può stare altrove). */
    private val script = """
        (function () {
          if (window.__canvasShift) return; window.__canvasShift = true;
          function send(u, s, t) {
            try { if (t && t.length < 3000000 && /^\s*[\[{]/.test(t)) CanvasShift.capture(String(u), s, t); else if (s == 401) CanvasShift.capture(String(u), s, ''); } catch (e) {}
          }
          // Le intestazioni delle chiamate all'API (il token d'accesso): servono per rileggere i turni di tutte le creator.
          function keep(u, h) {
            try {
              if (!/api\.creatorspro\.com/.test(String(u)) || !h) return;
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
              try { var u = r.url || (a[0] && a[0].url) || a[0]; r.clone().text().then(function (t) { send(u, r.status, t); }); } catch (e) {}
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

    /**
     * La pagina mostra una creator alla volta, i turni di Antonio sono su tutte: con la sessione della
     * pagina si chiedono gli eventi di ogni creator per questo mese e il prossimo. La fine la dice [Bridge.pulled].
     */
    private fun pullScript(ranges: List<Pair<String, String>>): String {
        // L'anno della busta paga: da gennaio scorso, così a gennaio c'è ancora dicembre.
        val year = java.time.LocalDate.now().year
        val yearFrom = "${year - 1}-01-01T00:00:00.000Z"
        val yearTo = "$year-12-31T23:59:59.999Z"
        val list = ranges.joinToString(",") { "['${it.first}','${it.second}']" }
        return """
        (function () {
          var opt = { method: 'GET', credentials: 'include', headers: window.__cvHeaders || {} };
          var api = 'https://api.creatorspro.com/v1/timeline/', ranges = [$list];
          // Due parti (turni e busta paga): la fine si dice quando sono finite tutte e due.
          var parts = 2, good = false;
          function end(ok) { good = good || ok; if (--parts == 0) try { CanvasShift.pulled(good); } catch (e) {} }
          fetch(api + 'creators', opt).then(function (r) { return r.ok ? r.json() : null; }).then(function (j) {
            var cs = (j && j.data) || [];
            var left = cs.length * ranges.length;
            if (!left) return end(false);
            cs.forEach(function (c) {
              ranges.forEach(function (g) {
                fetch(api + 'events?startedAt=' + encodeURIComponent(g[0]) + '&endedAt=' + encodeURIComponent(g[1]) + '&creatorId=' + c.id, opt)
                  .catch(function () {}).then(function () { if (--left == 0) end(true); });
              });
            });
          }).catch(function () { end(false); });
          // La busta paga: chi è Antonio, i suoi mesi di quest'anno, i turni di ognuno (venduto, guadagno, percentuale).
          var wage = 'https://api.creatorspro.com/v1/sellers-wage/wages';
          fetch('https://api.creatorspro.com/v1/accounts/members', opt).then(function (r) { return r.ok ? r.json() : null; }).then(function (m) {
            var id = m && m.data && m.data.id;
            if (!id) return;
            return fetch(wage + '?startedAt=' + encodeURIComponent('$yearFrom') + '&endedAt=' + encodeURIComponent('$yearTo') + '&memberId=' + id, opt)
              .then(function (r) { return r.ok ? r.json() : null; }).then(function (w) {
                // Solo i mesi finiti da meno di due mesi: i vecchi non cambiano più.
                var since = Date.now() - 62 * 86400000;
                var ids = ((w && w.data) || []).filter(function (x) { return x && x.info && x.info.id && Date.parse(x.info.endedAt) > since; })
                  .map(function (x) { return x.info.id; });
                return Promise.all(ids.map(function (i) { return fetch(wage + '/' + i, opt).catch(function () {}); }));
              });
          }).catch(function () {}).then(function () { end(false); });
        })()
        """
    }

    /** Questo mese e il prossimo, coi confini a mezzanotte di qui come li chiede la pagina. */
    internal fun months(today: java.time.LocalDate, zone: java.time.ZoneId): List<Pair<String, String>> =
        (0L..1L).map { i ->
            val first = today.withDayOfMonth(1).plusMonths(i)
            val a = first.atStartOfDay(zone).toInstant()
            val b = first.plusMonths(1).atStartOfDay(zone).toInstant().minusMillis(1)
            a.toString() to b.toString()
        }

    /** Chiede alla pagina (già entrata) i turni di tutte le creator. */
    fun pull(view: WebView) =
        view.evaluateJavascript(pullScript(months(java.time.LocalDate.now(), java.time.ZoneId.systemDefault())), null)

    @Volatile var pullEndedAt = 0L
        private set

    private object Bridge {
        @JavascriptInterface
        fun pulled(ok: Boolean) { pullEndedAt = System.currentTimeMillis() }

        @JavascriptInterface
        fun capture(url: String, status: Int, body: String) = ShiftStore.capture(url, status, body)

        @JavascriptInterface
        fun text(text: String) = ShiftStore.pageText(text)
    }

    /**
     * L'accesso con Google apre una finestra a parte (popup) che poi parla con la pagina che l'ha
     * aperta. Una WebView senza finestre multiple la ignora e resta tutta bianca (Antonio, 1.377):
     * qui la finestra diventa una seconda WebView che lo schermo mostra sopra, e si chiude da sola.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun allowPopups(view: WebView, context: Context, popups: PopupHost) {
        view.settings.setSupportMultipleWindows(true)
        view.settings.javaScriptCanOpenWindowsAutomatically = true
        view.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(parent: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val child = WebView(parent.context)
                child.settings.javaScriptEnabled = true
                child.settings.domStorageEnabled = true
                child.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
                child.settings.setSupportMultipleWindows(true)
                child.settings.javaScriptCanOpenWindowsAutomatically = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(child, true)
                child.webViewClient = WebViewClient()
                child.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) = popups.close(window)
                }
                popups.open(child)
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) = popups.close(window)
        }
    }

    /** Chi mostra le finestre aperte dalla pagina (solo la schermata d'accesso: quella nascosta non ne apre). */
    interface PopupHost {
        fun open(view: WebView)
        fun close(view: WebView)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "RequiresFeature")
    fun prepare(view: WebView, context: Context, popups: PopupHost? = null, onPage: (String) -> Unit = {}) {
        if (popups != null) allowPopups(view, context, popups)
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

    /** Legge il testo visibile della pagina aperta (per il file dei dati letti), poi [then] sul thread principale. */
    fun readText(view: WebView, then: () -> Unit) =
        view.evaluateJavascript("(function(){try{return document.body?document.body.innerText:''}catch(e){return ''}})()") { raw ->
            // evaluateJavascript restituisce la stringa in JSON: si toglie l'involucro.
            ShiftStore.pageText(runCatching { org.json.JSONArray("[$raw]").optString(0) }.getOrDefault(raw.orEmpty()))
            then()
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
                    // La pagina ha letto la sua creator: ora tutte le altre, con la stessa sessione.
                    if (ShiftStore.readAt.value != before) {
                        delay(1_500)
                        val asked = System.currentTimeMillis()
                        pull(view)
                        while (pullEndedAt < asked) delay(400)
                        delay(1_500)
                    }
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
        runCatching {
            android.webkit.WebStorage.getInstance().deleteOrigin("https://creatorspro.com")
            android.webkit.WebStorage.getInstance().deleteOrigin("https://app.creatorspro.com")
        }
        ShiftStore.unlink()
    }
}
