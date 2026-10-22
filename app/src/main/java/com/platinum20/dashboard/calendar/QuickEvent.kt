package com.platinum20.dashboard.calendar

import com.platinum20.dashboard.notes.QuickParse
import java.time.LocalDateTime
import java.time.LocalTime

/** Un evento scritto a parole, pronto per il calendario. */
data class QuickEventDraft(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
) {
    fun toDraft(calendarId: Long) = EventDraft(calendarId = calendarId, title = title, allDay = allDay, start = start, end = end)
}

/**
 * Dal "+": "venerdì alle 20 cena da Luca", "domani dalle 9 alle 11 corso",
 * "giovedì alle 15 dentista per 2 ore", "sabato tutto il giorno gita".
 * Giorno e ora li capisce [QuickParse]; qui si aggiungono la durata e l'intera giornata.
 * Senza un giorno o un'ora non è un evento: null. Funzione pura.
 */
object QuickEvent {
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"
    private fun rx(p: String) = Regex("$B0(?:$p)$B1", RegexOption.IGNORE_CASE)

    private val allDayWords = rx("tutto il giorno|tutta la giornata|l'intera giornata")
    private val range = rx("(?:dalle|dall') ?(\\d{1,2})(?:[:.](\\d{2}))? (?:alle|all'|fino alle) ?(\\d{1,2})(?:[:.](\\d{2}))?")
    private val halfHour = rx("per mezz'ora")
    private val hourAndHalf = rx("per un'ora e mezza|per un'ora e mezzo")
    private val oneHour = rx("per un'ora")
    private val hours = rx("per (\\d{1,2}) or[ae]( e mezza| e mezzo)?")
    private val minutes = rx("per (\\d{1,3}) min(?:uti)?")

    /** Durata quando non si dice: un'ora. */
    const val DEFAULT_MINUTES = 60L

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): QuickEventDraft? {
        var s = " " + input.replace('’', '\'').trim() + " "
        fun cut(m: MatchResult, with: String = " ") {
            s = s.substring(0, m.range.first) + with + s.substring(m.range.last + 1)
        }

        val allDay = allDayWords.find(s)?.also { cut(it) } != null

        var minutesLong: Long? = null
        var endClock: Pair<Int, Int>? = null
        range.find(s)?.let { m ->
            endClock = m.groupValues[3].toInt() to (m.groupValues[4].toIntOrNull() ?: 0)
            // L'inizio lo legge QuickParse, con le sue regole per mattina e pomeriggio.
            cut(m, " alle ${m.groupValues[1]}" + (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { ":$it" } ?: "") + " ")
        }
        if (endClock == null) {
            halfHour.find(s)?.let { minutesLong = 30; cut(it) }
                ?: hourAndHalf.find(s)?.let { minutesLong = 90; cut(it) }
                ?: oneHour.find(s)?.let { minutesLong = 60; cut(it) }
                ?: hours.find(s)?.let { m ->
                    minutesLong = m.groupValues[1].toLong() * 60 + if (m.groupValues[2].isNotEmpty()) 30 else 0
                    cut(m)
                }
                ?: minutes.find(s)?.let { m -> minutesLong = m.groupValues[1].toLong(); cut(m) }
        }

        val entry = QuickParse.parse(s, now)
        val due = entry.due ?: return null
        val title = entry.text.trim().replaceFirstChar { it.uppercase() }
        if (title.isEmpty()) return null
        if (allDay) {
            val day = due.toLocalDate().atStartOfDay()
            return QuickEventDraft(title, day, day, allDay = true)
        }
        val end = endClock?.let { (h, m) -> endAfter(due, h, m) }
            ?: due.plusMinutes(minutesLong?.takeIf { it > 0 } ?: DEFAULT_MINUTES)
        return QuickEventDraft(title, due, end, allDay = false)
    }

    /** "Dalle 9 alle 11": la fine è la prima ora utile dopo l'inizio (anche "dalle 14 alle 6" di sera → 18). */
    private fun endAfter(start: LocalDateTime, hour: Int, minute: Int): LocalDateTime? {
        if (hour > 24 || minute > 59) return null
        var end = start.toLocalDate().atTime(LocalTime.of(hour % 24, minute))
        if (hour == 24) end = end.plusDays(1)
        if (!end.isAfter(start) && hour < 12) end = end.plusHours(12)
        if (!end.isAfter(start)) end = end.plusDays(1)
        return end
    }
}
