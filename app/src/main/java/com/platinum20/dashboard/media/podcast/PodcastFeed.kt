package com.platinum20.dashboard.media.podcast

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Un podcast: quello che serve per mostrarlo e per scaricarne gli episodi. */
data class Podcast(
    val id: String,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artwork: String,
    /** Salvato nella tua libreria di Spotify (si segue e si toglie da lì). */
    val fromSpotify: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("author", author).put("feed", feedUrl).put("art", artwork)

    companion object {
        fun fromJson(o: JSONObject) = Podcast(
            id = o.optString("id"),
            title = o.optString("title"),
            author = o.optString("author"),
            feedUrl = o.optString("feed"),
            artwork = o.optString("art"),
        )
    }
}

/** Un episodio, letto dal feed RSS del podcast. */
data class Episode(
    val id: String,
    val title: String,
    val audioUrl: String,
    /** Millisecondi dall'epoca, 0 se il feed non dà la data. */
    val publishedAt: Long,
    /** Durata in secondi, 0 se non indicata. */
    val durationSec: Long,
    val description: String,
    val image: String,
    val podcastTitle: String,
    val feedUrl: String,
)

/** Lettura dei feed RSS dei podcast e delle risposte della ricerca di iTunes. */
object PodcastFeed {
    private const val MAX_DESCRIPTION = 400

    /** Gli episodi del feed, dal più nuovo; quelli senza audio (enclosure) vengono scartati. */
    fun parse(xml: String, feedUrl: String): List<Episode> {
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                // Niente DTD né entità esterne: il feed arriva dalla rete.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.trim().toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return emptyList()
        val channel = doc.getElementsByTagName("channel").item(0) as? Element ?: return emptyList()
        val podcastTitle = channel.childText("title")
        val channelImage = channel.childAttr("itunes:image", "href")
            .ifBlank { (channel.child("image"))?.childText("url").orEmpty() }
        val items = channel.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { i ->
            val item = items.item(i) as? Element ?: return@mapNotNull null
            val audio = secure(item.childAttr("enclosure", "url"))
            if (audio.isBlank()) return@mapNotNull null
            Episode(
                id = item.childText("guid").ifBlank { audio },
                title = cleanText(item.childText("title")).ifBlank { "Episodio senza titolo" },
                audioUrl = audio,
                publishedAt = parseDate(item.childText("pubDate")),
                durationSec = parseDuration(item.childText("itunes:duration")),
                description = cleanText(item.childText("itunes:summary").ifBlank { item.childText("description") }).let {
                    if (it.length > MAX_DESCRIPTION) it.take(MAX_DESCRIPTION).trimEnd() + "…" else it
                },
                image = secure(item.childAttr("itunes:image", "href").ifBlank { channelImage }),
                podcastTitle = cleanText(podcastTitle),
                feedUrl = feedUrl,
            )
        }.distinctBy { it.id }.sortedByDescending { it.publishedAt }
    }

    /** I risultati della ricerca di iTunes; quelli senza feed RSS non si possono ascoltare e vengono scartati. */
    fun parseSearch(json: String): List<Podcast> {
        val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: JSONArray()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val feed = secure(o.optString("feedUrl"))
            if (feed.isBlank()) return@mapNotNull null
            Podcast(
                id = o.optString("collectionId").ifBlank { feed },
                title = o.optString("collectionName").trim(),
                author = o.optString("artistName").trim(),
                feedUrl = feed,
                artwork = secure(o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") }),
            )
        }.distinctBy { it.feedUrl }
    }

    /** "1:02:03", "62:03" o "3723" in secondi; 0 se manca o non si capisce. */
    fun parseDuration(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0
        val parts = text.split(':')
        if (parts.size > 3) return 0
        val numbers = parts.map { it.trim().toDoubleOrNull() ?: return 0 }
        return numbers.fold(0.0) { total, n -> total * 60 + n }.toLong().coerceAtLeast(0)
    }

