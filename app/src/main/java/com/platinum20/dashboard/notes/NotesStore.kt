package com.platinum20.dashboard.notes

import com.platinum20.dashboard.bank.BankTx
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
    /** Quando (millisecondi): con [allDay] è la mezzanotte del giorno. Null = senza data. */
    val dueAt: Long? = null,
    /** Se si ripete, spuntandolo la scadenza passa alla volta dopo invece di chiuderlo. */
    val repeat: Repeat? = null,
    /** Scadenza originale di un ricorrente rimandato dalla notifica: la volta dopo si calcola da qui. */
    val snoozedFrom: Long? = null,
    /** Quando è stato spuntato (millisecondi), per "La tua settimana"; null per i vecchi e gli aperti. */
    val doneAt: Long? = null,
    /** Le volte in cui un ricorrente è stato spuntato (millisecondi, le più recenti): non si chiude mai, ma conta. */
    val doneTimes: List<Long> = emptyList(),
    /**
     * Tutto il giorno, senza ora: compleanni, viaggi, scadenze (le vecchie "date da ricordare", unite ai
     * promemoria nella 1.361). Non va mai in "Scaduti": se si ripete passa da solo alla volta dopo.
     */
    val allDay: Boolean = false,
    /** Quando avvisare: uguale per promemoria e date, come negli eventi del calendario. */
    val alert: Alert = Alert.ON_TIME,
    /** Note libere sotto il titolo, come la descrizione di un evento. */
    val notes: String = "",
    /** Luogo, come negli eventi del calendario. */
    val place: String = "",
    /** L'anno vero di un compleanno o anniversario, per dire quanti anni compie; null se non si sa. */
    val firstYear: Int? = null,
) {
    /** Segna una spunta del ricorrente, tenendo solo le ultime [MAX_DONE_TIMES]. */
    fun withDoneTime(at: Long): TaskItem = copy(doneTimes = (doneTimes + at).takeLast(MAX_DONE_TIMES))

    /** Il giorno della scadenza così com'è salvata. */
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate? = dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /**
     * Il giorno in cui cade la prossima volta: se si ripete ed è già passato, la volta dopo oggi (come un evento:
     * niente si spunta, passa da solo; Antonio, 1.373). Per il resto, il giorno salvato.
     */
    fun nextDay(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        nextAt(today, zone)?.toLocalDate()

    /** Come [nextDay], con l'ora: per le cose di tutto il giorno la mezzanotte. */
    fun nextAt(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        val at = Instant.ofEpochMilli(dueAt ?: return null).atZone(zone).toLocalDateTime()
        val r = repeat
        if (r == null || !at.toLocalDate().isBefore(today)) return at
        // Contata dalla prima volta, così il 31 del mese torna al 31; mai prima di oggi, anche se l'ora è passata.
        var next = r.nextAfter(at, today.minusDays(1).atTime(23, 59, 59))
        if (next.toLocalDate().isBefore(today)) next = r.next(next)
        return next
    }

    /** Quanti giorni mancano alla prossima volta; null senza data. */
    fun daysLeft(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long? =
        nextDay(today, zone)?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * Quanti anni compie alla prossima ricorrenza (compleanni, anniversari). Solo se si conosce l'anno vero:
     * con l'anno corrente o il precedente non si sa, e non lo diciamo.
     */
    fun yearsAtNext(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Int? {
        val first = firstYear ?: return null
        if (repeat != Repeat.YEARLY) return null
        return nextDay(today, zone)?.let { it.year - first }?.takeIf { it >= 2 }
    }

    /** Il titolo con gli anni che compie: "Compleanno di Marta (30 anni)". */
    fun titleWithYears(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String =
        text + (yearsAtNext(today, zone)?.let { " ($it anni)" } ?: "")

    /**
     * Si spunta solo una cosa senza data (Antonio, 1.372: promemoria e date sono come gli eventi, passano da soli).
     */
    val tickable: Boolean get() = dueAt == null

    /**
     * Quando suonare l'avviso (millisecondi), null se non serve. Per tutto il giorno alle [ALL_DAY_ALERT],
     * per gli altri all'ora; "il giorno prima" toglie un giorno a entrambi.
     */
    fun alertAt(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (done || alert == Alert.NONE) return null
        val next = nextAt(today, zone) ?: return null
        val base = (if (allDay) next.toLocalDate().atTime(ALL_DAY_ALERT) else next).atZone(zone)
        return (if (alert == Alert.DAY_BEFORE) base.minusDays(1) else base).toInstant().toEpochMilli()
    }

    companion object {
        /** Bastano per qualche mese di un ricorrente di ogni giorno: servono solo a "La tua settimana". */
        const val MAX_DONE_TIMES = 60

        /** L'ora dell'avviso per le cose di tutto il giorno. */
        val ALL_DAY_ALERT: LocalTime = LocalTime.of(9, 0)

        /** Una data di tutto il giorno (le vecchie "date da ricordare"): ogni anno se [yearly]. */
        fun date(id: Long, title: String, date: LocalDate, yearly: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): TaskItem = TaskItem(
            id, title.trim(),
            dueAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            repeat = if (yearly) Repeat.YEARLY else null,
            allDay = true,
            firstYear = if (yearly) date.year else null,
        )
    }
}

/** Quando avvisare, per promemoria e date: all'ora (o alle 9 per tutto il giorno), il giorno prima, mai. */
enum class Alert(val label: String) {
    ON_TIME("All'ora"),
    DAY_BEFORE("Il giorno prima"),
    NONE("Nessun avviso"),
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
    val habits: List<Habit> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    /** Tetto di spesa del mese in centesimi, null se non impostato. */
    val budgetCents: Long? = null,
    val parcels: List<Parcel> = emptyList(),
    val fixedExpenses: List<FixedExpense> = emptyList(),
    /** I movimenti del conto che Antonio ha eliminato: la lettura successiva non li rimette. */
    val bankIgnored: Set<String> = emptySet(),
) {
    /** Pacchi ancora in viaggio, prima quelli attesi prima; quelli senza data in fondo. */
    val incoming: List<Parcel>
        get() = parcels.filter { !it.delivered }.sortedBy { it.expected ?: LocalDate.MAX }

    /**
     * Le cose da fare aperte, prima quelle con scadenza (dal più vicino): widget, contatori, notifica del mattino.
     * Le date di tutto il giorno (compleanni) no: non si "fanno" e non scadono.
     */
    val openTasks: List<TaskItem>
        get() {
            // Un promemoria di un giorno già passato è passato, come un evento: non resta "da fare".
            val today = LocalDate.now()
            return tasks.filter { !it.done && !it.allDay && it.nextDay(today)?.isBefore(today) != true }
                .sortedWith(compareBy<TaskItem> { it.dueAt ?: Long.MAX_VALUE })
        }

    /** Promemoria e date di tutto il giorno ancora aperti, insieme: per "Adesso" e la notifica del mattino. */
    val openItems: List<TaskItem>
        get() = tasks.filter { !it.done }

    /** Le date di tutto il giorno (compleanni, scadenze): servono alla rubrica e ai suggerimenti. */
    val dates: List<TaskItem>
        get() = tasks.filter { it.allDay }
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
        rollDates()
        // Le categorie imparano parole nuove: quelle rimaste in "Altro" si risistemano da sole.
        update { d -> Expenses.recategorized(d.expenses).let { if (it == d.expenses) d else d.copy(expenses = it) } }
        val prefs = appContext.getSharedPreferences("notes_upgrades", Context.MODE_PRIVATE)
        if (prefs.getInt("categories", 0) < CATEGORIES_VERSION) {
            update(::withUpgradedCategories)
            prefs.edit().putInt("categories", CATEGORIES_VERSION).apply()
        }
    }

    /** Porta alla volta dopo compleanni e date ricorrenti già passati, con il loro avviso. */
    fun rollDates(today: LocalDate = LocalDate.now()) {
        val before = _data.value.tasks
        update { withDatesRolled(it, today) }
        _data.value.tasks.filter { t -> before.none { it == t } }.forEach { syncReminder(it.id) }
    }

    /** Sale quando le categorie cambiano forma (1.289: Bollette e Acquisti): le spese vecchie si spostano una volta. */
    private const val CATEGORIES_VERSION = 1

    /** Le spese (e le fisse) che con le categorie nuove stanno meglio altrove; vedi [Expenses.upgradeCategory]. */
    fun withUpgradedCategories(d: NotesData): NotesData {
        val expenses = d.expenses.map { e -> Expenses.upgradeCategory(e.category, e.text).let { if (it == e.category) e else e.copy(category = it) } }
        val fixed = d.fixedExpenses.map { f -> Expenses.upgradeCategory(f.category, f.text).let { if (it == f.category) f else f.copy(category = it) } }
        return if (expenses == d.expenses && fixed == d.fixedExpenses) d else d.copy(expenses = expenses, fixedExpenses = fixed)
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

    /** Una spesa fissa cambiata dalla sua pagina (nome, importo, giorno, categoria). */
    fun saveFixedExpense(item: FixedExpense) = update { d ->
        d.copy(fixedExpenses = d.fixedExpenses.map { if (it.id == item.id) item else it })
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
        if (task?.alertAt() != null) TaskReminderWorker.schedule(appContext, task)
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

    /**
     * Salva una data di tutto il giorno (compleanno, scadenza): è un promemoria come gli altri, con
     * [TaskItem.allDay]. Restituisce l'id (per l'"Annulla"), null se il titolo è vuoto. Una data che c'è già
     * tiene note, luogo e avviso.
     */
    fun saveDate(id: Long?, title: String, date: LocalDate, yearly: Boolean): Long? {
        val clean = title.trim().ifEmpty { return null }
        val fresh = TaskItem.date(id ?: newId(), clean, date, yearly)
        val old = id?.let(::task)
        val item = old?.copy(
            text = clean, dueAt = fresh.dueAt, allDay = true, snoozedFrom = null,
            repeat = if (yearly) Repeat.YEARLY else old.repeat.takeIf { it != Repeat.YEARLY },
            firstYear = if (yearly) old.firstYear ?: date.year else null,
        ) ?: fresh
        update { d -> d.copy(tasks = if (d.tasks.any { it.id == item.id }) d.tasks.map { if (it.id == item.id) item else it } else listOf(item) + d.tasks) }
        syncReminder(item.id)
        return item.id
    }

    /** Con l'ora o tutto il giorno: passando a tutto il giorno l'ora si perde, tornando indietro si mette alle 9. */
    fun setTaskAllDay(id: Long, allDay: Boolean) {
        val zone = ZoneId.systemDefault()
        update { d ->
            d.copy(tasks = d.tasks.map { t ->
                if (t.id != id || t.allDay == allDay) t else {
                    val day = t.nextDay(LocalDate.now(), zone) ?: LocalDate.now()
                    val at = if (allDay) day.atStartOfDay(zone) else day.atTime(TaskItem.ALL_DAY_ALERT).atZone(zone)
                    t.copy(allDay = allDay, dueAt = at.toInstant().toEpochMilli(), snoozedFrom = null)
                }
            })
        }
        syncReminder(id)
    }

    /** Solo il giorno di una cosa di tutto il giorno, che resta senza ora. */
    fun setTaskDay(id: Long, day: LocalDate) {
        val at = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Un anno lontano scritto a mano è l'anno vero (compleanno): da lì si contano gli anni.
        val real = day.year.takeIf { it < LocalDate.now().year - 1 }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(dueAt = at, snoozedFrom = null, firstYear = real ?: it.firstYear) else it }) }
        syncReminder(id)
    }

    fun setTaskAlert(id: Long, alert: Alert) {
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(alert = alert) else it }) }
        syncReminder(id)
    }

    fun setTaskNotes(id: Long, notes: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(notes = notes.trim()) else it })
    }

    fun setTaskPlace(id: Long, place: String) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(place = place.trim()) else it })
    }

    /**
     * Le cose che si ripetono, di un giorno già passato, vanno alla volta dopo: così la data salvata è
     * sempre la prossima (avviso compreso). Pura, per i test; si fa all'avvio.
     */
    internal fun withDatesRolled(d: NotesData, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): NotesData {
        val rolled = d.tasks.map { t ->
            val next = t.nextAt(today, zone)
            if (t.done || t.repeat == null || next == null || next.toLocalDate() == t.day(zone)) t
            else t.copy(dueAt = next.atZone(zone).toInstant().toEpochMilli(), snoozedFrom = null)
        }
        return if (rolled == d.tasks) d else d.copy(tasks = rolled)
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
        // Scritta adesso: l'ora è questa, per l'ordine nel giorno.
        val item = parsed.copy(id = newId(), at = parsed.at ?: if (parsed.date == LocalDate.now()) LocalTime.now().withSecond(0).withNano(0) else null)
        update { d -> d.copy(expenses = listOf(item) + d.expenses) }
        return item
    }

    fun saveExpense(item: Expense) = update { d ->
        d.copy(expenses = d.expenses.map { if (it.id == item.id) item else it })
    }

    fun deleteExpense(id: Long) = update { d ->
        val bankId = d.expenses.firstOrNull { it.id == id }?.bankId
        d.copy(expenses = d.expenses.filter { it.id != id }, bankIgnored = bankId?.let { d.bankIgnored + it } ?: d.bankIgnored)
    }

    /** I movimenti letti dal conto Revolut: aggiunge, aggiorna o toglie le spese collegate. */
    fun syncBank(txs: List<BankTx>, since: LocalDate) = update { d -> BankSync.apply(d, txs, since, newId()) }

    fun restoreExpense(item: Expense) = update { d ->
        if (d.expenses.any { it.id == item.id }) d
        else d.copy(expenses = listOf(item) + d.expenses, bankIgnored = item.bankId?.let { d.bankIgnored - it } ?: d.bankIgnored)
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

    /** Aggiunge le date scelte dalla rubrica come date "ogni anno"; restituisce le nuove, per "Annulla". */
    fun importContactDates(picked: List<ContactDate>, today: LocalDate = LocalDate.now()): List<TaskItem> {
        val base = newId()
        val added = picked.mapIndexed { i, c -> TaskItem.date(base + i, c.title, c.date(today), yearly = true) }
        if (added.isNotEmpty()) update { d -> d.copy(tasks = d.tasks + added) }
        added.forEach { syncReminder(it.id) }
        return added
    }

    /** Toglie le date appena importate: serve ad "Annulla". */
    fun removeTasks(ids: Set<Long>) {
        update { d -> d.copy(tasks = d.tasks.filter { it.id !in ids }) }
        ids.forEach(::syncReminder)
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
                        .apply { if (it.allDay) put("allDay", true) }
                        .apply { if (it.alert != Alert.ON_TIME) put("alert", it.alert.name) }
                        .apply { if (it.notes.isNotEmpty()) put("notes", it.notes) }
                        .apply { if (it.place.isNotEmpty()) put("place", it.place) }
                        .apply { it.firstYear?.let { y -> put("firstYear", y) } }
                )
            }
        })
        .put("notes", JSONArray().apply {
            d.notes.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("updatedAt", it.updatedAt).put("pinned", it.pinned).put("createdAt", it.createdAt)
                    .apply { if (it.photo.isNotEmpty()) put("photo", it.photo) })
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
                        .apply { it.bankId?.let { b -> put("bankId", b) } }
                        .apply { it.at?.let { t -> put("at", "%02d:%02d".format(t.hour, t.minute)) } }
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
        .apply { if (d.bankIgnored.isNotEmpty()) put("bankIgnored", JSONArray(d.bankIgnored.toList())) }
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
        val parsedTasks = (0 until tasks.length()).map { i ->
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
                    allDay = o.optBoolean("allDay"),
                    alert = runCatching { Alert.valueOf(o.optString("alert")) }.getOrDefault(Alert.ON_TIME),
                    notes = o.optString("notes"),
                    place = o.optString("place"),
                    firstYear = if (o.has("firstYear")) o.optInt("firstYear") else null,
                )
            }
        // Prima della 1.361 le date da ricordare erano a parte: ora sono promemoria di tutto il giorno.
        val oldDates = (0 until countdowns.length()).mapNotNull { i ->
            val o = countdowns.getJSONObject(i)
            val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
            TaskItem.date(o.getLong("id"), o.optString("title"), date, o.optBoolean("yearly"))
        }.filter { c -> parsedTasks.none { it.id == c.id } }
        return NotesData(
            tasks = parsedTasks + oldDates,
            notes = (0 until notes.length()).map { i ->
                val o = notes.getJSONObject(i)
                o.optLong("updatedAt").let { updated ->
                    Note(o.getLong("id"), o.optString("text"), updated, o.optBoolean("pinned"), o.optLong("createdAt", updated), o.optString("photo"))
                }
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
                    bankId = o.optString("bankId").takeIf { it.isNotEmpty() },
                    // Prima della 1.349 le spese non avevano l'ora.
                    at = o.optString("at").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
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
            bankIgnored = json.optJSONArray("bankIgnored")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            parcels = (0 until parcels.length()).map { i ->
                val o = parcels.getJSONObject(i)
                Parcel(o.getLong("id"), o.optString("title"), o.optString("code"), o.date("expected"), o.date("deliveredOn"))
            },
        )
    }
}
