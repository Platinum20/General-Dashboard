package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import com.platinum20.dashboard.notes.NoteText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.platinum20.dashboard.notes.Expenses
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * "Prossimi giorni": tutto quello che arriva un giorno (promemoria, date da ricordare, pacchi, eventi del
 * calendario, addebiti) in una lista sola divisa per giorno, con quanto manca a destra. In Oggi la stessa
 * lista si ferma a una settimana; qui va avanti finché c'è qualcosa.
 */
internal data class DueRow(
    val key: String,
    val day: LocalDate?,
    /** Per l'ordine dentro il giorno: prima le cose di tutto il giorno, poi per orario. */
    val at: Long,
    val task: TaskItem? = null,
    val countdown: Countdown? = null,
    val parcel: Parcel? = null,
    val event: CalendarEvent? = null,
    val charge: FixedExpense? = null,
)

/** Un giorno della lista. [late] = "Scaduti" in cima; [day] null e non late = pacchi senza data, in fondo. */
internal data class DueDay(
    val day: LocalDate?,
    val late: Boolean,
    val rows: List<DueRow>,
    /** Un giorno già passato (cose fatte, pacchi arrivati, eventi finiti): sta sopra, scorrendo in su. */
    val past: Boolean = false,
) {
    val key: String get() = when {
        past -> "p${day?.toEpochDay()}"
        late -> "late"
        day == null -> "nodate"
        else -> "d${day.toEpochDay()}"
    }
}

/** Quanti giorni indietro si guarda: scorrendo in su in Prossimi giorni si torna nel passato. */
internal const val PAST_DAYS = 60

/**
 * Il passato di Prossimi giorni, dal più vecchio a adesso: promemoria fatti (nel giorno in cui li hai fatti),
 * pacchi arrivati, eventi del calendario già finiti negli ultimi [PAST_DAYS] giorni. Va sopra [dueDays].
 */
internal fun pastDays(
    tasks: List<TaskItem>,
    parcels: List<Parcel>,
    events: List<CalendarEvent>,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<DueDay> {
    val today = now.toLocalDate()
    val nowMs = now.atZone(zone).toInstant().toEpochMilli()
    fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val rows = mutableListOf<DueRow>()
    // I promemoria con data spuntati vanno nel giorno in cui li hai fatti (o della scadenza, per i vecchi).
    tasks.filter { it.done && it.dueAt != null }.forEach { t ->
        val at = t.doneAt ?: t.dueAt!!
        if (at <= nowMs) rows += DueRow("task-${t.id}", dayOf(at), at, task = t)
    }
    parcels.forEach { p -> p.deliveredOn?.let { rows += DueRow("parcel-${p.id}", it, startOf(it), parcel = p) } }
    events.filter { it.end <= nowMs }.distinctBy { it.eventId to it.begin }.forEach { e ->
        rows += DueRow("event-${e.eventId}-${e.begin}", dayOf(e.begin), if (e.allDay) startOf(dayOf(e.begin)) else e.begin, event = e)
    }
    return rows.filter { !it.day!!.isAfter(today) }
        .groupBy { it.day!! }.toSortedMap()
        .map { (day, r) -> DueDay(day, false, r.sortedBy { it.at }, past = true) }
}

/** Quanti giorni di calendario si guardano per gli eventi: più di un anno, così c'è anche l'anno dopo (Antonio). */
internal const val EVENT_DAYS = com.platinum20.dashboard.today.Upcoming.DAYS

internal fun dueDays(
    tasks: List<TaskItem>,
    countdowns: List<Countdown>,
    parcels: List<Parcel>,
    events: List<CalendarEvent>,
    fixedExpenses: List<FixedExpense> = emptyList(),
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<DueDay> {
    val today = now.toLocalDate()
    val nowMs = now.atZone(zone).toInstant().toEpochMilli()
    fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    val late = mutableListOf<DueRow>()
    val dated = mutableListOf<DueRow>()
    val undated = mutableListOf<DueRow>()
    tasks.filter { !it.done && it.dueAt != null }.forEach { t ->
        val row = DueRow("task-${t.id}", dayOf(t.dueAt!!), t.dueAt, task = t)
        if (t.dueAt < nowMs) late += row else dated += row
    }
    countdowns.forEach { c ->
        val day = c.nextDate(today)
        // Una data non annuale già passata non ha più niente da contare.
        if (!day.isBefore(today)) dated += DueRow("date-${c.id}", day, startOf(day), countdown = c)
    }
    parcels.filter { !it.delivered }.forEach { p ->
        val day = p.expected
        when {
            day == null -> undated += DueRow("parcel-${p.id}", null, Long.MAX_VALUE, parcel = p)
            day.isBefore(today) -> late += DueRow("parcel-${p.id}", day, startOf(day), parcel = p)
            else -> dated += DueRow("parcel-${p.id}", day, startOf(day), parcel = p)
        }
    }
    events.filter { it.end > nowMs }.distinctBy { it.eventId to it.begin }.forEach { e ->
        // Un evento iniziato ieri e ancora in corso conta oggi.
        val day = maxOf(dayOf(e.begin), today)
        dated += DueRow("event-${e.eventId}-${e.begin}", day, if (e.allDay) startOf(day) else maxOf(e.begin, startOf(day)), event = e)
    }
    fixedExpenses.forEach { f ->
        val day = f.nextDate(today)
        dated += DueRow("charge-${f.id}", day, startOf(day), charge = f)
    }
    return buildList {
        if (late.isNotEmpty()) add(DueDay(null, true, late.sortedBy { it.at }))
        dated.groupBy { it.day!! }.toSortedMap().forEach { (day, rows) -> add(DueDay(day, false, rows.sortedBy { it.at })) }
        if (undated.isNotEmpty()) add(DueDay(null, false, undated))
    }
}

private val longDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val longDayYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)

/** Il titolo di un giorno: "Scaduti", "Oggi", "Domani", "Mercoledì 28 ottobre" (con l'anno se non è questo). */
internal fun DueDay.label(today: LocalDate = LocalDate.now()): String = when {
    late -> "Scaduti"
    day == null -> "Senza data"
    day == today -> "Oggi"
    day == today.plusDays(1) -> "Domani"
    else -> day.format(if (day.year == today.year) longDay else longDayYear).replaceFirstChar { it.uppercase() }
}

/** Corto, per la linea dei giorni e per le righe dell'anteprima: "Mer 28", "Mar 9 mar" se è più in là di un mese. */
internal fun DueDay.shortLabel(today: LocalDate = LocalDate.now()): String {
    val d = day
    return when {
        late -> "Scaduti"
        d == null -> "Senza data"
        d == today -> "Oggi"
        d == today.plusDays(1) -> "Domani"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}" +
            if (d.isAfter(today.plusDays(27))) " " + d.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN) else ""
    }
}

