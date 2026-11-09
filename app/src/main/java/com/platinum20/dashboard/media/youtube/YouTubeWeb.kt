package com.platinum20.dashboard.media.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.platinum20.dashboard.media.desktopUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * YouTube senza registrazioni su Google Cloud: usa l'accesso già fatto nell'app (i cookie del sito)
 * e legge i dati che YouTube mette nelle sue pagine, caricandole in una WebView invisibile.
 * Le novità dei canali seguiti arrivano poi dai feed RSS pubblici, come con l'API ufficiale.
 */
object YouTubeWeb {
    private const val SITE = "https://www.youtube.com"
    private val lock = Mutex()

    class NotLoggedIn : IOException("Accedi a YouTube dalle Impostazioni per vedere il tuo feed")

    private class Page(
        val loggedIn: Boolean, val channels: List<YtChannel>, val videos: List<YtVideo>, val playlists: List<YtPlaylist>,
        val next: String? = null,
    )

    /** Una pagina della Home: [next] è il segno di YouTube per chiedere i video dopo (null = finiti). */
    class FeedPage(val videos: List<YtVideo>, val next: String?)

    /** Canali seguiti: elenco delle iscrizioni più i canali dei video nel feed Iscrizioni. */
    suspend fun subscriptions(context: Context): List<YtChannel> {
        val channels = page(context, "/feed/channels", requireLogin = true)
        val feed = runCatching { page(context, "/feed/subscriptions", requireLogin = true) }.getOrNull()
        val fromFeed = feed?.videos.orEmpty()
            .filter { it.channelId.isNotBlank() }
            .map { YtChannel(it.channelId, it.channel, "") }
        // Prima i canali con video recenti nel feed, poi gli altri: al massimo 120 feed RSS da leggere.
        return (fromFeed + channels.channels).distinctBy { it.id }.take(120)
    }

    suspend fun playlists(context: Context): List<YtPlaylist> =
        page(context, "/feed/playlists", requireLogin = true).playlists
            .filter { it.id != "LL" && it.id != "WL" }

    suspend fun playlistItems(context: Context, playlistId: String): List<YtVideo> =
        page(context, "/playlist?list=" + URLEncoder.encode(playlistId, "UTF-8"), requireLogin = true).videos

    suspend fun liked(context: Context): List<YtVideo> = playlistItems(context, "LL")

    suspend fun watchLater(context: Context): List<YtVideo> = playlistItems(context, "WL")

    /** Video consigliati della Home, come nell'app di YouTube. */
    /** La cronologia di YouTube del tuo account, con quanto hai visto di ogni video. */
    suspend fun history(context: Context): List<YtVideo> =
        page(context, "/feed/history", requireLogin = true).videos

    suspend fun home(context: Context): FeedPage =
        page(context, "/", requireLogin = true).let { FeedPage(it.videos, it.next) }

    /**
     * I consigliati dopo [token], come quando scorri la Home sul sito: due pagine per volta, così aprire
     * la pagina nascosta (qualche secondo) vale una quarantina di video.
     */
    suspend fun homeMore(context: Context, token: String): FeedPage = withAccount(context) { page ->
        val raw = page.run(
            """
            (async function() {
              var all = [], token = ${JSONObject.quote(token)};
              for (var i = 0; i < 2 && token; i++) {
                var r = await __cvApi('browse', {continuation: token});
                all = all.concat(__cvCollect(r).videos);
                token = __cvNext(r);
              }
              return {videos: all, next: token};
            })()
            """.trimIndent(),
            timeoutMs = 60_000,
        )
        val o = JSONObject(raw)
        FeedPage(videos(o.optJSONArray("videos")), o.optString("next").takeIf { it.isNotBlank() && it != "null" })
    }

    suspend fun search(context: Context, query: String): List<YtVideo> =
        page(context, "/results?search_query=" + URLEncoder.encode(query, "UTF-8"), requireLogin = false).videos