    /** Data RSS ("Tue, 22 Oct 2026 06:00:00 +0200", anche con fusi come "GMT" o "PDT"), 0 se non si capisce. */
    fun parseDate(raw: String): Long {
        // Il giorno della settimana si toglie: molti feed lo sbagliano e il parser rifiuterebbe la data.
        val text = raw.trim().replace(Regex("\\s+"), " ").replace(Regex("^[A-Za-z]{3,}\\.?,?\\s*"), "")
        if (text.isEmpty()) return 0
        for (pattern in listOf("d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss zzz", "d MMM yyyy HH:mm Z", "d MMM yyyy HH:mm zzz")) {
            runCatching {
                return ZonedDateTime.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).toInstant().toEpochMilli()
            }
        }
        return 0
    }

    /** Il telefono non scarica in chiaro: gli indirizzi http passano a https (i servizi di podcast lo reggono). */
    /** Titolo confrontabile: minuscolo, senza accenti, punteggiatura e "podcast" finale. */
    fun normalizeTitle(title: String): String =
        java.text.Normalizer.normalize(title.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .removeSuffix(" podcast")
            .trim()

    /** Tra i risultati di iTunes, il feed RSS dello stesso podcast di Spotify (stesso titolo), se c'è. */
    fun matchFeed(showTitle: String, results: List<Podcast>): Podcast? {
        val key = normalizeTitle(showTitle)
        if (key.isBlank()) return null
        return results.firstOrNull { normalizeTitle(it.title) == key }
    }

    /** "2026-10-21", "2026-10" o "2026" di Spotify in millisecondi (mezzogiorno UTC), 0 se non si capisce. */
    fun spotifyDate(date: String): Long {
        val parts = date.split('-').mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0
        return runCatching {
            java.time.LocalDate.of(parts[0], parts.getOrElse(1) { 1 }, parts.getOrElse(2) { 1 })
                .atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrDefault(0)
    }

    /** Un episodio di Spotify come gli altri: l'"indirizzo" è spotify:episode:…, che suona nel riproduttore di Spotify. */
    fun fromSpotify(e: com.platinum20.dashboard.media.spotify.SpEpisode, podcast: Podcast): Episode = Episode(
        id = e.uri,
        title = cleanText(e.title).ifBlank { "Episodio senza titolo" },
        audioUrl = e.uri,
        publishedAt = spotifyDate(e.releaseDate),
        durationSec = e.durationMs / 1000,
        description = cleanText(e.description).take(MAX_DESCRIPTION),
        image = e.image.ifBlank { podcast.artwork },
        podcastTitle = podcast.title,
        feedUrl = podcast.feedUrl,
    )

    fun secure(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true)) "https://" + trimmed.substring(7) else trimmed
    }

    /** Testo senza HTML, con le entità più comuni sciolte e gli spazi in ordine. */
    fun cleanText(html: String): String = html
        .replace(Regex("(?i)<br\\s*/?>|</p>"), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun Element.child(tag: String): Element? {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.equals(tag, ignoreCase = true)) return node as Element
        }
        return null
    }

    private fun Element.childText(tag: String): String = child(tag)?.textContent?.trim().orEmpty()

    private fun Element.childAttr(tag: String, attr: String): String = child(tag)?.getAttribute(attr)?.trim().orEmpty()
}

/** Da dove riprendere un episodio e quanto ne resta. */
object PodcastProgress {
    /** Sotto i 5 secondi o negli ultimi 30 l'episodio riparte da capo. */
    fun resumeAt(savedMs: Long, durationMs: Long): Long = when {
        savedMs < 5_000 -> 0
        durationMs > 0 && savedMs > durationMs - 30_000 -> 0
        else -> savedMs
    }

    /** "45 min", "1 h 2 min", "restano 12 min" se l'hai già iniziato. */
    fun label(durationSec: Long, savedMs: Long = 0): String {
        if (durationSec <= 0) return ""
        val leftSec = if (savedMs > 0) (durationSec - savedMs / 1000).coerceAtLeast(0) else durationSec
        val minutes = ((leftSec + 30) / 60).coerceAtLeast(1)
        val text = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min".replace(" 0 min", "") else "$minutes min"
        return if (savedMs > 0) "restano $text" else text
    }
}

/** Quando è uscito un episodio, detto come lo direbbe una persona. */
object EpisodeDate {
    private val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")

    fun label(publishedAt: Long, now: java.time.ZonedDateTime): String {
        if (publishedAt <= 0) return ""
        val day = java.time.Instant.ofEpochMilli(publishedAt).atZone(now.zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(day, now.toLocalDate())
        return when {
            days < 0 -> "${day.dayOfMonth} ${months[day.monthValue - 1]}"
            days == 0L -> "oggi"
            days == 1L -> "ieri"
            days < 7 -> "$days giorni fa"
            day.year == now.year -> "${day.dayOfMonth} ${months[day.monthValue - 1]}"
            else -> "${day.dayOfMonth} ${months[day.monthValue - 1]} ${day.year}"
        }
    }
}
