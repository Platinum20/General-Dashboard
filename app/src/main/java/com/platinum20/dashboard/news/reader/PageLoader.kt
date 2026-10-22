package com.platinum20.dashboard.news.reader

import com.platinum20.dashboard.net.WebFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Scarica la pagina di un articolo: prima direttamente (veloce), poi, se il sito ci blocca
 * come bot (403, 429, 503), passando dal motore del browser con [WebFetcher].
 * I siti che bloccano vengono ricordati, così le volte dopo si va subito dal browser.
 */
object PageLoader {

    private val blockedHosts = ConcurrentHashMap.newKeySet<String>()
    private val BLOCKED_CODES = listOf("HTTP 403", "HTTP 429", "HTTP 503")

    suspend fun load(url: String): FetchedPage? {
        val host = runCatching { java.net.URL(url).host }.getOrNull()
        if (host == null || host !in blockedHosts) {
            try {
                return withContext(Dispatchers.IO) { ArticleExtractor.download(url, WebPageRenderer::cookiesFor) }
            } catch (e: IOException) {
                if (host != null && BLOCKED_CODES.any { e.message?.contains(it) == true }) blockedHosts += host
                else return null
            }
        }
        return WebFetcher.fetchText(url)?.let { FetchedPage(url, it) }
    }
}