    private suspend fun page(context: Context, path: String, requireLogin: Boolean): Page = lock.withLock {
        val raw = withTimeoutOrNull(30_000) { withContext(Dispatchers.Main) { load(context.applicationContext, SITE + path) } }
            ?: throw IOException("YouTube non risponde, riprova")
        val page = withContext(Dispatchers.Default) { parse(raw) }
        if (requireLogin && !page.loggedIn) throw NotLoggedIn()
        page
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(context: Context, url: String): String = suspendCancellableCoroutine { cont ->
        val webView = WebView(context)
        val handler = Handler(Looper.getMainLooper())
        var done = false
        fun finish(result: String) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.destroy()
            if (cont.isActive) cont.resume(result)
        }
        // I dati sono già nella pagina quando finisce di caricare; si riprova qualche volta se non ci sono ancora.
        fun extract(attempt: Int) {
            webView.evaluateJavascript(EXTRACT_SCRIPT) { value ->
                val text = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
                if (text != null && text.contains("\"ready\":true")) finish(text)
                else if (attempt < 6) handler.postDelayed({ extract(attempt + 1) }, 1_000)
                else finish(text ?: "{}")
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            userAgentString = desktopUserAgent(userAgentString)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!done) handler.postDelayed({ extract(0) }, 300)
            }
        }
        cont.invokeOnCancellation { handler.post { finish("{}") } }
        webView.loadUrl(url)
    }

    private fun parse(raw: String): Page {
        val o = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        fun <T> list(key: String, map: (JSONObject) -> T?): List<T> {
            val array = o.optJSONArray(key) ?: JSONArray()
            return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(map) }
        }
        return Page(
            loggedIn = o.optBoolean("loggedIn"),
            channels = list("channels") { c ->
                c.optString("id").takeIf { it.isNotBlank() }?.let { YtChannel(it, c.optString("title"), c.optString("thumb")) }
            }.distinctBy { it.id },
            videos = videos(o.optJSONArray("videos")),
            playlists = list("playlists") { p ->
                p.optString("id").takeIf { it.isNotBlank() }?.let {
                    YtPlaylist(it, p.optString("title"), p.optString("thumb"), p.optInt("count"))
                }
            }.distinctBy { it.id },
            next = o.optString("next").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    /** I video raccolti dallo script di lettura, senza doppioni. */
    internal fun videos(array: JSONArray?): List<YtVideo> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val v = array.optJSONObject(i) ?: return@mapNotNull null
            v.optString("id").takeIf { it.isNotBlank() }?.let {
                YtVideo(
                    it, v.optString("title"), v.optString("channel"), v.optString("channelId"), null, v.optBoolean("short"),
                    durationSec = durationSeconds(v.optString("length")),
                    live = v.optBoolean("live"),
                    upcoming = v.optLong("start") > 0,
                    startsAt = v.optLong("start").takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it) },
                    channelAvatar = v.optString("avatar"),
                    watchedPercent = v.optInt("watched"),
                    membersOnly = v.optBoolean("members"),
                )
            }
        }.distinctBy { it.id }
    }

    /**
     * Una pagina di youtube.com aperta di nascosto col tuo accesso: da qui si fanno le stesse richieste
     * che fa il sito (Guarda più tardi, Mi piace, cronologia), che l'API ufficiale di Google non permette.
     */
    class AccountPage internal constructor(private val webView: WebView) {
        private var calls = 0

        /**
         * Esegue [expr], un'espressione JavaScript che può usare `await` e le funzioni `__cvApi(endpoint, corpo)`,
         * `__cvCollect(dati)` e `__cvCont(dati)`, e restituisce il risultato in JSON (o un errore).
         */
        suspend fun run(expr: String, timeoutMs: Long = 45_000): String {
            val id = "__cv" + (++calls)
            val start = "(function(){window.$id=null;(async function(){try{var r=await ($expr);" +
                "window.$id=JSON.stringify({ok:true,v:(r===undefined?null:r)});}catch(e){" +
                "window.$id=JSON.stringify({ok:false,e:String((e&&e.message)||e)});}})();return 1;})()"
            withContext(Dispatchers.Main) { webView.evaluateJavascript(start, null) }
            val text = withTimeoutOrNull(timeoutMs) {
                var found: String? = null
                while (found == null) {
                    delay(250)
                    found = withContext(Dispatchers.Main) { evaluate(webView, "window.$id") }
                }
                found
            } ?: throw IOException("YouTube non risponde, riprova")
            val o = JSONObject(text)
            if (!o.optBoolean("ok")) throw IOException("YouTube: " + o.optString("e"))
            return o.opt("v")?.toString() ?: "null"
        }
    }

    /** Apre la pagina del tuo account e la chiude alla fine di [block]; errore se non hai fatto l'accesso. */
    suspend fun <T> withAccount(context: Context, block: suspend (AccountPage) -> T): T = lock.withLock {
        val webView = withTimeoutOrNull(40_000) { withContext(Dispatchers.Main) { open(context.applicationContext, "$SITE/feed/playlists") } }
            ?: throw IOException("YouTube non risponde, riprova")
        try {
            val page = AccountPage(webView)
            val loggedIn = page.run(ACCOUNT_SCRIPT)
            if (loggedIn != "true") throw NotLoggedIn()
            block(page)
        } finally {
            withContext(Dispatchers.Main + NonCancellable) { webView.stopLoading(); webView.destroy() }
        }
    }

    private suspend fun evaluate(webView: WebView, script: String): String? = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(script) { value ->
            val text = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
            if (cont.isActive) cont.resume(text)
        }
    }

    /** Carica la pagina e aspetta che la configurazione del sito (ytcfg) sia pronta. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun open(context: Context, url: String): WebView = suspendCancellableCoroutine { cont ->
        val webView = WebView(context)
        val handler = Handler(Looper.getMainLooper())
        var done = false
        fun check(attempt: Int) {
            webView.evaluateJavascript("!!(window.ytcfg&&ytcfg.get&&ytcfg.get('INNERTUBE_API_KEY'))") { value ->
                if (done) return@evaluateJavascript
                if (value == "true" || attempt >= 10) {
                    done = true
                    if (cont.isActive) cont.resume(webView) else webView.destroy()
                } else {
                    handler.postDelayed({ check(attempt + 1) }, 500)
                }
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            userAgentString = desktopUserAgent(userAgentString)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!done) handler.postDelayed({ check(0) }, 300)
            }
        }
        cont.invokeOnCancellation {
            handler.post {
                done = true
                handler.removeCallbacksAndMessages(null)
                webView.destroy()
            }
        }
        webView.loadUrl(url)
    }

    // Le funzioni usate da AccountPage.run: la firma SAPISIDHASH è quella che il sito calcola dai suoi cookie.
    // (Calcolata: COLLECT_FN è dichiarata più sotto.)
    private val ACCOUNT_SCRIPT: String get() = """
(async function() {
  window.__cvCollect = """ + COLLECT_FN + """;
  window.__cvNext = """ + NEXT_FN + """;
  window.__cvCont = function(data) {
    var s = JSON.stringify(data || {});
    var m = s.match(/"continuationCommand":\{"token":"([^"]+)","request":"CONTINUATION_REQUEST_TYPE_BROWSE"/) ||
            s.match(/"continuationCommand":\{"token":"([^"]+)"/);
    return m ? m[1] : null;
  };
  window.__cvHash = async function(cookie, ts, origin) {
    var m = document.cookie.match(new RegExp('(?:^|; )' + cookie.replace(/[-_]/g, '\\$&') + '=([^;]+)'));
    if (!m) return null;
    var buf = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(ts + ' ' + m[1] + ' ' + origin));
    return Array.from(new Uint8Array(buf)).map(function(b) { return ('0' + b.toString(16)).slice(-2); }).join('');
  };
  window.__cvApi = async function(endpoint, body) {
    var origin = 'https://www.youtube.com';
    var ts = Math.floor(Date.now() / 1000);
    var auth = [];
    var names = [['SAPISID', 'SAPISIDHASH'], ['__Secure-1PAPISID', 'SAPISID1PHASH'], ['__Secure-3PAPISID', 'SAPISID3PHASH']];
    for (var i = 0; i < names.length; i++) {
      var h = await __cvHash(names[i][0], ts, origin);
      if (h) auth.push(names[i][1] + ' ' + ts + '_' + h);
    }
    if (!auth.length) throw new Error('accesso a YouTube scaduto');
    body = body || {};
    body.context = ytcfg.get('INNERTUBE_CONTEXT');
    var res = await fetch('/youtubei/v1/' + endpoint + '?prettyPrint=false', {
      method: 'POST', credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': auth.join(' '),
        'X-Origin': origin,
        'X-Goog-AuthUser': String(ytcfg.get('SESSION_INDEX') || 0),
        'X-Goog-Visitor-Id': ytcfg.get('VISITOR_DATA') || '',
        'X-Youtube-Client-Name': String(ytcfg.get('INNERTUBE_CONTEXT_CLIENT_NAME') || 1),
        'X-Youtube-Client-Version': ytcfg.get('INNERTUBE_CLIENT_VERSION') || ''
      },
      body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status + ' su ' + endpoint);
    return await res.json();
  };
  return !!ytcfg.get('LOGGED_IN');
})()
"""

    /** "1:02:03" o "12:34" in secondi. */
    private fun durationSeconds(text: String): Int =
        text.split(":").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size in 2..3 }?.fold(0) { acc, n -> acc * 60 + n } ?: 0

    // Percorre i dati della pagina (ytInitialData) e raccoglie canali, video e playlist
    // qualunque sia la struttura: YouTube la cambia spesso, i singoli blocchi molto meno.
    private const val COLLECT_FN = """
function(data) {
  var channels = [], videos = [], playlists = [];
  function text(t) {
    if (!t) return '';
    if (typeof t === 'string') return t;
    if (t.simpleText) return t.simpleText;
    if (t.content) return t.content;
    if (t.runs) return t.runs.map(function(r) { return r.text; }).join('');
    return '';
  }
  function thumb(t) {
    var list = t && (t.thumbnails || (t.image && t.image.sources) || t.sources);
    if (!list || !list.length) return '';
    var url = list[list.length - 1].url || '';
    return url.indexOf('//') === 0 ? 'https:' + url : url;
  }
  function byline(r) {
    var b = r.shortBylineText || r.ownerText || r.longBylineText;
    var run = b && b.runs && b.runs[0];
    var id = run && run.navigationEndpoint && run.navigationEndpoint.browseEndpoint ? run.navigationEndpoint.browseEndpoint.browseId : '';
    return {name: run ? run.text : '', id: id || ''};
  }
  function lockup(l) {
    var meta = l.metadata && l.metadata.lockupMetadataViewModel;
    var title = meta ? text(meta.title) : '';
    var type = l.contentType || '';
    if (type.indexOf('VIDEO') >= 0) {
      var channel = '', channelId = '';
      try {
        var parts = meta.metadata.contentMetadataViewModel.metadataRows[0].metadataParts[0];
        channel = text(parts.text);
        var cmd = parts.text.commandRuns && parts.text.commandRuns[0].onTap.innertubeCommand.browseEndpoint;
        if (cmd) channelId = cmd.browseId;
      } catch (e) {}
      var img = JSON.stringify(l.contentImage || {});
      var len = (img.match(/"text":"(\d{1,2}(?::\d{2}){1,2})"/) || [])[1] || '';
      var start = parseInt((JSON.stringify(l).match(/"startTime":"(\d{9,11})"/) || [])[1] || '0', 10);
      var live = /LIVE/.test(img) && !len && !start;
      var watched = parseInt((img.match(/"startPercent":(\d+)/) || [])[1] || '0', 10);
      var avatar = '';
      try { avatar = thumb(meta.image.decoratedAvatarViewModel.avatar.avatarViewModel.image); } catch (e) {}
      var members = /MEMBERS_ONLY/.test(JSON.stringify(meta || {}));
      videos.push({id: l.contentId, title: title, channel: channel, channelId: channelId, length: len, live: live, start: start, avatar: avatar, watched: watched, members: members});
    } else if (type.indexOf('PLAYLIST') >= 0) {
      var count = 0;
      try { count = parseInt(JSON.stringify(l.contentImage).match(/"text":"(\d+)/)[1], 10) || 0; } catch (e) {}
      var img = '';
      try { img = thumb(l.contentImage.collectionThumbnailViewModel.primaryThumbnail.thumbnailViewModel.image); } catch (e) {}
      playlists.push({id: l.contentId, title: title, thumb: img, count: count});
    }
  }
  function walk(node, depth) {
    if (!node || typeof node !== 'object' || depth > 60) return;
    if (Array.isArray(node)) { for (var i = 0; i < node.length; i++) walk(node[i], depth + 1); return; }
    for (var key in node) {
      var v = node[key];
      if (!v || typeof v !== 'object') continue;
      // Le pubblicità (adSlotRenderer, inFeedAdLayoutRenderer, promoted...) contengono video finti: si saltano intere.
      if (/^ad[A-Z]|Ad[A-Z]|^promoted|Promoted|^statementBanner|^brandVideo/.test(key)) continue;
      if (key === 'channelRenderer' && v.channelId) {
        channels.push({id: v.channelId, title: text(v.title), thumb: thumb(v.thumbnail)});
      } else if ((key === 'videoRenderer' || key === 'playlistVideoRenderer' || key === 'gridVideoRenderer' ||
                  key === 'compactVideoRenderer') && v.videoId) {
        var by = byline(v);
        var overlays = JSON.stringify(v.thumbnailOverlays || []) + JSON.stringify(v.badges || []);
        var start = parseInt((v.upcomingEventData && v.upcomingEventData.startTime) || '0', 10);
        var live = /LIVE/.test(overlays) && !v.lengthText && !start;
        var watched = parseInt((overlays.match(/"percentDurationWatched":(\d+)/) || [])[1] || '0', 10);
        var avatar = '';
        try { avatar = thumb(v.channelThumbnailSupportedRenderers.channelThumbnailWithLinkRenderer.thumbnail); } catch (e) {}
        if (!avatar && v.channelThumbnail) avatar = thumb(v.channelThumbnail);
        var members = /MEMBERS_ONLY/.test(overlays);
        videos.push({id: v.videoId, title: text(v.title), channel: by.name, channelId: by.id, length: text(v.lengthText), live: live, start: start, avatar: avatar, watched: watched, members: members});
      } else if (key === 'reelItemRenderer' && v.videoId) {
        videos.push({id: v.videoId, title: text(v.headline), channel: '', channelId: '', short: true});
      } else if ((key === 'gridPlaylistRenderer' || key === 'playlistRenderer') && v.playlistId) {
        playlists.push({id: v.playlistId, title: text(v.title), thumb: thumb(v.thumbnail || (v.thumbnails && v.thumbnails[0])),
                        count: parseInt(text(v.videoCountText || v.videoCountShortText || v.videoCount), 10) || 0});
      } else if (key === 'lockupViewModel' && v.contentId) {
        lockup(v);
      }
      walk(v, depth + 1);
    }
  }
  walk(data, 0);
  return {channels: channels, videos: videos, playlists: playlists};
}
"""

    // Il segno per "carica altri" è nell'ultimo continuationItemRenderer (in fondo alla griglia): il primo
    // continuationCommand della pagina può essere quello di un filtro in alto, che ricomincerebbe da capo.
    private const val NEXT_FN = """
function(data) {
  var last = null;
  function token(e) {
    if (!e) return null;
    if (e.continuationCommand && e.continuationCommand.token) return e.continuationCommand.token;
    var cmds = e.commandExecutorCommand && e.commandExecutorCommand.commands;
    if (cmds) for (var i = 0; i < cmds.length; i++) { var t = token(cmds[i]); if (t) return t; }
    return null;
  }
  function walk(node, depth) {
    if (!node || typeof node !== 'object' || depth > 60) return;
    if (Array.isArray(node)) { for (var i = 0; i < node.length; i++) walk(node[i], depth + 1); return; }
    for (var key in node) {
      var v = node[key];
      if (!v || typeof v !== 'object') continue;
      if (key === 'continuationItemRenderer') {
        var t = token(v.continuationEndpoint) || token(v.button && v.button.buttonRenderer && v.button.buttonRenderer.command);
        if (t) last = t;
      }
      walk(v, depth + 1);
    }
  }
  walk(data, 0);
  return last;
}
"""

    private const val EXTRACT_SCRIPT = """
(function() {
  var data = window.ytInitialData;
  if (!data) return JSON.stringify({ready: false});
  var cfg = window.ytcfg && ytcfg.get ? ytcfg : null;
  var loggedIn = !!(cfg && cfg.get('LOGGED_IN'));
  var r = (""" + COLLECT_FN + """)(data);
  var next = (""" + NEXT_FN + """)(data);
  return JSON.stringify({ready: true, loggedIn: loggedIn, channels: r.channels, videos: r.videos, playlists: r.playlists, next: next});
})();
"""
}
