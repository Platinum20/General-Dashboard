package com.platinum20.dashboard.media.spotify

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.NowPlaying
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.PlayerCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
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
        val shown = startAt ?: item
        Playback.starting(PlaySource.SPOTIFY, shown.title, shown.subtitle, shown.image)
        play(context, contextUri, if (contextUri == null) listOf(item.uri) else emptyList(), startAt?.uri)
    }

    /** Link di Spotify (open.spotify.com/track/..., spotify:album:...) suonato nell'app. */
    fun playLink(context: Context, link: String, title: String = "", subtitle: String = "", artwork: String = ""): Boolean {
        val uri = spotifyUri(link) ?: return false
        Playback.starting(PlaySource.SPOTIFY, title.ifBlank { "Spotify" }, subtitle, artwork)
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
            var result = runCatching { playOn(device, contextUri, uris, offsetUri) }
            // 404: il riproduttore nascosto si è scollegato (rete cambiata, standby lungo) e Spotify
            // non conosce più quel dispositivo. Si riavvia la pagina e si riprova una volta.
            if ((result.exceptionOrNull() as? SpotifyApiException)?.code == 404) {
                restart(context)
                val again = withTimeoutOrNull(25_000) { ready.await() }
                if (again != null) result = runCatching { playOn(again, contextUri, uris, offsetUri) }
            }
            result.exceptionOrNull()?.let { _error.value = spotifyMessage(it) }
        }
    }

    private suspend fun playOn(device: String, contextUri: String?, uris: List<String>, offsetUri: String?, positionMs: Long = 0) {
        val token = SpotifyAuth.token() ?: throw SpotifyApiException("Collega Spotify per ascoltare", 401)
        SpotifyApi.playOn(token, device, contextUri, uris, offsetUri, positionMs)
    }

    /**
     * Riprende [trackUri] (dentro [contextUri], se c'è) da [positionMs] nel riproduttore dell'app.
     * Serve per "Continua" e quando Spotify ha perso lo stato dopo una pausa lunga.
     * Con [autoplay] falso il brano si carica e si mette subito in pausa (es. aperto da una nota):
     * Spotify non sa caricare senza suonare.
     */
    fun resume(
        context: Context,
        trackUri: String,
        contextUri: String?,
        positionMs: Long,
        autoplay: Boolean = true,
        title: String = "",
        subtitle: String = "",
        artwork: String = "",
    ) {
        _error.value = null
        Playback.starting(PlaySource.SPOTIFY, title.ifBlank { "Spotify" }, subtitle, artwork)
        NowPlaying.pauseOthers(MediaService.SPOTIFY, radio = true)
        start(context)
        scope.launch {
            val device = withTimeoutOrNull(25_000) { ready.await() }
            if (device == null) {
                if (_error.value == null) _error.value = "Il riproduttore di Spotify non si avvia, riprova"
                return@launch
            }
            val ctx = contextUri?.takeIf { it.isNotBlank() && !it.startsWith("spotify:artist") }
            val uris = if (ctx == null) listOf(trackUri) else emptyList()
            var result = runCatching { playOn(device, ctx, uris, trackUri.takeIf { ctx != null }, positionMs) }
            if ((result.exceptionOrNull() as? SpotifyApiException)?.code == 404) {
                restart(context)
                val again = withTimeoutOrNull(25_000) { ready.await() }
                if (again != null) result = runCatching { playOn(again, ctx, uris, trackUri.takeIf { ctx != null }, positionMs) }
            }
            result.exceptionOrNull()?.let { _error.value = spotifyMessage(it) }
            if (result.isSuccess && !autoplay) {
                // Il riproduttore parte appena riceve il brano: lo si ferma non appena è suo.
                delay(400)
                NowPlaying.command(MediaService.SPOTIFY, PlayerCommand.PAUSE)
                SpotifyAuth.token()?.let { token -> runCatching { SpotifyApi.pause(token) } }
                Playback.settle(PlaySource.SPOTIFY)
            }
        }
    }

    private fun restart(context: Context) {
        MediaWebViews.release(MediaService.SPOTIFY)
        forget()
        start(context)
    }

    private var appContext: Context? = null

    /** Il dispositivo non vale più: chi suona dopo aspetta un nuovo "ready". */
    private fun forget() {
        deviceId = null
        if (ready.isCompleted) ready = CompletableDeferred()
    }

    /** Prepara il riproduttore nascosto (se non è già pronto). */
    fun start(context: Context) {
        appContext = context.applicationContext
        SpotifyAuth.init(context)
        val existing = MediaWebViews.webViewFor(MediaService.SPOTIFY)
        if (existing != null && existing.url?.startsWith(BASE_URL) == true) return
        forget()
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
        fun onNotReady() = forget()

        /** Il riproduttore ha cambiato brano o stato: il player dell'app si aggiorna subito, senza aspettare il giro. */
        @JavascriptInterface
        fun onState() = NowPlaying.refresh(MediaService.SPOTIFY)

        /** Il brano è finito da solo: parte il successivo della coda unica. */
        @JavascriptInterface
        fun onEnded() {
            val context = appContext ?: return
            scope.launch { com.platinum20.dashboard.media.PlayQueue.ended(context) }
        }

        @JavascriptInterface
        fun onError(kind: String, message: String) {
            _error.value = when (kind) {
                "initialization_error" -> "Questo telefono non riesce ad avviare il riproduttore di Spotify: aggiorna \"Android System WebView\" dal Play Store e riprova"
                "authentication_error" -> "Ricollega Spotify con la chiave in alto per ascoltare nell'app"
                "account_error" -> "Per ascoltare nell'app serve Spotify Premium"
                else -> "Spotify: $message"
            }
        }
    }

    /** Messaggio in italiano per un errore del comando "suona" (mai il testo grezzo dell'API o della rete). */
    fun spotifyMessage(error: Throwable): String = when {
        error is SpotifyApiException -> when (error.code) {
            401 -> "Ricollega Spotify con la chiave in alto per ascoltare nell'app"
            404 -> "Il riproduttore di Spotify si è scollegato: riprova tra qualche secondo"
            else -> error.message ?: "Spotify non risponde, riprova più tardi"
        }
        error is java.net.UnknownHostException || error is java.net.SocketTimeoutException || error is java.net.ConnectException ->
            "Nessuna connessione: Spotify non è raggiungibile"
        else -> "Spotify non risponde, riprova più tardi"
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
<button data-testid="control-button-skip-back" onclick="window.cp && cp.getCurrentState().then(function (s) { if (s && s.position > 3000) cp.seek(0); else cp.previousTrack(); })"></button>
<button data-testid="control-button-pause" onclick="window.cp && cp.pause()"></button>
<button data-testid="control-button-play" onclick="window.cp && cp.resume()"></button>
<script>
window.onSpotifyWebPlaybackSDKReady = function () {
  var player = new Spotify.Player({
    name: 'Canvas',
    volume: 1.0,
    getOAuthToken: function (cb) { cb(CanvasSpotify.token()); }
  });
  window.cp = player;
  player.addListener('ready', function (e) { CanvasSpotify.onReady(e.device_id); });
  player.addListener('not_ready', function () { CanvasSpotify.onNotReady(); });
  ['initialization_error', 'authentication_error', 'account_error', 'playback_error'].forEach(function (k) {
    player.addListener(k, function (e) { CanvasSpotify.onError(k, (e && e.message) || ''); });
  });
  // Lo stato vero si rilegge ogni secondo: gli eventi del riproduttore a volte non arrivano
  // (play rimasto acceso mentre la musica suona).
  setInterval(function () { player.getCurrentState().then(function (s) { if (s) apply(s, true); }); }, 1000);
  player.addListener('player_state_changed', function (s) { apply(s, false); });
  function apply(s, polled) {
    var ms = navigator.mediaSession;
    if (!ms) return;
    var t = s && s.track_window && s.track_window.current_track;
    // Spotify a volte "dimentica" lo stato dopo una pausa: il brano resta, solo in pausa,
    // così il player dell'app non sparisce e "Riproduci" lo fa ripartire da dove era.
    if (!t) {
      if (window.cpState) window.cpState.paused = true;
      if (ms.metadata) ms.playbackState = 'paused';
      return;
    }
    var prev = window.cpState;
    window.cpState = { pos: s.position || 0, dur: s.duration || 0, at: Date.now(), paused: !!s.paused,
      uri: t.uri || '', ctx: (s.context && s.context.uri) || '' };
    // Finito da solo: suonava, era quasi in fondo, ora è fermo all'inizio dello stesso brano.
    if (prev && !prev.paused && s.paused && (s.position || 0) < 1500 && prev.uri === t.uri && prev.dur > 0 &&
        prev.pos + (Date.now() - prev.at) > prev.dur - 4000) CanvasSpotify.onEnded();
    var changed = !prev || prev.paused !== !!s.paused || prev.uri !== (t.uri || '');
    // Dalla più piccola alla più grande: chi legge prende l'ultima, cioè la copertina più nitida.
    var images = ((t.album && t.album.images) || []).slice().sort(function (a, b) { return (a.width || 0) - (b.width || 0); });
    if (!polled || changed || !ms.metadata) ms.metadata = new MediaMetadata({
      title: t.name,
      artist: (t.artists || []).map(function (a) { return a.name; }).join(', '),
      album: t.album ? t.album.name : '',
      artwork: images.map(function (i) { return { src: i.url }; })
    });
    ms.playbackState = s.paused ? 'paused' : 'playing';
    if (!polled || changed) CanvasSpotify.onState();
  }
  if (player.activateElement) player.activateElement();
  player.connect();
};
</script>
<script src="https://sdk.scdn.co/spotify-player.js"></script>
</body></html>"""
}
