package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Le tre righe del blocco "quando" delle schede: quanto manca, l'ora (o il giorno) grande, il giorno e la durata.
 * [late] = un treno in ritardo: il blocco si colora d'arancio (attenzione), mai rosso.
 */
data class WhenText3(val countdown: String, val big: String, val sub: String, val late: Boolean = false)

/**
 * Il "quando" delle schede nel blocco colorato (1.528, Antonio scelta B): è la domanda a cui risponde la pagina.
 * Pura, per i test: "Tra 2 ore" / "18:30 – 19:30" / "Oggi · 1 ora".
 */
object WhenHero {
    private val hour = DateTimeFormatter.ofPattern("H:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

    fun of(from: Long, to: Long, allDay: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): WhenText3 {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = Instant.ofEpochMilli(from).atZone(zone)
        val end = Instant.ofEpochMilli(to).atZone(zone)
        val day = start.toLocalDate()
        return if (allDay) {
            WhenText3(countdown(from, to, true, now, zone), dayName(day, today, long = true), "Tutto il giorno")
        } else {
            val big = start.format(hour) + (if (to > from) " – " + end.format(hour) else "")
            val sub = dayName(day, today, long = false) + (if (to > from) " · " + duration(to - from) else "")
            WhenText3(countdown(from, to, false, now, zone), big, sub)
        }
    }

    /** Quanto manca: "Tra 45 minuti", "Tra 2 ore", "Domani", "Tra 3 giorni"; "In corso" e "Finito" quando serve. */
    fun countdown(from: Long, to: Long, allDay: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(maxOf(to - 1, from)).atZone(zone).toLocalDate()
        if (allDay) {
            return when {
                !today.isBefore(day) && !today.isAfter(endDay) -> "Oggi"
                today.isAfter(endDay) -> "Finito"
                else -> days(ChronoUnit.DAYS.between(today, day))
            }
        }
        return when {
            now >= to && to > from -> "Finito"
            now >= from && now < to -> "In corso · finisce tra " + span(to - now)
            now >= from -> "Adesso"
            day != today -> days(ChronoUnit.DAYS.between(today, day))
            else -> "Tra " + span(from - now)
        }
    }

    /**
     * Il quando di un treno (2026-11-20): prima di partire "Parte tra 33 min", in viaggio "Arriva tra 2 h 10",
     * sugli orari spostati da un ritardo fresco ([live], da [com.platinum20.dashboard.trips.TrainLive.fresh]).
     * Con il ritardo l'ora grande è quella vera e sotto resta il programmato. Soppresso: lo dice, orari del biglietto.
     */
    fun train(from: Long, to: Long, live: com.platinum20.dashboard.trips.TrainStatus?, now: Long, zone: ZoneId = ZoneId.systemDefault()): WhenText3 {
        if (to <= from) return of(from, to, false, now, zone)
        val shift = com.platinum20.dashboard.trips.TrainLive.shiftMs(live)
        val leave = from + shift
        val arrive = to + shift
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(leave).atZone(zone).toLocalDate()
        val countdown = when {
            live?.cancelled == true -> "Soppresso"
            now >= arrive -> "Finito"
            now >= leave -> "Arriva tra " + short(arrive - now)
            day != today -> days(ChronoUnit.DAYS.between(today, day))
            else -> "Parte tra " + short(leave - now)
        }
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(hour)
        val sub = listOfNotNull(
            dayName(day, today, long = false),
            duration(to - from),
            if (shift > 0) "programmato ${at(from)} – ${at(to)}" else null,
        ).joinToString(" · ")
        return WhenText3(countdown, at(leave) + " – " + at(arrive), sub, late = shift > 0 || live?.cancelled == true)
    }

    /** "33 min", "1 h 20", "2 h": corto, sta nella riga sopra l'ora grande. */
    fun short(ms: Long): String {
        val min = ((ms + 59_999) / 60_000).coerceAtLeast(1)
        return when {
            min < 60 -> "$min min"
            min % 60 == 0L -> "${min / 60} h"
            else -> "${min / 60} h ${min % 60}"
        }
    }

    private fun days(n: Long): String = when (n) {
        0L -> "Oggi"
        1L -> "Domani"
        else -> "Tra $n giorni"
    }

    /** "45 minuti", "1 ora", "2 ore" (arrotondato all'ora da due ore in su), "1 ora e 30". */
    fun span(ms: Long): String {
        val min = ((ms + 59_999) / 60_000).coerceAtLeast(1)
        return when {
            min < 60 -> if (min == 1L) "1 minuto" else "$min minuti"
            min < 120 -> if (min % 60 == 0L) "1 ora" else "1 ora e ${min % 60}"
            else -> "${(min + 30) / 60} ore"
        }
    }

    /** La durata di un evento: "45 min", "1 ora", "5 h 30". */
    fun duration(ms: Long): String {
        val d = Duration.ofMillis(ms)
        val h = d.toHours()
        val m = d.toMinutesPart()
        return when {
            h == 0L -> "$m min"
            m == 0 -> if (h == 1L) "1 ora" else "$h ore"
            else -> "$h h $m"
        }
    }

    private fun dayName(day: LocalDate, today: LocalDate, long: Boolean): String = when (ChronoUnit.DAYS.between(today, day)) {
        0L -> "Oggi"
        1L -> "Domani"
        -1L -> "Ieri"
        else -> day.format(dayFmt).replaceFirstChar { it.uppercase() }
    }.let { if (long && (it == "Oggi" || it == "Domani" || it == "Ieri")) it + ", " + day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)) else it }
}

/**
 * Il blocco colorato in cima alla scheda (1.528): quanto manca piccolo sopra, l'ora grande, il giorno sotto.
 * Il solo blocco colorato della pagina; toccandolo si cambia il quando, se si può.
 */
@Composable
fun WhenHeroCard(text: WhenText3, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    CanvasCard(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.bouncyPress(0.98f) else Modifier),
        onClick = onClick,
        onLongClick = onLongClick,
        shape = MaterialTheme.shapes.extraLarge,
        // Un treno in ritardo: blocco arancio, "Parte tra" in arancio pieno (una sola cosa da guardare).
        colors = CardDefaults.cardColors(
            containerColor = if (text.late) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (text.late) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        val soft = LocalContentColor.current.copy(alpha = 0.72f)
        val urgent = Urgent.color(darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (text.countdown.isNotBlank()) {
                Text(
                    text.countdown.uppercase(Locale.ITALIAN),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (text.late) urgent else soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text.big,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (text.sub.isNotBlank()) {
                Text(text.sub, style = MaterialTheme.typography.bodyMedium, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
