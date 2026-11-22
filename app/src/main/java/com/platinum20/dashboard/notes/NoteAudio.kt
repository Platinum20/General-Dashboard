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
 * `[🎙 Audio 0:12](audio:/percorso)`; toccandola si ascolta, toccandola di nuovo si ferma. Il file sta nell'app.
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

    /** Tocco sulla riga: suona, o si ferma se stava già suonando. */
    fun toggle(path: String) {
        if (_playing.value == path) { stopPlaying(); return }
        stopPlaying()
        if (!File(path).exists()) return
        // Una sola cosa suona alla volta: il player di Canvas si mette in pausa.
        runCatching { com.platinum20.dashboard.media.CanvasPlayer.pause() }
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stopPlaying() }
                prepare()
                start()
            }
            _playing.value = path
        }.onFailure { stopPlaying() }
    }

    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playing.value = null
    }
}
