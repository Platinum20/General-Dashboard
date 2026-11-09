package com.platinum20.dashboard.tickets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.platinum20.dashboard.calendar.EventDetail
import com.platinum20.dashboard.mail.GmailImap
import com.platinum20.dashboard.mail.MimeParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId

/**
 * Il QR del biglietto di un evento, preso dalla mail (Gmail con password per app) e salvato sul telefono:
 * la seconda volta si apre subito, anche senza rete (in stazione capita).
 */
object TicketQrStore {
    private const val PREFS = "ticket_qr"
    private const val MAX_CODES = 4
    /** Se la mail non c'era, si riprova solo dopo un po': il biglietto può arrivare più tardi. */
    private const val RETRY_MS = 6 * 3_600_000L
    private val lock = Mutex()

    private fun dir(context: Context) = File(context.filesDir, "tickets").apply { mkdirs() }

    /** I codici già salvati per l'evento, in ordine. */
    fun cached(context: Context, eventId: Long): List<File> =
        (0 until MAX_CODES).map { File(dir(context), "$eventId-$it.png") }.filter { it.exists() }

    /** I codici dell'evento: quelli salvati, altrimenti cercati nella mail. Vuoto se non ci sono o Gmail non è collegato. */
    suspend fun find(context: Context, event: EventDetail): List<File> {
        cached(context, event.id).takeIf { it.isNotEmpty() }?.let { return it }
        val query = TicketMail.query(event.title, event.description, event.begin, ZoneId.systemDefault()) ?: return emptyList()
        GmailImap.init(context)
        if (!GmailImap.linked.value || GmailImap.rejected.value) return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val missKey = "miss_${event.id}"
        if (System.currentTimeMillis() - prefs.getLong(missKey, 0L) < RETRY_MS) return emptyList()
        return lock.withLock {
            cached(context, event.id).takeIf { it.isNotEmpty() }?.let { return@withLock it }
            val codes = runCatching { search(context, query) }.getOrDefault(emptyList())
            if (codes.isEmpty()) {
                prefs.edit().putLong(missKey, System.currentTimeMillis()).apply()
                emptyList()
            } else withContext(Dispatchers.IO) {
                codes.take(MAX_CODES).mapIndexed { i, bmp ->
                    File(dir(context), "${event.id}-$i.png").also { f ->
                        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        }
    }

    /** Le mail più recenti che corrispondono, una alla volta, finché una ha dei codici. */
    private suspend fun search(context: Context, query: String): List<Bitmap> {
        val session = GmailImap.session(context) ?: return emptyList()
        return session.use { s ->
            val uids = withContext(Dispatchers.IO) { s.search(query) }.sortedDescending().take(4)
            for (uid in uids) {
                val raw = withContext(Dispatchers.IO) { s.raw(uid) } ?: continue
                val codes = codes(context, raw)
                if (codes.isNotEmpty()) return@use codes
            }
            emptyList()
        }
    }

    /** Tutti i codici (QR, Aztec…) nelle immagini e nei PDF della mail, ritagliati, senza doppioni. */
    private suspend fun codes(context: Context, raw: String): List<Bitmap> {
        val pages = withContext(Dispatchers.IO) { pages(context, raw) }
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417)
                .build()
        )
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Bitmap>()
        try {
            for (page in pages) {
                val found = runCatching { scanner.process(InputImage.fromBitmap(page, 0)).await() }.getOrDefault(emptyList())
                for (code in found) {
                    val box = code.boundingBox ?: continue
                    val key = code.rawValue ?: code.rawBytes?.contentToString() ?: box.toString()
                    if (!seen.add(key)) continue
                    val (l, t, r, b) = TicketMail.crop(box.left, box.top, box.right, box.bottom, page.width, page.height).toList()
                    if (r - l < 40 || b - t < 40) continue
                    out += Bitmap.createBitmap(page, l, t, r - l, b - t)
                }
            }
        } finally {
            scanner.close()
        }
        return out
    }

    /** Le "pagine" in cui cercare: le immagini su fondo bianco, ogni pagina dei PDF, le immagini remote col codice. */
    private fun pages(context: Context, raw: String): List<Bitmap> {
        val out = mutableListOf<Bitmap>()
        for (part in MimeParse.binaryParts(raw)) {
            if (part.type == "application/pdf") out += pdfPages(context, part.bytes)
            else image(part.bytes)?.let { out += it }
        }
        MimeParse.htmlBody(raw)?.let { html ->
            TicketMail.codeImageUrls(html).forEach { url -> download(url)?.let(::image)?.let { out += it } }
        }
        return out
    }

    /** Un'immagine su fondo bianco (i QR trasparenti non si leggono); i loghi piccoli si saltano. */
    private fun image(bytes: ByteArray): Bitmap? {
        val src = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        if (src.width < 80 || src.height < 80) return null
        return Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.WHITE)
            Canvas(it).drawBitmap(src, 0f, 0f, null)
        }
    }

    private fun pdfPages(context: Context, bytes: ByteArray): List<Bitmap> {
        val file = File(context.cacheDir, "ticket.pdf")
        val out = mutableListOf<Bitmap>()
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    for (i in 0 until minOf(renderer.pageCount, 4)) {
                        val page = renderer.openPage(i)
                        try {
                            // Abbastanza grande perché un QR piccolo stampato nel PDF resti nitido.
                            val width = 1800
                            val height = (page.height * width.toFloat() / page.width).toInt().coerceIn(1, 4000)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            out += bmp
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (_: Exception) {
            // PDF rotto o tagliato: si va avanti con il resto.
        } finally {
            file.delete()
        }
        return out
    }

    private fun download(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) return@runCatching null
            conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16_384)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > 3_000_000) return@runCatching null
                }
                buf.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
