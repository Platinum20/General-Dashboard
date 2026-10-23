package com.platinum20.dashboard.news

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.platinum20.dashboard.media.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.util.Locale

/** Il copione del punto del giorno letto ad alta voce: un'apertura e una parte per tema. */
object RecapReader {
    const val MAX_THEMES = 6
    private val ordinals = listOf("Primo", "Secondo", "Terzo", "Quarto", "Quinto", "Sesto")

    /** Le parti da leggere una dopo l'altra; vuoto se non ci sono temi. */
    fun script(themes: List<RecapTheme>, now: LocalTime): List<String> {
        val chosen = themes.take(MAX_THEMES)
        if (chosen.isEmpty()) return emptyList()
        val time = "%d:%02d".format(now.hour, now.minute)
        val count = if (chosen.size == 1) "un tema" else "${chosen.size} temi"
        val parts = mutableListOf("Il punto delle $time, $count.")
        chosen.forEachIndexed { i, theme ->
            val title = clean(theme.headline.title).trimEnd('.', ' ')
            val sentence = firstSentence(clean(theme.headline.summary))
                .takeIf { it.isNotBlank() && !it.startsWith(title.take(30), ignoreCase = true) }
            val sources = if (theme.sourceCount > 1) " Ne parlano ${theme.sourceCount} testate." else ""
            parts += "${ordinals[i]} tema: $title." + (sentence?.let { " $it" } ?: "") + sources
        }
        return parts
    }

