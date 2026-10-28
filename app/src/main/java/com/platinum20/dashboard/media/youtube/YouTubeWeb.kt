package com.platinum20.dashboard.media.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.platinum20.dashboard.media.desktopUserAgent
import kotlinx.coroutines.Dispatchers
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

    private class Page(val loggedIn: Boolean, val channels: List<YtChannel>, val videos: List<YtVideo>, val playlists: List<YtPlaylist>)

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

    suspend fun home(context: Context): List<YtVideo> =
        page(context, "/", requireLogin = true).videos

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
            videos = list("videos") { v ->
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
            }.distinctBy { it.id },
            playlists = list("playlists") { p ->
                p.optString("id").takeIf { it.isNotBlank() }?.let {
                    YtPlaylist(it, p.optString("title"), p.optString("thumb"), p.optInt("count"))
                }
            }.distinctBy { it.id },
        )
    }

    /** "1:02:03" o "12:34" in secondi. */
    private fun durationSeconds(text: String): Int =
        text.split(":").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size in 2..3 }?.fold(0) { acc, n -> acc * 60 + n } ?: 0

    // Percorre i dati della pagina (ytInitialData) e raccoglie canali, video e playlist
    // qualunque sia la struttura: YouTube la cambia spesso, i singoli blocchi molto meno.
    private const val EXTRACT_SCRIPT = """
(function() {
  var data = window.ytInitialData;
  if (!data) return JSON.stringify({ready: false});
  var cfg = window.ytcfg && ytcfg.get ? ytcfg : null;
  var loggedIn = !!(cfg && cfg.get('LOGGED_IN'));
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
  return JSON.stringify({ready: true, loggedIn: loggedIn, channels: channels, videos: videos, playlists: playlists});
})();
"""
}
