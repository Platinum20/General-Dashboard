package com.platinum20.dashboard.search

import com.platinum20.dashboard.notes.NotesSearch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Un pezzo di calendario chiesto a parole: un giorno, una settimana, un mese. */
data class WhenSpan(val from: LocalDate, val to: LocalDate, val label: String) {
    fun contains(day: LocalDate): Boolean = !day.isBefore(from) && !day.isAfter(to)
}

/**
 * Le parole del tempo in Cerca (2026-11-26, punto 5): "domani", "venerdì", "marzo", "12 marzo", "prossima settimana"
 * mostrano quel pezzo di calendario. Si possono unire a una parola: "turno venerdì" = i turni di venerdì.
 * Funzioni pure, provate nei test.
 */
object SearchWhen {
    private val MONTHS = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    private val DAYS = mapOf(
        "lunedi" to DayOfWeek.MONDAY, "martedi" to DayOfWeek.TUESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "giovedi" to DayOfWeek.THURSDAY, "venerdi" to DayOfWeek.FRIDAY, "sabato" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY,
    )
    private val IT = Locale.ITALIAN
    private val NUMERIC = Regex("""(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?""")

    /**
     * Il tempo dentro la ricerca e quello che resta: "turno venerdì" → (venerdì, "turno"). Il tempo si cerca
     * all'inizio o alla fine, al più tre parole. Null se non c'è.
     */
    fun split(query: String, today: LocalDate): Pair<WhenSpan, String>? {
        val words = NotesSearch.normalize(query).split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for (n in minOf(3, words.size) downTo 1) {
            parse(words.takeLast(n).joinToString(" "), today)?.let { return it to words.dropLast(n).joinToString(" ") }
            parse(words.take(n).joinToString(" "), today)?.let { return it to words.drop(n).joinToString(" ") }
        }
        return null
    }

    /** Solo il tempo, tutta la frase: null se è altro. */
    fun parse(text: String, today: LocalDate): WhenSpan? {
        val q = NotesSearch.normalize(text).removePrefix("di ").removePrefix("il ").trim()
        when (q) {
            "oggi" -> return day(today, "Oggi")
            "domani" -> return day(today.plusDays(1), "Domani")
            "dopodomani" -> return day(today.plusDays(2), "Dopodomani")
            "ieri" -> return day(today.minusDays(1), "Ieri")
            "questa settimana", "settimana" -> return WhenSpan(today, today.with(DayOfWeek.SUNDAY), "Questa settimana")
            "prossima settimana", "settimana prossima" -> {
                val monday = today.with(DayOfWeek.MONDAY).plusWeeks(1)
                return WhenSpan(monday, monday.plusDays(6), "La prossima settimana")
            }
            "weekend", "fine settimana", "questo weekend" -> {
                val sat = if (today.dayOfWeek == DayOfWeek.SUNDAY) today.minusDays(1) else today.with(DayOfWeek.SATURDAY)
                return WhenSpan(maxOf(sat, today), sat.plusDays(1), "Il fine settimana")
            }
            "questo mese", "mese" -> return month(today.withDayOfMonth(1))
            "prossimo mese", "mese prossimo" -> return month(today.withDayOfMonth(1).plusMonths(1))
        }
        // "venerdì", "venerdi prossimo": il prossimo (oggi, se è oggi).
        val dayWord = q.removeSuffix(" prossimo").removeSuffix(" prossima").removePrefix("prossimo ").removePrefix("prossima ")
        DAYS[dayWord]?.let { dow ->
            var d = today
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            if (dayWord != q && d == today) d = d.plusWeeks(1)
            return day(d, label(d, today))
        }
        // "marzo", "marzo 2027": il marzo più vicino a oggi.
        val parts = q.split(' ')
        val monthAt = MONTHS.indexOf(parts.first())
        if (monthAt >= 0 && parts.size <= 2) {
            val year = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && year == null) return null
            return month(year?.let { LocalDate.of(it, monthAt + 1, 1) } ?: nearestMonth(monthAt + 1, today))
        }
        // "12 marzo", "12 marzo 2027".
        if (parts.size in 2..3) {
            val d = parts[0].toIntOrNull()
            val m = MONTHS.indexOf(parts[1])
            val y = parts.getOrNull(2)?.toIntOrNull()
            if (d != null && m >= 0 && (parts.size == 2 || y != null)) {
                return dayOf(d, m + 1, y, today)?.let { day(it, label(it, today)) }
            }
        }
        // "12/3", "12/03/2027".
        NUMERIC.matchEntire(q)?.let { r ->
            val d = r.groupValues[1].toInt()
            val m = r.groupValues[2].toInt()
            val y = r.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            return dayOf(d, m, y, today)?.let { day(it, label(it, today)) }
        }
        return null
    }

    private fun day(d: LocalDate, label: String) = WhenSpan(d, d, label)

    private fun month(first: LocalDate) = WhenSpan(
        first, first.plusMonths(1).minusDays(1),
        first.month.getDisplayName(TextStyle.FULL_STANDALONE, IT).replaceFirstChar { it.uppercase() } + " " + first.year,
    )

    /** Il mese con quel numero più vicino a oggi, avanti o indietro (a novembre "ottobre" è quello appena passato). */
    internal fun nearestMonth(month: Int, today: LocalDate): LocalDate {
        val base = today.withDayOfMonth(1)
        return listOf(today.year - 1, today.year, today.year + 1)
            .map { LocalDate.of(it, month, 1) }
            .minBy { kotlin.math.abs(java.time.temporal.ChronoUnit.MONTHS.between(base, it)) }
    }

    private fun dayOf(d: Int, m: Int, y: Int?, today: LocalDate): LocalDate? {
        if (m !in 1..12) return null
        if (y != null) return runCatching { LocalDate.of(y, m, d) }.getOrNull()
        val year = nearestMonth(m, today).year
        return runCatching { LocalDate.of(year, m, d) }.getOrNull()
    }

    /** "Venerdì 28 novembre", con l'anno solo se non è quello di oggi. */
    fun label(d: LocalDate, today: LocalDate): String {
        val name = d.dayOfWeek.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { it.uppercase() }
        val month = d.month.getDisplayName(TextStyle.FULL, IT)
        return "$name ${d.dayOfMonth} $month" + if (d.year != today.year) " ${d.year}" else ""
    }
}
