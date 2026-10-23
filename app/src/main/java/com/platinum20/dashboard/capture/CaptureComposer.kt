package com.platinum20.dashboard.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Il fumetto per scrivere al volo: toccando il + si apre sopra la barra, nello stesso vetro
 * della dettatura, e sale con la tastiera. Chiunque può aprirlo con un testo già pronto.
 */
object CaptureComposer {
    /** Il testo con cui aprirlo; null quando è chiuso. */
    var request by mutableStateOf<String?>(null)
        private set

    fun open(text: String = "") {
        request = text
    }

    fun close() {
        request = null
    }
}
