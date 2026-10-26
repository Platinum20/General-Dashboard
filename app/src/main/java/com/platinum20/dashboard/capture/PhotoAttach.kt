package com.platinum20.dashboard.capture

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.platinum20.dashboard.notes.OcrLine
import com.platinum20.dashboard.notes.Receipt
import kotlinx.coroutines.tasks.await

/**
 * La graffetta della barra: una foto qualsiasi diventa testo (letto sul telefono, senza rete) e
 * poi è il solito "+" a capire cosa farne. Uno scontrino diventa una spesa, il resto passa da [CaptureGuess].
 */
object PhotoAttach {
    /** Oltre questo la foto è un documento: basta l'inizio, il resto si scrive a mano se serve. */
    private const val MAX_CHARS = 600

    /** Poche righe (un biglietto, un invito) vanno su una riga sola, così la data diventa una scadenza. */
    private const val SHORT_ROWS = 3

    suspend fun read(context: Context, uri: Uri): String? = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.bottom) }
            }
            captureText(Receipt.rows(lines))
        } finally {
            recognizer.close()
        }
    }.getOrNull()

    /** Pura: le righe lette diventano il testo della barra; null se nella foto non c'è niente da leggere. */
    fun captureText(rows: List<String>): String? {
        Receipt.parse(rows)?.let { return it.captureText }
        val clean = rows.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        val text = if (clean.size <= SHORT_ROWS) clean.joinToString(" ") else clean.joinToString("\n")
        return if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS).substringBeforeLast(' ').trimEnd() + "…"
    }
}
