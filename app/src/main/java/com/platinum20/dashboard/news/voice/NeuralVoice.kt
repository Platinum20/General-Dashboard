package com.platinum20.dashboard.news.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

/** Un pezzo di testo da dire in una volta: una frase di una parte, dal carattere [start]. */
data class SpeechPiece(val part: Int, val start: Int, val text: String)

/** Come spezzare il copione in frasi: funzioni pure, provate nei test. */
object SpeechPieces {
    /** Oltre questa lunghezza una frase si spezza a una virgola: la prima voce parte prima. */
    const val MAX_CHARS = 220

    /** Le frasi da dire dalla parte [index], carattere [char], fino alla fine del copione. */
    fun from(parts: List<String>, index: Int, char: Int): List<SpeechPiece> =
        (index until parts.size).flatMap { i ->
            val offset = if (i == index) char.coerceIn(0, parts[i].length) else 0
            sentences(parts[i].substring(offset)).map { (start, text) -> SpeechPiece(i, offset + start, text) }
        }

    /** Le frasi di un testo con la loro posizione: si chiude a . ! ? … e si spezzano quelle lunghe. */
    fun sentences(text: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var begin = 0
        fun emit(end: Int) {
            val raw = text.substring(begin, end)
            val lead = raw.length - raw.trimStart().length
            val clean = raw.trim()
            if (clean.isNotEmpty()) out += (begin + lead) to clean
            begin = end
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val atEnd = i + 1 >= text.length || text[i + 1].isWhitespace()
            val long = i - begin >= MAX_CHARS && (c == ',' || c == ';' || c == ':') && atEnd
            if ((c == '.' || c == '!' || c == '?' || c == '…') && atEnd || long) emit(i + 1)
            i++
        }
        emit(text.length)
        return out
    }
}

/**
 * La voce neurale Piper sul telefono (sherpa-onnx): genera una frase alla volta mentre suona la
 * precedente, così non ci sono attese tra una frase e l'altra.
 */
object NeuralVoice {
    /** Un thread solo: il motore non regge due generazioni insieme. */
    private val engineThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: OfflineTts? = null
    private var engineVoice: ReadingVoice? = null
    private var job: Job? = null
    @Volatile private var track: AudioTrack? = null

    private val _failure = MutableStateFlow<String?>(null)
    /**
     * Perché Piper non è partito l'ultima volta (null se va): si vede in Impostazioni sotto le voci,
     * così non si legge per sbaglio con la voce del telefono senza saperlo.
     */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    fun clearFailure() {
        _failure.value = null
    }

    /** Il motore genera davvero [text] con [voice]: quanti campioni e a che frequenza. Per le verifiche. */
    internal fun probe(voice: ReadingVoice, files: VoiceFiles, text: String): Pair<Int, Int> {
        val tts = engineFor(voice, files)
        return tts.generate(text, 0, 1.0f).samples.size to tts.sampleRate()
    }

    /**
     * Legge da [index]/[char] in poi. [onProgress] dice a che carattere è arrivata la voce,
     * [onDone] arriva in fondo al copione, [onError] se il motore non parte (si torna alla voce di sistema).
     */
    fun speak(
        voice: ReadingVoice,
        files: VoiceFiles,
        parts: List<String>,
        index: Int,
        char: Int,
        onProgress: (part: Int, char: Int) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        stop()
        val pieces = SpeechPieces.from(parts, index, char)
        job = scope.launch {
            var out: AudioTrack? = null
            try {
                val tts = withContext(engineThread) { engineFor(voice, files) }
                val rate = tts.sampleRate()
                val ready = Channel<Pair<SpeechPiece, FloatArray>>(capacity = 2)
                launch(engineThread) {
                    // Un errore del motore arriva a chi suona, che torna alla voce di sistema.
                    try {
                        for (piece in pieces) {
                            ensureActive()
                            ready.send(piece to tts.generate(piece.text, 0, 1.0f).samples)
                        }
                        ready.close()
                    } catch (e: CancellationException) {
                        ready.close()
                    } catch (e: Throwable) {
                        ready.close(e)
                    }
                }
                out = openTrack(rate).also { track = it }
                _failure.value = null
                out.play()
                var written = 0L
                val step = rate / 10
                for ((piece, samples) in ready) {
                    var offset = 0
                    while (offset < samples.size) {
                        ensureActive()
                        val n = minOf(step, samples.size - offset)
                        out!!.write(samples, offset, n, AudioTrack.WRITE_BLOCKING)
                        offset += n
                        written += n
                        onProgress(piece.part, piece.start + (piece.text.length.toLong() * offset / samples.size).toInt())
                    }
                }
                // L'ultima frase è ancora nel buffer: si chiude quando l'ha detta tutta.
                while (isActive && (out!!.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < written) delay(50)
                ensureActive()
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("NeuralVoice", "Piper non parte con ${voice.label}", e)
                _failure.value = describe(e)
                onError(e)
            } finally {
                out?.let(::release)
            }
        }
    }

    /** "UnsatisfiedLinkError: libsherpa-onnx-jni.so non trovata" → una riga leggibile. */
    private fun describe(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        return (root.javaClass.simpleName + (root.message?.let { ": $it" } ?: "")).take(140)
    }

    fun stop() {
        job?.cancel()
        job = null
        track?.let(::release)
    }

    /** Ferma e libera [out]; il blocco di scrittura in corso si sblocca subito. */
    @Synchronized
    private fun release(out: AudioTrack) {
        if (track === out) track = null
        if (out.state == AudioTrack.STATE_UNINITIALIZED) return
        runCatching { out.pause(); out.flush(); out.stop() }
        out.release()
    }

    /** Il motore resta caricato tra una lettura e l'altra: aprirlo costa circa un secondo. */
    private fun engineFor(voice: ReadingVoice, files: VoiceFiles): OfflineTts {
        engine?.takeIf { engineVoice == voice }?.let { return it }
        engine?.release()
        engine = null
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = files.model.absolutePath,
                    tokens = files.tokens.absolutePath,
                    dataDir = files.dataDir.absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        return OfflineTts(config = config).also {
            engine = it
            engineVoice = voice
        }
    }

    private fun openTrack(rate: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(min, rate * 4 / 4))
            .build()
    }
}
