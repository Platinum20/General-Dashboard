package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.platinum20.dashboard.notes.NoteText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.calendar.CalendarEvent
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
 * calendario) in una lista sola divisa per giorno, con quanto manca a destra. In Oggi la stessa
 * lista si ferma a una settimana; qui va avanti finché c'è qualcosa.
 */
internal data class DueRow(
    val key: String,
    val day: LocalDate?,
    /** Per l'ordine dentro il giorno: prima le cose di tutto il giorno, poi per orario. */
    val at: Long,
    val task: TaskItem? = null,
    val parcel: Parcel? = null,
    val event: CalendarEvent? = null,
    /** Il rinnovo dell'offerta SIM: tocco = pagina SIM. */
    val simRenewal: Boolean = false,
    /** Una nota senza data: sta sempre in Oggi finché c'è (Antonio, calendario unico). */
    val note: com.platinum20.dashboard.notes.Note? = null,
    /** La riga "OGGI" in cima a oggi: si disegna come il segno di oggi, non come una voce. */
    val today: Boolean = false,
    /** Un promemoria o un pacco in ritardo, portato a oggi: qui il giorno in cui scadeva. */
    val overdueFrom: LocalDate? = null,
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
    /** Il calendario unico: le cose fatte oggi stanno sotto OGGI (in [dueDays]), qui solo i giorni prima. */
    withToday: Boolean = false,
): List<DueDay> {
    val today = now.toLocalDate()
    val nowMs = now.atZone(zone).toInstant().toEpochMilli()
    fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val rows = mutableListOf<DueRow>()
    // I promemoria con data spuntati vanno nel giorno in cui li hai fatti (o della scadenza, per i vecchi).
    // Nel calendario unico anche quelli senza data: fatti nel giorno in cui li hai spuntati (Antonio).
    tasks.filter { it.done && (it.dueAt != null || withToday) }.forEach { t ->
        val at = t.doneAt ?: t.dueAt ?: t.createdAt
        if (at <= nowMs) rows += DueRow("task-${t.id}", dayOf(at), at, task = t)
    }
    // Un promemoria o una data che non si ripete, passato: resta nel suo giorno come un evento, non diventa "scaduto".
    tasks.filter { !it.done && it.repeat == null && it.dueAt != null }.forEach { t ->
        val day = t.day(zone) ?: return@forEach
        if (day.isBefore(today)) rows += DueRow("task-${t.id}", day, if (t.allDay) startOf(day) else t.dueAt!!, task = t)
    }
    parcels.forEach { p -> p.deliveredOn?.let { rows += DueRow("parcel-${p.id}", it, startOf(it), parcel = p) } }
    events.filter { it.end <= nowMs }.distinctBy { it.eventId to it.begin }.forEach { e ->
        rows += DueRow("event-${e.eventId}-${e.begin}", dayOf(e.begin), if (e.allDay) startOf(dayOf(e.begin)) else e.begin, event = e)
    }
    return rows.filter { if (withToday) it.day!!.isBefore(today) else !it.day!!.isAfter(today) }
        .groupBy { it.day!! }.toSortedMap()
        .map { (day, r) -> DueDay(day, false, r.sortedBy { it.at }, past = true) }
}

/** Quanti giorni di calendario si guardano per gli eventi: più di un anno, così c'è anche l'anno dopo (Antonio). */
internal const val EVENT_DAYS = com.platinum20.dashboard.today.Upcoming.DAYS

