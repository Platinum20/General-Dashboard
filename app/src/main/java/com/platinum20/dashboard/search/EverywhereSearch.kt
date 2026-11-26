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
    /** Quanti risultati per gruppo al più: si vedono i primi, gli altri con "Mostra altri". */
    const val MAX = 30

    /** Gli impegni dentro un pezzo di calendario ([WhenSpan]), in ordine di ora; con [rest] solo quelli con quelle parole. */
    fun eventsIn(byDay: Map<LocalDate, List<CalendarEvent>>, span: WhenSpan, rest: String): List<Pair<LocalDate, CalendarEvent>> =
        byDay.filterKeys { span.contains(it) }
            .flatMap { (day, list) -> list.map { day to it } }
            .filter { (_, e) -> rest.isBlank() || SearchMatch.score(rest, e.title, e.location, e.mates.joinToString(" ")) > 0 }
            .distinctBy { it.second.eventId to it.second.begin }
            .sortedBy { it.second.begin }

    /** Gli impegni che contengono la parola, dal più vicino a oggi (prima i prossimi, poi i passati). */
    fun events(byDay: Map<LocalDate, List<CalendarEvent>>, query: String, today: LocalDate): List<Pair<LocalDate, CalendarEvent>> {
        val q = NotesSearch.normalize(query)
        if (q.isEmpty()) return emptyList()
        val all = byDay.flatMap { (day, list) -> list.map { day to it } }
            // Le parole in qualunque ordine, anche con un errore (2026-11-26).
            .filter { (_, e) -> SearchMatch.score(query, e.title, e.location, e.mates.joinToString(" ")) > 0 }
            // Un evento di più giorni compare una volta sola, nel primo giorno.
            .distinctBy { it.second.eventId to it.second.begin }
        val (next, past) = all.partition { !it.first.isBefore(today) }
        return (next.sortedBy { it.second.begin } + past.sortedByDescending { it.second.begin }).take(MAX)
    }

    /** Le notizie con la parola nel titolo o nel sommario, le più recenti prima. */
    fun news(items: List<NewsItem>, query: String): List<NewsItem> {
        val q = NotesSearch.normalize(query)
        if (q.isEmpty()) return emptyList()
        return items.filter { SearchMatch.score(query, it.title, it.summary) > 0 }
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAt }
            .take(MAX)
    }
}
