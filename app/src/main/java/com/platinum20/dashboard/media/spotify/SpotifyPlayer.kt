package com.platinum20.dashboard.media.spotify

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.NowPlaying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Spotify suona dentro l'app: il riproduttore ufficiale di Spotify (Web Playback SDK, serve Premium)
 * gira in una pagina nascosta fatta da noi, mai il sito. Si comanda dalle schermate dell'app, dalla
 * notifica e dalla bolla come gli altri player. L'audio resta protetto (Widevine), non viene estratto.
 */
object SpotifyPlayer {
    private const val BASE_URL = "https://canvas.platinum20.app/spotify/"
    private val scope = MainScope()
    @Volatile private var deviceId: String? = null
    private var ready = CompletableDeferred<String>()

    private val _error = MutableStateFlow<String?>(null)
    /** Ultimo problema del riproduttore (Premium mancante, collegamento da rifare...). */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun consumeError() {
        _error.value = null
    }

    /** Fa partire un brano, o un album/playlist dall'inizio o da [startAt]. */
    fun play(context: Context, item: SpItem, startAt: SpItem? = null) {
        val contextUri = item.uri.takeIf { item.kind != SpKind.TRACK && item.kind != SpKind.EPISODE }
        play(context, contextUri, if (contextUri == null) listOf(item.uri) else emptyList(), startAt?.uri)
    }

    /** Link di Spotify (open.spotify.com/track/..., spotify:album:...) suonato nell'app. */
    fun playLink(context: Context, link: String): Boolean {
        val uri = spotifyUri(link) ?: return false
        val kind = uri.split(":").getOrNull(1)
        if (kind == "track" || kind == "episode") play(context, null, listOf(uri), null) else play(context, uri, emptyList(), null)
        return true
    }

    private fun play(context: Context, contextUri: String?, uris: List<String>, offsetUri: String?) {
        _error.value = null
        NowPlaying.pauseOthers(MediaService.SPOTIFY, radio = true)
        start(context)
        scope.launch {
            val device = withTimeoutOrNull(25_000) { ready.await() }
            if (device == null) {
                if (_error.value == null) _error.value = "Il riproduttore di Spotify non si avvia, riprova"
                return@launch
            }
            val result = runCatching {
                val token = SpotifyAuth.token() ?: throw SpotifyApiException("Collega Spotify per ascoltare", 401)
                SpotifyApi.playOn(token, device, contextUri, uris, offsetUri)
            }
            result.exceptionOrNull()?.let { _error.value = it.message }
        }
    }

    /** Prepara il riproduttore nascosto (se non è già pronto). */
    fun start(context: Context) {
        SpotifyAuth.init(context)
        val existing = MediaWebViews.webViewFor(MediaService.SPOTIFY)
        if (existing != null && existing.url?.startsWith(BASE_URL) == true) return
        deviceId = null
        if (ready.isCompleted) ready = CompletableDeferred()
        MediaWebViews.obtainHidden(context, MediaService.SPOTIFY, BASE_URL, PAGE, Bridge, "CanvasSpotify")
    }

    /** Ponte con la pagina nascosta: le chiamate arrivano su un thread dello script, non su quello principale. */
    private object Bridge {
        @JavascriptInterface
        fun token(): String = runBlocking { runCatching { SpotifyAuth.token() }.getOrNull() }.orEmpty()

        @JavascriptInterface
        fun onReady(id: String) {
            deviceId = id
            ready.complete(id)
        }

        @JavascriptInterface
        fun onError(kind: String, message: String) {
            _error.value = when (kind) {
                "initialization_error" -> "Questo telefono non riesce ad avviare il riproduttore di Spotify ($message)"
                "authentication_error" -> "Ricollega Spotify con la chiave in alto per ascoltare nell'app"
                "account_error" -> "Per ascoltare nell'app serve Spotify Premium"
                else -> "Spotify: $message"
            }
        }
    }

    /** spotify:track:ID da un link di open.spotify.com (anche con /intl-it/) o da un URI già pronto. */
    fun spotifyUri(link: String): String? {
        val text = link.trim()
        if (Regex("""^spotify:(track|album|playlist|artist|show|episode):[A-Za-z0-9]+$""").matches(text)) return text
        val parsed = Uri.parse(text)
        if (parsed.host?.endsWith("spotify.com") != true) return null
        val segments = parsed.pathSegments.filterNot { it.startsWith("intl-") }
        val kind = segments.getOrNull(0)?.takeIf { it in setOf("track", "album", "playlist", "artist", "show", "episode") } ?: return null
        val id = segments.getOrNull(1) ?: return null
        return "spotify:$kind:$id"
    }

    // La pagina: niente da vedere, solo il riproduttore. Comunica titolo e stato tramite Media Session
    // (letti da NowPlaying come per gli altri player) e ha i tasti con gli stessi nomi del web player,
    // così notifica, bolla e timer la comandano senza codice in più.
    private const val PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"></head>
<body style="background:#000">
<button data-testid="control-button-playpause" onclick="window.cp && cp.togglePlay()"></button>
<button data-testid="control-button-skip-forward" onclick="window.cp && cp.nextTrack()"></button>
<button data-testid="control-button-skip-back" onclick="window.cp && cp.previousTrack()"></button>
<script>
window.onSpotifyWebPlaybackSDKReady = function () {
  var player = new Spotify.Player({
    name: 'Canvas',
    volume: 1.0,
    getOAuthToken: function (cb) { cb(CanvasSpotify.token()); }
  });
  window.cp = player;
  player.addListener('ready', function (e) { CanvasSpotify.onReady(e.device_id); });
  ['initialization_error', 'authentication_error', 'account_error', 'playback_error'].forEach(function (k) {
    player.addListener(k, function (e) { CanvasSpotify.onError(k, (e && e.message) || ''); });
  });
  player.addListener('player_state_changed', function (s) {
    var ms = navigator.mediaSession;
    if (!ms) return;
    var t = s && s.track_window && s.track_window.current_track;
    if (!t) { ms.metadata = null; ms.playbackState = 'none'; return; }
    var images = (t.album && t.album.images) || [];
    ms.metadata = new MediaMetadata({
      title: t.name,
      artist: (t.artists || []).map(function (a) { return a.name; }).join(', '),
      album: t.album ? t.album.name : '',
      artwork: images.map(function (i) { return { src: i.url }; })
    });
    ms.playbackState = s.paused ? 'paused' : 'playing';
  });
  if (player.activateElement) player.activateElement();
  player.connect();
};
</script>
<script src="https://sdk.scdn.co/spotify-player.js"></script>
</body></html>"""
}
