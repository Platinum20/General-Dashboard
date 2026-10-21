package com.platinum20.dashboard.news.reader

import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException

/** Un pezzo di testo con il suo stile: serve a ricostruire grassetti, corsivi e link nel lettore. */
data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
)

sealed interface ReaderBlock {
    data class Heading(val text: String) : ReaderBlock
    data class Paragraph(val runs: List<TextRun>) : ReaderBlock
    data class Quote(val runs: List<TextRun>) : ReaderBlock
    data class ListItem(val runs: List<TextRun>, val marker: String) : ReaderBlock
    data class Image(val url: String, val caption: String?) : ReaderBlock
    /** [embed] = true per i player dei siti video (YouTube, ...), false per un file video diretto. */
    data class Video(val url: String, val embed: Boolean, val poster: String?) : ReaderBlock
    data object Divider : ReaderBlock
}

data class ReaderArticle(
    val url: String,
    val title: String,
    val byline: String?,
    val leadImage: String?,
    val blocks: List<ReaderBlock>,
) {
    /** Lunghezza del testo vero e proprio, per capire se l'estrazione è riuscita o se c'è un paywall. */
    val textLength: Int
        get() = blocks.sumOf { block ->
            when (block) {
                is ReaderBlock.Paragraph -> block.runs.sumOf { it.text.length }
                is ReaderBlock.Quote -> block.runs.sumOf { it.text.length }
                is ReaderBlock.ListItem -> block.runs.sumOf { it.text.length }
                is ReaderBlock.Heading -> block.text.length
                else -> 0
            }
        }
}

/**
 * Scarica la pagina di un articolo e ne tiene solo il contenuto (testo, immagini, video),
 * come la modalità lettura dei browser. L'estrazione del corpo usa Readability (lo stesso
 * algoritmo di Firefox), poi il risultato viene convertito in blocchi da disegnare in Compose.
 */
object ArticleExtractor {

    fun fetch(url: String): ReaderArticle {
        val doc = try {
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
                .timeout(20_000)
                .followRedirects(true)
                .maxBodySize(0)
                .get()
        } catch (e: IOException) {
            throw IOException("Impossibile scaricare l'articolo.", e)
        }
        return extract(doc.location().ifBlank { url }, doc)
    }

    fun extract(url: String, doc: Document): ReaderArticle {
        fixLazyMedia(doc)
        val leadImage = doc.metaContent("og:image")?.let { doc.absolute(it) }
        val metaTitle = doc.metaContent("og:title")
        val metaAuthor = doc.metaContent("author")

        val article = Readability4JExtended(url, doc).parse()
        val contentHtml = article.content ?: throw IOException("Articolo non trovato nella pagina.")
        val content = Jsoup.parseBodyFragment(contentHtml, url).body()

        // og:title di solito è pulito; il titolo della pagina spesso finisce con " | Nome del giornale".
        var title = metaTitle?.cleanText()?.takeIf { it.isNotBlank() }
            ?: article.title?.cleanText()?.substringBeforeLast(" | ")?.takeIf { it.isNotBlank() }
            ?: ""
        // Il titolo lo mostriamo già sopra: se l'articolo inizia ripetendolo, lo togliamo
        // (tenendo la versione più completa delle due).
        val blocks = BlockBuilder().apply { walk(content) }.build().let { list ->
            val first = list.firstOrNull()
            if (first is ReaderBlock.Heading && sameTitle(first.text, title)) {
                if (first.text.length > title.length) title = first.text
                list.drop(1)
            } else {
                list
            }
        }
        // L'immagine di copertina serve solo se l'articolo non ne ha già una in apertura.
        val opensWithImage = blocks.take(3).any { it is ReaderBlock.Image || it is ReaderBlock.Video }
        val alreadyInside = blocks.any { it is ReaderBlock.Image && leadImage != null && sameImage(it.url, leadImage) }

        return ReaderArticle(
            url = url,
            title = title,
            byline = (article.byline ?: metaAuthor)?.cleanText()?.takeIf { it.isNotBlank() && it.length < 120 },
            leadImage = leadImage?.takeIf { !opensWithImage && !alreadyInside },
            blocks = blocks,
        )
    }

