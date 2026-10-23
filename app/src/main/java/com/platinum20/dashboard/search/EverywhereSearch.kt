package com.platinum20.dashboard.search

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.notes.NotesSearch
import java.time.LocalDate

/**
 * La ricerca di Oggi oltre a Note: impegni del calendario e notizie già scaricate.
 * Stesse regole di [NotesSearch] (niente accenti, niente maiuscole). Funzione pura.
 */
object EverywhereSearch {
    /** Quanti risultati per gruppo: è una ricerca veloce, non un archivio. */
    const val MAX = 8

    /** Gli impegni che contengono la parola, dal più vicino a oggi (prima i prossimi, poi i passati). */
    fun events(byDay: Map<LocalDate, List<CalendarEvent>>, query: String, today: LocalDate): List<Pair<LocalDate, CalendarEvent>> {
        val q = NotesSearch.normalize(query)
        if (q.isEmpty()) return emptyList()
        val all = byDay.flatMap { (day, list) -> list.map { day to it } }
            .filter { (_, e) -> NotesSearch.normalize(e.title).contains(q) || NotesSearch.normalize(e.location).contains(q) }
            // Un evento di più giorni compare una volta sola, nel primo giorno.
            .distinctBy { it.second.eventId to it.second.begin }
        val (next, past) = all.partition { !it.first.isBefore(today) }
        return (next.sortedBy { it.second.begin } + past.sortedByDescending { it.second.begin }).take(MAX)
    }

    /** Le notizie con la parola nel titolo o nel sommario, le più recenti prima. */
    fun news(items: List<NewsItem>, query: String): List<NewsItem> {
        val q = NotesSearch.normalize(query)
        if (q.isEmpty()) return emptyList()
        return items.filter { NotesSearch.normalize(it.title).contains(q) || NotesSearch.normalize(it.summary).contains(q) }
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAt }
            .take(MAX)
    }
}
