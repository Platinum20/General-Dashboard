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
    fun addFront(photo: String, scan: DocScan): Document {
        val now = System.currentTimeMillis()
        val title = uniqueTitle(scan.kind.label, _docs.value)
        val doc = Document(now, scan.kind, title, photo, number = scan.number, createdAt = now)
            .let { withExpiry(it, scan.expiry) }
        update { listOf(doc) + it }
        return doc
    }

    /** Il retro del documento [id]: completa numero e scadenza se dal fronte non si leggevano. */
    fun addBack(id: Long, photo: String, scan: DocScan?) {
        val doc = doc(id) ?: return
        doc.back?.let { File(it).delete() }
        var next = doc.copy(back = photo, number = doc.number ?: scan?.number)
        if (doc.expiry == null && scan?.expiry != null) next = withExpiry(next, scan.expiry)
        // Se il fronte non si capiva ("Documento") il retro dice cos'è.
        if (doc.kind == DocKind.OTHER && scan != null && scan.kind != DocKind.OTHER) {
            next = next.copy(kind = scan.kind, title = uniqueTitle(scan.kind.label, _docs.value.filter { it.id != id }))
        }
        update { list -> list.map { if (it.id == id) next else it } }
    }

    fun removeBack(id: Long) {
        val doc = doc(id) ?: return
        doc.back?.let { File(it).delete() }
        update { list -> list.map { if (it.id == id) it.copy(back = null) else it } }
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
        listOfNotNull(doc.front, doc.back).forEach { File(it).delete() }
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

    /**
     * Copia la foto nell'app già dritta (la fotocamera la salva ruotata con l'EXIF) e non più grande
     * di 2000 px: così si vede uguale ovunque e il PDF resta leggero.
     */
    suspend fun keep(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
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
            val out = File(dir, "${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            out.absolutePath
        }.getOrNull()
    }

    /** Il PDF come una fotocopia: fronte e retro sulla stessa pagina A4. */
    suspend fun pdf(context: Context, doc: Document): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "documents/${doc.pdfName}").apply { parentFile?.mkdirs() }
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val photos = listOfNotNull(doc.front, doc.back).mapNotNull { BitmapFactory.decodeFile(it) }
            val margin = 48f
            val slot = (842f - margin * (photos.size + 1)) / photos.size.coerceAtLeast(1)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            photos.forEachIndexed { i, bmp ->
                // Mai più larga di un foglio e, per una tessera, non più grande del doppio del vero.
                val scale = minOf((595f - margin * 2) / bmp.width, minOf(slot, 340f) / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val top = margin + i * (slot + margin)
                page.canvas.drawBitmap(bmp, null, RectF((595f - w) / 2, top, (595f + w) / 2, top + h), paint)
            }
            pdf.finishPage(page)
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        out
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
                .put("createdAt", d.createdAt).put("countdownId", d.countdownId))
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
            )
        }
    }
}
