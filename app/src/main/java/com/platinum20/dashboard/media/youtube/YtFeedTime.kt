package com.platinum20.dashboard.media.youtube

import java.time.Duration
import java.time.Instant

/**
 * La pagina Iscrizioni di YouTube dice solo "3 ore fa": qui diventa un momento, così i video nuovi letti
 * da lì si mettono in ordine con quelli dei feed RSS (1.532, Antonio: "gli ultimi video sono di martedì").
 */
object YtFeedTime {
    private val units = listOf(
        Regex("^(sec|second)") to Duration.ofSeconds(1),
        Regex("^(min)") to Duration.ofMinutes(1),
        Regex("^(or[ae]|hour)") to Duration.ofHours(1),
        Regex("^(giorn|day)") to Duration.ofDays(1),
        Regex("^(settiman|week)") to Duration.ofDays(7),
        Regex("^(mes[ei]|month)") to Duration.ofDays(30),
        Regex("^(ann[oi]|year)") to Duration.ofDays(365),
    )
    private val amount = Regex("""(\d+)\s+([a-zà-ù]+)\s+(fa|ago)\b""")

    /** "2 ore fa", "Trasmesso in streaming 1 giorno fa", "3 weeks ago" → circa quando; null se non si capisce. */
    fun ago(text: String, now: Instant): Instant? {
        val m = amount.find(text.lowercase()) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        val unit = units.firstOrNull { it.first.containsMatchIn(m.groupValues[2]) }?.second ?: return null
        return now.minus(unit.multipliedBy(n))
    }

    /**
     * Aggiunge ai video dei feed RSS quelli della pagina Iscrizioni che mancano (i feed RSS di YouTube a volte non
     * rispondono o arrivano in ritardo, la pagina no). Chi è già nei feed tiene la data esatta; senza una data
     * leggibile si usa l'inizio della diretta in programma, altrimenti il video si lascia fuori.
     */
    fun withFeedVideos(rss: List<YtVideo>, feed: List<YtVideo>, now: Instant): List<YtVideo> {
        val known = rss.map { it.id }.toSet()
        val extra = feed.filter { it.id !in known && it.channelId.isNotBlank() }.mapNotNull { v ->
            val at = ago(v.uploadedText, now) ?: v.startsAt ?: return@mapNotNull null
            v.copy(published = at)
        }
        return (rss + extra).distinctBy { it.id }.sortedByDescending { it.published }
    }
}
