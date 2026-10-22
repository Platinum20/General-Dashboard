package com.platinum20.dashboard.adblock

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Blocco pubblicità di tutte le pagine web dell'app (YouTube, Spotify, articoli dei giornali),
 * con le liste di uBlock Origin: richieste bloccate, elementi nascosti e scriptlet nella pagina.
 *
 * Le liste sono dentro l'APK e si aggiornano da sole ogni pochi giorni.
 */
object AdBlocker {

    private class FilterList(val id: String, val trusted: Boolean, val bundled: Boolean, val urls: List<String>)

    private const val UBO_CDN = "https://ublockorigin.github.io/uAssetsCDN"
    private const val UBO_PAGES = "https://ublockorigin.pages.dev"
    private const val JSDELIVR = "https://cdn.jsdelivr.net/gh/uBlockOrigin/uAssetsCDN@main"

    private fun ubo(name: String) = FilterList(
        "ublock-$name", trusted = true, bundled = true,
        urls = listOf("$UBO_CDN/filters/$name.min.txt", "$UBO_PAGES/filters/$name.min.txt", "$JSDELIVR/filters/$name.min.txt"),
    )

    private fun thirdParty(id: String) = FilterList(
        id, trusted = false, bundled = true,
        urls = listOf(
            "https://easylist.to/easylist/$id.txt", "$UBO_PAGES/thirdparties/$id.txt", "$JSDELIVR/thirdparties/$id.txt",
        ),
    )

    private val lists = listOf(
        thirdParty("easylist"),
        thirdParty("easyprivacy"),
        ubo("filters"),
        ubo("privacy"),
        ubo("quick-fixes"),
        ubo("unbreak"),
        // EasyList Italy: solo da scaricare, il server non è raggiungibile quando si compila l'app.
        FilterList("easylistitaly", trusted = false, bundled = false, urls = listOf("https://easylist-downloads.adblockplus.org/easylistitaly.txt")),
    )

    private const val PREFS = "adblock"
    private const val KEY_UPDATED = "lists_updated"
    private const val UPDATE_EVERY_MS = 4L * 24 * 60 * 60 * 1000
    private const val TAG = "AdBlocker"

    data class Status(
        /** Liste complete caricate (prima vale solo la lista dell'app). */
        val ready: Boolean = false,
        val rules: Int = 0,
        /** Ultimo aggiornamento riuscito delle liste, null se si usano quelle dell'APK. */
        val updatedAt: Long? = null,
        val updating: Boolean = false,
        val lastError: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _blocked = MutableStateFlow(0)
    /** Richieste bloccate da quando l'app è aperta. */
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    @Volatile
    private var engine: FilterEngine? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updateLock = Mutex()
    @Volatile
    private var contentScript: String? = null

    /** Avvia il caricamento delle liste in sottofondo. Si può chiamare più volte. */
    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _status.update { it.copy(updatedAt = prefs.getLong(KEY_UPDATED, 0L).takeIf { t -> t > 0 }) }
        scope.launch {
            // Subito la lista dell'app (piccola), poi tutte le altre.
            engine = runCatching { FilterEngine.Builder().addList(builtinList(), trusted = true).build() }.getOrNull()
            reload()
            val last = prefs.getLong(KEY_UPDATED, 0L)
            if (System.currentTimeMillis() - last > UPDATE_EVERY_MS) update()
        }
    }

    /** Scarica di nuovo le liste (dalle impostazioni o ogni pochi giorni). */
    fun updateNow() {
        if (!::appContext.isInitialized) return
        scope.launch { update() }
    }

