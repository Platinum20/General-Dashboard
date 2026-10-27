package com.platinum20.dashboard.notes

import com.platinum20.dashboard.capture.SavedLinks
import android.content.Context
import com.platinum20.dashboard.today.Briefing
import com.platinum20.dashboard.widget.DashboardWidget
import com.platinum20.dashboard.widget.TasksWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
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
    /** Quando è stato spuntato (millisecondi), per "La tua settimana"; null per i vecchi e gli aperti. */
    val doneAt: Long? = null,
    /** Le volte in cui un ricorrente è stato spuntato (millisecondi, le più recenti): non si chiude mai, ma conta. */
    val doneTimes: List<Long> = emptyList(),
) {
    /** Segna una spunta del ricorrente, tenendo solo le ultime [MAX_DONE_TIMES]. */
    fun withDoneTime(at: Long): TaskItem = copy(doneTimes = (doneTimes + at).takeLast(MAX_DONE_TIMES))

    companion object {
        /** Bastano per qualche mese di un ricorrente di ogni giorno: servono solo a "La tua settimana". */
        const val MAX_DONE_TIMES = 60
    }
}

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

/**
 * Un'abitudine: ogni giorno, oppure [perWeek] volte a settimana ("palestra 3 volte").
 * Si spunta e conta i giorni (o le settimane) di fila.
 */
data class Habit(
    val id: Long,
    val title: String,
    val done: Set<LocalDate> = emptySet(),
    /** 0 = ogni giorno; da 1 a 6 = quante volte a settimana. */
    val perWeek: Int = 0,
    /** Ora del promemoria ("Ricordamelo alle 21"), null se non serve. */
    val remindAt: LocalTime? = null,
) {
    val weekly: Boolean get() = perWeek in 1..6

    fun isDone(day: LocalDate = LocalDate.now()): Boolean = day in done

    /** Quante volte è stata fatta nella settimana (da lunedì) che contiene [day], fino a quel giorno. */
    fun weekCount(day: LocalDate = LocalDate.now()): Int {
        val monday = day.with(DayOfWeek.MONDAY)
        return done.count { !it.isBefore(monday) && !it.isAfter(day) }
    }

    /** Settimane di fila con l'obiettivo raggiunto; quella in corso conta solo se è già raggiunto. */
    fun weekStreak(today: LocalDate = LocalDate.now()): Int {
        if (!weekly) return 0
        var monday = today.with(DayOfWeek.MONDAY)
        if (weekCount(today) < perWeek) monday = monday.minusWeeks(1)
        var count = 0
        while (weekCount(monday.plusDays(6)) >= perWeek) {
            count++
            monday = monday.minusWeeks(1)
        }
        return count
    }

    /**
     * Da ricordare in Adesso? Ogni giorno: dal tardo pomeriggio se oggi manca. Settimanale:
     * solo quando, senza farla oggi, non si arriverebbe più all'obiettivo della settimana.
     */
    fun needsReminder(now: LocalDateTime): Boolean {
        val today = now.toLocalDate()
        if (isDone(today) || now.hour < Briefing.HABIT_HOUR) return false
        if (!weekly) return true
        val missing = perWeek - weekCount(today)
        val daysLeft = DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 1
        return missing > 0 && missing == daysLeft
    }

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

    /**
     * La riga sotto il nome: la serie di giorni di fila; dall'ora in cui Adesso ricorda
     * le abitudini, se oggi manca ancora, "Manca oggi" in evidenza.
     */
    fun statusLine(now: LocalDateTime): HabitLine {
        val today = now.toLocalDate()
        if (weekly) {
            val week = "${weekCount(today)} su $perWeek questa settimana"
            val streak = weekStreak(today)
            val text = when {
                needsReminder(now) -> "Manca oggi · $week"
                streak > 1 -> "$week · $streak settimane di fila"
                streak == 1 && weekCount(today) >= perWeek -> "$week · obiettivo raggiunto"
                else -> week
            }
            return HabitLine(text, urgent = needsReminder(now))
        }
        if (!isDone(today) && now.hour >= Briefing.HABIT_HOUR) {
            val streak = streak(today)
            return HabitLine(if (streak > 0) "Manca oggi · $streak di fila" else "Manca oggi", urgent = true)
        }
        return HabitLine(
            when (val streak = streak(today)) {
                0 -> "Inizia oggi"
                1 -> "1 giorno"
                else -> "$streak giorni di fila"
            },
            urgent = false,
        )
    }
}

