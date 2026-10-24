package com.platinum20.dashboard.calendar

import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** La scheda completa di un evento, letta dalla tabella degli eventi. */
data class EventDetail(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val description: String,
    /** Si ripete: si modificano testo e luogo della serie, non gli orari. */
    val recurring: Boolean,
    val calendarId: Long,
    val calendarName: String,
    val writable: Boolean,
    val color: Int,
)

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val primary: Boolean,
)

/** Evento in modifica nell'app: [id] null per uno nuovo. */
data class EventDraft(
    val id: Long? = null,
    val calendarId: Long,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    /** Per gli eventi che si ripetono gli orari della serie restano quelli del calendario. */
    val recurring: Boolean = false,
) {
    /** Cosa manca per salvare, a parole; null se è tutto a posto. */
    fun problem(): String? = when {
        title.isBlank() -> "Scrivi un titolo"
        recurring -> null
        allDay && end.toLocalDate().isBefore(start.toLocalDate()) -> "La fine è prima dell'inizio"
        !allDay && !end.isAfter(start) -> "La fine deve essere dopo l'inizio"
        else -> null
    }

    /**
     * I campi da scrivere nel calendario. Gli eventi di tutto il giorno vanno in UTC a mezzanotte
     * e finiscono a mezzanotte del giorno dopo l'ultimo, come vuole il provider.
     */
    fun toValues(zone: ZoneId): Map<String, Any?> {
        val values = linkedMapOf<String, Any?>(
            CalendarContract.Events.TITLE to title.trim(),
            CalendarContract.Events.EVENT_LOCATION to location.trim(),
            CalendarContract.Events.DESCRIPTION to description.trim(),
        )
        if (id == null) values[CalendarContract.Events.CALENDAR_ID] = calendarId
        if (recurring) return values
        if (allDay) {
            val first = start.toLocalDate()
            val last = end.toLocalDate().let { if (it.isBefore(first)) first else it }
            values[CalendarContract.Events.DTSTART] = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            values[CalendarContract.Events.DTEND] = last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            values[CalendarContract.Events.ALL_DAY] = 1
            values[CalendarContract.Events.EVENT_TIMEZONE] = "UTC"
        } else {
            values[CalendarContract.Events.DTSTART] = start.atZone(zone).toInstant().toEpochMilli()
            values[CalendarContract.Events.DTEND] = end.atZone(zone).toInstant().toEpochMilli()
            values[CalendarContract.Events.ALL_DAY] = 0
            values[CalendarContract.Events.EVENT_TIMEZONE] = zone.id
        }
        return values
    }

    companion object {
        /** Nuovo evento nel giorno scelto: alla prossima ora piena (o alle 9 se è un altro giorno), per un'ora. */
        fun new(calendarId: Long, day: LocalDate, now: LocalDateTime = LocalDateTime.now()): EventDraft {
            val start = if (day == now.toLocalDate()) {
                now.toLocalDate().atTime(now.hour, 0).plusHours(1)
            } else {
                day.atTime(9, 0)
            }
            return EventDraft(calendarId = calendarId, start = start, end = start.plusHours(1))
        }

        fun from(event: EventDetail, zone: ZoneId): EventDraft {
            val (start, end) = if (event.allDay) {
                // Nel provider la fine è la mezzanotte del giorno dopo: nell'app mostriamo l'ultimo giorno.
                val first = Instant.ofEpochMilli(event.begin).atOffset(ZoneOffset.UTC).toLocalDate()
                val last = Instant.ofEpochMilli(event.end).atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1)
                first.atStartOfDay() to (if (last.isBefore(first)) first else last).atStartOfDay()
            } else {
                Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDateTime() to
                    Instant.ofEpochMilli(event.end).atZone(zone).toLocalDateTime()
            }
            return EventDraft(
                id = event.id,
                calendarId = event.calendarId,
                title = event.title,
                location = event.location,
                description = event.description,
                allDay = event.allDay,
                start = start,
                end = end,
                recurring = event.recurring,
            )
        }
    }
}

