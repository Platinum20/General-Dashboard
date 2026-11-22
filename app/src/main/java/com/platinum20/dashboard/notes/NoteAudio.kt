package com.platinum20.dashboard.notes

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * L'audio nella nota (1.588): si registra dal tasto Aggiungi e nella nota resta una riga-link
 * `[🎙 Audio 0:12](audio:/percorso)`; l'editor ci disegna sopra una barra con play/pausa, avanzamento e durata. Il file sta nell'app.
 */
object NoteAudio {
    const val SCHEME = "audio:"

    private var recorder: MediaRecorder? = null
    private var recording: File? = null
    private var startedAt = 0L
    private var player: MediaPlayer? = null

    /** Il file che sta suonando adesso, null = niente. */
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    /** Quando è cominciata la registrazione in corso (millisecondi), null = non si registra. */
    private val _recordingSince = MutableStateFlow<Long?>(null)
    val recordingSince: StateFlow<Long?> = _recordingSince.asStateFlow()

    /** Comincia a registrare; false se il microfono non si apre. */
    fun start(context: Context): Boolean {
        stopPlaying()
        cancel()
        return runCatching {
            val file = File(context.filesDir, "note-audio/${System.currentTimeMillis()}.m4a").apply { parentFile?.mkdirs() }
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recording = file
            startedAt = System.currentTimeMillis()
            _recordingSince.value = startedAt
            true
        }.getOrElse { cancel(); false }
    }

    /** Ferma la registrazione: la riga da mettere nella nota, o null se è troppo corta o non è andata. */
    fun stop(): String? {
        val r = recorder ?: return null
        val file = recording
        val ms = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        recording = null
        _recordingSince.value = null
        if (!ok || file == null || ms < 700) { file?.delete(); return null }
        return line(file.absolutePath, ms)
    }

    /** Lascia perdere la registrazione in corso. */
    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        recording?.delete()
        recording = null
        _recordingSince.value = null
    }

    /** La riga della nota per un audio lungo [ms]. */
    fun line(path: String, ms: Long): String = "[🎙 Audio ${duration(ms)}]($SCHEME$path)"

    /** "0:12", "3:05". */
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(1)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** In pausa (1.589): il file resta pronto in [playing] e riparte da dove era. */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Il tasto play/pausa della barra: suona, mette in pausa o riprende. */
    fun toggle(path: String) {
        val p = player
        if (_playing.value == path && p != null) {
            runCatching { if (p.isPlaying) { p.pause(); _paused.value = true } else { pauseOthers(); p.start(); _paused.value = false } }
            return
        }
        play(path, 0f)
    }

    /** Toccando la barra di avanzamento: va a quel punto ([fraction] da 0 a 1) e suona. */
    fun seek(path: String, fraction: Float) {
        val p = player
        if (_playing.value == path && p != null) {
            runCatching { p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt()); if (!p.isPlaying) { pauseOthers(); p.start(); _paused.value = false } }
            return
        }
        play(path, fraction)
    }

    /** Dove è arrivato [path], da 0 a 1 (0 se non è lui a suonare). */
    fun progress(path: String): Float {
        val p = player ?: return 0f
        if (_playing.value != path) return 0f
        return runCatching { if (p.duration > 0) p.currentPosition.toFloat() / p.duration else 0f }.getOrDefault(0f)
    }

    /** Quanto dura [path] in millisecondi (dal file), 0 se non si legge. */
    fun length(path: String): Long = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try { r.setDataSource(path); r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L } finally { r.release() }
    }.getOrDefault(0L)

    // Una sola cosa suona alla volta: il player di Canvas si mette in pausa.
    private fun pauseOthers() { runCatching { com.platinum20.dashboard.media.CanvasPlayer.pause() } }

    private fun play(path: String, from: Float) {
        stopPlaying()
        if (!File(path).exists()) return
        pauseOthers()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stopPlaying() }
                prepare()
                if (from > 0f) seekTo((duration * from.coerceIn(0f, 1f)).toInt())
                start()
            }
            _playing.value = path
            _paused.value = false
        }.onFailure { stopPlaying() }
    }

    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playing.value = null
        _paused.value = false
    }
}
