package com.platinum20.dashboard.news

import java.text.Normalizer
import java.time.Duration
import java.time.Instant

/** Un tema del giorno: notizie simili raccolte insieme, con il titolo più rappresentativo. */
data class RecapTheme(
    val keywords: List<String>,
    val headline: NewsItem,
    val items: List<NewsItem>,
) {
    val sourceCount: Int get() = items.map { it.source.id }.distinct().size
}

/**
 * Recap automatico senza AI: raggruppa i titoli che condividono parole importanti
 * e ordina i gruppi per quante testate ne parlano. Tutto in locale, gratis.
 */
object NewsRecap {

    fun build(items: List<NewsItem>, max: Int = 10, now: Instant = Instant.now()): List<RecapTheme> {
        val recent = items
            .filter { it.publishedAt == null || Duration.between(it.publishedAt, now).toHours() < 24 }
            .take(400)
        if (recent.isEmpty()) return emptyList()

        val words = recent.map { words(it.title) }
        // Le parole presenti in troppi titoli (es. "Italia") non aiutano a distinguere i temi.
        val df = HashMap<String, Int>()
        words.forEach { w -> w.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val maxDf = maxOf(4, (recent.size * 0.12).toInt())
        val keys = words.map { w -> w.keys.filter { (df[it] ?: 0) in 2..maxDf }.toSet() }

        val neighbors = recent.indices.map { i ->
            recent.indices.filter { j -> j != i && (keys[i] intersect keys[j]).size >= 2 }
        }

        // Clustering "a leader": il titolo più collegato apre il gruppo e prende i suoi vicini.
        // Evita le catene che unirebbero temi diversi.
        val order = recent.indices.sortedWith(
            compareByDescending<Int> { i -> neighbors[i].map { recent[it].source.id }.distinct().size }
                .thenByDescending { neighbors[it].size }
                .thenByDescending { recent[it].publishedAt ?: Instant.EPOCH }
        )
        val assigned = BooleanArray(recent.size)
        val groups = mutableListOf<List<Int>>()
        for (leader in order) {
            if (assigned[leader]) continue
            val members = listOf(leader) + neighbors[leader].filter { !assigned[it] }
            members.forEach { assigned[it] = true }
            groups += members
        }

        val themes = groups
            .map { members -> toTheme(members.map { recent[it] }, members.map { words[it] }, members.map { keys[it] }) }
            .sortedWith(
                compareByDescending<RecapTheme> { it.sourceCount }
                    .thenByDescending { it.items.size }
                    .thenByDescending { it.headline.publishedAt ?: Instant.EPOCH }
            )
        return themes.take(max)
    }

    private fun toTheme(items: List<NewsItem>, words: List<Map<String, String>>, keys: List<Set<String>>): RecapTheme {
        val counts = HashMap<String, Int>()
        keys.forEach { k -> k.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val top = counts.entries
            .filter { items.size == 1 || it.value >= 2 }
            .sortedByDescending { it.value }
            .take(3)
            .map { entry ->
                // La forma della parola usata più spesso nei titoli, con l'iniziale maiuscola.
                words.mapNotNull { it[entry.key] }
                    .groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }!!.key
                    .replaceFirstChar { it.uppercase() }
            }
        val sorted = items.sortedByDescending { it.publishedAt ?: Instant.EPOCH }
        return RecapTheme(keywords = top, headline = items.first(), items = sorted)
    }

    /** Parole significative del titolo: radice -> parola originale. */
    private fun words(title: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        Regex("[\\p{L}\\p{N}]+").findAll(title.replace('’', '\'')).forEach { m ->
            val original = m.value
            val lower = original.lowercase()
            if (lower.length < 3 || lower in STOPWORDS) return@forEach
            if (lower.all { it.isDigit() } && lower.length < 4) return@forEach
            result.putIfAbsent(stem(lower), original)
        }
        return result
    }

    /** Radice grezza: senza accenti e vocali finali, così "elezione" ed "elezioni" coincidono. */
    private fun stem(word: String): String {
        val plain = Normalizer.normalize(word, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        var s = plain
        while (s.length > 4 && s.last() in "aeiou") s = s.dropLast(1)
        return s
    }

    private val STOPWORDS = setOf(
        "che", "chi", "non", "per", "con", "tra", "fra", "dal", "dai", "dei", "del", "della", "delle", "dello",
        "degli", "alla", "alle", "allo", "agli", "nel", "nella", "nelle", "nello", "negli", "sul", "sulla",
        "sulle", "sullo", "sugli", "dalla", "dalle", "dallo", "dagli", "una", "uno", "gli", "lei", "lui", "loro",
        "suo", "sua", "suoi", "sue", "mio", "mia", "come", "dove", "quando", "anche", "ancora", "più", "piu",
        "meno", "molto", "tutto", "tutti", "tutte", "questo", "questa", "questi", "queste", "quello", "quella",
        "sono", "sarà", "sara", "essere", "stato", "stata", "stati", "era", "hanno", "aveva", "avere", "fare",
        "fa", "dopo", "prima", "ecco", "oggi", "ieri", "domani", "ora", "anni", "anno", "giorni", "giorno",
        "mesi", "mese", "nuovo", "nuova", "nuovi", "nuove", "video", "foto", "live", "diretta", "news",
        "ultime", "notizie", "ultim", "cosa", "così", "cosi", "ogni", "senza", "sotto", "sopra", "contro",
        "verso", "fino", "solo", "già", "gia", "poi", "mai", "sempre", "via", "parla", "dice", "detto",
        "the", "and", "for", "with", "from", "after", "about", "into", "over", "will", "has", "have",
        "are", "was", "were", "its", "his", "her", "their", "new", "says", "what", "how", "why",
    )
}