    private fun sameTitle(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val x = norm(a)
        val y = norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x.startsWith(y) || y.startsWith(x))
    }

    /**
     * I giornali caricano le immagini "pigramente": il vero indirizzo sta in data-src o srcset,
     * mentre src è un segnaposto. Lo rimettiamo in src prima dell'estrazione.
     */
    private fun fixLazyMedia(doc: Document) {
        for (img in doc.select("img")) {
            val src = img.attr("src")
            val lazy = LAZY_ATTRS.firstNotNullOfOrNull { attr -> img.attr(attr).takeIf { it.isNotBlank() } }
            val fromSrcset = bestFromSrcset(img.attr("data-srcset").ifBlank { img.attr("srcset") })
            val fromPicture = img.parent()?.takeIf { it.tagName() == "picture" }
                ?.select("source")?.firstNotNullOfOrNull { s ->
                    bestFromSrcset(s.attr("data-srcset").ifBlank { s.attr("srcset") })
                }
            val placeholder = src.isBlank() || src.startsWith("data:") || src.contains("placeholder") || src.contains("blank.")
            val best = lazy ?: fromSrcset ?: fromPicture
            if (best != null && (placeholder || fromSrcset != null || fromPicture != null)) img.attr("src", best)
        }
        for (video in doc.select("video")) {
            if (video.attr("src").isBlank()) {
                video.selectFirst("source[src]")?.let { video.attr("src", it.attr("src")) }
            }
        }
        for (frame in doc.select("iframe")) {
            if (frame.attr("src").isBlank() || frame.attr("src") == "about:blank") {
                frame.attr("data-src").takeIf { it.isNotBlank() }?.let { frame.attr("src", it) }
            }
        }
    }

    /** Dal srcset prende la versione più grande, senza esagerare per il telefono. */
    private fun bestFromSrcset(srcset: String): String? {
        if (srcset.isBlank()) return null
        val candidates = srcset.split(Regex(",\\s+")).mapNotNull { part ->
            val pieces = part.trim().split(Regex("\\s+"))
            val u = pieces.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val width = pieces.getOrNull(1)?.let { d ->
                when {
                    d.endsWith("w") -> d.dropLast(1).toIntOrNull()
                    d.endsWith("x") -> d.dropLast(1).toFloatOrNull()?.times(1000)?.toInt()
                    else -> null
                }
            } ?: 0
            u to width
        }
        val sensible = candidates.filter { it.second <= 1600 }.ifEmpty { candidates }
        return sensible.maxByOrNull { it.second }?.first
    }

    private fun sameImage(a: String, b: String): Boolean {
        // Stessa immagine in formati diversi: "foto-1200x630.jpg" e "foto.jpg".
        fun key(u: String) = u.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("-\\d+x\\d+$"), "")
        return key(a).isNotBlank() && key(a) == key(b)
    }

    private fun Document.metaContent(property: String): String? =
        selectFirst("meta[property=$property], meta[name=$property]")?.attr("content")?.takeIf { it.isNotBlank() }

    private fun Document.absolute(u: String): String =
        if (u.startsWith("//")) "https:$u" else runCatching { java.net.URL(java.net.URL(location()), u).toString() }.getOrDefault(u)

    private val LAZY_ATTRS = listOf("data-src", "data-lazy-src", "data-original", "data-lazy", "data-url", "data-hi-res-src")

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
}

private fun String.cleanText(): String = replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

/** Percorre l'HTML estratto e lo trasforma in blocchi: paragrafi, titoletti, immagini, video... */
private class BlockBuilder {
    private val blocks = mutableListOf<ReaderBlock>()
    private val pending = mutableListOf<TextRun>()
    /** Immagini trovate dentro un paragrafo: vanno mostrate subito dopo il suo testo. */
    private val pendingMedia = mutableListOf<ReaderBlock>()

