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
)

enum class PlayerCommand(val js: String) { TOGGLE("toggle"), NEXT("next"), PREVIOUS("prev") }

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
            )
            val wasPlaying = _tracks.value[service]?.playing == true
            _tracks.update { it + (service to track) }
            if (track.playing) ensureService()
            // Un solo player alla volta: quando questo parte, gli altri si mettono in pausa.
            if (track.playing && !wasPlaying) pauseOthers(service)
        }
    }

    /** Mette in pausa tutto ciò che suona tranne [keep] (null = tutto), radio compresa. */
    fun pauseOthers(keep: MediaService?) {
        _tracks.value.values.filter { it.playing && it.service != keep }.forEach { command(it.service, PlayerCommand.TOGGLE) }
        if (keep != null) com.platinum20.dashboard.media.radio.RadioStore.pause()
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
          return JSON.stringify({ title: title, artist: m ? m.artist : '', art: art, playing: playing });
        })()
    """

    // Spotify ha i suoi tasti; per YouTube si comanda direttamente il video.
    private const val COMMAND_SCRIPT = """
        function (cmd) {
          var ids = { toggle: 'control-button-playpause', next: 'control-button-skip-forward', prev: 'control-button-skip-back' };
          var button = document.querySelector('[data-testid="' + ids[cmd] + '"]');
          if (button) { button.click(); return; }
          var v = document.querySelector('video, audio');
          if (!v) return;
          if (cmd === 'toggle') { if (v.paused) v.play(); else v.pause(); }
          else if (cmd === 'prev') { v.currentTime = 0; }
          else if (cmd === 'next') {
            var n = document.querySelector('.ytp-next-button, .player-controls-next-button, button[aria-label*="successivo" i], button[aria-label*="next" i]');
            if (n) n.click();
          }
        }
    """
}
