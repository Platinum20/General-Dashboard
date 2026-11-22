package com.platinum20.dashboard.notes

import android.content.Context
import com.platinum20.dashboard.news.reader.ArticleExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File

/**
 * L'anteprima di un link nella nota (1.590): titolo e immagine della pagina, presi una volta sola e tenuti in un file,
 * così la scheda si vede subito anche senza rete.
 */
object LinkPreview {
    data class Card(val title: String, val image: String?, val site: String)

    private val _cards = MutableStateFlow<Map<String, Card>>(emptyMap())
    val cards: StateFlow<Map<String, Card>> = _cards.asStateFlow()
    private val asked = mutableSetOf<String>()
    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "link-previews.json")

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file(context).readText())
            _cards.value = o.keys().asSequence().associateWith { k ->
                val c = o.getJSONObject(k)
                Card(c.optString("title"), c.optString("image").takeIf { it.isNotBlank() }, c.optString("site"))
            }
        }
    }

    /** Scarica titolo e immagine di [url] se non li ha già. */
    suspend fun fetch(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            synchronized(this@LinkPreview) {
                load(context)
                if (url in _cards.value || !asked.add(url)) return@withContext
            }
            val card = runCatching {
                val page = ArticleExtractor.download(url)
                val doc = Jsoup.parse(page.html, page.url)
                fun meta(p: String) = doc.selectFirst("meta[property=$p], meta[name=$p]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
                Card(
                    title = meta("og:title") ?: meta("twitter:title") ?: doc.title().trim().ifEmpty { NoteLines.host(url) },
                    image = (meta("og:image") ?: meta("twitter:image"))?.let { runCatching { java.net.URL(java.net.URL(page.url), it).toString() }.getOrNull() },
                    site = meta("og:site_name") ?: NoteLines.host(url),
                )
            }.getOrNull() ?: return@withContext
            synchronized(this@LinkPreview) {
                _cards.value = _cards.value + (url to card)
                runCatching {
                    val o = JSONObject()
                    _cards.value.forEach { (k, c) -> o.put(k, JSONObject().put("title", c.title).put("image", c.image ?: "").put("site", c.site)) }
                    file(context).writeText(o.toString())
                }
            }
            Unit
        }
    }
}
