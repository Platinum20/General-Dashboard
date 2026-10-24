package com.platinum20.dashboard.media

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/** Il brano o video in corso in uno dei player web. */
data class Track(
    val service: MediaService,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val playing: Boolean,
    /** Solo Spotify nell'app: dove si è e quanto dura (0 se non si sa). */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Solo Spotify nell'app: il brano e l'album/playlist da cui viene, per riprenderlo dopo. */
    val uri: String = "",
    val contextUri: String = "",
)

enum class PlayerCommand(val js: String) { TOGGLE("toggle"), NEXT("next"), PREVIOUS("prev"), PAUSE("pause"), PLAY("play") }

/**
 * Legge cosa stanno suonando YouTube e Spotify (dai dati che le pagine danno al sistema
 * tramite Media Session) e li comanda: play/pausa, avanti, indietro.
 * Tiene vivo il servizio in primo piano che fa continuare l'audio a schermo spento.
 */
object NowPlaying {
    private val scope = MainScope()
    private var poller: Job? = null
    private lateinit var appContext: Context
    private var serviceRunning = false

    private val _tracks = MutableStateFlow<Map<MediaService, Track>>(emptyMap())
    val tracks: StateFlow<Map<MediaService, Track>> = _tracks.asStateFlow()

    /** Il brano da mostrare in notifica: quello che suona, altrimenti l'ultimo aperto. */
    val current: Track?
        get() = _tracks.value.values.let { all -> all.firstOrNull { it.playing } ?: all.firstOrNull() }

