package com.platinum20.dashboard.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Scrivere al volo: toccando il + la barra in basso diventa il campo, nello stesso vetro,
 * e sale con la tastiera. Chiunque può aprirlo con un testo già pronto.
 */
object CaptureComposer {
    /** Il testo con cui aprirlo; null quando è chiuso. */
    var request by mutableStateOf<String?>(null)
        private set

    /** Quello che si sta scrivendo nella barra. */
    var text by mutableStateOf(TextFieldValue(""))

    val isOpen: Boolean get() = request != null

    /** Si sta dettando dentro il campo: la tastiera che si chiude non deve annullare la nota. */
    var dictating by mutableStateOf(false)

    fun open(text: String = "") {
        this.text = TextFieldValue(text, TextRange(text.length))
        request = text
    }

    fun close() {
        request = null
        dictating = false
    }

    /** Dettato tenendo premuto il tasto invia: si aggiunge a quello che si stava scrivendo. */
    fun append(dictated: String) {
        val merged = joinDictated(text.text, dictated)
        text = TextFieldValue(merged, TextRange(merged.length))
        if (request == null) request = merged
    }

    /** Pura: campo più voce, con uno spazio solo dove serve. */
    fun joinDictated(current: String, dictated: String): String {
        val before = current.trimEnd()
        val voice = dictated.trim()
        return when {
            voice.isEmpty() -> current
            before.isEmpty() -> voice
            else -> "$before $voice"
        }
    }
}
