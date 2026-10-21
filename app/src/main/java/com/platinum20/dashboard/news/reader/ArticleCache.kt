package com.platinum20.dashboard.news.reader

/**
 * Articoli già scaricati e puliti durante la verifica della lista notizie:
 * così il lettore li apre subito, senza riscaricarli.
 */
object ArticleCache {

    private const val MAX_ENTRIES = 120

    private val entries = object : LinkedHashMap<String, ReaderArticle>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderArticle>?) = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(url: String): ReaderArticle? = entries[url]

    @Synchronized
    fun put(url: String, article: ReaderArticle) {
        entries[url] = article
    }
}
