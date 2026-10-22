package com.platinum20.dashboard.ui.screens

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.media.youtubeWatchUrl
import java.time.LocalDate

/**
 * Tutto si apre dentro Canvas: pagine web nel lettore interno (con il blocco pubblicità),
 * video di YouTube e Spotify nei loro player, eventi nel calendario dell'app.
 * Lo fornisce MainActivity; nelle anteprime e nei test è null e i tocchi non fanno nulla.
 */
class InAppNav(
    val openWeb: (url: String, title: String) -> Unit,
    val openCalendar: () -> Unit,
    val openEvent: (CalendarEvent) -> Unit,
    val newEvent: (LocalDate) -> Unit,
)

val LocalInAppNav = staticCompositionLocalOf<InAppNav?> { null }

/** Dove va aperto un link dentro l'app. */
sealed interface LinkTarget {
    data class YouTube(val watchUrl: String) : LinkTarget
    data class Spotify(val url: String) : LinkTarget
    data class Web(val url: String) : LinkTarget
    /** Niente da aprire (schemi sconosciuti, link vuoti). */
    data object None : LinkTarget
}

object InAppLinks {
    fun target(link: String): LinkTarget {
        val trimmed = link.trim()
        val scheme = Uri.parse(trimmed).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return LinkTarget.None
        youtubeWatchUrl(trimmed)?.let { return LinkTarget.YouTube(it) }
        if (Uri.parse(trimmed).host?.lowercase() == "open.spotify.com") return LinkTarget.Spotify(trimmed)
        return LinkTarget.Web(trimmed)
    }

    /**
     * Un link toccato dentro la pagina web interna resta lì? Sì per le pagine normali;
     * YouTube e Spotify vanno invece nei loro player dell'app, non nel sito mobile.
     */
    fun staysInPage(link: String): Boolean = target(link) is LinkTarget.Web

    /**
     * Gli inviti ad aprire un'altra app (intent://…) restano nell'app se hanno un indirizzo web
     * di riserva: restituisce quell'indirizzo, altrimenti null.
     */
    fun intentFallback(uri: String): String? {
        if (!uri.startsWith("intent:", ignoreCase = true)) return null
        val fallback = Regex("""[;#]S\.browser_fallback_url=([^;]+)""").find(uri)?.groupValues?.get(1) ?: return null
        return Uri.decode(fallback).takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
