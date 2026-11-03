package com.platinum20.dashboard.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Condivide un testo lungo come file .txt: messo dentro l'Intent (fino a qualche MB di risposte
 * catturate) supera il limite di Android e fa chiudere l'app che lo riceve (successo con Revolut).
 */
internal fun shareTextFile(context: Context, name: String, text: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, name).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(name, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/**
 * Salva il testo come file nella cartella Download del telefono, da allegare dove serve (Antonio
 * non sapeva come mandarlo condividendolo). Da Android 10 senza permessi; prima scrive nella
 * cartella dell'app dentro Android/data. Restituisce dove l'ha messo, null se non è riuscito.
 */
internal fun saveTextToDownloads(context: Context, name: String, text: String): String? = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return null
        "Download"
    } else {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: return null
        File(dir, name).writeText(text)
        dir.absolutePath
    }
}.getOrNull()

/** Il file salvato porta l'ora, così salvarlo due volte non lo sovrascrive né lo rinomina a caso. */
internal fun stampedName(base: String, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String =
    "$base-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".txt"
