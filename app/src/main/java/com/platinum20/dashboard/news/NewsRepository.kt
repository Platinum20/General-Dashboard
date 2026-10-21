package com.platinum20.dashboard.news

import android.text.Html
import android.util.Xml
import com.platinum20.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

data class NewsSource(val id: String, val name: String, val feedUrl: String)

data class NewsItem(
    val title: String,
    val link: String,
    val summary: String,
    val imageUrl: String?,
    val publishedAt: Instant?,
    val source: NewsSource,
)

data class SourceFailure(val source: NewsSource, val reason: String)

data class NewsResult(val items: List<NewsItem>, val failures: List<SourceFailure>)

object NewsSources {
    val all = listOf(
        // Solo fonti gratuite: niente testate con abbonamento (la Repubblica, Il Sole 24 Ore, ...).
        NewsSource("ansa", "ANSA", "https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml"),
        NewsSource("ilpost", "Il Post", "https://www.ilpost.it/feed/"),
        NewsSource("open", "Open", "https://www.open.online/feed/"),
        NewsSource("rainews", "RaiNews", "https://www.rainews.it/rss/tutti"),
        NewsSource("fanpage", "Fanpage", "https://www.fanpage.it/feed/"),
    )
}

class NewsRepository {

    /** Scarica tutti i feed in parallelo e li unisce, dal più recente. */
    suspend fun fetchAll(sources: List<NewsSource> = NewsSources.all): NewsResult = coroutineScope {
        val results = sources.map { source ->
            async(Dispatchers.IO) { source to runCatching { fetch(source) } }
        }.awaitAll()

        val failed = results.mapNotNull { (source, result) ->
            result.exceptionOrNull()?.let { SourceFailure(source, describe(it)) }
        }
        if (failed.size == sources.size) {
            throw IOException("Impossibile scaricare le notizie. Controlla la connessione.")
        }
        val items = results
            .flatMap { it.second.getOrDefault(emptyList()) }
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAt ?: Instant.EPOCH }
        NewsResult(items, failed)
    }

    /**
     * Alcuni siti rifiutano lo user agent da browser (protezioni anti-bot), altri quello da lettore RSS:
     * proviamo il primo e, se il feed non arriva o è vuoto, ritentiamo con l'altro.
     */
    private suspend fun fetch(source: NewsSource): List<NewsItem> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (userAgent in USER_AGENTS) {
            val result = runCatching { fetchWith(source, userAgent) }
            val items = result.getOrNull()
            if (!items.isNullOrEmpty()) return@withContext items
            lastError = result.exceptionOrNull() ?: IOException("feed vuoto")
        }
        throw lastError ?: IOException("feed vuoto")
    }

    private fun fetchWith(source: NewsSource, userAgent: String): List<NewsItem> {
        var url = URL(source.feedUrl)
        // HttpURLConnection non segue i redirect tra http e https: li seguiamo a mano.
        repeat(MAX_REDIRECTS) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.8")
                setRequestProperty("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("HTTP $code")
                    url = URL(url, location)
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val body = conn.inputStream.use { it.readBytes() }
                return parse(trimLeadingJunk(body).inputStream(), source)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("troppi redirect")
    }

    /**
     * I feed WordPress (come quello del Post) a volte hanno spazi o BOM prima di `<?xml`:
     * il parser di Android in quel caso rifiuta l'intero documento.
     */
    private fun trimLeadingJunk(body: ByteArray): ByteArray {
        var start = 0
        if (body.size >= 3 && body[0] == 0xEF.toByte() && body[1] == 0xBB.toByte() && body[2] == 0xBF.toByte()) start = 3
        while (start < body.size && body[start].toInt().toChar().isWhitespace()) start++
        return if (start == 0) body else body.copyOfRange(start, body.size)
    }

    private fun describe(e: Throwable): String = when (e) {
        is UnknownHostException -> "nessuna connessione"
        is SocketTimeoutException -> "timeout"
        is SSLException -> "errore SSL"
        is XmlPullParserException -> "feed non valido"
        else -> e.message?.take(40) ?: e.javaClass.simpleName
    }

    private fun parse(input: InputStream, source: NewsSource): List<NewsItem> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Modalità tollerante: entità HTML come &nbsp; o markup sporco non fanno fallire tutto il feed.
            runCatching { setFeature(FEATURE_RELAXED, true) }
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
        try {
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
        } catch (e: XmlPullParserException) {
            // Feed troncato o malformato a metà: teniamo le notizie già lette.
            if (items.isEmpty()) throw e
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
        const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"
        const val MAX_REDIRECTS = 5
        val USER_AGENTS = listOf(
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
            "GeneralDashboard/${BuildConfig.VERSION_NAME} (Android; lettore RSS)",
        )
    }
}
