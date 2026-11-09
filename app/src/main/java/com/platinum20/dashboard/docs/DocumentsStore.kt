package com.platinum20.dashboard.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Un documento: fronte e (se c'è) retro, le foto stanno in filesDir/documents. */
data class Document(
    val id: Long,
    val kind: DocKind,
    val title: String,
    val front: String,
    val back: String? = null,
    val number: String? = null,
    val expiry: LocalDate? = null,
    val createdAt: Long,
    /** La data "Scade …" nel calendario, per spostarla o toglierla insieme al documento. */
    val countdownId: Long? = null,
    /** Le foto intere, prima del ritaglio: "Foto intera" torna qui se il ritaglio ha sbagliato. */
    val frontFull: String? = null,
    val backFull: String? = null,
    /** Già raddrizzato e ritagliato (dalla 1.358): i documenti di prima lo fanno alla prima apertura. */
    val straight: Boolean = true,
) {
    /** Il nome del PDF condiviso: "Patente.pdf", "Carta d'identità.pdf". */
    val pdfName: String get() = title.replace(Regex("""[\\/:*?"<>|]"""), "").trim().ifEmpty { "Documento" } + ".pdf"
}

/**
 * I documenti fotografati dalla graffetta. Restano solo sul telefono (un file JSON e le foto nella
 * cartella privata dell'app); il PDF si crea al momento di condividerlo.
 */
object DocumentsStore {
    private const val FILE = "documents.json"
    private const val DIR = "documents"

    /** Dopo un fronte, la foto che arriva entro questo tempo è il suo retro. */
    const val BACK_WINDOW_MS = 15 * 60_000L

    private lateinit var file: File
    private lateinit var dir: File
    private val _docs = MutableStateFlow<List<Document>>(emptyList())
    val docs: StateFlow<List<Document>> = _docs.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        val app = context.applicationContext
        file = File(app.filesDir, FILE)
        dir = File(app.filesDir, DIR).apply { mkdirs() }
        _docs.value = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    fun doc(id: Long): Document? = _docs.value.firstOrNull { it.id == id }

    /** Il documento appena fotografato che aspetta ancora il retro, se c'è. */
    fun waitingBack(now: Long = System.currentTimeMillis()): Document? = waitingBack(_docs.value, now)

    /** Pura: il più recente senza retro, fotografato da meno di [BACK_WINDOW_MS]. */
    fun waitingBack(docs: List<Document>, now: Long): Document? =
        docs.filter { it.back == null && now - it.createdAt in 0..BACK_WINDOW_MS }.maxByOrNull { it.createdAt }

    /** Un fronte nuovo: diventa un documento e, se ha la scadenza, una data nel calendario. */
    fun addFront(photo: Kept, scan: DocScan): Document {
        val now = System.currentTimeMillis()
        val title = uniqueTitle(scan.kind.label, _docs.value)
        val doc = Document(now, scan.kind, title, photo.path, number = scan.number, createdAt = now, frontFull = photo.full)
            .let { withExpiry(it, scan.expiry) }
        update { listOf(doc) + it }
        return doc
    }

    /** Il retro del documento [id]: completa numero e scadenza se dal fronte non si leggevano. */
    fun addBack(id: Long, photo: Kept, scan: DocScan?) {
        val doc = doc(id) ?: return
        listOfNotNull(doc.back, doc.backFull).forEach { File(it).delete() }
        var next = doc.copy(back = photo.path, backFull = photo.full, number = doc.number ?: scan?.number)
        if (doc.expiry == null && scan?.expiry != null) next = withExpiry(next, scan.expiry)
        // Se il fronte non si capiva ("Documento") il retro dice cos'è.
        if (doc.kind == DocKind.OTHER && scan != null && scan.kind != DocKind.OTHER) {
            next = next.copy(kind = scan.kind, title = uniqueTitle(scan.kind.label, _docs.value.filter { it.id != id }))
        }
        update { list -> list.map { if (it.id == id) next else it } }
    }

    fun removeBack(id: Long) {
        val doc = doc(id) ?: return
        listOfNotNull(doc.back, doc.backFull).forEach { File(it).delete() }
        update { list -> list.map { if (it.id == id) it.copy(back = null, backFull = null) else it } }
    }

    fun rename(id: Long, title: String) {
        val clean = title.trim().ifEmpty { return }
        val doc = doc(id) ?: return
        val next = doc.copy(title = clean).let { withExpiry(it, it.expiry) }
        update { list -> list.map { if (it.id == id) next else it } }
    }

    fun setNumber(id: Long, number: String) = update { list ->
        list.map { if (it.id == id) it.copy(number = number.trim().ifEmpty { null }) else it }
    }

    fun setExpiry(id: Long, expiry: LocalDate?) {
        val doc = doc(id) ?: return
        val next = withExpiry(doc, expiry)
        update { list -> list.map { if (it.id == id) next else it } }
    }

    /** Elimina documento, foto e data nel calendario. */
    fun delete(id: Long) {
        val doc = doc(id) ?: return
        listOfNotNull(doc.front, doc.back, doc.frontFull, doc.backFull).forEach { File(it).delete() }
        doc.countdownId?.let(NotesStore::deleteTask)
        update { list -> list.filter { it.id != id } }
    }

    /** La scadenza va (o si sposta, o si toglie) nel calendario come data "Scade …". */
    private fun withExpiry(doc: Document, expiry: LocalDate?): Document {
        if (expiry == null) {
            doc.countdownId?.let(NotesStore::deleteTask)
            return doc.copy(expiry = null, countdownId = null)
        }
        val id = NotesStore.saveDate(doc.countdownId, expiryTitle(doc.title), expiry, yearly = false)
        return doc.copy(expiry = expiry, countdownId = id)
    }

    fun expiryTitle(title: String) = "Scade: $title"

    /** "Patente", e se c'è già "Patente 2": due tessere sanitarie (di famiglia) non si confondono. */
    fun uniqueTitle(base: String, docs: List<Document>): String {
        val taken = docs.map { it.title }.toSet()
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it !in taken }
    }

    /** Una foto tenuta: quella ritagliata che si vede e l'intera da cui viene (null se non c'era niente da tagliare). */
    data class Kept(val path: String, val full: String?)

    /**
     * Copia la foto nell'app già dritta (la fotocamera la salva ruotata con l'EXIF) e non più grande
     * di 2000 px, poi la raddrizza e la ritaglia sul bordo del documento ([DocCrop]). L'intera resta
     * accanto, per tornarci se il ritaglio ha sbagliato.
     */
    suspend fun keep(context: Context, uri: Uri, kind: DocKind): Kept? = withContext(Dispatchers.IO) {
        runCatching {
            init(context)
            val resolver = context.contentResolver
            val rotation = resolver.openInputStream(uri)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2
            val raw = resolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }!!
            val scale = minOf(1f, 2000f / maxOf(raw.width, raw.height))
            val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
            val bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            val full = write(bitmap, "full")
            val cut = DocCrop.straighten(bitmap, kind)
            if (cut === bitmap) Kept(full, null) else Kept(write(cut), full)
        }.getOrNull()
    }

    /** Un file nuovo a ogni modifica: le immagini già mostrate non restano vecchie nella cache. */
    private fun write(bitmap: Bitmap, tag: String = "doc"): String {
        val out = File(dir, "$tag-${System.nanoTime()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return out.absolutePath
    }

    /** I documenti fotografati prima della 1.358: raddrizzati e ritagliati una volta, l'intera resta. */
    suspend fun straighten(context: Context, id: Long) = withContext(Dispatchers.IO) {
        init(context)
        val doc = doc(id)?.takeIf { !it.straight } ?: return@withContext
        suspend fun side(path: String?): Kept? {
            path ?: return null
            val src = BitmapFactory.decodeFile(path) ?: return Kept(path, null)
            val cut = runCatching { DocCrop.straighten(src, doc.kind) }.getOrDefault(src)
            return if (cut === src) Kept(path, null) else Kept(write(cut), path)
        }
        val front = side(doc.front) ?: return@withContext
        val back = side(doc.back)
        update { list ->
            list.map {
                if (it.id != id) it
                else it.copy(front = front.path, frontFull = front.full, back = back?.path, backFull = back?.full, straight = true)
            }
        }
    }

    /** Un quarto di giro in senso orario del fronte o del retro. */
    suspend fun turn(context: Context, id: Long, back: Boolean) = withContext(Dispatchers.IO) {
        init(context)
        val doc = doc(id) ?: return@withContext
        val path = (if (back) doc.back else doc.front) ?: return@withContext
        val src = BitmapFactory.decodeFile(path) ?: return@withContext
        val next = write(DocCrop.turnRight(src))
        // L'intera non si tocca mai: se questa è l'intera (nessun ritaglio), resta da parte come intera.
        val full = (if (back) doc.backFull else doc.frontFull)
        if (full != null) File(path).delete()
        val keepFull = full ?: path
        update { list ->
            list.map {
                when {
                    it.id != id -> it
                    back -> it.copy(back = next, backFull = keepFull)
                    else -> it.copy(front = next, frontFull = keepFull)
                }
            }
        }
    }

    /** Torna alla foto intera, se il ritaglio ha tagliato male. */
    fun useFull(id: Long, back: Boolean) {
        val doc = doc(id) ?: return
        val full = (if (back) doc.backFull else doc.frontFull) ?: return
        (if (back) doc.back else doc.front)?.let { File(it).delete() }
        update { list ->
            list.map {
                when {
                    it.id != id -> it
                    back -> it.copy(back = full, backFull = null)
                    else -> it.copy(front = full, frontFull = null)
                }
            }
        }
    }

    /**
     * Pura: dove va una foto sul foglio A4 (595 × 842 punti), in punti. Una tessera ritagliata va a
     * grandezza vera (85,6 mm), come una fotocopia; un foglio o una foto intera riempie la sua metà.
     */
    fun pdfSize(kind: DocKind, cropped: Boolean, width: Int, height: Int, slotW: Float, slotH: Float): Pair<Float, Float> {
        val a = width.toFloat() / height
        val real = when {
            !cropped -> null
            kind == DocKind.PASSPORT && a in 1.25f..1.75f -> 125f
            kind !in setOf(DocKind.VEHICLE, DocKind.INSURANCE, DocKind.OTHER) && a in DocCrop.CARD_RANGE -> 85.6f
            else -> null
        }
        val fit = minOf(slotW / width, slotH / height)
        val scale = real?.let { minOf(it / 25.4f * 72f / width, fit) } ?: fit
        return width * scale to height * scale
    }

    /** Il PDF come una fotocopia: fronte e retro sullo stesso foglio A4, uno sotto l'altro. */
    suspend fun pdf(context: Context, doc: Document): File = withContext(Dispatchers.IO) {
        val dirOut = File(context.cacheDir, "documents").apply { mkdirs() }
        dirOut.listFiles()?.forEach { it.delete() }
        val out = File(dirOut, doc.pdfName)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            val photos = listOfNotNull(doc.front to (doc.frontFull != null), doc.back?.let { it to (doc.backFull != null) })
                .mapNotNull { (path, cropped) -> BitmapFactory.decodeFile(path)?.let { it to cropped } }
            val margin = 56f
            val gap = 28f
            val slotW = 595f - margin * 2
            val slotH = (842f - margin * 2 - gap) / 2
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var top = margin
            photos.forEach { (bmp, cropped) ->
                val (w, h) = pdfSize(doc.kind, cropped, bmp.width, bmp.height, slotW, slotH)
                // Al massimo 300 dpi: il PDF resta leggero da mandare.
                val px = (w / 72f * 300f).toInt()
                val img = if (bmp.width > px * 1.2f) Bitmap.createScaledBitmap(bmp, px, maxOf(1, (bmp.height * px / bmp.width.toFloat()).toInt()), true) else bmp
                page.canvas.drawBitmap(img, null, RectF((595f - w) / 2, top, (595f + w) / 2, top + h), paint)
                top += h + gap
            }
            pdf.finishPage(page)
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        out
    }

    /** Il PDF nei Download del telefono (Android 10 e dopo, senza permessi): si apre da File. */
    suspend fun savePdf(context: Context, doc: Document): Boolean = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return@withContext false
        runCatching {
            val file = pdf(context, doc)
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, doc.pdfName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
            true
        }.getOrDefault(false)
    }

    private fun update(change: (List<Document>) -> List<Document>) {
        val next = change(_docs.value)
        if (next == _docs.value) return
        _docs.value = next
        if (::file.isInitialized) runCatching {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(serialize(next))
            tmp.renameTo(file)
        }
    }

    internal fun serialize(docs: List<Document>): String = JSONArray().apply {
        docs.forEach { d ->
            put(JSONObject()
                .put("id", d.id).put("kind", d.kind.name).put("title", d.title).put("front", d.front)
                .put("back", d.back).put("number", d.number).put("expiry", d.expiry?.toString())
                .put("createdAt", d.createdAt).put("countdownId", d.countdownId)
                .put("frontFull", d.frontFull).put("backFull", d.backFull).put("straight", d.straight))
        }
    }.toString()

    internal fun parse(raw: String): List<Document> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun str(key: String) = if (o.isNull(key)) null else o.optString(key).ifEmpty { null }
            Document(
                id = o.getLong("id"),
                kind = DocKind.entries.firstOrNull { it.name == o.optString("kind") } ?: DocKind.OTHER,
                title = o.getString("title"),
                front = o.getString("front"),
                back = str("back"),
                number = str("number"),
                expiry = str("expiry")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                createdAt = o.optLong("createdAt"),
                countdownId = if (o.isNull("countdownId")) null else o.optLong("countdownId"),
                frontFull = str("frontFull"),
                backFull = str("backFull"),
                straight = o.optBoolean("straight", false),
            )
        }
    }
}
