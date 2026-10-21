package com.platinum20.dashboard.news

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class NewsSource(val id: String, val name: String, val feedUrl: String)

data class NewsItem(
    val title: String,
    val link: String,
    val summary: String,
    val imageUrl: String?,
    val publishedAt: Instant?,
    val source: NewsSource,
)

data class NewsResult(val items: List<NewsItem>, val failedSources: List<NewsSource>)

object NewsSources {
    val all = listOf(
        NewsSource("ansa", "ANSA", "https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml"),
        NewsSource("ilpost", "Il Post", "https://www.ilpost.it/feed/"),
        NewsSource("repubblica", "la Repubblica", "https://www.repubblica.it/rss/homepage/rss2.0.xml"),
        NewsSource("sole24ore", "Il Sole 24 Ore", "https://www.ilsole24ore.com/rss/italia.xml"),
        NewsSource("fanpage", "Fanpage", "https://www.fanpage.it/feed/"),
    )
}

class NewsRepository {

    /** Scarica tutti i feed in parallelo e li unisce, dal più recente. */
    suspend fun fetchAll(sources: List<NewsSource> = NewsSources.all): NewsResult = coroutineScope {
        val results = sources.map { source ->
            async(Dispatchers.IO) { source to runCatching { fetch(source) } }
        }.awaitAll()

        val failed = results.filter { it.second.isFailure }.map { it.first }
        if (failed.size == sources.size) {
            throw IOException("Impossibile scaricare le notizie. Controlla la connessione.")
        }
        val items = results
            .flatMap { it.second.getOrDefault(emptyList()) }
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAt ?: Instant.EPOCH }
        NewsResult(items, failed)
    }

    private suspend fun fetch(source: NewsSource): List<NewsItem> = withContext(Dispatchers.IO) {
        val conn = (URL(source.feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { parse(it, source) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(input: InputStream, source: NewsSource): List<NewsItem> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        val items = mutableListOf<NewsItem>()

        var inItem = false
        var title = ""
        var link = ""
        var description = ""
        var content = ""
        var date = ""
        var image: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    if (name == "item" || name == "entry") {
                        inItem = true
                        title = ""; link = ""; description = ""; content = ""; date = ""; image = null
                    } else if (inItem) {
                        when (name) {
                            "title" -> title = parser.safeText()
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    if (link.isEmpty()) link = href
                                } else {
                                    link = parser.safeText()
                                }
                            }
                            "description", "summary" -> description = parser.safeText()
                            "content:encoded", "content" -> content = parser.safeText()
                            "pubdate", "dc:date", "published", "updated" -> if (date.isEmpty()) date = parser.safeText()
                            "media:content", "media:thumbnail" -> {
                                val url = parser.getAttributeValue(null, "url")
                                val medium = parser.getAttributeValue(null, "medium")
                                val type = parser.getAttributeValue(null, "type")
                                val isImage = medium == null || medium == "image" || type?.startsWith("image") == true
                                if (image == null && url != null && isImage) image = url
                            }
                            "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                val url = parser.getAttributeValue(null, "url")
                                if (image == null && url != null && type.startsWith("image")) image = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    if (inItem && (name == "item" || name == "entry")) {
                        inItem = false
                        val cleanTitle = htmlToText(title)
                        if (cleanTitle.isNotBlank() && link.isNotBlank()) {
                            items += NewsItem(
                                title = cleanTitle,
                                link = link.trim(),
                                summary = htmlToText(description.ifBlank { content }).take(280),
                                imageUrl = (image ?: firstImage(content) ?: firstImage(description))
                                    ?.replace("&amp;", "&")
                                    ?.let { if (it.startsWith("//")) "https:$it" else it },
                                publishedAt = parseDate(date),
                                source = source,
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    /** Legge il testo del tag corrente, CDATA compreso, senza perdersi nei tag annidati. */
    private fun XmlPullParser.safeText(): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (nextToken()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> text?.let { sb.append(it) }
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private fun htmlToText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace('￼', ' ') // segnaposto delle immagini
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun firstImage(html: String): String? =
        Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)

    private fun parseDate(raw: String): Instant? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .recoverCatching { OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { Instant.parse(s) }
            .getOrNull()
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    }
}
