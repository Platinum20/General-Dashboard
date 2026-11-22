package com.platinum20.dashboard.notes

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Esporta in PDF" (1.588): titolo, testo (titoletti in grassetto, punti, numeri, spunte, citazioni) e foto su fogli A4.
 * Si salva nei Download del telefono, come i Documenti.
 */
object NotePdf {
    private const val W = 595
    private const val H = 842
    private const val M = 48f

    /** Pura: le righe della nota come si leggono sul foglio, con il loro tipo. */
    fun rows(body: String): List<Pair<NoteLines.Kind, String>> = NoteLines.parse(body).map { l ->
        val text = NoteLines.plainLine(body.substring(l.start, l.end))
        val pad = "    ".repeat(l.level)
        l.kind to when (l.kind) {
            NoteLines.Kind.BULLET -> "$pad•  $text"
            NoteLines.Kind.NUMBER -> "$pad${l.number}.  $text"
            NoteLines.Kind.CHECK -> pad + (if (l.done) "☑  " else "☐  ") + text
            NoteLines.Kind.CALLOUT -> NoteLines.calloutEmoji(body.substring(l.start, l.end)) + "  " + text
            NoteLines.Kind.QUOTE -> "“$text”"
            NoteLines.Kind.DIVIDER -> "────────────"
            NoteLines.Kind.TABLE -> NoteTable.cells(body.substring(l.start, l.end)).joinToString("   ·   ")
            else -> text
        }
    }

    /** Pura: il nome del file, dal titolo ("Spesa di casa.pdf"). */
    fun fileName(title: String): String =
        (title.replace(Regex("[\\\\/:*?\"<>|\\n]"), " ").trim().take(60).ifEmpty { "Nota" }) + ".pdf"

    suspend fun make(context: Context, title: String, body: String, photos: List<String>): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "note-pdf").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, fileName(title))
        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = M
        fun newPage() {
            page?.let { pdf.finishPage(it) }
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
            page!!.canvas.drawColor(android.graphics.Color.WHITE)
            y = M
        }
        fun block(text: String, size: Float, bold: Boolean = false, italic: Boolean = false, gap: Float = 6f) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                color = android.graphics.Color.rgb(27, 27, 27)
                typeface = Typeface.create(Typeface.SANS_SERIF, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
            }
            val layout = StaticLayout.Builder.obtain(text.ifEmpty { " " }, 0, text.ifEmpty { " " }.length, paint, (W - 2 * M).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).build()
            if (y + layout.height > H - M) newPage()
            val c = page!!.canvas
            c.save()
            c.translate(M, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gap
        }
        try {
            newPage()
            block(title.ifBlank { "Nota" }, 22f, bold = true, gap = 14f)
            rows(body).forEach { (kind, text) ->
                when (kind) {
                    NoteLines.Kind.HEADING -> block(text, 16f, bold = true, gap = 8f)
                    NoteLines.Kind.QUOTE -> block(text, 12f, italic = true)
                    else -> block(text, 12f)
                }
            }
            photos.forEach { path ->
                val bmp = BitmapFactory.decodeFile(path) ?: return@forEach
                val maxW = W - 2 * M
                val maxH = (H - 2 * M) * 0.6f
                val fit = minOf(maxW / bmp.width, maxH / bmp.height)
                val w = bmp.width * fit
                val h = bmp.height * fit
                if (y + h > H - M) newPage()
                page!!.canvas.drawBitmap(bmp, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                y += h + 12f
                bmp.recycle()
            }
            page?.let { pdf.finishPage(it) }
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        out
    }

    /** Il PDF nei Download (Android 10 e dopo, senza permessi). */
    suspend fun saveToDownloads(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return@withContext false
        runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
            true
        }.getOrDefault(false)
    }
}
