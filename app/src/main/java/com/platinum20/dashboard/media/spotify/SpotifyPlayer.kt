package com.platinum20.dashboard.media.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ExternalBackend
import com.platinum20.dashboard.media.ExternalState
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.createMediaWebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Spotify nel player unico. L'audio è protetto (Widevine) ed esce solo dal riproduttore ufficiale di
 * Spotify (Web Playback SDK, serve Premium): gira in una pagina nascosta fatta da noi, mai il sito,
 * e non viene estratto. Tutto il resto è del player unico ([CanvasPlayer]): coda, comandi, notifica.
 * Qui Spotify fa solo da uscita audio per una voce alla volta.
 *
 * Il riproduttore si accende all'avvio dell'app (se Spotify è collegato) e resta pronto, così il
 * play non deve aspettare che si colleghi. Lo stato arriva dalla pagina a ogni cambio e una volta al
 * secondo (gli eventi dell'SDK a volte non arrivano).
 */
object SpotifyPlayer : ExternalBackend {
    private const val BASE_URL = "https://canvas.platinum20.app/spotify/"
    private val scope = MainScope()
    private var webView: WebView? = null
    private var appContext: Context? = null
    private var ready = CompletableDeferred<String>()
    private var playJob: Job? = null

    /** La voce che il player unico ci ha chiesto di suonare (uri di Spotify). */
    private var target: String? = null
    /** Caricata ferma: appena Spotify parte la si mette in pausa (Spotify non sa caricare senza suonare). */
    private var pauseOnStart = false
    private var last: Reading? = null

    internal data class Reading(val uri: String, val paused: Boolean, val positionMs: Long, val durationMs: Long, val at: Long)

    private val _state = MutableStateFlow<ExternalState?>(null)
    override val state: StateFlow<ExternalState?> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Ultimo problema del riproduttore (Premium mancante, collegamento da rifare...). */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun consumeError() {
        _error.value = null
    }

    // ---------------------------------------------------------------- dalle schermate

    /** Un brano o un episodio entra nella coda e suona; un album o una playlist da [startAt]. */
    fun play(context: Context, item: SpItem, startAt: SpItem? = null) {
        if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) {
            CanvasPlayer.play(context, item.asQueueItem(), true)
            return
        }
        playParent(context, item, startAt?.uri)
    }

    /** Link di Spotify (open.spotify.com/track/..., spotify:album:...) suonato nell'app. */
    fun playLink(context: Context, link: String, title: String = "", subtitle: String = "", artwork: String = ""): Boolean {
        val uri = spotifyUri(link) ?: return false
        val (kind, id) = uri.split(":").let { it[1] to it[2] }
        when (kind) {
            "track", "episode" -> CanvasPlayer.play(
                context,
                ResumeItem(ResumeKind.SPOTIFY, uri, title.ifBlank { "Spotify" }, subtitle, artwork, at = System.currentTimeMillis()),
            )
            else -> playParent(context, SpItem(id, uri, SpKind.valueOf(kind.uppercase()), title, subtitle, artwork), null)
        }
        return true
    }

    fun SpItem.asQueueItem(parent: SpItem? = null) = ResumeItem(
        ResumeKind.SPOTIFY, uri, title, subtitle, image.ifBlank { parent?.image.orEmpty() },
        at = System.currentTimeMillis(), link = parent?.uri.orEmpty(),
    )

    /** I brani di un album, playlist o podcast entrano nella coda, da [startUri] in poi. */
    private fun playParent(context: Context, parent: SpItem, startUri: String?) {
        val loaded = SpotifyStore.children.value[parent.id]?.data
        scope.launch {
            val children = loaded ?: runCatching {
                val token = SpotifyAuth.token() ?: throw SpotifyApiException("Collega Spotify per ascoltare", 401)
                SpotifyApi.children(token, parent)
            }.getOrElse {
                _error.value = if (it is PlaylistLockedException) it.message else spotifyMessage(it)
                return@launch
            }
            val tracks = children.filter { it.kind == SpKind.TRACK || it.kind == SpKind.EPISODE }
            if (tracks.isEmpty()) {
                _error.value = "Niente da ascoltare qui"
                return@launch
            }
            val start = startUri?.let { s -> tracks.indexOfFirst { it.uri == s } }?.coerceAtLeast(0) ?: 0
            CanvasPlayer.playList(context, tracks.map { it.asQueueItem(parent) }, start)
        }
    }

    // ---------------------------------------------------------------- uscita audio del player unico

    /** Accende il riproduttore nascosto, se Spotify è collegato e non è già acceso. */
    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    fun start(context: Context) {
        appContext = context.applicationContext
        SpotifyAuth.init(context)
        if (webView != null) return
        val app = context.applicationContext
        webView = createMediaWebView(MutableContextWrapper(app), MediaService.SPOTIFY, blockAds = false) { null }.also {
            it.addJavascriptInterface(Bridge, "CanvasSpotify")
            it.loadDataWithBaseURL(BASE_URL, PAGE, "text/html", "UTF-8", null)
        }
    }

    private fun restart(context: Context) {
        webView?.run {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        if (ready.isCompleted) ready = CompletableDeferred()
        start(context)
    }

    private fun js(script: String) {
        webView?.evaluateJavascript("window.cp && $script", null)
    }

    override fun play(context: Context, item: ResumeItem, startMs: Long, autoplay: Boolean) {
        _error.value = null
        target = item.key
        pauseOnStart = !autoplay
        last = null
        _state.value = ExternalState(playing = false, loading = true, positionMs = startMs, durationMs = item.durationMs)
        start(context)
        playJob?.cancel()
        playJob = scope.launch {
            var result = runCatching { send(item.key, startMs) }
            // 404: il riproduttore si è scollegato (rete cambiata, standby lungo) e Spotify non conosce
            // più quel dispositivo. Si riavvia la pagina e si riprova una volta.
            if ((result.exceptionOrNull() as? SpotifyApiException)?.code == 404) {
                restart(context)
                result = runCatching { send(item.key, startMs) }
            }
            result.exceptionOrNull()?.let { fail(spotifyMessage(it)) }
        }
    }

    private suspend fun send(uri: String, startMs: Long) {
        val device = withTimeoutOrNull(20_000) { ready.await() }
            ?: throw SpotifyApiException(_error.value ?: "Il riproduttore di Spotify non si avvia, riprova", 0)
        val token = SpotifyAuth.token() ?: throw SpotifyApiException("Collega Spotify per ascoltare", 401)
        SpotifyApi.playOn(token, device, null, listOf(uri), null, startMs)
    }

    private fun fail(message: String) {
        _error.value = message
        _state.value = (_state.value ?: ExternalState(false, false, 0, 0)).copy(playing = false, loading = false, error = message)
    }

    override fun pause() = js("cp.pause()")

    override fun resume() {
        val s = _state.value
        // Spotify ha perso il brano (pausa lunga, errore): lo si rimanda da dove era.
        if (s?.error != null || last == null) {
            val uri = target ?: return
            val context = appContext ?: return
            play(context, ResumeItem(ResumeKind.SPOTIFY, uri, "", at = 0, durationMs = s?.durationMs ?: 0), s?.positionMs ?: 0, true)
            return
        }
        js("cp.resume()")
    }

    override fun seekTo(positionMs: Long) {
        js("cp.seek(${positionMs.coerceAtLeast(0)})")
        _state.value = _state.value?.copy(positionMs = positionMs)
    }

    override fun stop() {
        playJob?.cancel()
        target = null
        last = null
        js("cp.pause()")
        _state.value = null
    }

    /** Stato letto dalla pagina (thread principale). */
    private fun onReading(reading: Reading) {
        val uri = target ?: return
        if (reading.uri != uri) return
        val before = last
        last = reading
        if (pauseOnStart && !reading.paused) {
            pauseOnStart = false
            js("cp.pause()")
        }
        _state.value = ExternalState(
            playing = !reading.paused,
            loading = false,
            positionMs = reading.positionMs,
            durationMs = reading.durationMs,
        )
        if (ended(before, reading)) {
            target = null
            CanvasPlayer.externalEnded()
        }
    }

    /**
     * Finito da solo: suonava ed era quasi in fondo, ora è fermo all'inizio dello stesso brano (Spotify
     * a fine brano torna all'inizio in pausa). Funzione pura, provata nei test.
     */
    internal fun ended(before: Reading?, now: Reading): Boolean {
        if (before == null || before.paused || !now.paused || before.uri != now.uri || before.durationMs <= 0) return false
        val reached = before.positionMs + (now.at - before.at)
        return now.positionMs < 1_500 && reached > before.durationMs - 4_000
    }

    internal fun endedForTest(beforePos: Long, beforePaused: Boolean, nowPos: Long, nowPaused: Boolean, duration: Long, gapMs: Long): Boolean =
        ended(Reading("u", beforePaused, beforePos, duration, 0), Reading("u", nowPaused, nowPos, duration, gapMs))

    /** Ponte con la pagina nascosta: le chiamate arrivano su un thread dello script, non su quello principale. */
    private object Bridge {
        @JavascriptInterface
        fun token(): String = runBlocking { runCatching { SpotifyAuth.token() }.getOrNull() }.orEmpty()

        @JavascriptInterface
        fun onReady(id: String) {
            scope.launch { ready.complete(id) }
        }

        @JavascriptInterface
        fun onNotReady() {
            scope.launch { if (ready.isCompleted) ready = CompletableDeferred() }
        }

        @JavascriptInterface
        fun onState(uri: String, paused: Boolean, position: Double, duration: Double) {
            val reading = Reading(uri, paused, position.toLong(), duration.toLong(), System.currentTimeMillis())
            scope.launch { onReading(reading) }
        }

        @JavascriptInterface
        fun onError(kind: String, message: String) {
            val text = when (kind) {
                "initialization_error" -> "Questo telefono non riesce ad avviare il riproduttore di Spotify: aggiorna \"Android System WebView\" dal Play Store e riprova"
                "authentication_error" -> "Ricollega Spotify con la chiave in alto per ascoltare nell'app"
                "account_error" -> "Per ascoltare nell'app serve Spotify Premium"
                else -> "Spotify: $message"
            }
            scope.launch { if (target != null) fail(text) else _error.value = text }
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

    // La pagina: niente da vedere, solo il riproduttore. Manda lo stato all'app a ogni cambio e una
    // volta al secondo; i comandi arrivano dall'app su window.cp.
    private const val PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"></head>
<body style="background:#000">
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
  function send(s) {
    var t = s && s.track_window && s.track_window.current_track;
    if (!t) return;
    CanvasSpotify.onState(t.uri || '', !!s.paused, s.position || 0, s.duration || 0);
  }
  player.addListener('player_state_changed', send);
  setInterval(function () { player.getCurrentState().then(send); }, 1000);
  if (player.activateElement) player.activateElement();
  player.connect();
};
</script>
<script src="https://sdk.scdn.co/spotify-player.js"></script>
</body></html>"""
}
