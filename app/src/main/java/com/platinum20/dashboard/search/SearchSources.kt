package com.platinum20.dashboard.search

import com.platinum20.dashboard.docs.Document
import com.platinum20.dashboard.mail.MailMessage

/**
 * Un'impostazione che si trova in Cerca (2026-11-26, punto 4): "tema", "pioggia", "WindTre". [page] = il nome di
 * una SettingsPage, null = la pagina principale delle Impostazioni (dove sta il tema).
 */
data class SettingEntry(val title: String, val line: String, val page: String?, val words: String = "")

/** Le fonti di Cerca che vivono sul telefono, oltre al calendario: funzioni pure, provate nei test. */
object SearchSources {
    /** Le impostazioni, con le parole con cui le si cerca. Una riga = una cosa che si regola. */
    val SETTINGS = listOf(
        SettingEntry("Tema", "Canvas, Material You o Puro", null, "colori scuro chiaro aspetto"),
        SettingEntry("Card della Home", "Quali mostrare e in che ordine", "HOME", "ordine nascondi riordina"),
        SettingEntry("Località del meteo", "Il posto del meteo", "HOME", "citta posto meteo"),
        SettingEntry("Voce di lettura", "Chi legge news e riassunti", "NEWS", "piper paola riccardo leggi ascolta"),
        SettingEntry("Fonti delle news", "Quali giornali leggere", "NEWS", "giornali notizie fonti"),
        SettingEntry("Riempi la coda in automatico", "Tuoi brani Spotify a fine coda", "MEDIA", "coda musica"),
        SettingEntry("Musica simile", "Discover Weekly, Daily Mix, radio", "MEDIA", "lastfm last.fm mix"),
        SettingEntry("Riconosci canzoni", "Il token AudD per sapere cosa suona", "MEDIA", "audd canzone shazam"),
        SettingEntry("Riproduci il video successivo", "Dopo un video, parte un proposto", "MEDIA", "autoplay youtube"),
        SettingEntry("Escludi Shorts", "Niente Shorts in Video", "MEDIA", "youtube"),
        SettingEntry("Escludi live", "Niente dirette in Video", "MEDIA", "youtube dirette"),
        SettingEntry("Escludi video per membri", "Niente video solo per abbonati", "MEDIA", "youtube"),
        SettingEntry("Mostra i Non mi piace", "Stima di Return YouTube Dislike", "MEDIA", "dislike youtube"),
        SettingEntry("SponsorBlock", "Salta sponsor e intro nei video", "SPONSORBLOCK", "sponsor salta youtube"),
        SettingEntry("Scarica i salvati", "Musica e video salvati anche senza rete", "MEDIA", "download offline"),
        SettingEntry("Notifica del mattino", "Il riepilogo della giornata", "ALERTS", "mattina riepilogo"),
        SettingEntry("Sveglia con la radio", "Una radio al posto della sveglia", "ALERTS", "sveglia radio"),
        SettingEntry("Avvisami se sta per piovere", "Un avviso prima della pioggia", "ALERTS", "pioggia meteo"),
        SettingEntry("Viaggio dal vivo", "Il treno nella notifica", "ALERTS", "treno notifica"),
        SettingEntry("Turno dal vivo", "Il turno nella notifica", "ALERTS", "turno lavoro notifica"),
        SettingEntry("Aggiornamenti automatici", "Avviso quando c'è una versione nuova", "ALERTS", "versione aggiorna"),
        SettingEntry("Account", "WindTre, Creators Pro, Gmail, Spotify, YouTube", "ACCOUNTS", "windtre sim creators turni gmail posta spotify youtube accesso login"),
        SettingEntry("Scarica solo col Wi-Fi", "Niente download coi dati", "DATA", "wifi rete dati"),
        SettingEntry("Blocco pubblicità", "Niente pubblicità negli articoli", "DATA", "adblock pubblicita"),
        SettingEntry("Backup", "Salva e ripristina i tuoi dati", "DATA", "salva ripristina copia"),
        SettingEntry("Informazioni", "Versione, aggiornamenti e licenze", "INFO", "versione aggiornamento licenze"),
    )

    fun settings(query: String, list: List<SettingEntry> = SETTINGS): List<Pair<SettingEntry, Int>> =
        list.map { it to SearchMatch.score(query, it.title, it.words, it.line) }.filter { it.second > 0 }.sortedByDescending { it.second }

    /** Le mail lette dall'app con la parola nell'oggetto, nel mittente o nel testo: le più nuove prima. */
    fun mail(list: List<MailMessage>, query: String): List<Pair<MailMessage, Int>> =
        list.map { it to SearchMatch.score(query, it.subject, it.from, it.snippet, it.body) }
            .filter { it.second > 0 }
            .sortedByDescending { it.first.at }

    /** I documenti fotografati per nome o numero. */
    fun docs(list: List<Document>, query: String): List<Pair<Document, Int>> =
        list.map { it to SearchMatch.score(query, it.title, it.number.orEmpty(), it.kind.label) }.filter { it.second > 0 }

    /** Le #etichette o i nomi (persone dei turni) che corrispondono, con "#" o senza. */
    fun names(list: List<String>, query: String): List<Pair<String, Int>> {
        val q = query.trim().removePrefix("#")
        if (q.isEmpty()) return emptyList()
        return list.distinct().map { it to SearchMatch.score(q, it) }.filter { it.second > 0 }.sortedByDescending { it.second }
    }

    /** "biglietto", "biglietti": la ricerca chiede gli eventi che hanno un biglietto salvato. */
    fun asksTickets(query: String): Boolean = SearchMatch.words(query).any { it.startsWith("bigliett") }

    /** Gli eventi con un biglietto, dai nomi dei file salvati ("<id evento>-<n>.png"). */
    fun ticketEvents(fileNames: List<String>): Set<Long> =
        fileNames.mapNotNull { it.substringBefore('-', "").toLongOrNull() }.toSet()
}
