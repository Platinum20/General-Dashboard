package com.platinum20.dashboard.trips

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.shifts.PayMonth
import com.platinum20.dashboard.shifts.ShiftEarning
import com.platinum20.dashboard.shifts.ShiftParse
import com.platinum20.dashboard.shifts.ShiftPay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Prima del turno, mentre lavori, finito. */
enum class ShiftPhase { SOON, WORKING, DONE }

/**
 * La notifica viva del turno (Antonio, 2026-11-21, proposta A di /mnt/project-files/notifica-turno/ senza la riga
 * dello scaglione dopo): nasce tre quarti d'ora prima, mentre lavori la linea va dall'inizio alla fine col venduto
 * finora, alla fine diventa il riepilogo che si toglie col dito. Stesso disegno del treno. Logica pura, per i test.
 */
object ShiftLive {
    /** Il tempo di prepararsi e uscire. */
    const val LEAD_MS = 45 * 60_000L
    /** Mentre lavori i numeri si rileggono più spesso della lettura oraria: il venduto cresce turno per turno. */
    const val READ_EVERY_MS = 15 * 60_000L
    /** Oltre questa età il venduto in notifica dice "letto alle …". */
    const val STALE_MS = 20 * 60_000L
    /** Quanto il riepilogo può ancora aggiornarsi col guadagno che Creators Pro segna dopo la fine. */
    const val DONE_MS = 12 * 60 * 60_000L

    private val hour = DateTimeFormatter.ofPattern("H:mm")

    fun phase(event: CalendarEvent, now: Long): ShiftPhase? {
        if (event.allDay || event.end <= event.begin) return null
        return when {
            now < event.begin - LEAD_MS -> null
            now < event.begin -> ShiftPhase.SOON
            now < event.end -> ShiftPhase.WORKING
            now < event.end + DONE_MS -> ShiftPhase.DONE
            else -> null
        }
    }

    /** "5 h", "4 h 30 min", "45 min". */
    fun length(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    /**
     * La notifica adesso, o null se non è il momento. [earning] = la busta paga del turno (mentre lavori anche a
     * zero); [month] = il mese, per il riepilogo; [readAt] = l'ultima lettura di Creators Pro, detta quando i numeri
     * mancano ancora (lo stato dice la verità, 2026-11-21: Antonio non vedeva i soldi e non sapeva perché).
     */
    fun card(
        event: CalendarEvent,
        now: Long,
        earning: ShiftEarning?,
        month: PayMonth?,
        zone: ZoneId = ZoneId.systemDefault(),
        readAt: Long = 0L,
    ): LiveCard? {
        val phase = phase(event, now) ?: return null
        val start = at(event.begin, zone)
        val end = at(event.end, zone)
        val company = event.mates.takeIf { it.isNotEmpty() }?.let { ShiftParse.company(it.map(ShiftParse::firstName)) }
        val span = listOfNotNull("$start → $end", company?.replaceFirstChar { it.lowercase() }).joinToString(" · ")
        val money = earning?.takeIf { ShiftPay.worked(it) || phase == ShiftPhase.WORKING }
        // Mentre lavori il guadagno è sempre "finora"; a turno finito lo resta solo finché Creators Pro non chiude il conto.
        val earned = money?.let { ShiftPay.money(it.earned) + if (phase == ShiftPhase.WORKING || it.current) " finora" else "" }
        val sold = money?.let { "Venduto ${ShiftPay.money(it.sold)} · ${ShiftPay.percent(it.percent)} → $earned" }
        val lines = buildList {
            if (phase != ShiftPhase.SOON && sold != null) add(sold)
            if (phase != ShiftPhase.SOON && sold == null) add(missing(readAt, zone))
            // Numeri vecchi: si dice da quando (la rilettura dei 15 minuti non è passata).
            if (phase == ShiftPhase.WORKING && sold != null && readAt > 0 && now - readAt > STALE_MS) add("letto alle ${at(readAt, zone)}")
            if (phase == ShiftPhase.DONE && month != null) add("${ShiftPay.monthName(month, zone)}: ${ShiftPay.money(month.total)} in ${if (month.shifts == 1) "1 turno" else "${month.shifts} turni"}")
        }
        val text = when {
            phase == ShiftPhase.WORKING && money != null -> "Venduto ${ShiftPay.money(money.sold)} · $earned"
            phase == ShiftPhase.DONE && money != null -> "Venduto ${ShiftPay.money(money.sold)} · guadagnati $earned"
            else -> span
        }
        val marks = buildList {
            if (earned != null && lines.isNotEmpty() && phase != ShiftPhase.SOON) add(LiveMark(0, earned, MarkKind.NOW))
            if (sold == null && lines.isNotEmpty()) add(LiveMark(0, lines[0], MarkKind.SOFT))
            if (lines.size > 1) add(LiveMark(1, lines[1], MarkKind.SOFT))
        }
        return LiveCard(
            phase = if (phase == ShiftPhase.SOON) LivePhase.SOON else LivePhase.ON_BOARD,
            title = com.platinum20.dashboard.ui.screens.RowText.event(event),
            text = text,
            chip = start,
            countdownTo = if (phase == ShiftPhase.SOON) event.begin else event.end,
            lines = lines,
            header = when (phase) {
                ShiftPhase.SOON -> "inizia ${LiveTrip.inText(event.begin - now)}"
                ShiftPhase.WORKING -> "finisce ${LiveTrip.inText(event.end - now)}"
                ShiftPhase.DONE -> "finito alle $end"
            },
            marks = marks,
            track = LiveTrack(event.begin, event.end),
            depart = start,
            arrive = end,
            sub = listOfNotNull(company, length(event.end - event.begin)).joinToString(" · "),
            done = phase == ShiftPhase.DONE,
        )
    }

    /** "Venduto: non ancora segnato · letto alle 18:05": Creators Pro non ha ancora i numeri di questo turno. */
    /** Dopo una lettura andata a vuoto si riprova prima dei 15 minuti. */
    const val RETRY_MS = 5 * 60_000L

    /**
     * Se rileggere adesso: conta l'ultima busta paga letta davvero ([payReadAt]), non la timeline, che si legge
     * anche quando la lettura si ferma prima dei soldi; [tried] = l'ultimo tentativo, per non insistere ogni minuto.
     */
    fun shouldRead(now: Long, payReadAt: Long, tried: Long): Boolean =
        now - payReadAt >= READ_EVERY_MS && now - tried >= RETRY_MS

    fun missing(readAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Venduto: non ancora segnato" + if (readAt > 0) " · letto alle ${at(readAt, zone)}" else ""

    private fun at(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).format(hour)
}