    /** Toglie quello che a voce non ha senso: indirizzi, virgolette, simboli, spazi doppi. */
    fun clean(text: String): String = text
        .replace(Regex("https?://\\S+|www\\.\\S+"), "")
        .replace(Regex("[«»“”\"„]"), "")
        .replace(Regex("[‘’]"), "'")
        .replace(Regex("[#*_|~^<>\\[\\]{}]"), " ")
        .replace(Regex("\\s*[–—]\\s*"), ", ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** La prima frase (fino a 220 caratteri), così ogni tema dura pochi secondi. */
    fun firstSentence(text: String): String {
        if (text.isBlank()) return ""
        val end = Regex("[.!?](\\s|$)").find(text)?.range?.first
        val sentence = if (end != null) text.substring(0, end + 1) else text
        return if (sentence.length > 220) sentence.take(220).substringBeforeLast(' ') + "…" else sentence
    }
}

/**
 * Cosa sta leggendo la voce, con il tempo stimato dal testo: così la lettura sta nel player unico
 * come una canzone, con la barra da trascinare per scegliere da dove leggere.
 */
data class RecapListening(
    val part: Int,
    val total: Int,
    val speaking: Boolean,
    val error: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** La voce di Android (italiano, sul telefono e gratis) che legge il copione del punto del giorno. */
object RecapSpeaker {
    /** Ritmo medio della voce italiana: serve a trasformare i caratteri in tempo per la barra. */
    private const val MS_PER_CHAR = 62L

    private var tts: TextToSpeech? = null
    private var ready = false
    private var parts: List<String> = emptyList()
    private var starts: List<Long> = emptyList()
    private var totalMs = 0L

    private val _state = MutableStateFlow<RecapListening?>(null)
    val state: StateFlow<RecapListening?> = _state.asStateFlow()

    fun play(context: Context, script: List<String>) {
        if (script.isEmpty()) return
        // Un solo audio alla volta: musica, radio, podcast e video si mettono in pausa.
        NowPlaying.pauseOthers(null, radio = true, recap = false)
        com.platinum20.dashboard.media.Playback.starting(com.platinum20.dashboard.media.PlaySource.SPEECH)
        parts = script
        starts = timeline(script)
        totalMs = starts.last() + script.last().length * MS_PER_CHAR
        _state.value = RecapListening(0, script.size, speaking = false, durationMs = totalMs)
        val engine = tts
        if (engine != null && ready) speakFrom(0, 0) else if (engine == null) {
            tts = TextToSpeech(context.applicationContext) init@{ status ->
                ready = status == TextToSpeech.SUCCESS
                if (!ready) {
                    _state.value = RecapListening(0, parts.size, speaking = false, error = "Sintesi vocale non disponibile sul telefono")
                    return@init
                }
                val language = tts?.setLanguage(Locale.ITALIAN) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _state.value = RecapListening(
                        0, parts.size, speaking = false,
                        error = "Manca la voce italiana: aggiungila in Impostazioni › Sintesi vocale",
                    )
                    return@init
                }
                tts?.setOnUtteranceProgressListener(listener)
                seekTo(_state.value?.positionMs ?: 0)
            }
        }
    }

    /** Dove comincia ogni parte, in millisecondi stimati: funzione pura, provata nei test. */
    internal fun timeline(script: List<String>): List<Long> =
        script.runningFold(0L) { acc, text -> acc + text.length * MS_PER_CHAR }.dropLast(1)

    /** La parte e il carattere da cui leggere per arrivare a [positionMs], a inizio parola. */
    internal fun locate(script: List<String>, positionMs: Long): Pair<Int, Int> {
        if (script.isEmpty()) return 0 to 0
        val begins = timeline(script)
        val part = begins.indexOfLast { it <= positionMs }.coerceAtLeast(0)
        val text = script[part]
        val raw = ((positionMs - begins[part]) / MS_PER_CHAR).toInt().coerceIn(0, text.length)
        val char = if (raw == 0) 0 else (text.lastIndexOf(' ', raw - 1) + 1).coerceAtLeast(0)
        return part to char
    }

    /** Salta a [positionMs] del testo, come trascinando la barra di una canzone. */
    fun seekTo(positionMs: Long) {
        if (parts.isEmpty()) return
        val (part, char) = locate(parts, positionMs.coerceIn(0, totalMs))
        speakFrom(part, char)
    }

    fun pause() {
        val now = _state.value ?: return
        if (!now.speaking) return
        tts?.stop()
        _state.value = now.copy(speaking = false)
    }

    fun resume() {
        val now = _state.value ?: return
        NowPlaying.pauseOthers(null, radio = true, recap = false)
        seekTo(now.positionMs)
    }

    fun toggle() {
        val now = _state.value ?: return
        if (now.speaking) pause() else resume()
    }

    /** Passa al tema dopo; dall'ultimo si ferma. */
    fun next() {
        val now = _state.value ?: return
        if (now.part + 1 >= parts.size) stop() else speakFrom(now.part + 1, 0)
    }

    /** Il tema da capo; se si era appena all'inizio, quello prima. */
    fun previous() {
        val now = _state.value ?: return
        val inPart = now.positionMs - (starts.getOrNull(now.part) ?: 0)
        speakFrom(if (inPart < 3_000 && now.part > 0) now.part - 1 else now.part, 0)
    }

    fun stop() {
        tts?.stop()
        _state.value = null
    }

    private fun speakFrom(index: Int, char: Int) {
        val engine = tts ?: return
        if (!ready || index !in parts.indices) return
        engine.stop()
        engine.speak(parts[index].substring(char), TextToSpeech.QUEUE_FLUSH, null, "punto-$index-$char")
        for (i in index + 1 until parts.size) engine.speak(parts[i], TextToSpeech.QUEUE_ADD, null, "punto-$i-0")
        _state.value = RecapListening(index, parts.size, speaking = true, positionMs = position(index, char), durationMs = totalMs)
    }

    private fun position(part: Int, char: Int): Long = (starts.getOrNull(part) ?: 0) + char * MS_PER_CHAR

    /** "punto-3-120" → parte 3, dal carattere 120. */
    private fun parse(id: String?): Pair<Int, Int>? {
        val bits = id?.removePrefix("punto-")?.split('-') ?: return null
        val part = bits.getOrNull(0)?.toIntOrNull() ?: return null
        return part to (bits.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (part, char) = parse(utteranceId) ?: return
            _state.value = RecapListening(part, parts.size, speaking = true, positionMs = position(part, char), durationMs = totalMs)
        }

        // Parola per parola: la barra segue la voce.
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (part, char) = parse(utteranceId) ?: return
            val now = _state.value ?: return
            if (!now.speaking) return
            _state.value = now.copy(part = part, positionMs = position(part, char + start))
        }

        override fun onDone(utteranceId: String?) {
            val (part, _) = parse(utteranceId) ?: return
            if (part >= parts.size - 1) _state.value = null
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.value = _state.value?.copy(speaking = false, error = "La voce si è interrotta")
        }
    }
}