    private suspend fun update() = updateLock.withLock {
        _status.update { it.copy(updating = true, lastError = null) }
        var changed = 0
        var failed = 0
        withContext(Dispatchers.IO) {
            for (list in lists) {
                val text = list.urls.firstNotNullOfOrNull { url -> runCatching { download(url) }.getOrNull() }
                if (text == null) {
                    failed++
                    continue
                }
                val dest = listFile(list.id)
                val tmp = File(dest.parentFile, "${list.id}.tmp")
                tmp.writeText(text)
                if (tmp.renameTo(dest)) changed++
            }
        }
        if (changed > 0) {
            val now = System.currentTimeMillis()
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_UPDATED, now).apply()
            _status.update { it.copy(updatedAt = now) }
            reload()
        }
        _status.update {
            it.copy(
                updating = false,
                lastError = when {
                    changed == 0 -> "Liste non raggiungibili: restano quelle di prima"
                    failed > 0 -> "$failed liste non aggiornate"
                    else -> null
                },
            )
        }
    }

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "GeneralDashboard/${BuildConfig.VERSION_NAME} (Android)")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // Una pagina di errore o di accesso al Wi-Fi non è una lista.
            if (text.length < 2_000 || text.take(500).contains("<html", ignoreCase = true)) throw IOException("lista non valida")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun listFile(id: String) = File(appContext.filesDir, "adblock/$id.txt").also { it.parentFile?.mkdirs() }

    private fun builtinList(): String = appContext.assets.open("adblock/builtin.txt").bufferedReader().use { it.readText() }

    private fun reload() {
        val started = System.currentTimeMillis()
        val built = runCatching {
            val builder = FilterEngine.Builder()
            for (list in lists) {
                val text = readList(list) ?: continue
                builder.addList(text, list.trusted)
            }
            builder.addList(builtinList(), trusted = true)
            builder.build()
        }.onFailure { Log.w(TAG, "Liste non caricate", it) }.getOrNull() ?: return
        engine = built
        _status.update { it.copy(ready = true, rules = built.ruleCount) }
        Log.i(TAG, "${built.ruleCount} regole in ${System.currentTimeMillis() - started} ms")
    }

    private fun readList(list: FilterList): String? {
        val downloaded = listFile(list.id)
        if (downloaded.exists()) runCatching { return downloaded.readText() }
        if (!list.bundled) return null
        return runCatching {
            appContext.assets.open("adblock/lists/${list.id}.txt").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun script(): String = contentScript ?: appContext.assets.open("adblock/content.js")
        .bufferedReader().use { it.readText() }.also { contentScript = it }

    /**
     * Collega il blocco a una WebView. Restituisce la sessione da usare in shouldInterceptRequest
     * e onPageStarted. Con [inPage] false si bloccano solo le richieste (pagine invisibili).
     */
    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    fun attach(webView: WebView, inPage: Boolean = true, pageUrl: String = ""): Session {
        init(webView.context)
        val session = Session(pageUrl)
        if (inPage) {
            webView.addJavascriptInterface(Bridge(), "GDBridge")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, script(), setOf("*"))
            } else {
                session.injectLate = true
            }
        }
        return session
    }

    class Session internal constructor(@Volatile private var pageUrl: String) {
        internal var injectLate = false

        /** Da chiamare in shouldInterceptRequest: la risposta vuota da dare al posto della richiesta, o null. */
        fun intercept(request: WebResourceRequest): WebResourceResponse? {
            val url = request.url?.toString() ?: return null
            if (request.isForMainFrame) {
                pageUrl = url
                return null
            }
            if (!AppSettings.adBlock.value || !url.startsWith("http")) return null
            val current = engine ?: return null
            val type = requestType(request)
            val verdict = runCatching { current.match(url, pageUrl, type) }.getOrNull()
            if (verdict != FilterEngine.Verdict.BLOCK) return null
            _blocked.update { it + 1 }
            return emptyResponse(type)
        }

        /** La pagina principale è cambiata (anche senza ricaricare, come su YouTube). */
        fun onPage(view: WebView, url: String?) {
            if (url.isNullOrEmpty() || !url.startsWith("http")) return
            pageUrl = url
            // WebView molto vecchie: lo script entra a pagina già avviata, un po' meno efficace.
            if (injectLate && AppSettings.adBlock.value) view.evaluateJavascript(script(), null)
        }
    }

    /** I metodi che lo script nella pagina può chiamare: restituiscono solo filtri. */
    private class Bridge {
        @JavascriptInterface
        fun page(url: String?): String {
            if (!AppSettings.adBlock.value || url == null) return "{}"
            val current = engine ?: return "{}"
            return runCatching {
                val host = Request.hostOf(url)
                Json.page(current.pageFilters(url, host))
            }.getOrDefault("{}")
        }

        @JavascriptInterface
        fun generic(host: String?, keys: String?): String {
            if (!AppSettings.adBlock.value || host == null || keys.isNullOrEmpty()) return "[]"
            val current = engine ?: return "[]"
            return runCatching { Json.strings(current.genericSelectors(host, keys.split('\n'))) }.getOrDefault("[]")
        }
    }

    /** La WebView non dice il tipo di richiesta: lo ricaviamo dall'intestazione Accept e dall'estensione. */
    internal fun requestType(request: WebResourceRequest): RequestType {
        val headers = request.requestHeaders.orEmpty()
        val accept = (headers["Accept"] ?: headers["accept"]).orEmpty()
        val path = request.url?.path.orEmpty().lowercase()
        return typeFrom(accept, path)
    }

    internal fun typeFrom(accept: String, path: String): RequestType {
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        return when {
            accept.startsWith("text/html") -> RequestType.SUBDOCUMENT
            accept.startsWith("text/css") -> RequestType.STYLESHEET
            accept.startsWith("image/") -> RequestType.IMAGE
            ext == "js" || ext == "mjs" -> RequestType.SCRIPT
            ext == "css" -> RequestType.STYLESHEET
            ext in imageExt -> RequestType.IMAGE
            ext in fontExt -> RequestType.FONT
            ext in mediaExt -> RequestType.MEDIA
            accept.contains("application/json") -> RequestType.XHR
            else -> RequestType.UNKNOWN
        }
    }

    private val imageExt = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp")
    private val fontExt = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val mediaExt = setOf("mp4", "webm", "m3u8", "mp3", "m4a", "aac", "ogg", "mpd", "m4s", "ts")

    /** GIF trasparente di un pixel: al posto delle immagini bloccate, senza l'icona di immagine rotta. */
    private val transparentGif = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
    )

    private fun emptyResponse(type: RequestType): WebResourceResponse {
        val (mime, body) = when (type) {
            RequestType.SCRIPT -> "application/javascript" to ByteArray(0)
            RequestType.STYLESHEET -> "text/css" to ByteArray(0)
            RequestType.IMAGE -> "image/gif" to transparentGif
            RequestType.SUBDOCUMENT -> "text/html" to ByteArray(0)
            else -> "text/plain" to ByteArray(0)
        }
        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(body))
    }
}
