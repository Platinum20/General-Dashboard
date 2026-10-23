package com.platinum20.dashboard.today

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Un'abitudine nella settimana: quante volte e quante ne servivano (7 per quelle di ogni giorno). */
data class HabitWeek(val title: String, val count: Int, val target: Int) {
    val reached: Boolean get() = count >= target
    val text: String get() = "$title · $count su $target"
}

/** Le spese variabili della settimana per categoria, contro la media delle 4 settimane prima. */
data class SpendWeek(
    val cents: Long,
    val averageCents: Long,
    /** La categoria che è cresciuta di più rispetto alla media, con la percentuale; null se nessuna. */
    val biggestRise: Pair<ExpenseCategory, Int>?,
) {
    /** Differenza percentuale con la media, null se prima non c'era niente da confrontare. */
    val changePercent: Int? get() = if (averageCents <= 0) null else ((cents - averageCents) * 100.0 / averageCents).roundToInt()
}

enum class ComingKind { EVENT, DATE, MONEY }

data class ComingLine(val kind: ComingKind, val day: LocalDate, val text: String)

/** "La tua settimana": com'è andata (lun-dom) e cosa arriva nella prossima. */
data class WeekReviewData(
    val monday: LocalDate,
    val headline: String,
    val tasksDone: Int,
    val habits: List<HabitWeek>,
    val spend: SpendWeek?,
    val parcelsArrived: Int,
    val coming: List<ComingLine>,
) {
    val isEmpty: Boolean get() = tasksDone == 0 && habits.isEmpty() && spend == null && parcelsArrived == 0 && coming.isEmpty()
}

object WeekReview {
    /** La card c'è dalla domenica alle 17 al lunedì a mezzogiorno. */
    const val FROM_HOUR = 17
    const val UNTIL_HOUR = 12

    /** Il lunedì della settimana da raccontare a quest'ora, null fuori dalla finestra della card. */
    fun weekToReview(now: LocalDateTime): LocalDate? {
        val day = now.toLocalDate()
        return when {
            now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= FROM_HOUR -> day.with(DayOfWeek.MONDAY)
            now.dayOfWeek == DayOfWeek.MONDAY && now.hour < UNTIL_HOUR -> day.minusWeeks(1)
            else -> null
        }
    }

    fun build(
        monday: LocalDate,
        tasks: List<TaskItem>,
        habits: List<Habit>,
        expenses: List<Expense>,
        fixed: List<FixedExpense>,
        parcels: List<Parcel>,
        countdowns: List<Countdown>,
        zone: ZoneId = ZoneId.systemDefault(),
        /** Gli impegni del calendario della settimana nuova, giorno per giorno (vuoto senza permesso). */
        events: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    ): WeekReviewData {
        val sunday = monday.plusDays(6)
        fun inWeek(d: LocalDate) = !d.isBefore(monday) && !d.isAfter(sunday)

        fun msInWeek(ms: Long) = inWeek(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())
        // I ricorrenti non si chiudono mai: contano le volte in cui sono stati spuntati.
        val tasksDone = tasks.count { t -> t.done && t.doneAt?.let(::msInWeek) == true } +
            tasks.sumOf { t -> t.doneTimes.count(::msInWeek) }

        val habitWeeks = habits.map { h ->
            HabitWeek(h.title, h.done.count(::inWeek), if (h.weekly) h.perWeek else 7)
        }

        val spend = spendFor(monday, expenses)
        val parcelsArrived = parcels.count { p -> p.deliveredOn?.let(::inWeek) == true }

        val nextMonday = monday.plusWeeks(1)
        val nextSunday = nextMonday.plusDays(6)
        fun inNext(d: LocalDate) = !d.isBefore(nextMonday) && !d.isAfter(nextSunday)
        val coming = buildList {
            addAll(eventLines(events, nextMonday, zone))
            countdowns.forEach { c ->
                val next = c.nextDate(nextMonday)
                if (inNext(next)) {
                    val years = c.yearsAtNext(nextMonday)
                    add(ComingLine(ComingKind.DATE, next, "${dayName(next)} · ${c.title}" + (years?.let { " ($it anni)" } ?: "")))
                }
            }
            fixed.forEach { f ->
                val next = f.nextDate(nextMonday)
                if (inNext(next)) add(ComingLine(ComingKind.MONEY, next, "${dayName(next)} · ${f.text} ${Expenses.format(f.cents)}"))
            }
        }.sortedWith(compareBy<ComingLine> { it.day }.thenBy { it.kind.ordinal })

        return WeekReviewData(
            monday = monday,
            headline = headline(tasksDone, habitWeeks, spend),
            tasksDone = tasksDone,
            habits = habitWeeks,
            spend = spend,
            parcelsArrived = parcelsArrived,
            coming = coming,
        )
    }

