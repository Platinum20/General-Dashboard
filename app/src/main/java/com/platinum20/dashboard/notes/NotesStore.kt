package com.platinum20.dashboard.notes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TaskItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Note(
    val id: Long,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
) {
    /** La prima riga fa da titolo. */
    val title: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val body: String get() = text.trim().substringAfter('\n', "").trim()
}

data class NotesData(
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    val openTasks: List<TaskItem> get() = tasks.filter { !it.done }
}

/** Promemoria e note salvati in un file JSON sul telefono. */
object NotesStore {
    private const val FILE = "notes.json"

    private lateinit var file: File
    private val _data = MutableStateFlow(NotesData())
    val data: StateFlow<NotesData> = _data.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, FILE)
        _data.value = runCatching { parse(file.readText()) }.getOrDefault(NotesData())
    }

    private fun newId(): Long = System.nanoTime()

    fun addTask(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        update { it.copy(tasks = listOf(TaskItem(newId(), clean)) + it.tasks) }
    }

    fun toggleTask(id: Long) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun editTask(id: Long, text: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(text = text.trim()) else it })
    }

    fun deleteTask(id: Long) = update { d -> d.copy(tasks = d.tasks.filter { it.id != id }) }

    fun clearDone() = update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }

    /** Salva una nota (nuova se [id] è null) e ne restituisce l'id. Una nota vuota viene eliminata. */
    fun saveNote(id: Long?, text: String): Long? {
        if (text.isBlank()) {
            if (id != null) deleteNote(id)
            return null
        }
        val noteId = id ?: newId()
        update { d ->
            val existing = d.notes.firstOrNull { it.id == noteId }
            if (existing != null && existing.text == text) return@update d
            val note = Note(noteId, text, System.currentTimeMillis(), existing?.pinned ?: false)
            d.copy(notes = listOf(note) + d.notes.filter { it.id != noteId })
        }
        return noteId
    }

    fun togglePin(id: Long) = update { d ->
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }

    fun deleteNote(id: Long) = update { d -> d.copy(notes = d.notes.filter { it.id != id }) }

    fun note(id: Long): Note? = _data.value.notes.firstOrNull { it.id == id }

    private fun update(change: (NotesData) -> NotesData) {
        val next = change(_data.value)
        if (next == _data.value) return
        _data.value = next
        if (::file.isInitialized) {
            runCatching {
                val tmp = File(file.parentFile, "$FILE.tmp")
                tmp.writeText(serialize(next))
                tmp.renameTo(file)
            }
        }
    }

    private fun serialize(d: NotesData): String = JSONObject()
        .put("tasks", JSONArray().apply {
            d.tasks.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("createdAt", it.createdAt))
            }
        })
        .put("notes", JSONArray().apply {
            d.notes.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("updatedAt", it.updatedAt).put("pinned", it.pinned))
            }
        })
        .toString()

    private fun parse(raw: String): NotesData {
        val json = JSONObject(raw)
        val tasks = json.optJSONArray("tasks") ?: JSONArray()
        val notes = json.optJSONArray("notes") ?: JSONArray()
        return NotesData(
            tasks = (0 until tasks.length()).map { i ->
                val o = tasks.getJSONObject(i)
                TaskItem(o.getLong("id"), o.optString("text"), o.optBoolean("done"), o.optLong("createdAt"))
            },
            notes = (0 until notes.length()).map { i ->
                val o = notes.getJSONObject(i)
                Note(o.getLong("id"), o.optString("text"), o.optLong("updatedAt"), o.optBoolean("pinned"))
            },
        )
    }
}
