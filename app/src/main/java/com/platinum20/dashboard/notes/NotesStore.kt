package com.platinum20.dashboard.notes

import android.content.Context
import com.platinum20.dashboard.widget.DashboardWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class TaskItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Scadenza con promemoria (millisecondi), null se non c'è. */
    val dueAt: Long? = null,
    /** Se si ripete, spuntandolo la scadenza passa alla volta dopo invece di chiuderlo. */
    val repeat: Repeat? = null,
    /** Scadenza originale di un ricorrente rimandato dalla notifica: la volta dopo si calcola da qui. */
    val snoozedFrom: Long? = null,
)

/** Una data da aspettare: compleanni, viaggi, scadenze. Se [yearly] si ripete ogni anno. */
data class Countdown(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val yearly: Boolean = false,
) {
    fun nextDate(today: LocalDate = LocalDate.now()): LocalDate {
        if (!yearly) return date
        var next = date.withYear(today.year)
        if (next.isBefore(today)) next = next.plusYears(1)
        return next
    }

    fun daysLeft(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, nextDate(today))

    /**
     * Quanti anni compie alla prossima ricorrenza (compleanni, anniversari). Solo se la data
     * è stata scritta con l'anno vero: con l'anno corrente o il precedente non si sa, e non lo diciamo.
     */
    fun yearsAtNext(today: LocalDate = LocalDate.now()): Int? =
        if (!yearly) null else (nextDate(today).year - date.year).takeIf { it >= 2 }
}

/** Un'abitudine da tenere ogni giorno: si spunta e conta i giorni di fila. */
data class Habit(
    val id: Long,
    val title: String,
    val done: Set<LocalDate> = emptySet(),
) {
    fun isDone(day: LocalDate = LocalDate.now()): Boolean = day in done

    /** Giorni di fila fino a oggi; se oggi manca ancora, la serie di ieri è ancora viva. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var day = if (today in done) today else today.minusDays(1)
        var count = 0
        while (day in done) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Gli ultimi [days] giorni, dal più vecchio a oggi. */
    fun lastDays(today: LocalDate = LocalDate.now(), days: Int = 7): List<Pair<LocalDate, Boolean>> =
        (days - 1 downTo 0).map { today.minusDays(it.toLong()).let { d -> d to (d in done) } }
}

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
    val countdowns: List<Countdown> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    /** Tetto di spesa del mese in centesimi, null se non impostato. */
    val budgetCents: Long? = null,
    val parcels: List<Parcel> = emptyList(),
) {
    /** Pacchi ancora in viaggio, prima quelli attesi prima; quelli senza data in fondo. */
    val incoming: List<Parcel>
        get() = parcels.filter { !it.delivered }.sortedBy { it.expected ?: LocalDate.MAX }

    /** I promemoria aperti, prima quelli con scadenza (dal più vicino). */
    val openTasks: List<TaskItem>
        get() = tasks.filter { !it.done }.sortedWith(compareBy<TaskItem> { it.dueAt ?: Long.MAX_VALUE })

    /** Le date in arrivo, dalla più vicina; quelle non ripetute già passate spariscono. */
    val upcoming: List<Countdown>
        get() = countdowns.filter { it.daysLeft() >= 0 }.sortedBy { it.daysLeft() }
}

/** Promemoria e note salvati in un file JSON sul telefono. */
object NotesStore {
    private const val FILE = "notes.json"

