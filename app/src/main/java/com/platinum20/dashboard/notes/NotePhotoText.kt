package com.platinum20.dashboard.notes

import android.content.Context
import android.net.Uri
import com.platinum20.dashboard.capture.PhotoAttach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.File

/**
 * Il testo scritto nelle foto delle note (Antonio, 2026-11-23, idea 30): la lavagna, lo scontrino della garanzia, il
 * cartello. Canvas legge ogni foto una volta sola, sul telefono, e lo tiene per percorso in un file suo: così Cerca
 * ovunque trova la nota anche per quello che c'è nella foto. Si rifà da solo (non va nel backup): basta rileggere le foto.
 */
object NotePhotoText {
    private const val FILE = "note-photo-text.json"
    /** Foto lette per giro: le altre al giro dopo, così il telefono non lavora a lungo tutto insieme. */
    private const val PER_RUN = 12

    private var file: File? = null
    /** Quello che c'è nel file: si riscrive solo se è cambiato. */
    private var saved: Map<String, String> = emptyMap()
    private val _texts = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Percorso della foto → testo letto ("" = letta, niente scritto). */
    val texts: StateFlow<Map<String, String>> = _texts.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        saved = runCatching { parse(f.readText()) }.getOrDefault(emptyMap())
        _texts.value = saved
    }

    /** Il testo di una foto, se l'ha già letta. */
    fun of(path: String): String? = _texts.value[path]?.takeIf { it.isNotBlank() }

    /** Pura: le foto delle note che non sono ancora state lette, dalla nota più nuova. */
    fun missing(notes: List<Note>, known: Set<String>): List<String> =
        notes.sortedByDescending { it.updatedAt }.flatMap(NotesSearch::photosOf).distinct().filter { it !in known }

    /** Pura: tiene solo le foto ancora in qualche nota (quelle tolte non servono più). */
    fun prune(texts: Map<String, String>, notes: List<Note>): Map<String, String> {
        val used = notes.flatMap(NotesSearch::photosOf).toSet()
        return texts.filterKeys { it in used }
    }

    /** Legge le foto nuove delle note (al più [PER_RUN] per volta) e salva. */
    suspend fun scan(context: Context, notes: List<Note>) {
        init(context)
        val todo = missing(notes, _texts.value.keys).take(PER_RUN)
        var next = prune(_texts.value, notes)
        _texts.value = next
        for (path in todo) {
            yield()
            // Un file che non c'è più conta come letto: se no tornerebbe in testa a ogni giro.
            val rows = if (File(path).exists()) PhotoAttach.rows(context, Uri.fromFile(File(path))) else emptyList()
            next = next + (path to rows.joinToString("\n"))
            // Subito in memoria: se il giro si ferma a metà (la nota cambia) quello letto resta.
            _texts.value = next
        }
        if (next == saved) return
        withContext(Dispatchers.IO) { runCatching { file?.writeText(serialize(next)); saved = next } }
    }

    internal fun serialize(texts: Map<String, String>): String =
        JSONObject().apply { texts.forEach { (k, v) -> put(k, v) } }.toString()

    internal fun parse(raw: String): Map<String, String> {
        val o = JSONObject(raw)
        return o.keys().asSequence().associateWith { o.optString(it) }
    }
}
