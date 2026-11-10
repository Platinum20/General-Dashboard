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
        if (e.eventId < -1) shift(e.title)?.let { return it }
        return e.title.ifBlank { "Evento" }
    }

    /**
     * Il nome di un turno: "Turno su Debora e Chiara", solo i nomi. Lo stesso in lista e come titolo della
     * scheda, dove la riga delle creator non c'è più (Antonio). Null se il turno non dice le creator.
     */
    fun shift(title: String): String? {
        val creators = com.platinum20.dashboard.shifts.ShiftParse.creators(title).map(com.platinum20.dashboard.shifts.ShiftParse::firstName)
        return if (creators.isEmpty()) null else "Turno su " + andList(creators)
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
