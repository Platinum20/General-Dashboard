package com.platinum20.dashboard.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Dettatura "come WhatsApp": si tiene premuto il +, si parla, si rilascia.
 * Usa il riconoscimento vocale del telefono senza la sua finestra: lo stato
 * (in ascolto, volume, testo parziale) lo disegna la barra di Canvas.
 */
class Dictation(private val context: Context) {
    var listening by mutableStateOf(false)
        private set

    /** Volume della voce, 0..1: fa pulsare il microfono. */
    var level by mutableFloatStateOf(0f)
        private set

    /** Quello che ha capito finora, mentre si parla. */
    var partial by mutableStateOf("")
        private set

    /** Trascinato verso l'alto: continua anche senza dito, finché non si ritocca il microfono. */
    var locked by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var intent: Intent? = null
    /** Le frasi già capite mentre è bloccato: il riconoscitore si ferma a ogni pausa lunga e riparte. */
    private var heard = ""
    private var onDone: ((String?) -> Unit)? = null
    private var released = false

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Parte l'ascolto; [done] riceve il testo (null se non ha capito niente). */
    fun start(done: (String?) -> Unit) {
        cancel()
        onDone = done
        released = false
        locked = false
        heard = ""
        partial = ""
        level = 0f
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                // Il riconoscitore dà circa da -2 a 10 dB.
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (silence && keepGoing()) return
                finish(partial.takeIf { it.isNotBlank() })
            }
            override fun onResults(results: Bundle?) {
                val text = best(results)
                if (text != null) heard = join(heard, text)
                partial = heard
                if (keepGoing()) return
                finish(heard.takeIf { it.isNotBlank() })
            }
            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let { partial = join(heard, it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Finché si tiene premuto non chiude da solo alla prima pausa.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        this.intent = intent
        listening = true
        r.startListening(intent)
    }

    /** Microfono bloccato: si può staccare il dito e parlare con calma. */
    fun lock() {
        if (listening && !released) locked = true
    }

    /** Bloccato e non ancora inviato: dopo una pausa riascolta invece di chiudere. */
    private fun keepGoing(): Boolean {
        if (!locked || released) return false
        val r = recognizer ?: return false
        intent?.let { r.startListening(it) } ?: return false
        return true
    }

    /** Dito sollevato: smette di ascoltare e aspetta il risultato finale. */
    fun release() {
        if (!listening || released) return
        released = true
        listening = false
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.destroy()
        recognizer = null
        onDone = null
        listening = false
        locked = false
        level = 0f
    }

    /** "Annulla" mentre è bloccato: si butta tutto. */
    fun discard() {
        cancel()
        heard = ""
        partial = ""
    }

    private fun finish(text: String?) {
        val done = onDone
        cancel()
        partial = ""
        done?.invoke(text?.trim()?.replaceFirstChar { it.uppercase() })
    }

    private fun join(a: String, b: String) = if (a.isBlank()) b else "$a $b"

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