data class HabitLine(val text: String, val urgent: Boolean)

data class Note(
    val id: Long,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    /** Quando è stata scritta: l'ordine e la linea del tempo delle Note. Le vecchie note non lo avevano: vale l'ultima modifica. */
    val createdAt: Long = updatedAt,
    /** La foto allegata con la graffetta (file nell'app), vuota per le note di solo testo. */
    val photo: String = "",
) {
    /** La prima riga fa da titolo. */
    val title: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    /** Senza l'indirizzo canvas://open delle cose salvate da "Salva nelle note". */
    val body: String get() = SavedLinks.withoutLink(text).substringAfter('\n', "").trim()
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
    val fixedExpenses: List<FixedExpense> = emptyList(),
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

    /** Il file delle Note non si leggeva: nome della copia salvata, da dire una volta all'utente. */
    private val _readProblem = MutableStateFlow<String?>(null)
    val readProblem: StateFlow<String?> = _readProblem.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, FILE)
        val (loaded, copy) = load(file, LocalDate.now())
        _data.value = loaded
        _readProblem.value = copy?.name
        applyFixedExpenses()
        // Le categorie imparano parole nuove: quelle rimaste in "Altro" si risistemano da sole.
        update { d -> Expenses.recategorized(d.expenses).let { if (it == d.expenses) d else d.copy(expenses = it) } }
    }

    fun readProblemShown() {
        _readProblem.value = null
    }

    /**
     * Legge il file delle Note. Se esiste ma non si legge (rovinato, o scritto da una versione
     * più nuova) lo copia accanto come notes.json.bad-AAAA-MM-GG prima di partire vuoti,
     * così la prima modifica non cancella i dati vecchi. Restituisce i dati e la copia fatta.
     */
    internal fun load(file: File, today: LocalDate): Pair<NotesData, File?> {
        val raw = runCatching { file.readText() }.getOrNull()
        if (raw.isNullOrBlank()) return NotesData() to null
        runCatching { parse(raw) }.onSuccess { return it to null }
        var copy = File(file.parentFile, "${file.name}.bad-$today")
        var n = 2
        while (copy.exists()) copy = File(file.parentFile, "${file.name}.bad-$today-${n++}")
        return NotesData() to runCatching { file.copyTo(copy) }.getOrNull()
    }

    /** Aggiunge le spese fisse arrivate a scadenza (anche più mesi, se l'app non si apriva da tempo). */
    fun applyFixedExpenses(today: LocalDate = LocalDate.now()) = update { d -> withFixedApplied(d, today, ::newId) }

    internal fun withFixedApplied(d: NotesData, today: LocalDate, id: () -> Long): NotesData {
        var expenses = d.expenses
        val fixed = d.fixedExpenses.map { f ->
            val months = f.dueMonths(today)
            if (months.isEmpty()) return@map f
            expenses = months.reversed().map { Expense(id(), f.cents, f.text, f.category, f.dateIn(it), fixedId = f.id) } + expenses
            f.copy(lastMonth = months.last())
        }
        return if (fixed == d.fixedExpenses) d else d.copy(expenses = expenses, fixedExpenses = fixed)
    }

    /** "ogni mese il 5 affitto 650": salva la spesa fissa (e la aggiunge se oggi è il giorno). */
    fun addFixedExpense(text: String, today: LocalDate = LocalDate.now()): FixedExpense? {
        val parsed = Expenses.parseFixed(text, today) ?: return null
        val item = parsed.copy(id = newId())
        update { d -> withFixedApplied(d.copy(fixedExpenses = d.fixedExpenses + item), today, ::newId) }
        return item
    }

    /** Smette di aggiungerla; quelle già aggiunte restano nei mesi passati. */
    fun deleteFixedExpense(id: Long) = update { d -> d.copy(fixedExpenses = d.fixedExpenses.filter { it.id != id }) }

    /** "Annulla" subito dopo averla scritta: via la spesa fissa e anche quelle che ha già aggiunto. */
    fun undoFixedExpense(id: Long) = update { d ->
        d.copy(fixedExpenses = d.fixedExpenses.filter { it.id != id }, expenses = d.expenses.filter { it.fixedId != id })
    }

    fun restoreFixedExpense(item: FixedExpense) = update { d ->
        if (d.fixedExpenses.any { it.id == item.id }) d else d.copy(fixedExpenses = d.fixedExpenses + item)
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
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = !it.done, doneAt = if (it.done) null else System.currentTimeMillis()) else it }) }
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
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(done = true, doneAt = System.currentTimeMillis()) else it }) }
        syncReminder(id)
    }

    /** Un promemoria ricorrente non si chiude: passa alla prossima volta. */
    private fun advanceRepeat(task: TaskItem): Boolean {
        val repeat = task.repeat ?: return false
        val due = task.snoozedFrom ?: task.dueAt ?: return false
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(due).atZone(zone).toLocalDateTime()
        val next = repeat.nextAfter(from, LocalDateTime.now()).atZone(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) it.copy(dueAt = next, snoozedFrom = null).withDoneTime(now) else it }) }
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

    /** Elimina i promemoria spuntati; [which] sceglie quali (in Note solo quelli senza data, in Prossimi giorni quelli con). */
    fun clearDone(which: (TaskItem) -> Boolean = { true }) = update { d -> d.copy(tasks = d.tasks.filter { !it.done || !which(it) }) }

    /** Restituisce l'id della data salvata (per l'"Annulla"), null se il titolo è vuoto. */
    fun saveCountdown(id: Long?, title: String, date: LocalDate, yearly: Boolean): Long? {
        val clean = title.trim().ifEmpty { return null }
        val item = Countdown(id ?: newId(), clean, date, yearly)
        update { d -> d.copy(countdowns = d.countdowns.filter { it.id != item.id } + item) }
        return item.id
    }

    /** Restituisce l'id dell'abitudine salvata (per l'"Annulla"), null se il titolo è vuoto. */
    fun saveHabit(id: Long?, title: String, perWeek: Int = 0, remindAt: LocalTime? = null): Long? {
        val clean = title.trim().ifEmpty { return null }
        val target = perWeek.coerceIn(0, 6)
        val habitId = id ?: newId()
        update { d ->
            if (id == null) d.copy(habits = d.habits + Habit(habitId, clean, perWeek = target, remindAt = remindAt))
            else d.copy(habits = d.habits.map { if (it.id == id) it.copy(title = clean, perWeek = target, remindAt = remindAt) else it })
        }
        syncHabitReminder(habitId)
        return habitId
    }

    fun deleteHabit(id: Long) {
        update { d -> d.copy(habits = d.habits.filter { it.id != id }) }
        syncHabitReminder(id)
    }

    /** Rimette un'abitudine eliminata per sbaglio, con la sua serie: serve ad "Annulla". */
    fun restoreHabit(habit: Habit) {
        update { d -> if (d.habits.any { it.id == habit.id }) d else d.copy(habits = d.habits + habit) }
        syncHabitReminder(habit.id)
    }

    fun toggleHabit(id: Long, day: LocalDate = LocalDate.now()) {
        update { d ->
            d.copy(habits = d.habits.map { h ->
                if (h.id != id) h else h.copy(done = if (day in h.done) h.done - day else h.done + day)
            })
        }
        syncHabitReminder(id)
    }

    /** Segna l'abitudine fatta in [day] (mai il contrario): serve al "Fatto" della notifica. */
    fun markHabitDone(id: Long, day: LocalDate = LocalDate.now()) {
        if (habit(id)?.isDone(day) != false) return
        toggleHabit(id, day)
    }

    fun habit(id: Long): Habit? = _data.value.habits.firstOrNull { it.id == id }

    /** Rimette (o toglie) l'avviso dell'abitudine: fatta oggi, l'avviso passa al prossimo giorno utile. */
    private fun syncHabitReminder(id: Long) {
        if (!::appContext.isInitialized) return
        val habit = habit(id)
        if (habit?.remindAt != null) HabitReminderWorker.schedule(appContext, habit)
        else HabitReminderWorker.cancel(appContext, id)
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

    /** Un pacco trovato da solo (notifiche, SMS): restituisce l'id per poterlo annullare. */
    fun addParcel(title: String, code: String, expected: LocalDate?): Long {
        val item = Parcel(newId(), title.trim().ifEmpty { code }, code.trim().uppercase(), expected)
        update { d -> d.copy(parcels = d.parcels + item) }
        return item.id
    }

    /** Segna consegnato il pacco con questo codice; true se ce n'era uno in viaggio. */
    fun markDelivered(code: String): Boolean {
        val target = _data.value.parcels.firstOrNull { !it.delivered && it.code.equals(code, ignoreCase = true) } ?: return false
        update { d -> d.copy(parcels = d.parcels.map { if (it.id == target.id) it.copy(deliveredOn = LocalDate.now()) else it }) }
        return true
    }

    /** Una spesa già pronta (es. letta da una notifica della banca): restituisce quella salvata. */
    fun addExpense(item: Expense): Expense {
        val saved = item.copy(id = newId())
        update { d -> d.copy(expenses = listOf(saved) + d.expenses) }
        return saved
    }

    /** Un promemoria con scadenza già nota, senza interpretare il testo. */
    fun addTaskAt(text: String, dueAt: Long): TaskItem {
        val task = TaskItem(newId(), text.trim(), dueAt = dueAt)
        update { it.copy(tasks = listOf(task) + it.tasks) }
        syncReminder(task.id)
        return task
    }

    fun toggleDelivered(id: Long) = update { d ->
        d.copy(parcels = d.parcels.map { if (it.id == id) it.copy(deliveredOn = if (it.delivered) null else LocalDate.now()) else it })
    }

    fun renameParcel(id: Long, title: String) = update { d ->
        d.copy(parcels = d.parcels.map { if (it.id == id && title.isNotBlank()) it.copy(title = title.trim()) else it })
    }

    fun deleteParcel(id: Long) = update { d -> d.copy(parcels = d.parcels.filter { it.id != id }) }

    fun restoreParcel(item: Parcel) = update { d ->
        if (d.parcels.any { it.id == item.id }) d else d.copy(parcels = d.parcels + item)
    }

    fun deleteCountdown(id: Long) = update { d -> d.copy(countdowns = d.countdowns.filter { it.id != id }) }

    /** Aggiunge le date scelte dalla rubrica come date "ogni anno"; restituisce le nuove, per "Annulla". */
    fun importContactDates(picked: List<ContactDate>, today: LocalDate = LocalDate.now()): List<Countdown> {
        val base = newId()
        val added = picked.mapIndexed { i, c -> Countdown(base + i, c.title, c.date(today), yearly = true) }
        if (added.isNotEmpty()) update { d -> d.copy(countdowns = d.countdowns + added) }
        return added
    }

    /** Toglie le date appena importate: serve ad "Annulla". */
    fun removeCountdowns(ids: Set<Long>) = update { d -> d.copy(countdowns = d.countdowns.filter { it.id !in ids }) }

    fun restoreCountdown(item: Countdown) = update { d ->
        if (d.countdowns.any { it.id == item.id }) d else d.copy(countdowns = d.countdowns + item)
    }

    /** Salva una nota (nuova se [id] è null) e ne restituisce l'id. Una nota vuota viene eliminata. */
    fun saveNote(id: Long?, text: String): Long? {
        // Una foto senza testo resta una nota: si cancella solo con Elimina.
        if (text.isBlank() && id?.let(::note)?.photo.isNullOrEmpty()) {
            if (id != null) deleteNote(id)
            return null
        }
        val noteId = id ?: newId()
        update { d ->
            val existing = d.notes.firstOrNull { it.id == noteId }
            if (existing != null && existing.text == text) return@update d
            val now = System.currentTimeMillis()
            val note = Note(noteId, text, now, existing?.pinned ?: false, existing?.createdAt ?: now, existing?.photo.orEmpty())
            d.copy(notes = listOf(note) + d.notes.filter { it.id != noteId })
        }
        return noteId
    }

    /** Una foto in cui Canvas non ha trovato niente da fare: diventa una nota con la foto. */
    fun addPhotoNote(text: String, photo: String): Note {
        val now = System.currentTimeMillis()
        val note = Note(newId(), text, now, createdAt = now, photo = photo)
        update { d -> d.copy(notes = listOf(note) + d.notes) }
        return note
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

    /** Tutti i dati in JSON, per il backup. */
    fun exportJson(): String = serialize(_data.value)

    /** Ripristino da backup: sostituisce tutto e rimette i promemoria con la loro notifica. */
    fun replaceAll(next: NotesData) {
        val before = _data.value.tasks.map { it.id }
        // Le spese fisse scadute da quando è stato fatto il backup vanno aggiunte subito.
        update { withFixedApplied(next, LocalDate.now(), ::newId) }
        before.forEach(::syncReminder)
        next.tasks.forEach { syncReminder(it.id) }
    }

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
        if (::appContext.isInitialized) {
            runCatching { DashboardWidget.updateAll(appContext) }
            runCatching { TasksWidget.updateAll(appContext) }
        }
    }

    internal fun serialize(d: NotesData): String = JSONObject()
        .put("tasks", JSONArray().apply {
            d.tasks.forEach {
                put(
                    JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("createdAt", it.createdAt)
                        .apply { it.dueAt?.let { due -> put("dueAt", due) } }
                        .apply { it.repeat?.let { r -> put("repeat", r.name) } }
                        .apply { it.snoozedFrom?.let { s -> put("snoozedFrom", s) } }
                        .apply { it.doneAt?.let { t -> put("doneAt", t) } }
                        .apply { if (it.doneTimes.isNotEmpty()) put("doneTimes", JSONArray(it.doneTimes)) }
                )
            }
        })
        .put("notes", JSONArray().apply {
            d.notes.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("updatedAt", it.updatedAt).put("pinned", it.pinned).put("createdAt", it.createdAt)
                    .apply { if (it.photo.isNotEmpty()) put("photo", it.photo) })
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
                    JSONObject().put("id", h.id).put("title", h.title).put("perWeek", h.perWeek)
                        .put("done", JSONArray(h.done.filter { it.isAfter(since) }.sorted().map { it.toString() }))
                        .apply { h.remindAt?.let { put("remindAt", "%02d:%02d".format(it.hour, it.minute)) } }
                )
            }
        })
        .put("expenses", JSONArray().apply {
            d.expenses.forEach {
                put(
                    JSONObject().put("id", it.id).put("cents", it.cents).put("text", it.text)
                        .put("category", it.category.name).put("date", it.date.toString())
                        .apply { it.fixedId?.let { f -> put("fixedId", f) } }
                )
            }
        })
        .put("fixedExpenses", JSONArray().apply {
            d.fixedExpenses.forEach {
                put(
                    JSONObject().put("id", it.id).put("cents", it.cents).put("text", it.text)
                        .put("category", it.category.name).put("day", it.day).put("from", it.from.toString())
                        .apply { it.lastMonth?.let { m -> put("lastMonth", m.toString()) } }
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

    internal fun parse(raw: String): NotesData {
        val json = JSONObject(raw)
        val tasks = json.optJSONArray("tasks") ?: JSONArray()
        val notes = json.optJSONArray("notes") ?: JSONArray()
        val countdowns = json.optJSONArray("countdowns") ?: JSONArray()
        val habits = json.optJSONArray("habits") ?: JSONArray()
        val expenses = json.optJSONArray("expenses") ?: JSONArray()
        val parcels = json.optJSONArray("parcels") ?: JSONArray()
        val fixedExpenses = json.optJSONArray("fixedExpenses") ?: JSONArray()
        fun JSONObject.month(key: String): YearMonth? =
            if (has(key)) runCatching { YearMonth.parse(optString(key)) }.getOrNull() else null
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
                    if (o.has("doneAt")) o.optLong("doneAt") else null,
                    o.optJSONArray("doneTimes")?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it > 0 } }.orEmpty(),
                )
            },
            notes = (0 until notes.length()).map { i ->
                val o = notes.getJSONObject(i)
                o.optLong("updatedAt").let { updated ->
                    Note(o.getLong("id"), o.optString("text"), updated, o.optBoolean("pinned"), o.optLong("createdAt", updated), o.optString("photo"))
                }
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
                    // Le abitudini salvate prima della 1.84 sono tutte quotidiane.
                    perWeek = o.optInt("perWeek", 0).coerceIn(0, 6),
                    // Prima della 1.104 le abitudini non avevano l'ora del promemoria.
                    remindAt = o.optString("remindAt").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
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
                    fixedId = if (o.has("fixedId")) o.optLong("fixedId") else null,
                )
            },
            fixedExpenses = (0 until fixedExpenses.length()).mapNotNull { i ->
                val o = fixedExpenses.getJSONObject(i)
                FixedExpense(
                    o.getLong("id"),
                    o.optLong("cents"),
                    o.optString("text"),
                    runCatching { ExpenseCategory.valueOf(o.optString("category")) }.getOrDefault(ExpenseCategory.OTHER),
                    o.optInt("day", 1).coerceIn(1, 31),
                    o.month("from") ?: return@mapNotNull null,
                    o.month("lastMonth"),
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