/** Il numero grande a destra e la parola piccola sotto: "Oggi", "Domani", "5 giorni", "Scaduto". */
internal fun dueBadge(day: LocalDate?, today: LocalDate = LocalDate.now(), late: Boolean = false): Pair<String, String?> {
    if (late) return "Scaduto" to null
    if (day == null) return "?" to null
    return when (val days = ChronoUnit.DAYS.between(today, day)) {
        0L -> "Oggi" to null
        1L -> "Domani" to null
        else -> "$days" to "giorni"
    }
}

@Composable
internal fun DueBadge(big: String, small: String?, error: Boolean = false, muted: Boolean = false, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            big,
            style = if (big.length > 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
            color = when {
                error -> MaterialTheme.colorScheme.error
                muted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
        )
        if (small != null) {
            Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("H:mm")

/** Un evento del calendario fra le cose con scadenza: si tocca per la sua scheda. */
@Composable
internal fun EventCard(event: CalendarEvent, day: LocalDate?, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(event.begin).atZone(zone)
    val to = Instant.ofEpochMilli(event.end).atZone(zone)
    val (big, small) = dueBadge(day)
    CanvasCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().bouncyPress()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "Evento",
                        if (event.allDay) "tutto il giorno" else "${from.format(clock)}–${to.format(clock)}",
                        event.location.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DueBadge(big, small)
        }
    }
}

/** Un addebito fisso fra le cose che arrivano: quanto, che cosa e i giorni che mancano. Si tocca per le Spese. */
@Composable
internal fun ChargeCard(charge: FixedExpense, day: LocalDate?, onClick: () -> Unit) {
    val (big, small) = dueBadge(day)
    CanvasCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().bouncyPress()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Addebito: ${charge.text.ifEmpty { charge.category.label }}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Expenses.format(charge.cents)} · ogni mese il ${charge.day}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DueBadge(big, small)
        }
    }
}

