package com.platinum20.dashboard.notes

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

/** Foto dello scontrino → testo (riconosciuto sul telefono, senza rete) → [ReceiptGuess]. */
object ReceiptReader {

    /** Dove la fotocamera salva la foto: un solo file in cache, sovrascritto ogni volta e cancellato dopo la lettura. */
    fun photoUri(context: Context): Uri {
        val file = File(context.cacheDir, "receipts/scontrino.jpg").apply { parentFile?.mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun read(context: Context, uri: Uri): ReceiptGuess? = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.bottom) }
            }
            Receipt.parse(Receipt.rows(lines))
        } finally {
            recognizer.close()
            // La foto non si tiene: serviva solo a leggere il totale.
            File(context.cacheDir, "receipts/scontrino.jpg").delete()
        }
    }.getOrNull()
}
