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

/** Cosa sta leggendo la voce. */
data class RecapListening(val part: Int, val total: Int, val speaking: Boolean, val error: String? = null)

/** La voce di Android (italiano, sul telefono e gratis) che legge il copione del punto del giorno. */
object RecapSpeaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var parts: List<String> = emptyList()

    private val _state = MutableStateFlow<RecapListening?>(null)
    val state: StateFlow<RecapListening?> = _state.asStateFlow()

    fun play(context: Context, script: List<String>) {
        if (script.isEmpty()) return
        // Un solo audio alla volta: musica, radio, podcast e video si mettono in pausa.
        NowPlaying.pauseOthers(null, radio = true)
        parts = script
        _state.value = RecapListening(0, script.size, speaking = false)
        val engine = tts
        if (engine != null && ready) speakFrom(0) else if (engine == null) {
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
                speakFrom(_state.value?.part ?: 0)
            }
        }
    }

    /** Passa al tema dopo; dall'ultimo si ferma. */
    fun next() {
        val now = _state.value ?: return
        if (now.part + 1 >= parts.size) stop() else speakFrom(now.part + 1)
    }

    fun stop() {
        tts?.stop()
        _state.value = null
    }

    private fun speakFrom(index: Int) {
        val engine = tts ?: return
        engine.stop()
        parts.drop(index).forEachIndexed { offset, text ->
            engine.speak(text, if (offset == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "punto-${index + offset}")
        }
        _state.value = RecapListening(index, parts.size, speaking = true)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val part = utteranceId?.removePrefix("punto-")?.toIntOrNull() ?: return
            _state.value = RecapListening(part, parts.size, speaking = true)
        }

        override fun onDone(utteranceId: String?) {
            val part = utteranceId?.removePrefix("punto-")?.toIntOrNull() ?: return
            if (part >= parts.size - 1) _state.value = null
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.value = _state.value?.copy(speaking = false, error = "La voce si è interrotta")
        }
    }
}