/** In cima a Prossimi giorni finché il calendario non è consentito: gli impegni stanno qui insieme al resto. */
@Composable
internal fun CalendarInvite(onAllow: () -> Unit, onLater: () -> Unit) {
    CanvasCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text("Anche gli impegni del calendario", style = MaterialTheme.typography.titleMedium)
            Text(
                "Li metto qui, nel loro giorno, insieme al resto",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("Non ora") }
                TextButton(onClick = onAllow) { Text("Consenti") }
            }
        }
    }
}

/**
 * Prossimi giorni come linea del tempo, uguale alla card di Oggi ma con tutto: una riga per cosa, il giorno
 * sulla prima riga di ogni giorno, e fra due giorni lontani la linea si allunga ([com.platinum20.dashboard.today.Upcoming.gapDp]).
 */
internal fun timelineEntries(groups: List<DueDay>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<TimelineEntry, DueRow>> {
    val hour = DateTimeFormatter.ofPattern("HH:mm")
    fun Long.time() = Instant.ofEpochMilli(this).atZone(zone).format(hour)
    var previous: LocalDate? = null
    // Il mese si confronta con la riga prima; da adesso in poi con oggi, così sotto "OGGI" non si ripete il mese.
    var monthPrevious: LocalDate? = null
    var reachedNow = false
    return groups.flatMap { g ->
        val day = g.day
        val gap = if (!g.late && day != null && previous != null) com.platinum20.dashboard.today.Upcoming.gapDp(previous!!, day) else 0f
        if (!reachedNow && !g.past && !g.late && day != null) {
            reachedNow = true
            monthPrevious = today
        }
        val month = if (!g.late && day != null) com.platinum20.dashboard.today.Upcoming.monthBreak(monthPrevious, day, today) else null
        if (day != null && !g.late) {
            previous = day
            monthPrevious = day
        }
        val label = when {
            g.late -> "Scaduti"
            day == null -> "Senza data"
            else -> com.platinum20.dashboard.today.Upcoming.dayLabel(day, today)
        }
        g.rows.mapIndexed { i, r ->
            val (kind, text) = when {
                r.task != null -> com.platinum20.dashboard.today.BriefKind.TASK to
                    ((r.task.dueAt?.takeIf { !g.late && !g.past }?.time()?.let { "$it  " } ?: "") + r.task.text)
                r.event != null -> com.platinum20.dashboard.today.BriefKind.EVENT to
                    ((if (r.event.allDay) "" else r.event.begin.time() + "  ") + r.event.title)
                r.countdown != null -> com.platinum20.dashboard.today.BriefKind.DATE to
                    (r.countdown.title + (r.countdown.yearsAtNext(today)?.let { " ($it anni)" } ?: ""))
                r.parcel != null -> com.platinum20.dashboard.today.BriefKind.PARCEL to
                    (if (g.past) "Arrivato: " else "Arriva: ") + r.parcel.title
                r.charge != null -> com.platinum20.dashboard.today.BriefKind.MONEY to
                    "Addebito: ${r.charge.text.ifEmpty { r.charge.category.label }} ${Expenses.format(r.charge.cents)}"
                else -> com.platinum20.dashboard.today.BriefKind.FREE to ""
            }
            TimelineEntry(
                key = r.key,
                kind = kind,
                text = text,
                dayLabel = if (i == 0) label else null,
                gapDp = if (i == 0) gap else 0f,
                month = if (i == 0) month else null,
                color = r.event?.color ?: 0,
                late = g.late,
                past = g.past,
            ) to r
        }
    }
}

/**
 * Le tacche della linea del tempo per le Note: una per giorno di inserimento, dalla più nuova in giù,
 * a partire dalla riga [first]. Le note fissate stanno in cima fuori dall'ordine: niente tacca.
 */
internal fun noteMarks(notes: List<com.platinum20.dashboard.notes.Note>, today: LocalDate, first: Int, zone: ZoneId = ZoneId.systemDefault()): List<DayMark> {
    var previous: LocalDate? = null
    return notes.mapIndexedNotNull { i, note ->
        if (note.pinned) return@mapIndexedNotNull null
        val day = Instant.ofEpochMilli(note.createdAt).atZone(zone).toLocalDate()
        if (day == previous) return@mapIndexedNotNull null
        val label = when (day) {
            today -> "Oggi"
            today.minusDays(1) -> "Ieri"
            else -> day.format(if (day.year == today.year) shortDay else shortDayYear).replaceFirstChar { it.uppercase() }
        }
        DayMark(first + i, label, day.toEpochDay().toDouble(), DayScroll.tick(day, today, previous)).also { previous = day }
    }
}

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val shortDayYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

/** Una riga della linea del tempo delle Note: un promemoria da spuntare o una nota. */
internal data class UndatedRow(val task: TaskItem? = null, val note: com.platinum20.dashboard.notes.Note? = null)

/**
 * Note come linea del tempo, uguale a Prossimi giorni: promemoria senza data e note insieme, per quando
 * li hai scritti, i più nuovi in cima. Le note fissate restano in cima, con "Fissata" al posto del giorno.
 */
internal fun undatedEntries(
    tasks: List<TaskItem>,
    notes: List<com.platinum20.dashboard.notes.Note>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<TimelineEntry, UndatedRow>> {
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val pinned = notes.filter { it.pinned }.sortedByDescending { it.createdAt }
    val rows = (tasks.map { it.createdAt to UndatedRow(task = it) } + notes.filter { !it.pinned }.map { it.createdAt to UndatedRow(note = it) })
        .sortedByDescending { it.first }
    val out = mutableListOf<Pair<TimelineEntry, UndatedRow>>()
    pinned.forEachIndexed { i, n ->
        out += TimelineEntry("note-${n.id}", com.platinum20.dashboard.today.BriefKind.FREE, n.title, if (i == 0) "Fissate" else null, icon = n.icon(), trailing = NoteText.badge(n.text)) to UndatedRow(note = n)
    }
    var previous: LocalDate? = null
    rows.forEach { (at, row) ->
        val day = dayOf(at)
        val first = day != previous
        val gap = if (first && previous != null) com.platinum20.dashboard.today.Upcoming.gapDp(day, previous!!) else 0f
        val month = com.platinum20.dashboard.today.Upcoming.monthBreak(previous, day, today)
        val entry = when {
            row.task != null -> TimelineEntry("task-${row.task.id}", com.platinum20.dashboard.today.BriefKind.TASK, row.task.text,
                if (first) com.platinum20.dashboard.today.Upcoming.dayLabel(day, today) else null, gap, month = month)
            else -> TimelineEntry("note-${row.note!!.id}", com.platinum20.dashboard.today.BriefKind.FREE, row.note.title.ifEmpty { "Nota" },
                if (first) com.platinum20.dashboard.today.Upcoming.dayLabel(day, today) else null, gap, icon = row.note.icon(),
                trailing = NoteText.badge(row.note.text), month = month)
        }
        out += entry to row
        previous = day
    }
    return out
}

/**
 * Spese come linea del tempo, uguale alle altre: una riga per spesa, le più recenti in cima, il giorno a
 * destra sulla prima spesa di quel giorno e la linea che si allunga fra giorni lontani.
 */
internal fun expenseEntries(expenses: List<com.platinum20.dashboard.notes.Expense>, today: LocalDate): List<Pair<TimelineEntry, com.platinum20.dashboard.notes.Expense>> {
    var previous: LocalDate? = null
    return expenses.sortedByDescending { it.date }.map { e ->
        val first = e.date != previous
        val gap = if (first && previous != null) com.platinum20.dashboard.today.Upcoming.gapDp(e.date, previous!!) else 0f
        val month = com.platinum20.dashboard.today.Upcoming.monthBreak(previous, e.date, today)
        previous = e.date
        TimelineEntry(
            "expense-${e.id}",
            com.platinum20.dashboard.today.BriefKind.MONEY,
            Expenses.displayName(e),
            if (first) com.platinum20.dashboard.today.Upcoming.dayLabel(e.date, today) else null,
            gap,
            // Icona e colore dicono la categoria, come la barra del riepilogo.
            color = e.category.color(),
            icon = e.category.icon(),
            trailing = Expenses.format(e.cents),
            month = month,
        ) to e
    }
}

private val NOTE_ICON = Icons.AutoMirrored.Outlined.Notes

/** Si vede subito che nota è: con la foto, una lista da spuntare, o testo. */
private fun com.platinum20.dashboard.notes.Note.icon() = when {
    photo.isNotEmpty() -> Icons.Outlined.Image
    com.platinum20.dashboard.notes.Checklist.hasItems(text) -> Icons.Outlined.Checklist
    else -> NOTE_ICON
}
