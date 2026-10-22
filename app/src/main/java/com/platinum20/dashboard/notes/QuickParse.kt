package com.platinum20.dashboard.notes

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Ogni quanto si ripete un promemoria. Spuntandolo, la scadenza passa alla volta successiva. */
enum class Repeat(val label: String) {
    DAILY("ogni giorno"),
    WEEKLY("ogni settimana"),
    MONTHLY("ogni mese"),
    YEARLY("ogni anno");

    fun next(from: LocalDateTime): LocalDateTime = when (this) {
        DAILY -> from.plusDays(1)
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonths(1)
        YEARLY -> from.plusYears(1)
    }

    /** La prima occorrenza dopo [now], partendo da [from]. */
    fun nextAfter(from: LocalDateTime, now: LocalDateTime): LocalDateTime {
        var next = next(from)
        while (!next.isAfter(now)) next = next(next)
        return next
    }
}

/** Un promemoria scritto a parole, già diviso in testo, scadenza e ripetizione. */
data class QuickEntry(val text: String, val due: LocalDateTime?, val repeat: Repeat?)

/**
 * Capisce le date scritte come si parla: "domani alle 9 dentista", "venerdì 18:30 palestra",
 * "tra 20 minuti forno", "ogni mese il 5 affitto", "15 novembre bollo auto".
 * Toglie dal testo le parole usate per la data. Funzione pura, niente rete.
 */
object QuickParse {
    // \b non conosce le lettere accentate: "lunedì" finirebbe a metà parola.
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"

    private val weekdays = listOf(
        "luned[iì]" to DayOfWeek.MONDAY,
        "marted[iì]" to DayOfWeek.TUESDAY,
        "mercoled[iì]" to DayOfWeek.WEDNESDAY,
        "gioved[iì]" to DayOfWeek.THURSDAY,
        "venerd[iì]" to DayOfWeek.FRIDAY,
        "sabato" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY,
    )
    private val weekdayGroup = weekdays.joinToString("|") { it.first }

    private val months = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    private val monthGroup = months.joinToString("|") + "|gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic"

    private fun rx(pattern: String) = Regex("$B0(?:$pattern)$B1", RegexOption.IGNORE_CASE)

    private val everyDay = rx("(?:ogni giorno|tutti i giorni)")
    private val everyWeekday = rx("(?:ogni|tutti i|tutte le) ($weekdayGroup)")
    private val everyWeek = rx("ogni settimana")
    private val everyMonthDay = rx("(?:il )?(\\d{1,2}) (?:di )?ogni mese|ogni mese (?:il )?(\\d{1,2})")
    private val everyMonth = rx("ogni mese")
    private val everyYear = rx("(?:ogni anno|tutti gli anni)")

