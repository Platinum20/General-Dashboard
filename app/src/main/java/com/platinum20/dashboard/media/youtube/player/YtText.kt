package com.platinum20.dashboard.media.youtube.player

import java.net.URLDecoder
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Testi del video come li scrive l'app di YouTube: età relativa e descrizione con i link veri. */
object YtText {
    /**
     * "7 ore fa", "3 giorni fa"…: NewPipe a volte dà la data ISO grezza ("2026-10-27T10:43:59-07:00").
     * Un testo già leggibile ("2 giorni fa") resta com'è.
     */
    fun age(uploaded: String, nowMs: Long): String {
        val text = uploaded.trim()
        val thenMs = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: return text
        val minutes = Duration.ofMillis((nowMs - thenMs).coerceAtLeast(0)).toMinutes()
        val hours = minutes / 60
        val days = hours / 24
        fun plural(n: Long, one: String, many: String) = if (n == 1L) "1 $one fa" else "$n $many fa"
        return when {
            minutes < 1 -> "adesso"
            minutes < 60 -> plural(minutes, "minuto", "minuti")
            hours < 24 -> plural(hours, "ora", "ore")
            days < 7 -> plural(days, "giorno", "giorni")
            days < 30 -> plural(days / 7, "settimana", "settimane")
            days < 365 -> plural(days / 30, "mese", "mesi")
            else -> plural(days / 365, "anno", "anni")
        }
    }

    private val anchor = Regex("<a\\s[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * YouTube accorcia i link della descrizione ("Discord: discord"): al posto del testo del link si
     * mette l'indirizzo vero (senza "https://www."). Hashtag, minutaggi e canali restano testo.
     */
    fun descriptionHtml(html: String): String = anchor.replace(html) { m ->
        val target = external(m.groupValues[1].replace("&amp;", "&")) ?: return@replace m.value
        target.replace("&", "&amp;")
    }

    /** L'indirizzo esterno di un link (anche dietro youtube.com/redirect), null se resta su YouTube. */
    private fun external(href: String): String? {
        val url = if ("youtube.com/redirect" in href) {
            Regex("[?&]q=([^&]+)").find(href)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: return null
        } else href
        if (!url.startsWith("http")) return null
        val host = url.substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("m.")
        if (host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com")) return null
        return url.substringAfter("://").removePrefix("www.").trimEnd('/')
    }

    private val link = Regex(
        "(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|gg|io|it|tv|me|ly|co|be|app|dev|shop|store|link|eu|uk|de|fr|es|info|xyz|gl)\\b(?:/[^\\s)\\]]*)?",
        RegexOption.IGNORE_CASE,
    )

    /** Dove stanno i link nella descrizione già in testo semplice, per renderli toccabili. */
    fun links(text: String): List<IntRange> = link.findAll(text)
        .map { m -> m.range.first..(m.range.last - m.value.length + m.value.trimEnd('.', ',', ';', ':', '!', '?').length) }
        .toList()
}
