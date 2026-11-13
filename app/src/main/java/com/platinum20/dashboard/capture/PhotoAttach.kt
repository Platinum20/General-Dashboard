package com.platinum20.dashboard.capture

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.platinum20.dashboard.docs.DocReader
import com.platinum20.dashboard.docs.DocScan
import com.platinum20.dashboard.docs.DocSide
import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.OcrLine
import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.OcrRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/**
 * La graffetta della barra: una foto qualsiasi viene letta sul telefono (senza rete) e Canvas
 * capisce cosa farne. Un biglietto con la data o un codice di
 * spedizione finiscono nella barra come testo, un documento (fronte e poi retro) va in Documenti;
 * tutto il resto si salva come nota con la foto.
 */
object PhotoAttach {
    sealed interface Found {
        /** Testo da controllare e inviare dalla barra. */
        data class Text(val text: String) : Found
        /** Niente da fare: la foto diventa una nota, con il testo letto (se c'è) per ritrovarla cercando. */
        data class Photo(val text: String) : Found
        /** Il fronte di un documento (carta d'identità, patente…): va in Documenti. */
        data class Document(val scan: DocScan) : Found
        /** Il retro del documento appena fotografato; [scan] se si riconosce anche da solo. */
        data class Back(val scan: DocScan?) : Found
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
            OcrRows.rows(lines)
        } finally {
            recognizer.close()
        }
    }.getOrDefault(emptyList())

    /**
     * Pura: dalle righe lette a cosa farne. Con [waitingBack] (un documento appena fotografato senza
     * retro) la foto che non è il fronte di un altro documento è il suo retro: quello della patente
     * ha date e sigle che altrimenti finirebbero nella barra.
     */
    fun decide(rows: List<String>, now: LocalDateTime = LocalDateTime.now(), waitingBack: Boolean = false): Found {
        val doc = DocReader.read(rows, now.toLocalDate())
        if (doc?.side == DocSide.FRONT) return Found.Document(doc)
        if (waitingBack) return Found.Back(doc)
        if (doc != null) return Found.Document(doc)
        val clean = rows.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val line = clean.joinToString(" ")
        if (Carrier.findCode(line) != null || QuickParse.parse(line, now).due != null) return Found.Text(cut(line, LINE_CHARS))
        return Found.Photo(cut(clean.joinToString("\n"), NOTE_CHARS))
    }

    private fun cut(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max).substringBeforeLast(' ').trimEnd() + "…"

    /**
     * Copia la foto dentro l'app: quella della galleria può sparire, quella della fotocamera è in cache. Il nome ha un pezzo
     * a caso: più foto scelte insieme arrivano nello stesso millisecondo (1.529).
     */
    suspend fun keep(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "note-photos/${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}.jpg").apply { parentFile?.mkdirs() }
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }.getOrNull()
    }
}
