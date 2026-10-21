package com.platinum20.dashboard.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
}

/**
 * Le pagine di YouTube e Spotify restano vive mentre si usa il resto dell'app,
 * così la musica continua anche cambiando sezione. Si chiudono con [release].
 */
object MediaWebViews {
    private val views = mutableMapOf<MediaService, WebView>()
    private val listeners = mutableMapOf<MediaService, MediaPageListener>()

    private val _open = MutableStateFlow<Set<MediaService>>(emptySet())
    /** Player aperti, anche se non sono a schermo. */
    val open: StateFlow<Set<MediaService>> = _open.asStateFlow()

    /** Restituisce la pagina del servizio, creandola se serve, pronta da mettere a schermo. */
    fun obtain(context: Context, service: MediaService, startUrl: String? = null): WebView {
        views[service]?.let { existing ->
            (existing.context as? MutableContextWrapper)?.baseContext = context
            (existing.parent as? ViewGroup)?.removeView(existing)
            if (startUrl != null) existing.loadUrl(startUrl)
            return existing
        }
        val webView = createMediaWebView(MutableContextWrapper(context), service, blockAds = AppSettings.adBlock.value) {
            listeners[service]
        }
        webView.loadUrl(startUrl ?: service.homeUrl)
        views[service] = webView
        _open.update { it + service }
        NowPlaying.start(context)
        return webView
    }

    fun webViewFor(service: MediaService): WebView? = views[service]

    fun openServices(): Set<MediaService> = views.keys.toSet()

    fun setListener(service: MediaService, listener: MediaPageListener?) {
        if (listener == null) listeners.remove(service) else listeners[service] = listener
    }

    /** La schermata è chiusa: la pagina resta in vita ma senza riferimenti all'Activity. */
    fun detach(service: MediaService) {
        val webView = views[service] ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        (webView.context as? MutableContextWrapper)?.let { it.baseContext = it.applicationContext }
    }

    fun release(service: MediaService) {
        val webView = views.remove(service) ?: return
        listeners.remove(service)
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
    val scriptAtStart = blockAds && AdBlocker.installScript(webView)

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
            if (blockAds && !request.isForMainFrame && AdBlocker.shouldBlock(request.url)) {
                return AdBlocker.emptyResponse()
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url
            if (target.scheme == "http" || target.scheme == "https") return false
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
            listener()?.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (blockAds && !scriptAtStart) AdBlocker.injectScript(view, url)
            listener()?.onPageFinished(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
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