/** Calcoli sui giorni del calendario, senza Android: si provano nei test. */
object CalendarDays {

    /** L'evento cade nel giorno indicato? Quelli di tutto il giorno si confrontano per data (sono in UTC). */
    fun occursOn(begin: Long, end: Long, allDay: Boolean, day: LocalDate, zone: ZoneId): Boolean {
        if (allDay) {
            val first = Instant.ofEpochMilli(begin).atOffset(ZoneOffset.UTC).toLocalDate()
            val last = Instant.ofEpochMilli(end).atOffset(ZoneOffset.UTC).toLocalDate()
            return !day.isBefore(first) && day.isBefore(maxOf(last, first.plusDays(1)))
        }
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Un evento di durata zero conta nel giorno in cui inizia.
        return if (end <= begin) begin in dayStart until dayEnd else begin < dayEnd && end > dayStart
    }

    /** Divide gli eventi nei giorni da [first] per [days] giorni; i giorni vuoti restano fuori. */
    fun group(events: List<CalendarEvent>, first: LocalDate, days: Int, zone: ZoneId): Map<LocalDate, List<CalendarEvent>> {
        val unique = events.distinctBy { it.eventId to it.begin }
        val result = linkedMapOf<LocalDate, List<CalendarEvent>>()
        for (i in 0 until days) {
            val day = first.plusDays(i.toLong())
            val list = unique.filter { occursOn(it.begin, it.end, it.allDay, day, zone) }
                // Lo stesso evento da due calendari (es. "Tutti i Santi" nelle festività di Google e del telefono): uno solo.
                .distinctBy { Triple(it.title.trim().lowercase(), it.allDay, if (it.allDay) 0L else it.begin) }
                .sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
            if (list.isNotEmpty()) result[day] = list
        }
        return result
    }

    /** Durata RFC 5545 ("PT1H", "P3600S", "P1D", "P1W") in millisecondi; un'ora se non si capisce. */
    fun durationMs(duration: String?): Long {
        val match = duration?.trim()?.uppercase()?.let { DURATION.matchEntire(it) } ?: return HOUR_MS
        val (weeks, days, hours, minutes, seconds) = match.destructured
        val total = (weeks.toLongOrNull() ?: 0) * 7 * 24 * HOUR_MS +
            (days.toLongOrNull() ?: 0) * 24 * HOUR_MS +
            (hours.toLongOrNull() ?: 0) * HOUR_MS +
            (minutes.toLongOrNull() ?: 0) * 60_000L +
            (seconds.toLongOrNull() ?: 0) * 1000L
        return if (total > 0) total else HOUR_MS
    }

    /** Ora di un evento nel giorno [day]: "Tutto il giorno", "10:00 – 11:30", "Fino alle 9:00", "Dalle 22:00". */
    fun timeLabel(begin: Long, end: Long, allDay: Boolean, day: LocalDate, zone: ZoneId): String {
        if (allDay) return "Tutto il giorno"
        val from = Instant.ofEpochMilli(begin).atZone(zone)
        val to = Instant.ofEpochMilli(end).atZone(zone)
        val startsBefore = from.toLocalDate().isBefore(day)
        val midnight = day.plusDays(1).atStartOfDay()
        // Finire a mezzanotte vuol dire finire nel giorno.
        val endsAfter = to.toLocalDateTime().isAfter(midnight)
        return when {
            startsBefore && !to.toLocalDateTime().isBefore(midnight) -> "Tutto il giorno"
            startsBefore -> "Fino alle ${to.toLocalTime().hhmm()}"
            endsAfter -> "Dalle ${from.toLocalTime().hhmm()}"
            else -> "${from.toLocalTime().hhmm()} – ${to.toLocalTime().hhmm()}"
        }
    }

    private fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)

    private val DURATION = Regex("""[+-]?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")
    private const val HOUR_MS = 60 * 60_000L
}