    fun build(): List<ReaderBlock> {
        flush()
        // Niente blocchi vuoti o immagini ripetute di fila.
        return blocks.filterIndexed { i, block -> i == 0 || block != blocks[i - 1] }
    }

    fun walk(parent: Element) {
        for (node in parent.childNodes()) visit(node)
    }

    private fun visit(node: Node) {
        when (node) {
            is TextNode -> if (!node.isBlank) pending += TextRun(node.text())
            is Element -> visitElement(node)
        }
    }

    private fun visitElement(el: Element) {
        when (el.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush()
                el.text().cleanText().takeIf { it.isNotBlank() }?.let { blocks += ReaderBlock.Heading(it) }
            }
            "p", "pre", "address" -> {
                flush()
                inline(el, bold = false, italic = false, link = null)
                flush()
            }
            "blockquote" -> {
                flush()
                val runs = InlineCollector().apply { collect(el) }.runs.normalized()
                if (runs.isNotEmpty()) blocks += ReaderBlock.Quote(runs)
            }
            "ul", "ol" -> {
                flush()
                val ordered = el.tagName().equals("ol", ignoreCase = true)
                el.children().filter { it.tagName().equals("li", ignoreCase = true) }.forEachIndexed { i, li ->
                    val runs = InlineCollector().apply { collect(li) }.runs.normalized()
                    if (runs.isNotEmpty()) blocks += ReaderBlock.ListItem(runs, if (ordered) "${i + 1}." else "•")
                    li.select("img").forEach { image(it, null)?.let { b -> blocks += b } }
                }
            }
            "figure" -> {
                flush()
                val caption = el.selectFirst("figcaption")?.text()?.cleanText()?.takeIf { it.isNotBlank() }
                val media = el.selectFirst("video, iframe, img")
                when (media?.tagName()?.lowercase()) {
                    "img" -> image(media, caption)?.let { blocks += it }
                    "video", "iframe" -> video(media)?.let { blocks += it }
                }
            }
            "img" -> {
                flush()
                image(el, null)?.let { blocks += it }
            }
            "video", "iframe", "embed" -> {
                flush()
                video(el)?.let { blocks += it }
            }
            "hr" -> {
                flush()
                blocks += ReaderBlock.Divider
            }
            "br" -> pending += TextRun("\n")
            "script", "style", "noscript", "form", "button", "input", "select", "svg", "figcaption" -> Unit
            "table" -> {
                flush()
                el.select("tr").forEach { tr ->
                    val text = tr.children().joinToString("  ·  ") { it.text().cleanText() }.trim()
                    if (text.isNotBlank()) blocks += ReaderBlock.Paragraph(listOf(TextRun(text)))
                }
            }
            else -> {
                if (el.children().any { it.isBlockLike() }) {
                    walk(el)
                } else {
                    inline(el, bold = false, italic = false, link = null)
                }
            }
        }
    }

    private fun inline(el: Element, bold: Boolean, italic: Boolean, link: String?) {
        val collector = InlineCollector(pendingMedia)
        collector.collect(el, bold, italic, link)
        pending += collector.runs
    }

    private fun flush() {
        val runs = pending.normalized()
        pending.clear()
        if (runs.isNotEmpty()) blocks += ReaderBlock.Paragraph(runs)
        blocks += pendingMedia
        pendingMedia.clear()
    }

    private fun Element.isBlockLike(): Boolean = tagName().lowercase() in BLOCK_TAGS

    companion object {
        val BLOCK_TAGS = setOf(
            "p", "div", "section", "article", "header", "footer", "main", "aside", "figure", "blockquote",
            "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "table", "pre", "hr", "video", "iframe", "img",
            "picture", "address",
        )
    }
}

/** Raccoglie il testo "in linea" di un elemento, con grassetti, corsivi e link. */
private class InlineCollector(private val media: MutableList<ReaderBlock>? = null) {
    val runs = mutableListOf<TextRun>()