internal fun dueDays(
    tasks: List<TaskItem>,
    parcels: List<Parcel>,
    events: List<CalendarEvent>,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    simRenewal: LocalDate? = null,
    /**
     * Il calendario unico (Antonio): Oggi c'è sempre, in cima, con le cose in ritardo,
     * i promemoria e le note senza data (sono "sempre a oggi" e a mezzanotte passano a domani da sole).
     */
    withToday: Boolean = false,
    notes: List<com.platinum20.dashboard.notes.Note> = emptyList(),
): List<DueDay> {
    val today = now.toLocalDate()
    val nowMs = now.atZone(zone).toInstant().toEpochMilli()
    fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    val late = mutableListOf<DueRow>()
    val dated = mutableListOf<DueRow>()
    val undated = mutableListOf<DueRow>()
    tasks.filter { !it.done && it.dueAt != null }.forEach { t ->
        // Come un evento: la prossima volta; di un giorno passato e non ripetuto sta nel passato, mai fra gli scaduti.
        val next = t.nextAt(today, zone) ?: return@forEach
        val day = next.toLocalDate()
        if (day.isBefore(today)) return@forEach
        dated += DueRow("task-${t.id}", day, if (t.allDay) startOf(day) else next.atZone(zone).toInstant().toEpochMilli(), task = t)
    }
    parcels.filter { !it.delivered }.forEach { p ->
        val day = p.expected
        when {
            day == null -> undated += DueRow("parcel-${p.id}", null, Long.MAX_VALUE, parcel = p)
            day.isBefore(today) -> late += DueRow("parcel-${p.id}", day, startOf(day), parcel = p)
            else -> dated += DueRow("parcel-${p.id}", day, startOf(day), parcel = p)
        }
    }
    // Nel calendario unico anche gli eventi di oggi già finiti stanno sotto OGGI: [pastDays] tiene solo i giorni
    // prima, e senza questo il turno fatto oggi spariva (Antonio).
    events.filter { it.end > nowMs || (withToday && dayOf(it.begin) == today) }.distinctBy { it.eventId to it.begin }.forEach { e ->
        // Un evento iniziato ieri e ancora in corso conta oggi.
        val day = maxOf(dayOf(e.begin), today)
        dated += DueRow("event-${e.eventId}-${e.begin}", day, if (e.allDay) startOf(day) else maxOf(e.begin, startOf(day)), event = e)
    }
    simRenewal?.takeIf { !it.isBefore(today) }?.let { dated += DueRow("sim-renewal", it, startOf(it), simRenewal = true) }
    if (withToday) {
        val todayRows = buildList {
            add(DueRow("today", today, Long.MIN_VALUE, today = true))
            // Cose in ritardo, eventi, turni e treni di oggi tutti insieme in ordine d'orario (Antonio, 1.400).
            addAll((late.sortedBy { it.at }.map { it.copy(day = today, overdueFrom = it.day) } +
                dated.filter { it.day == today }).sortedBy { it.at })
            tasks.filter { !it.done && it.dueAt == null }.sortedBy { it.createdAt }
                .forEach { t -> add(DueRow("task-${t.id}", today, Long.MAX_VALUE, task = t)) }
            undated.forEach { add(it.copy(day = today)) }
            // Le cose spuntate oggi restano in Oggi, segnate fatte (Antonio).
            tasks.filter { t -> t.done && dayOf(t.doneAt ?: t.dueAt ?: t.createdAt) == today }.sortedBy { it.doneAt ?: 0L }
                .forEach { t -> add(DueRow("task-${t.id}", today, t.doneAt ?: nowMs, task = t)) }
            // Le note fissate per prime, poi dalla più nuova.
            notes.sortedWith(compareByDescending<com.platinum20.dashboard.notes.Note> { it.pinned }.thenByDescending { it.createdAt })
                .forEach { n -> add(DueRow("note-${n.id}", today, Long.MAX_VALUE, note = n)) }
        }
        return buildList {
            add(DueDay(today, false, todayRows))
            dated.filter { it.day != today }.groupBy { it.day!! }.toSortedMap().forEach { (day, rows) -> add(DueDay(day, false, rows.sortedBy { it.at })) }
        }
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
                else -> MaterialTheme.colorScheme.onSurface
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
 * Un evento con inizio e fine sulla linea del tempo, come i treni (Antonio): in alto l'ora d'inizio e il nome
 * (per un treno la stazione di partenza), in basso l'ora di fine (per un treno l'arrivo), in mezzo quanto dura
 * con il treno o il luogo. Se finisce un altro giorno (notte), accanto alla fine c'è quel giorno.
 * [labels] = sulla pagina dell'evento, dove il nome c'è già: "Inizio" e "Fine" al posto del nome.
 * Null per gli eventi di tutto il giorno o senza durata: quelli restano una riga semplice.
 */
/** "A", "A e B", "A, B e C". */
internal fun andList(names: List<String>): String =
    if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " e " + names.last()

internal fun tripStops(
    event: CalendarEvent,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    labels: Boolean = false,
    /** Il treno adesso (viaggio vivo, 1.467): ritardo e binario accanto alla durata, solo se letti da poco. */
    live: com.platinum20.dashboard.trips.TrainStatus? = event.trip?.let { com.platinum20.dashboard.trips.TrainLive.fresh(event.eventId) },
): TripStops? {
    if (event.allDay || event.end <= event.begin) return null
    val trip = event.trip
    val hour = DateTimeFormatter.ofPattern("HH:mm")
    val from = Instant.ofEpochMilli(event.begin).atZone(zone)
    val to = Instant.ofEpochMilli(event.end).atZone(zone)
    val endDay = to.toLocalDate().takeIf { it != from.toLocalDate() }
        ?.let { com.platinum20.dashboard.today.Upcoming.dayLabel(it, today) }
    val duration = com.platinum20.dashboard.calendar.Trips.duration(event.begin, event.end)
    return if (trip != null) TripStops(
        departAt = from.format(hour),
        // Nel calendario dove va e quando arriva (Antonio); sulla pagina del viaggio le due stazioni.
        from = if (labels) trip.from ?: "Partenza" else RowText.event(event),
        arriveAt = to.format(hour),
        to = (if (labels) trip.to else "Arrivo a ${trip.to}") + endDay?.let { " · $it" }.orEmpty(),
        // In mezzo la durata, sotto treno, carrozza e posto (Antonio, 1.438).
        middle = listOfNotNull(duration, live?.let(com.platinum20.dashboard.trips.TrainLive::line)).joinToString(" · "),
        below = com.platinum20.dashboard.calendar.Trips.ticketLine(trip),
        begin = event.begin,
        end = event.end,
    ) else {
    // Il nome accanto all'ora d'inizio, come "Treno per …" e "Turno su A, B e C": niente riga in più (Antonio, 1.401).
    val creators = if (event.eventId < -1 && !labels) com.platinum20.dashboard.shifts.ShiftParse.creators(event.title) else emptyList()
    TripStops(
        departAt = from.format(hour),
        from = if (labels) "Inizio" else RowText.event(event),
        arriveAt = to.format(hour),
        to = listOfNotNull("Fine".takeIf { labels }, endDay).joinToString(" · "),
        // Il luogo sta già nella sua riga sulla pagina dell'evento.
        // Per un turno: durata, icona e "Da solo"/"In doppio con …"; sotto, su una riga sua, il venduto (Antonio).
        middle = if (creators.isNotEmpty()) duration
        else listOfNotNull(duration, event.location.trim().takeIf { it.isNotEmpty() && !labels }).joinToString(" · "),
        people = event.people.takeIf { creators.isNotEmpty() },
        extra = if (creators.isNotEmpty()) com.platinum20.dashboard.shifts.ShiftParse.company(event.mates.map(com.platinum20.dashboard.shifts.ShiftParse::firstName)) else "",
        below = event.location.trim().takeIf { creators.isNotEmpty() && it.isNotEmpty() },
        begin = event.begin,
        end = event.end,
    )
    }
}

/**
 * Prossimi giorni come linea del tempo, uguale alla card di Oggi ma con tutto: una riga per cosa, il giorno
 * sulla prima riga di ogni giorno. Fra due giorni lontani nessuno spazio in più: la lista resta compatta (Antonio, 1.401).
 */
internal fun timelineEntries(
    groups: List<DueDay>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    /**
     * Il calendario unico: ogni mese ha il suo segno, anche il primo e quello di oggi (Antonio).
     */
    months: Boolean = false,
): List<Pair<TimelineEntry, DueRow>> {
    val hour = DateTimeFormatter.ofPattern("HH:mm")
    fun Long.time() = Instant.ofEpochMilli(this).atZone(zone).format(hour)
    // Il mese si confronta con la riga prima; da adesso in poi con oggi, così sotto "OGGI" non si ripete il mese.
    var monthPrevious: LocalDate? = null
    var shown: java.time.YearMonth? = null
    var reachedNow = false
    var rowsBefore = 0
    return groups.flatMap { g ->
        val dayAfterDay = rowsBefore > 0
        rowsBefore += g.rows.size
        val day = g.day
        if (!reachedNow && !g.past && !g.late && day != null) {
            reachedNow = true
            // Nel calendario unico il mese di oggi ha il suo segno: non si salta.
            if (!months) monthPrevious = today
        }
        val month = when {
            g.late || day == null -> null
            months && monthPrevious == null -> com.platinum20.dashboard.today.Upcoming.monthLabel(day!!, today)
            else -> com.platinum20.dashboard.today.Upcoming.monthBreak(monthPrevious, day!!, today)
        }
        // Niente più "PIÙ AVANTI" (Antonio, 1.389): dopo oggi ogni giorno ha già la sua riga ("——— Domani ●").
        val later = false
        if (day != null && !g.late) {
            monthPrevious = day
            // Da qui in giù il mese si legge già: nel segno appena messo, o sotto "OGGI" il mese di oggi.
            if (month != null) shown = java.time.YearMonth.from(day)
            else if (!g.past && shown == null) shown = java.time.YearMonth.from(today)
        }
        val label = when {
            g.late -> "Scaduti"
            day == null -> "Senza data"
            else -> com.platinum20.dashboard.today.Upcoming.dayLabel(day, today, shown)
        }
        g.rows.mapIndexed { i, r ->
            val (kind, text) = when {
                r.today -> com.platinum20.dashboard.today.BriefKind.FREE to "Oggi"
                r.note != null -> com.platinum20.dashboard.today.BriefKind.FREE to r.note.title.ifEmpty { "Nota" }
                // In ritardo da un altro giorno: quel giorno al posto dell'ora ("Ieri  bolletta").
                r.task != null && r.overdueFrom != null && r.overdueFrom != today -> com.platinum20.dashboard.today.BriefKind.TASK to
                    (com.platinum20.dashboard.today.Upcoming.dayLabel(r.overdueFrom, today) + "  " + r.task.text)
                // Tutto il giorno (compleanni, scadenze): niente ora.
                // Gli anni che compie stanno nella riga sotto ([TimelineEntry.detail]), con quanto manca.
                r.task != null && r.task.allDay -> com.platinum20.dashboard.today.BriefKind.DATE to RowText.date(r.task)
                // L'ora va sulla riga sotto il nome (Antonio): vedi [TimelineEntry.time].
                r.task != null -> com.platinum20.dashboard.today.BriefKind.TASK to r.task.text
                r.event != null -> com.platinum20.dashboard.today.BriefKind.EVENT to RowText.event(r.event)
                // Pacchi e rinnovo SIM sono eventi del giorno (Antonio): stessa riga e stesso filtro degli eventi.
                r.parcel != null -> com.platinum20.dashboard.today.BriefKind.EVENT to
                    (if (g.past) "Arrivato: " else "Arriva: ") + r.parcel.title
                r.simRenewal -> com.platinum20.dashboard.today.BriefKind.EVENT to com.platinum20.dashboard.today.Upcoming.SIM_RENEWAL
                else -> com.platinum20.dashboard.today.BriefKind.FREE to ""
            }
            TimelineEntry(
                key = r.key,
                kind = kind,
                text = text,
                dayLabel = if (i == 0) label else null,
                dayNumber = if (i == 0 && !g.late) day?.dayOfMonth else null,
                color = r.event?.color ?: 0,
                late = g.late || r.overdueFrom != null,
                done = r.task?.done == true,
                past = g.past,
                // Il pallino colorato per tipo sulla linea (Antonio, 2026-11-11).
                dot = when {
                    r.simRenewal -> RowDot.SIM
                    r.parcel != null -> RowDot.PARCEL
                    r.note != null -> RowDot.NOTE
                    r.event?.trip != null -> RowDot.TRAIN
                    else -> null
                },
                trailing = r.note?.let { NoteText.badge(it.text) },
                month = if (i == 0) month else null,
                todayHeader = r.today,
                later = i == 0 && later,
                divider = i == 0 && dayAfterDay && !r.today,
                time = when {
                    r.task != null && r.overdueFrom == null && !r.task.allDay -> r.task.dueAt?.takeIf { !g.late && !g.past }?.time()
                    r.event != null && !r.event.allDay -> r.event.begin.time()
                    else -> null
                },
                trip = r.event?.let { tripStops(it, today, zone) },
                detail = r.task?.takeIf { it.allDay && !g.past && day != null }
                    ?.let { com.platinum20.dashboard.notes.WhenText.dateLine(it.yearsAtNext(today, zone), day!!, today) },
                image = r.note?.let { n -> com.platinum20.dashboard.capture.SavedLinks.find(n.text)?.image?.takeIf { it.isNotBlank() } ?: n.photo.takeIf { it.isNotEmpty() } },
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
 * Note come linea del tempo, nello stesso verso di Prossimi giorni (Antonio): promemoria senza data e note
 * per quando li hai scritti, le più vecchie in alto e le più nuove in fondo, vicino al +. Le note fissate
 * stanno in fondo, sotto le altre, con "Fissate" al posto del giorno. [limit] = solo le ultime (le card in Oggi).
 */
internal fun undatedEntries(
    tasks: List<TaskItem>,
    notes: List<com.platinum20.dashboard.notes.Note>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    /** I promemoria senza data già fatti: sulla linea nel giorno in cui li hai spuntati (Antonio). */
    done: List<TaskItem> = emptyList(),
    limit: Int? = null,
): List<Pair<TimelineEntry, UndatedRow>> {
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val pinned = notes.filter { it.pinned }.sortedBy { it.createdAt }.let { p -> limit?.let { p.takeLast(it) } ?: p }
    val all = (tasks.map { it.createdAt to UndatedRow(task = it) } + notes.filter { !it.pinned }.map { it.createdAt to UndatedRow(note = it) } +
        done.map { (it.doneAt ?: it.createdAt) to UndatedRow(task = it) })
        .sortedBy { it.first }
    val rows = limit?.let { all.takeLast((it - pinned.size).coerceAtLeast(0)) } ?: all
    val out = mutableListOf<Pair<TimelineEntry, UndatedRow>>()
    var previous: LocalDate? = null
    var shown: java.time.YearMonth? = java.time.YearMonth.from(today)
    rows.forEach { (at, row) ->
        val day = dayOf(at)
        val first = day != previous
        // In cima il mese della prima riga, se non è quello di oggi; poi a ogni cambio di mese.
        val month = com.platinum20.dashboard.today.Upcoming.monthBreak(previous ?: today, day, today)
        if (month != null) shown = java.time.YearMonth.from(day)
        val label = if (first) com.platinum20.dashboard.today.Upcoming.dayLabel(day, today, shown) else null
        val entry = when {
            row.task != null -> TimelineEntry("task-${row.task.id}", com.platinum20.dashboard.today.BriefKind.TASK, row.task.text,
                label, month = month, done = row.task.done, dayNumber = label?.let { day.dayOfMonth })
            else -> TimelineEntry("note-${row.note!!.id}", com.platinum20.dashboard.today.BriefKind.FREE, row.note.title.ifEmpty { "Nota" },
                label,
                trailing = NoteText.badge(row.note.text), month = month, dayNumber = label?.let { day.dayOfMonth })
        }
        out += entry to row
        previous = day
    }
    pinned.forEachIndexed { i, n ->
        out += TimelineEntry("note-${n.id}", com.platinum20.dashboard.today.BriefKind.FREE, n.title, if (i == 0) "Fissate" else null, trailing = NoteText.badge(n.text)) to UndatedRow(note = n)
    }
    return out
}

/**
 * Le chiavi delle righe della linea nella lista: la stessa cosa può comparire due volte (passato e futuro),
 * e due chiavi uguali in una LazyColumn la fanno chiudere. Le ripetute prendono un numero.
 */
internal fun timelineKeys(keys: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return keys.map { k ->
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n == 1) "tl-$k" else "tl-$k#$n"
    }
}

/**
 * Lo spazio vuoto in fondo perché "OGGI" possa stare in cima allo schermo. [below] = quanto c'è sotto OGGI,
 * null finché non è misurato: allora niente spazio. Prima era Int.MAX_VALUE e sottraendo il margine
 * il conto andava in overflow, un'altezza enorme che faceva chiudere la pagina all'apertura.
 */
internal fun nowRoom(viewport: Int, bottomPadding: Int, below: Int?): Int =
    if (below == null) 0 else (viewport.toLong() - bottomPadding - below).coerceIn(0, viewport.coerceAtLeast(0).toLong()).toInt()

/**
 * Dove va il segno "OGGI" in una linea che è quasi tutta passato (Note): prima della prima riga di oggi
 * o delle fissate, altrimenti in fondo ([entries] size). Sopra il passato, sotto oggi.
 */
internal fun nowIndex(entries: List<TimelineEntry>): Int =
    entries.indexOfFirst { it.dayLabel == "Oggi" || it.dayLabel == "Fissate" }.takeIf { it >= 0 } ?: entries.size

/** I filtri del calendario intero (Antonio): uno per le note, uno per gli eventi. */
internal enum class CalendarFilter(val label: String) { NOTES("Note"), EVENTS("Eventi") }

/**
 * A quale filtro appartiene una riga: eventi, promemoria con una data, date da ricordare, pacchi e rinnovo SIM
 * stanno insieme (1.361: sono la stessa cosa); le cose senza data con le note.
 */
internal fun DueRow.filter(): CalendarFilter = when {
    event != null || task?.dueAt != null || parcel != null || simRenewal -> CalendarFilter.EVENTS
    else -> CalendarFilter.NOTES
}

/**
 * Solo le righe del filtro scelto; i giorni rimasti vuoti spariscono. null = tutto. La riga di OGGI ([DueRow.today])
 * resta con ogni filtro: senza, oggi diventava un giorno qualsiasi col vecchio titolo "Oggi" o spariva (Antonio, 2026-11-10).
 */
internal fun List<DueDay>.only(filter: CalendarFilter?): List<DueDay> =
    if (filter == null) this else mapNotNull { d ->
        d.rows.filter { it.today || it.filter() == filter }.takeIf { it.isNotEmpty() }?.let { d.copy(rows = it) }
    }


/**
 * Dove va la riga "Abitudini" nel calendario (1.357): dentro OGGI, subito prima del primo giorno dopo oggi;
 * se dopo oggi non c'è niente, in fondo. [days] = per ogni riga della linea il suo giorno e se è passata.
 */
internal fun habitsIndex(days: List<Pair<LocalDate?, Boolean>>, nowAt: Int, today: LocalDate): Int {
    if (nowAt < 0) return days.size
    for (i in (nowAt + 1) until days.size) {
        val (day, past) = days[i]
        if (!past && day != null && day.isAfter(today)) return i
    }
    return days.size
}

/**
 * Quante righe mostra la card Calendario in Home dopo quelle di oggi (1.357, Antonio): il resto della settimana,
 * almeno [min]; e quante altre cose restano nel mese in corso, per la riga "Altre N a novembre".
 * [days] = per ogni riga dopo oggi il suo giorno (null = senza data).
 */
internal fun calendarCardCut(days: List<LocalDate?>, today: LocalDate, min: Int): Pair<Int, Int> {
    val weekEnd = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
    val week = days.count { it != null && it.isAfter(today) && !it.isAfter(weekEnd) }
    val shown = minOf(days.size, maxOf(week, min))
    val month = java.time.YearMonth.from(today)
    val more = days.drop(shown).count { it != null && java.time.YearMonth.from(it) == month }
    return shown to more
}
