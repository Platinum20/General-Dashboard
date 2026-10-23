package com.platinum20.dashboard.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.ViewParent
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.platinum20.dashboard.adblock.AdBlocker
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Quello che succede nella pagina del player, girato alla schermata che la sta mostrando. */
interface MediaPageListener {
    fun onProgress(progress: Int) {}
    fun onTitle(title: String?) {}
    fun onPageStarted(url: String) {}
    fun onPageFinished(url: String) {}
    fun onHistoryChanged(canGoBack: Boolean) {}
    fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {}
    fun onHideFullscreen() {}
    /** Indirizzo non web (es. il ritorno dal login di Spotify): true se l'ha gestito chi ascolta. */
    fun onAppLink(uri: Uri): Boolean = false
}

/**
 * Le pagine di YouTube e Spotify restano vive mentre si usa il resto dell'app,
 * così la musica continua anche cambiando sezione. Si chiudono con [release].
 */
object MediaWebViews {
    private val views = mutableMapOf<MediaService, WebView>()

    private val _open = MutableStateFlow<Set<MediaService>>(emptySet())
    /** Player aperti, anche se non sono a schermo. */
    val open: StateFlow<Set<MediaService>> = _open.asStateFlow()

    /**
     * Pagina nascosta fatta dall'app (mai a schermo), per esempio il riproduttore di Spotify:
     * [html] caricato con origine [baseUrl] e [bridge] raggiungibile dagli script come [bridgeName].
     * Se c'era la pagina del sito di quel servizio, viene chiusa.
     */
    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    fun obtainHidden(context: Context, service: MediaService, baseUrl: String, html: String, bridge: Any, bridgeName: String): WebView {
        views[service]?.let { existing ->
            if (existing.url?.startsWith(baseUrl) == true) return existing
            release(service)
        }
        val webView = createMediaWebView(MutableContextWrapper(context.applicationContext), service, blockAds = false) { null }
        webView.addJavascriptInterface(bridge, bridgeName)
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        views[service] = webView
        _open.update { it + service }
        NowPlaying.start(context)
        return webView
    }

    fun webViewFor(service: MediaService): WebView? = views[service]

    fun openServices(): Set<MediaService> = views.keys.toSet()

    fun release(service: MediaService) {
        val webView = views.remove(service) ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
        _open.update { it - service }
        NowPlaying.forget(service)
    }

    fun releaseAll() = MediaService.entries.forEach { release(it) }
}

/** User agent di Chrome senza i segni della WebView: Google altrimenti rifiuta l'accesso. */
fun browserUserAgent(webViewAgent: String): String =
    webViewAgent.replace("; wv", "").replace(Regex("""Version/\d+(\.\d+)* """), "")

/**
 * Carica la pagina solo quando la WebView è già sullo schermo. Dentro Compose la vista nasce staccata
 * dalla finestra: caricata così, la pagina di accesso di Spotify restava tutta nera (si vedeva solo lo sfondo).
 */
fun WebView.loadWhenAttached(url: String) {
    if (isAttachedToWindow) {
        loadUrl(url)
        return
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.removeOnAttachStateChangeListener(this)
            v.post { loadUrl(url) }
        }

        override fun onViewDetachedFromWindow(v: View) {}
    })
}

/**
 * La WebView dentro un FrameLayout, da passare ad AndroidView. Messa direttamente in Compose, la pagina
 * di accesso di Spotify si vedeva tutta nera (solo lo sfondo): verificato su emulatore Android 14 e 16.
 */
fun WebView.inFrame(context: Context): FrameLayout = FrameLayout(context).also { frame ->
    (parent as? ViewGroup)?.removeView(this)
    frame.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
}

/** User agent di Chrome per computer, con la stessa versione del motore della WebView. */
fun desktopUserAgent(webViewAgent: String): String {
    val chrome = Regex("""Chrome/([\d.]+)""").find(webViewAgent)?.groupValues?.get(1) ?: "130.0.0.0"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
}

@SuppressLint("SetJavaScriptEnabled")
fun createMediaWebView(
    context: Context,
    service: MediaService,
    blockAds: Boolean,
    listener: () -> MediaPageListener?,
): WebView = WebView(context).apply {
    val webView = this
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(service.desktopSite)
        builtInZoomControls = service.desktopSite
        displayZoomControls = false
        userAgentString = if (service.desktopSite) desktopUserAgent(userAgentString) else browserUserAgent(userAgentString)
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }
    // Senza blocco (pagine di accesso) non serve nemmeno lo script nella pagina.
    val adBlock = if (blockAds) AdBlocker.attach(webView) else null

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            listener()?.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            listener()?.onTitle(title)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            listener()?.onShowFullscreen(view, callback) ?: callback.onCustomViewHidden()
        }

        override fun onHideCustomView() {
            listener()?.onHideFullscreen()
        }

        // Spotify protegge l'audio con Widevine: senza questo permesso la musica non parte.
        override fun onPermissionRequest(request: PermissionRequest) {
            val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
        }
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return adBlock?.intercept(request)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url
            if (target.scheme == "http" || target.scheme == "https") return false
            if (listener()?.onAppLink(target) == true) return true
            // Gli inviti ad aprire l'app ufficiale (intent://, spotify:, vnd.youtube:) restano qui:
            // se c'è un indirizzo web alternativo lo apriamo nella pagina, altrimenti li ignoriamo.
            if (target.scheme == "intent") {
                runCatching { Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
                    ?.getStringExtra("browser_fallback_url")
                    ?.takeIf { it.startsWith("http") }
                    ?.let { view.loadUrl(it) }
            } else if (target.scheme == "mailto" || target.scheme == "tel") {
                runCatching {
                    view.context.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            adBlock?.onPage(view, url)
            listener()?.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (service == MediaService.SPOTIFY && MediaWebViews.consumeAutoplay(service)) {
                view.evaluateJavascript(SPOTIFY_AUTOPLAY, null)
            }
            listener()?.onPageFinished(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            adBlock?.onPage(view, url)
            listener()?.onHistoryChanged(view.canGoBack())
        }
    }
}

/** Indirizzo di un video YouTube aperto da un link (youtube.com, youtu.be, shorts) nella versione mobile. */
fun youtubeWatchUrl(link: String): String? {
    val uri = Uri.parse(link.trim())
    val host = uri.host?.lowercase() ?: return null
    return when {
        host == "youtu.be" -> uri.lastPathSegment?.let { "https://m.youtube.com/watch?v=$it" }
        host.endsWith("youtube.com") -> uri.buildUpon().authority("m.youtube.com").scheme("https").build().toString()
        else -> null
    }
}

// Preme "Riproduci" nella pagina del brano/album/playlist appena il web player è pronto (al massimo 20 secondi).
private const val SPOTIFY_AUTOPLAY = """
(function () {
  var tries = 0;
  var timer = setInterval(function () {
    tries++;
    var button = document.querySelector('[data-testid="action-bar-row"] [data-testid="play-button"]') ||
                 document.querySelector('main [data-testid="play-button"]');
    if (button) {
      clearInterval(timer);
      if (button.getAttribute('aria-label') && /pausa|pause/i.test(button.getAttribute('aria-label'))) return;
      button.click();
    } else if (tries > 40) {
      clearInterval(timer);
    }
  }, 500);
})();
"""