    fun collect(el: Element, bold: Boolean = false, italic: Boolean = false, link: String? = null) {
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> {
                    val text = node.wholeText
                    if (text.isNotEmpty()) runs += TextRun(text, bold, italic, link)
                }
                is Element -> when (node.tagName().lowercase()) {
                    "br" -> runs += TextRun("\n", bold, italic, link)
                    "b", "strong" -> collect(node, true, italic, link)
                    "i", "em", "cite" -> collect(node, bold, true, link)
                    "a" -> {
                        val href = node.absUrl("href").takeIf { it.startsWith("http") }
                        collect(node, bold, italic, href ?: link)
                    }
                    "img" -> image(node, null)?.let { media?.add(it) }
                    "video", "iframe" -> video(node)?.let { media?.add(it) }
                    "script", "style", "noscript", "button", "svg" -> Unit
                    "p", "div", "li" -> {
                        if (runs.isNotEmpty()) runs += TextRun("\n")
                        collect(node, bold, italic, link)
                    }
                    else -> collect(node, bold, italic, link)
                }
            }
        }
    }
}

/** Spazi sistemati: niente doppi spazi, niente spazi a inizio o fine riga, pezzi uguali uniti. */
private fun List<TextRun>.normalized(): List<TextRun> {
    val merged = mutableListOf<TextRun>()
    for (run in this) {
        val text = run.text.replace(' ', ' ').replace(Regex("[ \\t\\r\\f]*\\n[ \\t\\r\\f]*"), "\n")
            .replace(Regex("[ \\t\\r\\f]+"), " ")
        if (text.isEmpty()) continue
        val last = merged.lastOrNull()
        if (last != null && last.bold == run.bold && last.italic == run.italic && last.link == run.link) {
            merged[merged.lastIndex] = last.copy(text = last.text + text)
        } else {
            merged += run.copy(text = text)
        }
    }
    // Spazi doppi tra un pezzo e l'altro, e bordi.
    for (i in 1 until merged.size) {
        if (merged[i - 1].text.endsWith(" ") && merged[i].text.startsWith(" ")) {
            merged[i] = merged[i].copy(text = merged[i].text.trimStart(' '))
        }
    }
    if (merged.isNotEmpty()) {
        merged[0] = merged[0].copy(text = merged[0].text.trimStart())
        merged[merged.lastIndex] = merged.last().copy(text = merged.last().text.trimEnd())
    }
    val result = merged.filter { it.text.isNotEmpty() }
    return if (result.all { it.text.isBlank() }) emptyList() else result
}

private fun image(el: Element, caption: String?): ReaderBlock.Image? {
    val src = el.absUrl("src").ifBlank { el.attr("src") }
    if (!src.startsWith("http")) return null
    // Pixel di tracciamento e icone minuscole.
    val w = el.attr("width").toIntOrNull()
    val h = el.attr("height").toIntOrNull()
    if ((w != null && w < 80) || (h != null && h < 80)) return null
    return ReaderBlock.Image(src, caption)
}

private fun video(el: Element): ReaderBlock.Video? {
    val tag = el.tagName().lowercase()
    val src = el.absUrl("src").ifBlank { el.attr("src") }.let { if (it.startsWith("//")) "https:$it" else it }
    if (!src.startsWith("http")) return null
    return if (tag == "video") {
        ReaderBlock.Video(src, embed = false, poster = el.absUrl("poster").takeIf { it.isNotBlank() })
    } else {
        // Dei riquadri incorporati teniamo solo i player video, non pubblicità o social.
        if (!VIDEO_HOSTS.containsMatchIn(src)) return null
        ReaderBlock.Video(src, embed = true, poster = null)
    }
}

private val VIDEO_HOSTS = Regex(
    "youtube\\.com|youtube-nocookie\\.com|youtu\\.be|vimeo\\.com|dailymotion\\.com|dai\\.ly|jwplayer|jwplatform|" +
        "brightcove|kaltura|vidible|/video|video\\.|player\\.|/embed",
    RegexOption.IGNORE_CASE,
)
