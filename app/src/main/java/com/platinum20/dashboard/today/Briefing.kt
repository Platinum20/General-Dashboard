package com.platinum20.dashboard.today

import com.platinum20.dashboard.media.Resume
import com.platinum20.dashboard.media.ResumeItem
import android.content.Context
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Parcel
import java.time.YearMonth
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.asTemp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Di che cosa parla una riga di "Adesso": decide l'icona. */
enum class BriefKind { EVENT, RAIN, TASK, DATE, WEATHER, HABIT, PARCEL, MONEY, MEDIA, FREE }

data class BriefLine(val kind: BriefKind, val text: String, val urgent: Boolean = false)

/**
 * "Adesso": le poche cose che servono in questo momento, ricavate da meteo, agenda,
 * promemoria e date. Al mattino e di giorno guarda a oggi, la sera anche a domani.
 * Niente rete qui dentro: è una funzione pura, facile da provare.
 */
object Briefing {
    private val hour = DateTimeFormatter.ofPattern("HH:mm")

    /** Da quest'ora in poi "Adesso" guarda anche a domani. */
    const val EVENING_HOUR = 18

    /** Da quest'ora "Adesso" ricorda le abitudini non ancora spuntate. */
    const val HABIT_HOUR = 17

    fun build(
        now: LocalDateTime,
        forecast: Forecast?,
        today: List<CalendarEvent>?,
        tomorrow: List<CalendarEvent>?,
        tasks: List<TaskItem>,
        countdowns: List<Countdown>,
        zone: ZoneId = ZoneId.systemDefault(),
        max: Int = 4,
        /** false per il widget, che si aggiorna ogni ora: "alle 15:00" invece di "tra 25 min". */
        relative: Boolean = true,
        habits: List<Habit> = emptyList(),
        parcels: List<Parcel> = emptyList(),
        expenses: List<Expense> = emptyList(),
        budgetCents: Long? = null,
        fixedExpenses: List<FixedExpense> = emptyList(),
        /** Video lasciato a metà nelle ultime 24 ore (vedi Resume.forAdesso). */
        resume: ResumeItem? = null,
        /**
         * true quando sotto c'è la card "Domani" (in Oggi la sera): meteo, impegni, date, pacchi e
         * addebiti di domani li dice già lei, quindi qui non si ripetono. Il widget non ha la card.
         */
        tomorrowCard: Boolean = false,
    ): List<BriefLine> {
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val evening = now.hour >= EVENING_HOUR
        // Le righe su domani: solo se non c'è già la card che ne parla.
        val aboutTomorrow = evening && !tomorrowCard
        val lines = mutableListOf<BriefLine>()

        // Agenda: quello che è in corso, poi il prossimo impegno di oggi.
        val timed = today.orEmpty().filter { !it.allDay }
        timed.firstOrNull { it.begin <= nowMs && it.end > nowMs }?.let {
            lines += BriefLine(BriefKind.EVENT, "In corso: ${it.title}, fino alle ${it.end.at(zone)}")
        }
        val next = timed.firstOrNull { it.begin > nowMs }
        if (next != null) {
            val minutes = Duration.ofMillis(next.begin - nowMs).toMinutes()
            val text = when {
                relative && minutes < 60 -> "Tra ${minutes.coerceAtLeast(1)} min: ${next.title}"
                else -> "Alle ${next.begin.at(zone)}: ${next.title}"
            } + if (next.location.isNotBlank()) " · ${next.location}" else ""
            lines += BriefLine(BriefKind.EVENT, text, urgent = minutes < 60)
        }

        // Pioggia nelle prossime ore: è l'unica cosa del meteo che cambia i piani.
        forecast?.rainAlert?.let { lines += BriefLine(BriefKind.RAIN, it, urgent = true) }

        // Promemoria: prima gli scaduti, poi quelli in scadenza entro stasera.
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val overdue = tasks.count { !it.done && it.dueAt != null && it.dueAt < nowMs }
        val dueToday = tasks.filter { !it.done && it.dueAt != null && it.dueAt in nowMs until endOfDay }
        if (overdue > 0) {
            lines += BriefLine(BriefKind.TASK, if (overdue == 1) "1 promemoria scaduto" else "$overdue promemoria scaduti", urgent = true)
        }
        when (dueToday.size) {
            0 -> Unit
            1 -> lines += BriefLine(BriefKind.TASK, "Entro le ${dueToday[0].dueAt!!.at(zone)}: ${dueToday[0].text}")
            else -> lines += BriefLine(BriefKind.TASK, "${dueToday.size} promemoria entro stasera")
        }

        // Date: oggi sempre, domani solo la sera (al mattino è presto per pensarci).
        val date = now.toLocalDate()
        // Per compleanni e anniversari con l'anno vero, anche quanti anni compie.
        fun Countdown.label() = title + (yearsAtNext(date)?.let { " ($it anni)" } ?: "")
        countdowns.filter { it.daysLeft(date) == 0L }.forEach { lines += BriefLine(BriefKind.DATE, "Oggi: ${it.label()}") }
        if (aboutTomorrow) countdowns.filter { it.daysLeft(date) == 1L }.forEach { lines += BriefLine(BriefKind.DATE, "Domani: ${it.label()}") }

        // Pacchi attesi oggi (o in ritardo), la sera anche quelli di domani.
        val waiting = parcels.filter { !it.delivered && it.expected != null }
        waiting.filter { !it.expected!!.isAfter(date) }.takeIf { it.isNotEmpty() }?.let { list ->
            lines += BriefLine(BriefKind.PARCEL, "Pacco in arrivo: " + list.joinToString(", ") { it.title })
        }
        if (aboutTomorrow) waiting.filter { it.expected == date.plusDays(1) }.takeIf { it.isNotEmpty() }?.let { list ->
            lines += BriefLine(BriefKind.PARCEL, "Domani arriva: " + list.joinToString(", ") { it.title })
        }

        // Spese fisse: il giorno prima, per non trovarsi l'addebito a sorpresa.
        if (!tomorrowCard) fixedExpenses.filter { it.nextDate(date) == date.plusDays(1) }.takeIf { it.isNotEmpty() }?.let { list ->
            lines += BriefLine(
                BriefKind.MONEY,
                "Domani addebito: " + list.joinToString(", ") { "${it.text.ifEmpty { it.category.label }} ${Expenses.format(it.cents)}" },
            )
        }

        // Spese: se ne parla solo quando il tetto del mese è vicino.
        if (budgetCents != null && budgetCents > 0) {
            val spent = Expenses.total(expenses, YearMonth.from(date))
            if (spent >= budgetCents * 8 / 10) {
                lines += BriefLine(
                    BriefKind.MONEY,
                    if (spent > budgetCents) "Budget del mese superato: ${Expenses.format(spent)} su ${Expenses.format(budgetCents)}"
                    else "Spese del mese: ${Expenses.format(spent)} su ${Expenses.format(budgetCents)}",
                    urgent = spent > budgetCents,
                )
            }
        }

        // Abitudini: dal tardo pomeriggio, quelle non ancora fatte oggi (prima c'è tempo).
        if (now.hour >= HABIT_HOUR) {
            // Le settimanali solo quando serve farle oggi per restare nell'obiettivo.
            val missing = habits.filter { it.needsReminder(now) }
            when {
                missing.isEmpty() -> Unit
                missing.size <= 2 -> lines += BriefLine(BriefKind.HABIT, "Ancora da fare oggi: " + missing.joinToString(", ") { it.title })
                else -> lines += BriefLine(BriefKind.HABIT, "${missing.size} abitudini ancora da fare oggi")
            }
        }

        if (aboutTomorrow) {
            // La sera interessa com'è domani e a che ora si comincia.
            forecast?.daily?.firstOrNull { it.date == date.plusDays(1) }?.let { d ->
                lines += BriefLine(
                    BriefKind.WEATHER,
                    "Domani ${d.max.asTemp()} / ${d.min.asTemp()}" +
                        if (d.precipitationProbability >= 40) ", pioggia ${d.precipitationProbability}%" else "",
                )
            }
            val first = tomorrow.orEmpty().firstOrNull { !it.allDay } ?: tomorrow.orEmpty().firstOrNull()
            if (first != null) {
                lines += BriefLine(
                    BriefKind.EVENT,
                    if (first.allDay) "Domani: ${first.title}" else "Domani alle ${first.begin.at(zone)}: ${first.title}",
                )
            }
        } else if (!evening) {
            // Di giorno: avviso UV solo quando serve davvero.
            forecast?.today?.takeIf { it.uvMax >= 6 && now.hour < 16 }?.let {
                lines += BriefLine(BriefKind.WEATHER, "UV alto (${Math.round(it.uvMax)}): meglio la crema")
            }
        }

        // Ultima, così non scavalca impegni e scadenze.
        resume?.let { item ->
            val where = Resume.progressLabel(item).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            lines += BriefLine(BriefKind.MEDIA, "Riprendi: ${item.title}$where")
        }

        if (lines.isEmpty() && today != null && (!evening || tomorrow != null)) {
            lines += BriefLine(BriefKind.FREE, if (evening) "Niente in programma fino a domani" else "Nessun impegno né scadenza oggi")
        }
        // Le urgenze in cima, poi l'ordine in cui le abbiamo pensate.
        return lines.withIndex().sortedWith(compareBy({ !it.value.urgent }, { it.index })).map { it.value }.take(max)
    }

    /** Lo stesso riepilogo letto dai dati salvati sul telefono, per widget e lavori in background. */
    suspend fun load(context: Context, relative: Boolean = false, max: Int = 4): List<BriefLine> {
        NotesStore.init(context)
        val notes = NotesStore.data.value
        val day = LocalDate.now()
        val calendar = CalendarRepository.hasPermission(context)
        return build(
            now = LocalDateTime.now(),
            forecast = WeatherStore.cached(context),
            today = if (calendar) CalendarRepository.eventsOn(context, day) else null,
            tomorrow = if (calendar) CalendarRepository.eventsOn(context, day.plusDays(1)) else null,
            tasks = notes.openTasks,
            countdowns = notes.countdowns,
            habits = notes.habits,
            parcels = notes.parcels,
            expenses = notes.expenses,
            budgetCents = notes.budgetCents,
            fixedExpenses = notes.fixedExpenses,
            max = max,
            relative = relative,
        )
    }

    private fun Long.at(zone: ZoneId): String = Instant.ofEpochMilli(this).atZone(zone).format(hour)
}