    private var sleepJob: Job? = null
    private val _sleepAt = MutableStateFlow<Long?>(null)
    /** Quando il timer di spegnimento metterà in pausa i player (millisecondi), o null. */
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _sleepAt.value = null
        if (minutes == null) return
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            _tracks.value.values.filter { it.playing }.forEach { command(it.service, PlayerCommand.TOGGLE) }
            _sleepAt.value = null
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                MediaWebViews.openServices().forEach { poll(it) }
                delay(1500)
            }
        }
    }

    fun forget(service: MediaService) {
        _tracks.update { it - service }
        if (MediaWebViews.openServices().isEmpty()) {
            poller?.cancel()
            poller = null
            setSleepTimer(null)
            stopService()
        }
    }

    fun command(service: MediaService, command: PlayerCommand) {
        MediaWebViews.webViewFor(service)?.evaluateJavascript("($COMMAND_SCRIPT)('${command.js}')", null)
        // Aggiorna subito lo stato senza aspettare il giro successivo.
        scope.launch {
            delay(300)
            poll(service)
        }
    }

    /** Rilegge subito lo stato di [service] (chiamabile da qualunque thread). */
    fun refresh(service: MediaService) {
        scope.launch { poll(service) }
    }

    /** Salta a [positionMs] nel player della pagina (Spotify nell'app). */
    fun seek(service: MediaService, positionMs: Long) {
        MediaWebViews.webViewFor(service)?.evaluateJavascript("window.cp && cp.seek(${positionMs.coerceAtLeast(0)})", null)
        scope.launch {
            delay(300)
            poll(service)
        }
    }

    private fun poll(service: MediaService) {
        val webView = MediaWebViews.webViewFor(service) ?: return
        webView.evaluateJavascript(STATE_SCRIPT) { raw ->
            val json = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull()
                ?: return@evaluateJavascript
            val title = json.optString("title").removeSuffix(" - YouTube").trim()
            if (title.isBlank()) {
                _tracks.update { it - service }
                return@evaluateJavascript
            }
            val track = Track(
                service = service,
                title = title,
                artist = json.optString("artist"),
                artworkUrl = json.optString("art"),
                playing = json.optBoolean("playing"),
                positionMs = json.optLong("pos"),
                durationMs = json.optLong("dur"),
                uri = json.optString("uri"),
                contextUri = json.optString("ctx"),
            )
            val before = _tracks.value[service]
            val wasPlaying = before?.playing == true
            _tracks.update { it + (service to track) }
            // Spotify finisce in "Continua" come gli altri: a ogni brano nuovo e a ogni pausa.
            if (service == MediaService.SPOTIFY && track.uri.isNotBlank() &&
                (before?.uri != track.uri || before.playing != track.playing)
            ) rememberSpotify(track)
            if (track.playing) ensureService()
            // Un solo player alla volta: quando questo parte, gli altri si mettono in pausa.
            if (track.playing && !wasPlaying) pauseOthers(service)
        }
    }

    /**
     * Un solo player alla volta: mette in pausa le pagine che suonano tranne [keep] (null = tutte),
     * e se richiesto la radio, il podcast in ascolto e il player nativo di YouTube.
     */
    fun pauseOthers(
        keep: MediaService?,
        radio: Boolean = keep != null,
        nativeYouTube: Boolean = true,
        podcast: Boolean = true,
        recap: Boolean = true,
    ) {
        // "Pausa" e non "play/pausa": va mandata a tutte le pagine aperte senza fidarsi dello stato
        // letto (che può essere vecchio di un secondo e mezzo), e non fa ripartire chi era già fermo.
        MediaWebViews.openServices().filter { it != keep }.forEach { command(it, PlayerCommand.PAUSE) }
        // Spotify che suona nell'app di Spotify o su un altro dispositivo: si ferma anche quello.
        if (keep != MediaService.SPOTIFY) com.platinum20.dashboard.media.spotify.SpotifyStore.pauseRemote()
        if (radio) com.platinum20.dashboard.media.radio.RadioStore.pause()
        if (podcast) com.platinum20.dashboard.media.podcast.PodcastStore.pause()
        // La lettura si mette in pausa come le altre: resta nel player e riparte da dove era.
        if (recap) com.platinum20.dashboard.news.RecapSpeaker.pause()
        if (nativeYouTube) com.platinum20.dashboard.media.youtube.player.YtPlayer.pause()
    }

    private fun rememberSpotify(track: Track) {
        if (!::appContext.isInitialized) return
        MediaHistory.init(appContext)
        MediaHistory.record(
            ResumeItem(
                kind = ResumeKind.SPOTIFY,
                key = track.uri,
                title = track.title,
                subtitle = track.artist,
                image = track.artworkUrl,
                positionMs = track.positionMs,
                durationMs = track.durationMs,
                at = System.currentTimeMillis(),
                link = track.contextUri,
            )
        )
    }

    private fun ensureService() {
        if (serviceRunning) return
        serviceRunning = runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, MediaPlaybackService::class.java))
        }.isSuccess
    }

    private fun stopService() {
        if (!serviceRunning) return
        serviceRunning = false
        appContext.stopService(Intent(appContext, MediaPlaybackService::class.java))
    }

    internal fun onServiceStopped() {
        serviceRunning = false
    }

    // Prima la Media Session della pagina (titolo, artista, copertina, stato), poi l'elemento video/audio.
    private const val STATE_SCRIPT = """
        (function () {
          var s = navigator.mediaSession, m = s && s.metadata, v = document.querySelector('video, audio');
          var playing = s && s.playbackState === 'playing' ? true
            : s && s.playbackState === 'paused' ? false
            : !!(v && !v.paused && !v.ended);
          var art = m && m.artwork && m.artwork.length ? m.artwork[m.artwork.length - 1].src : '';
          var title = m && m.title ? m.title : (v && v.currentSrc ? document.title : '');
          var cs = window.cpState;
          var pos = cs ? (cs.paused ? cs.pos : cs.pos + (Date.now() - cs.at)) : 0;
          return JSON.stringify({ title: title, artist: m ? m.artist : '', art: art, playing: playing,
            pos: cs ? Math.min(pos, cs.dur || pos) : 0, dur: cs ? cs.dur : 0, uri: cs ? cs.uri : '', ctx: cs ? cs.ctx : '' });
        })()
    """

    // Spotify ha i suoi tasti; per YouTube si comanda direttamente il video.
    private const val COMMAND_SCRIPT = """
        function (cmd) {
          var ids = { toggle: 'control-button-playpause', next: 'control-button-skip-forward', prev: 'control-button-skip-back',
            pause: 'control-button-pause', play: 'control-button-play' };
          var button = document.querySelector('[data-testid="' + ids[cmd] + '"]');
          if (button) { button.click(); return; }
          var v = document.querySelector('video, audio');
          if (!v) return;
          if (cmd === 'toggle') { if (v.paused) v.play(); else v.pause(); }
          else if (cmd === 'pause') { v.pause(); }
          else if (cmd === 'play') { v.play(); }
          else if (cmd === 'prev') { v.currentTime = 0; }
          else if (cmd === 'next') {
            var n = document.querySelector('.ytp-next-button, .player-controls-next-button, button[aria-label*="successivo" i], button[aria-label*="next" i]');
            if (n) n.click();
          }
        }
    """
}