    private lateinit var file: File
    private lateinit var appContext: Context
    private val _data = MutableStateFlow(NotesData())
    val data: StateFlow<NotesData> = _data.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, FILE)
        _data.value = runCatching { parse(file.readText()) }.getOrDefault(NotesData())
    }

    private fun newId(): Long = System.nanoTime()

    /**
     * Aggiunge un promemoria scritto a parole: "domani alle 9 dentista" diventa
     * "Dentista" con scadenza e avviso. Restituisce quello che è stato salvato.
     */
    fun addTask(text: String): TaskItem? {
        if (text.isBlank()) return null
        val entry = QuickParse.parse(text)
        val due = entry.due?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val task = TaskItem(newId(), entry.text, dueAt = due, repeat = entry.repeat)
        update { it.copy(tasks = listOf(task) + it.tasks) }
        if (due != null) syncReminder(task.id)
        return task
    }

    fun toggleTask(id: Long) {
        val task = task(id) ?: return
        if (!task.done && advanceRepeat(task)) return
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = !it.done) else it }) }
        syncReminder(id)
    }

    /** Rimette un promemoria com'era prima della spunta: serve ad "Annulla". */
    fun restoreTask(task: TaskItem) {
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) task else it }) }
        syncReminder(task.id)
    }

    fun setTaskDone(id: Long) {
        val task = task(id) ?: return
        if (!task.done && advanceRepeat(task)) return
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true) else it }) }
        syncReminder(id)
    }

    /** Un promemoria ricorrente non si chiude: passa alla prossima volta. */
    private fun advanceRepeat(task: TaskItem): Boolean {
        val repeat = task.repeat ?: return false
        val due = task.snoozedFrom ?: task.dueAt ?: return false
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(due).atZone(zone).toLocalDateTime()
        val next = repeat.nextAfter(from, LocalDateTime.now()).atZone(zone).toInstant().toEpochMilli()
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) it.copy(dueAt = next, snoozedFrom = null) else it }) }
        syncReminder(task.id)
        return true
    }

    fun setTaskRepeat(id: Long, repeat: Repeat?) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(repeat = repeat) else it })
    }

    fun setTaskDue(id: Long, dueAt: Long?) {
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(dueAt = dueAt, snoozedFrom = null) else it }) }
        syncReminder(id)
    }

    /** "Rimanda" dalla notifica: sposta solo questa volta, anche per i ricorrenti. */
    fun snoozeTask(id: Long, until: Long) {
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.snoozed(until) else it }) }
        syncReminder(id)
    }

    fun task(id: Long): TaskItem? = _data.value.tasks.firstOrNull { it.id == id }

    private fun syncReminder(id: Long) {
        if (!::appContext.isInitialized) return
        val task = task(id)
        if (task?.dueAt != null && !task.done) TaskReminderWorker.schedule(appContext, task)
        else TaskReminderWorker.cancel(appContext, id)
    }

    fun editTask(id: Long, text: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(text = text.trim()) else it })
    }

    fun deleteTask(id: Long) {
        update { d -> d.copy(tasks = d.tasks.filter { it.id != id }) }
        syncReminder(id)
    }

    /** Rimette i promemoria eliminati per sbaglio: serve ad "Annulla". */
    fun restoreTasks(removed: List<TaskItem>) {
        update { d -> d.copy(tasks = d.tasks + removed.filter { r -> d.tasks.none { it.id == r.id } }) }
        removed.forEach { syncReminder(it.id) }
    }

    fun clearDone() = update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }

    fun saveCountdown(id: Long?, title: String, date: LocalDate, yearly: Boolean) {
        val clean = title.trim().ifEmpty { return }
        val item = Countdown(id ?: newId(), clean, date, yearly)
        update { d -> d.copy(countdowns = d.countdowns.filter { it.id != item.id } + item) }
    }

    fun saveHabit(id: Long?, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d ->
            if (id == null) d.copy(habits = d.habits + Habit(newId(), clean))
            else d.copy(habits = d.habits.map { if (it.id == id) it.copy(title = clean) else it })
        }
    }

    fun deleteHabit(id: Long) = update { d -> d.copy(habits = d.habits.filter { it.id != id }) }

    /** Rimette un'abitudine eliminata per sbaglio, con la sua serie: serve ad "Annulla". */
    fun restoreHabit(habit: Habit) = update { d ->
        if (d.habits.any { it.id == habit.id }) d else d.copy(habits = d.habits + habit)
    }

    fun toggleHabit(id: Long, day: LocalDate = LocalDate.now()) = update { d ->
        d.copy(habits = d.habits.map { h ->
            if (h.id != id) h else h.copy(done = if (day in h.done) h.done - day else h.done + day)
        })
    }

    /** "12,50 pizza": salva la spesa e la restituisce, null se manca l'importo. */
    fun addExpense(text: String, date: LocalDate = LocalDate.now()): Expense? {
        val parsed = Expenses.parse(text, date) ?: return null
        val item = parsed.copy(id = newId())
        update { d -> d.copy(expenses = listOf(item) + d.expenses) }
        return item
    }

    fun saveExpense(item: Expense) = update { d ->
        d.copy(expenses = d.expenses.map { if (it.id == item.id) item else it })
    }

    fun deleteExpense(id: Long) = update { d -> d.copy(expenses = d.expenses.filter { it.id != id }) }

    fun restoreExpense(item: Expense) = update { d ->
        if (d.expenses.any { it.id == item.id }) d else d.copy(expenses = listOf(item) + d.expenses)
    }

    fun setBudget(cents: Long?) = update { it.copy(budgetCents = cents?.takeIf { c -> c > 0 }) }

    fun saveParcel(id: Long?, title: String, code: String, expected: LocalDate?) {
        val clean = title.trim().ifEmpty { code.trim() }.ifEmpty { return }
        update { d ->
            val old = d.parcels.firstOrNull { it.id == id }
            val item = Parcel(id ?: newId(), clean, code.trim().uppercase(), expected, old?.deliveredOn)
            d.copy(parcels = d.parcels.filter { it.id != item.id } + item)
        }
    }

    fun toggleDelivered(id: Long) = update { d ->
        d.copy(parcels = d.parcels.map { if (it.id == id) it.copy(deliveredOn = if (it.delivered) null else LocalDate.now()) else it })
    }

    fun deleteParcel(id: Long) = update { d -> d.copy(parcels = d.parcels.filter { it.id != id }) }

    fun restoreParcel(item: Parcel) = update { d ->
        if (d.parcels.any { it.id == item.id }) d else d.copy(parcels = d.parcels + item)
    }

    fun deleteCountdown(id: Long) = update { d -> d.copy(countdowns = d.countdowns.filter { it.id != id }) }

    fun restoreCountdown(item: Countdown) = update { d ->
        if (d.countdowns.any { it.id == item.id }) d else d.copy(countdowns = d.countdowns + item)
    }

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

    /** Rimette una nota eliminata per sbaglio: serve ad "Annulla". */
    fun restoreNote(note: Note) = update { d ->
        if (d.notes.any { it.id == note.id }) d else d.copy(notes = listOf(note) + d.notes)
    }

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
        // Il widget mostra i promemoria di oggi: lo ridisegna a ogni modifica.
        if (::appContext.isInitialized) runCatching { DashboardWidget.updateAll(appContext) }
    }

    private fun serialize(d: NotesData): String = JSONObject()
        .put("tasks", JSONArray().apply {
            d.tasks.forEach {
                put(
                    JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("createdAt", it.createdAt)
                        .apply { it.dueAt?.let { due -> put("dueAt", due) } }
                        .apply { it.repeat?.let { r -> put("repeat", r.name) } }
                        .apply { it.snoozedFrom?.let { s -> put("snoozedFrom", s) } }
                )
            }
        })
        .put("notes", JSONArray().apply {
            d.notes.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("updatedAt", it.updatedAt).put("pinned", it.pinned))
            }
        })
        .put("countdowns", JSONArray().apply {
            d.countdowns.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.date.toString()).put("yearly", it.yearly))
            }
        })
        .put("habits", JSONArray().apply {
            // Basta poco più di un anno di storia per serie e settimane.
            val since = LocalDate.now().minusDays(400)
            d.habits.forEach { h ->
                put(
                    JSONObject().put("id", h.id).put("title", h.title)
                        .put("done", JSONArray(h.done.filter { it.isAfter(since) }.sorted().map { it.toString() }))
                )
            }
        })
        .put("expenses", JSONArray().apply {
            d.expenses.forEach {
                put(
                    JSONObject().put("id", it.id).put("cents", it.cents).put("text", it.text)
                        .put("category", it.category.name).put("date", it.date.toString())
                )
            }
        })
        .apply { d.budgetCents?.let { put("budgetCents", it) } }
        .put("parcels", JSONArray().apply {
            // I pacchi consegnati da più di un mese non servono più.
            val since = LocalDate.now().minusDays(30)
            d.parcels.filter { it.deliveredOn?.isBefore(since) != true }.forEach {
                put(
                    JSONObject().put("id", it.id).put("title", it.title).put("code", it.code)
                        .apply { it.expected?.let { e -> put("expected", e.toString()) } }
                        .apply { it.deliveredOn?.let { e -> put("deliveredOn", e.toString()) } }
                )
            }
        })
        .toString()

    private fun parse(raw: String): NotesData {
        val json = JSONObject(raw)
        val tasks = json.optJSONArray("tasks") ?: JSONArray()
        val notes = json.optJSONArray("notes") ?: JSONArray()
        val countdowns = json.optJSONArray("countdowns") ?: JSONArray()
        val habits = json.optJSONArray("habits") ?: JSONArray()
        val expenses = json.optJSONArray("expenses") ?: JSONArray()
        val parcels = json.optJSONArray("parcels") ?: JSONArray()
        fun JSONObject.date(key: String): LocalDate? =
            if (has(key)) runCatching { LocalDate.parse(optString(key)) }.getOrNull() else null
        return NotesData(
            tasks = (0 until tasks.length()).map { i ->
                val o = tasks.getJSONObject(i)
                TaskItem(
                    o.getLong("id"),
                    o.optString("text"),
                    o.optBoolean("done"),
                    o.optLong("createdAt"),
                    if (o.has("dueAt")) o.optLong("dueAt") else null,
                    runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrNull(),
                    if (o.has("snoozedFrom")) o.optLong("snoozedFrom") else null,
                )
            },
            notes = (0 until notes.length()).map { i ->
                val o = notes.getJSONObject(i)
                Note(o.getLong("id"), o.optString("text"), o.optLong("updatedAt"), o.optBoolean("pinned"))
            },
            countdowns = (0 until countdowns.length()).mapNotNull { i ->
                val o = countdowns.getJSONObject(i)
                val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
                Countdown(o.getLong("id"), o.optString("title"), date, o.optBoolean("yearly"))
            },
            habits = (0 until habits.length()).map { i ->
                val o = habits.getJSONObject(i)
                val done = o.optJSONArray("done") ?: JSONArray()
                Habit(
                    o.getLong("id"),
                    o.optString("title"),
                    (0 until done.length()).mapNotNull { runCatching { LocalDate.parse(done.getString(it)) }.getOrNull() }.toSet(),
                )
            },
            expenses = (0 until expenses.length()).mapNotNull { i ->
                val o = expenses.getJSONObject(i)
                Expense(
                    o.getLong("id"),
                    o.optLong("cents"),
                    o.optString("text"),
                    runCatching { ExpenseCategory.valueOf(o.optString("category")) }.getOrDefault(ExpenseCategory.OTHER),
                    o.date("date") ?: return@mapNotNull null,
                )
            },
            budgetCents = if (json.has("budgetCents")) json.optLong("budgetCents") else null,
            parcels = (0 until parcels.length()).map { i ->
                val o = parcels.getJSONObject(i)
                Parcel(o.getLong("id"), o.optString("title"), o.optString("code"), o.date("expected"), o.date("deliveredOn"))
            },
        )
    }
}