    /**
     * Gli impegni della settimana nuova: uno per riga, una volta sola anche se durano più giorni
     * (nel primo giorno della settimana in cui ci sono). Al massimo [MAX_EVENTS], i primi in ordine.
     */
    internal fun eventLines(events: Map<LocalDate, List<CalendarEvent>>, nextMonday: LocalDate, zone: ZoneId): List<ComingLine> {
        val nextSunday = nextMonday.plusDays(6)
        val seen = mutableSetOf<Pair<Long, Long>>()
        val lines = mutableListOf<ComingLine>()
        events.keys.filter { !it.isBefore(nextMonday) && !it.isAfter(nextSunday) }.sorted().forEach { day ->
            events[day].orEmpty().forEach { e ->
                if (seen.add(e.eventId to e.begin)) {
                    val time = if (e.allDay) "" else " " + Instant.ofEpochMilli(e.begin).atZone(zone).toLocalTime().let { "%d:%02d".format(it.hour, it.minute) }
                    lines += ComingLine(ComingKind.EVENT, day, "${dayName(day)}$time · ${e.title}")
                }
            }
        }
        return lines.take(MAX_EVENTS)
    }

    const val MAX_EVENTS = 6

    /** Spese a mano (non le fisse) della settimana contro la media delle 4 prima. Null se non ce ne sono. */
    internal fun spendFor(monday: LocalDate, expenses: List<Expense>): SpendWeek? {
        val variable = expenses.filter { it.fixedId == null }
        fun between(from: LocalDate, days: Long) = variable.filter { !it.date.isBefore(from) && it.date.isBefore(from.plusDays(days)) }
        val week = between(monday, 7)
        val before = between(monday.minusWeeks(4), 28)
        if (week.isEmpty()) return null
        val cents = week.sumOf { it.cents }
        val average = before.sumOf { it.cents } / 4
        val rise = if (before.isEmpty()) null else week.groupBy { it.category }
            .mapNotNull { (cat, list) ->
                val now = list.sumOf { it.cents }
                val avg = before.filter { it.category == cat }.sumOf { it.cents } / 4.0
                if (avg <= 0) null else cat to ((now - avg) * 100 / avg).roundToInt()
            }
            .filter { it.second >= 20 }
            .maxByOrNull { it.second }
        return SpendWeek(cents, average, rise)
    }

    /** La frase in cima: le cose buone prima, in breve. */
    internal fun headline(tasksDone: Int, habits: List<HabitWeek>, spend: SpendWeek?): String {
        val parts = mutableListOf<String>()
        if (tasksDone > 0) parts += if (tasksDone == 1) "1 cosa fatta" else "$tasksDone cose fatte"
        habits.filter { it.reached }.take(1).forEach { parts += "${it.title.lowercase()} ${it.count} su ${it.target}" }
        spend?.changePercent?.let { change ->
            parts += when {
                change <= -5 -> "spese $change%"
                change >= 5 -> "spese +$change%"
                else -> "spese come al solito"
            }
        }
        if (parts.isEmpty()) return "Settimana tranquilla: niente segnato in Canvas"
        val good = habits.isNotEmpty() && habits.count { it.reached } * 2 >= habits.size || tasksDone >= 5
        return (if (good) "Settimana buona: " else "La tua settimana: ") + parts.joinToString(", ")
    }

    private fun dayName(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
}
