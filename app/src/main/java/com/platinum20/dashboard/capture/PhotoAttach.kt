package com.platinum20.dashboard.capture

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.OcrLine
import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * La graffetta della barra: una foto qualsiasi viene letta sul telefono (senza rete) e Canvas
 * capisce cosa farne. Uno scontrino diventa una spesa, un biglietto con la data o un codice di
 * spedizione finiscono nella barra come testo; tutto il resto si salva come nota con la foto.
 */
object PhotoAttach {
    sealed interface Found {
        /** Testo da controllare e inviare dalla barra. */
        data class Text(val text: String) : Found
        /** Niente da fare: la foto diventa una nota, con il testo letto (se c'è) per ritrovarla cercando. */
        data class Photo(val text: String) : Found
    }

    /** Il testo nella barra resta su una riga, se no diventerebbe una nota. */
    private const val LINE_CHARS = 110

    /** Il testo letto che si tiene nella nota con la foto, per la ricerca. */
    private const val NOTE_CHARS = 600

    suspend fun rows(context: Context, uri: Uri): List<String> = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.bottom) }
            }
            Receipt.rows(lines)
        } finally {
            recognizer.close()
        }
    }.getOrDefault(emptyList())

    /** Pura: dalle righe lette a cosa farne. */
    fun decide(rows: List<String>, now: LocalDateTime = LocalDateTime.now()): Found {
        Receipt.parse(rows)?.let { return Found.Text(it.captureText) }
        val clean = rows.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val line = clean.joinToString(" ")
        if (Carrier.findCode(line) != null || QuickParse.parse(line, now).due != null) return Found.Text(cut(line, LINE_CHARS))
        return Found.Photo(cut(clean.joinToString("\n"), NOTE_CHARS))
    }

    private fun cut(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max).substringBeforeLast(' ').trimEnd() + "…"

    /** Copia la foto dentro l'app: quella della galleria può sparire, quella della fotocamera è in cache. */
    suspend fun keep(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "note-photos/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }.getOrNull()
    }
}
