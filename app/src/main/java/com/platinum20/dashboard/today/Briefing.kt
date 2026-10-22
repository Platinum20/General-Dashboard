package com.platinum20.dashboard.today

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.asTemp
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Di che cosa parla una riga di "Adesso": decide l'icona. */
enum class BriefKind { EVENT, RAIN, TASK, DATE, WEATHER, FREE }

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

    fun build(
        now: LocalDateTime,
        forecast: Forecast?,
        today: List<CalendarEvent>?,
        tomorrow: List<CalendarEvent>?,
        tasks: List<TaskItem>,
        countdowns: List<Countdown>,
        zone: ZoneId = ZoneId.systemDefault(),
        max: Int = 4,
    ): List<BriefLine> {
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val evening = now.hour >= EVENING_HOUR
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
                minutes < 60 -> "Tra ${minutes.coerceAtLeast(1)} min: ${next.title}"
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
        countdowns.filter { it.daysLeft(date) == 0L }.forEach { lines += BriefLine(BriefKind.DATE, "Oggi: ${it.title}") }
        if (evening) countdowns.filter { it.daysLeft(date) == 1L }.forEach { lines += BriefLine(BriefKind.DATE, "Domani: ${it.title}") }

        if (evening) {
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
        } else {
            // Di giorno: avviso UV solo quando serve davvero.
            forecast?.today?.takeIf { it.uvMax >= 6 && now.hour < 16 }?.let {
                lines += BriefLine(BriefKind.WEATHER, "UV alto (${Math.round(it.uvMax)}): meglio la crema")
            }
        }

        if (lines.isEmpty() && today != null && (!evening || tomorrow != null)) {
            lines += BriefLine(BriefKind.FREE, if (evening) "Niente in programma fino a domani" else "Nessun impegno né scadenza oggi")
        }
        // Le urgenze in cima, poi l'ordine in cui le abbiamo pensate.
        return lines.withIndex().sortedWith(compareBy({ !it.value.urgent }, { it.index })).map { it.value }.take(max)
    }

    private fun Long.at(zone: ZoneId): String = Instant.ofEpochMilli(this).atZone(zone).format(hour)
}