    /** Numeri scritti a lettere: "tra due ore", "fra dieci minuti". */
    private val numberWords = mapOf(
        "due" to 2L, "tre" to 3L, "quattro" to 4L, "cinque" to 5L, "sei" to 6L, "sette" to 7L,
        "otto" to 8L, "nove" to 9L, "dieci" to 10L, "quindici" to 15L, "venti" to 20L,
        "trenta" to 30L, "quaranta" to 40L, "quarantacinque" to 45L,
    )
    private val inAmount = rx("(?:tra|fra) (un|una|un'|mezz'|mezza|\\d+|${numberWords.keys.joinToString("|")}) ?(minut[oi]|min|or[ae]|giorn[oi]|settiman[ae]|mes[ei])(?: (e mezz[ao]))?")
    private val dayWord = rx("(oggi|domani|dopodomani)(?: (mattina|pomeriggio|sera))?")
    private val dayShort = rx("(stamattina|stasera|domattina|stanotte)")
    private val weekday = rx("(?:(?:il|di|questo|questa|prossimo|prossima) )?($weekdayGroup)(?: (prossim[oa]))?")
    private val numericDate = rx("(?:il )?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?")
    private val namedDate = rx("(?:il )?(\\d{1,2}) ($monthGroup)(?: (\\d{4}))?")
    private val dayOfMonth = rx("il (\\d{1,2})")
    private val noon = rx("(?:a )?mezzogiorno")
    private val time = rx("(?:alle|all'|ore|per le|verso le|h) ?(\\d{1,2})(?:[:.](\\d{2}))?(?: (e mezza|e mezzo|e un quarto))?")
    private val bareTime = rx("(\\d{1,2})[:.](\\d{2})")

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): QuickEntry {
        var s = " ${input.trim()} "
        val today = now.toLocalDate()
        var date: LocalDate? = null
        var clock: LocalTime? = null
        var exact: LocalDateTime? = null
        var repeat: Repeat? = null
        // "mattina" / "pomeriggio" / "sera": decide l'ora di base e se "alle 9" vuol dire 21.
        var part: String? = null

        fun take(regex: Regex, use: (MatchResult) -> Boolean) {
            val m = regex.find(s) ?: return
            if (use(m)) s = s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)
        }

        // Ripetizioni.
        take(everyDay) { repeat = Repeat.DAILY; true }
        take(everyWeekday) { m ->
            repeat = Repeat.WEEKLY
            date = today.with(TemporalAdjusters.nextOrSame(dayOf(m.groupValues[1])))
            true
        }
        if (repeat == null) take(everyWeek) { repeat = Repeat.WEEKLY; true }
        if (repeat == null) take(everyMonthDay) { m ->
            val day = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            if (day !in 1..31) return@take false
            repeat = Repeat.MONTHLY
            date = monthDay(today, day).let { if (it.isBefore(today)) monthDay(today.plusMonths(1), day) else it }
            true
        }
        if (repeat == null) take(everyMonth) { repeat = Repeat.MONTHLY; true }
        if (repeat == null) take(everyYear) { repeat = Repeat.YEARLY; true }

        // "Tra 20 minuti", "fra 3 giorni".
        take(inAmount) { m ->
            val n = when (m.groupValues[1].lowercase()) {
                "un", "una", "un'" -> 1L
                "mezz'", "mezza" -> 0L
                else -> numberWords[m.groupValues[1].lowercase()] ?: m.groupValues[1].toLong()
            }
            val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("min") -> exact = now.plusMinutes(n)
                // "Tra un'ora e mezza": la mezz'ora in più conta solo per le ore.
                unit.startsWith("or") -> exact = if (n == 0L) now.plusMinutes(30)
                    else now.plusHours(n).plusMinutes(if (m.groupValues[3].isNotEmpty()) 30 else 0)
                unit.startsWith("giorn") -> date = today.plusDays(n)
                unit.startsWith("settiman") -> date = today.plusWeeks(n)
                else -> date = today.plusMonths(n)
            }
            true
        }

        // Giorni a parole.
        take(dayShort) { m ->
            when (m.groupValues[1].lowercase()) {
                "stamattina" -> { date = today; part = "mattina" }
                "stasera", "stanotte" -> { date = today; part = "sera" }
                else -> { date = today.plusDays(1); part = "mattina" }
            }
            true
        }
        if (date == null) take(dayWord) { m ->
            date = when (m.groupValues[1].lowercase()) {
                "oggi" -> today
                "domani" -> today.plusDays(1)
                else -> today.plusDays(2)
            }
            part = m.groupValues[2].lowercase().ifEmpty { null }
            true
        }
        if (date == null) take(weekday) { m ->
            // "venerdì" detto di venerdì è quello della settimana dopo.
            date = today.with(TemporalAdjusters.next(dayOf(m.groupValues[1])))
            true
        }
        if (date == null) take(numericDate) { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            date = safeDate(today, y, mo, d) ?: return@take false
            true
        }
        if (date == null) take(namedDate) { m ->
            val mo = months.indexOfFirst { it.startsWith(m.groupValues[2].lowercase().take(3)) } + 1
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            date = safeDate(today, y, mo, m.groupValues[1].toInt()) ?: return@take false
            true
        }

        // L'ora.
        take(noon) { clock = LocalTime.NOON; true }
        if (clock == null) take(time) { m -> clock = clockOf(m.groupValues[1], m.groupValues[2], m.groupValues[3], part); clock != null }
        if (clock == null) take(bareTime) { m -> clock = clockOf(m.groupValues[1], m.groupValues[2], "", part); clock != null }

        // "Il 15" da solo: solo dopo aver tolto le ore, per non confonderlo con "alle 15".
        if (date == null && repeat == null) take(dayOfMonth) { m ->
            val d = m.groupValues[1].toInt()
            if (d !in 1..31) return@take false
            date = monthDay(today, d).let { if (it.isBefore(today)) monthDay(today.plusMonths(1), d) else it }
            true
        }

        val text = clean(s).ifEmpty { input.trim() }
        var due: LocalDateTime? = exact ?: when {
            date == null && clock == null && repeat == null -> null
            else -> {
                val day = date ?: today
                val at = clock ?: when (part) {
                    "mattina" -> LocalTime.of(9, 0)
                    "pomeriggio" -> LocalTime.of(15, 0)
                    "sera" -> LocalTime.of(20, 0)
                    // Oggi senza ora: tra un'ora tonda, non alle 9 già passate.
                    else -> if (day == today && date != null) nextHour(now) else LocalTime.of(9, 0)
                }
                var result = day.atTime(at)
                // Un'ora già passata senza giorno vuol dire domani.
                if (date == null && !result.isAfter(now)) result = result.plusDays(1)
                result
            }
        }
        val r = repeat
        if (r != null && due != null && !due.isAfter(now)) due = r.nextAfter(due, now)
        return QuickEntry(text, due, repeat)
    }

    private fun dayOf(word: String): DayOfWeek =
        weekdays.first { Regex(it.first, RegexOption.IGNORE_CASE).matches(word) }.second

    private fun monthDay(month: LocalDate, day: Int): LocalDate =
        month.withDayOfMonth(day.coerceAtMost(month.lengthOfMonth()))

    /** Una data senza anno è la prossima volta che arriva. */
    private fun safeDate(today: LocalDate, year: Int?, month: Int, day: Int): LocalDate? {
        val d = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isBefore(today)) d.plusYears(1) else d
    }

    private fun clockOf(h: String, m: String, half: String, part: String?): LocalTime? {
        var hour = h.toIntOrNull() ?: return null
        var minute = m.toIntOrNull() ?: 0
        when (half.lowercase()) {
            "e mezza", "e mezzo" -> minute = 30
            "e un quarto" -> minute = 15
        }
        if (hour > 23 || minute > 59) return null
        // "Alle 3" è quasi sempre il pomeriggio, a meno di dire "mattina".
        if (hour in 1..7 && part != "mattina") hour += 12
        if (hour < 12 && (part == "sera" || part == "pomeriggio")) hour += 12
        return LocalTime.of(hour, minute)
    }

    private fun nextHour(now: LocalDateTime): LocalTime = when {
        now.hour < 9 -> LocalTime.of(9, 0)
        now.hour >= 23 -> LocalTime.of(23, 59)
        else -> LocalTime.of(now.hour + 1, 0)
    }

    private fun clean(s: String): String = s
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,.;:])"), "$1")
        .trim()
        .trim(',', '.', ';', ':', '-', ' ')
        .replaceFirstChar { it.uppercase() }
}
