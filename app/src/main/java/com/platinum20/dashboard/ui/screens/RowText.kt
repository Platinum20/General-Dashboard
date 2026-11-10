package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.Upcoming
import java.time.LocalDate
import java.time.ZoneId

/**
 * Le parole di una riga del calendario, le stesse nell'app e nei widget (punto 15): una funzione sola,
 * così "Treno per Pescara" o "Turno su Debora" non cambiano da un posto all'altro. Pura, per i test.
 */
internal object RowText {
    /** Il nome di un evento in lista: treni e turni col loro nome, gli altri col titolo. */
    fun event(e: CalendarEvent): String {
        e.trip?.let { return "Treno per ${it.to}" }
        val creators = if (e.eventId < -1) com.platinum20.dashboard.shifts.ShiftParse.creators(e.title) else emptyList()
        if (creators.isNotEmpty()) return "Turno su " + andList(creators)
        return e.title.ifBlank { "Evento" }
    }

    /** Il nome di una data di tutto il giorno; età e quanto manca stanno nella riga sotto ([WhenText.dateLine]). */
    fun date(t: TaskItem): String = t.text

    /**
     * La data su una riga sola, dove non c'è la riga sotto (i widget): "Compleanno di Marta · 34 anni".
     */
    fun dateOneLine(t: TaskItem, day: LocalDate, zone: ZoneId): String =
        date(t) + (t.yearsAtNext(day, zone)?.let { " · $it anni" } ?: "")

    /** Il giorno sopra le sue righe: "Domani", "Gio 12", come nel calendario. */
    fun day(day: LocalDate, today: LocalDate): String = Upcoming.dayLabel(day, today)
}
