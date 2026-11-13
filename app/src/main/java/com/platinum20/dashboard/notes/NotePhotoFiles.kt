package com.platinum20.dashboard.notes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** I file delle foto delle note: ruotarle e condividerle (1.529). Le liste stanno in [NotePhotos]. */
object NotePhotoFiles {
    /**
     * Gira la foto di un quarto a destra e la scrive in un file nuovo (Coil ricorda le immagini per percorso, così si
     * vede subito girata). Restituisce il percorso nuovo, null se non ci è riuscito.
     */
    suspend fun rotate(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val exif = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2400) sample *= 2
            val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })!!
            val turned = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate((exif + 90).toFloat()) }, true)
            val file = File(context.filesDir, "note-photos/${System.currentTimeMillis()}-r.jpg").apply { parentFile?.mkdirs() }
            file.outputStream().use { turned.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            File(path).takeIf { it.parentFile?.name == "note-photos" }?.delete()
            file.absolutePath
        }.getOrNull()
    }

    /** Condivide il testo della nota con le sue foto (copiate in cache, l'unica cartella che il FileProvider apre). */
    fun share(context: Context, text: String, photos: List<String>) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val uris = photos.mapIndexedNotNull { i, path ->
            runCatching {
                val copy = File(dir, "nota-${i + 1}.jpg")
                File(path).copyTo(copy, overwrite = true)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
            }.getOrNull()
        }
        val send = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
            uris.size == 1 -> Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uris[0])
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        if (text.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("foto", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
