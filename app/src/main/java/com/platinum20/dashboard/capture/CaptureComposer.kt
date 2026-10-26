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

    fun open(text: String = "") {
        this.text = TextFieldValue(text, TextRange(text.length))
        request = text
    }

    fun close() {
        request = null
    }
}
