package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Le Note hanno tre tipi soli: con scadenza, senza scadenza, spese.
 * "Con scadenza" è una lista unica, per giorno: promemoria, date da ricordare, pacchi ed eventi del calendario
 * fanno la stessa cosa (arriva un giorno) e si vedono allo stesso modo, con quanto manca a destra.
 */
internal enum class DueGroup(val label: String) {
    LATE("Scaduti"),
    TODAY("Oggi"),
    TOMORROW("Domani"),
    WEEK("Questa settimana"),
    LATER("Più avanti"),
}

/** Una cosa con scadenza: uno solo fra promemoria, data, pacco ed evento. [day] null = pacco senza data. */
internal data class DueRow(
    val key: String,
    val day: LocalDate?,
    /** Per l'ordine dentro il giorno: prima le cose di tutto il giorno, poi per orario. */
    val at: Long,
    val task: TaskItem? = null,
    val countdown: Countdown? = null,
    val parcel: Parcel? = null,
    val event: CalendarEvent? = null,
)

/** Quanti giorni di calendario si guardano per gli eventi. */
internal const val EVENT_DAYS = 14

internal fun dueGroups(
    tasks: List<TaskItem>,
    countdowns: List<Countdown>,
    parcels: List<Parcel>,
    events: List<CalendarEvent>,
    now: LocalDateTime = LocalDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<DueGroup, List<DueRow>>> {
    val today = now.toLocalDate()
    val nowMs = now.atZone(zone).toInstant().toEpochMilli()
    fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    val late = mutableListOf<DueRow>()
    val dated = mutableListOf<DueRow>()
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
        val row = DueRow("parcel-${p.id}", day, day?.let(::startOf) ?: Long.MAX_VALUE, parcel = p)
        if (day != null && day.isBefore(today)) late += row else dated += row
    }
    events.filter { it.end > nowMs }.distinctBy { it.eventId to it.begin }.forEach { e ->
        // Un evento iniziato ieri e ancora in corso conta oggi.
        val day = maxOf(dayOf(e.begin), today)
        dated += DueRow("event-${e.eventId}-${e.begin}", day, if (e.allDay) startOf(day) else maxOf(e.begin, startOf(day)), event = e)
    }

    fun groupOf(day: LocalDate?): DueGroup = when {
        day == null -> DueGroup.LATER
        day == today -> DueGroup.TODAY
        day == today.plusDays(1) -> DueGroup.TOMORROW
        !day.isAfter(today.plusDays(7)) -> DueGroup.WEEK
        else -> DueGroup.LATER
    }
    val byGroup = dated.groupBy { groupOf(it.day) }
    return DueGroup.entries.mapNotNull { g ->
        val rows = if (g == DueGroup.LATE) late else byGroup[g].orEmpty()
        rows.takeIf { it.isNotEmpty() }?.let { g to it.sortedBy { r -> r.at } }
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
