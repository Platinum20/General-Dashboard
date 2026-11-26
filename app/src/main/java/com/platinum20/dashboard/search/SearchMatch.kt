package com.platinum20.dashboard.search

import com.platinum20.dashboard.notes.NotesSearch
import java.text.Normalizer

/**
 * Come Cerca confronta il testo (2026-11-26, Antonio, punto 1): le parole si cercano una per una, in qualunque
 * ordine ("turno marco" trova "Turno su Marco"), e una parola lunga con un errore di battitura si trova lo stesso
 * ("spotfy"). Senza accenti né maiuscole. Funzioni pure, provate nei test.
 */
object SearchMatch {
    private val SPLIT = Regex("""\s+""")
    private val WORD = Regex("""[\p{L}\p{N}]+""")
    private val MARKS = Regex("""\p{M}+""")

    /** Da quante lettere una parola si trova anche sbagliata (le corte darebbero troppe false somiglianze). */
    const val FUZZY_FROM = 5

    /** Le parole della ricerca, normalizzate. */
    fun words(query: String): List<String> = NotesSearch.normalize(query).split(SPLIT).filter { it.isNotEmpty() }

    /**
     * Quanto vale una cosa per la ricerca, 0 = non c'è. Ogni parola deve esserci nel titolo o in [more];
     * nel titolo vale il doppio. In più se il titolo è proprio la ricerca, o comincia con lei.
     */
    fun score(query: String, title: String, vararg more: String): Int {
        val tokens = words(query)
        if (tokens.isEmpty()) return 0
        val t = NotesSearch.normalize(title)
        val rest = more.map(NotesSearch::normalize)
        var total = 0
        for (tok in tokens) {
            val best = maxOf(tokenScore(t, tok) * 2, rest.maxOfOrNull { tokenScore(it, tok) } ?: 0)
            if (best == 0) return 0
            total += best
        }
        val q = tokens.joinToString(" ")
        total += when {
            t == q -> 20
            t.startsWith(q) -> 10
            t.contains(q) -> 4
            else -> 0
        }
        return total
    }

    /** Il testo contiene tutte le parole della ricerca (anche con un errore). */
    fun matches(text: String, query: String): Boolean = score(query, text) > 0

    /**
     * Un titolo "forte" per il risultato migliore: ci sono tutte le parole, esatte e a inizio di parola
     * ("Marco Mengoni" per "marco", non "Gianmarco").
     */
    fun strong(title: String, query: String): Boolean {
        val tokens = words(query)
        if (tokens.isEmpty()) return false
        val t = NotesSearch.normalize(title)
        return tokens.all { tokenScore(t, it) == 3 }
    }

    /** Quante parole della ricerca ci sono in [text]: per scegliere la riga di una nota da mostrare. */
    fun count(text: String, query: String): Int {
        val t = NotesSearch.normalize(text)
        return words(query).count { tokenScore(t, it) > 0 }
    }

    /** 3 = a inizio di parola, 2 = dentro una parola, 1 = una parola quasi uguale, 0 = niente. */
    internal fun tokenScore(text: String, token: String): Int {
        val at = text.indexOf(token)
        if (at >= 0) return if (at == 0 || !text[at - 1].isLetterOrDigit()) 3 else 2
        if (token.length < FUZZY_FROM) return 0
        return if (WORD.findAll(text).any { close(it.value, token) }) 1 else 0
    }

    /** Una parola è quasi uguale: al più un errore (due da otto lettere), o comincia quasi uguale. */
    internal fun close(word: String, token: String): Boolean {
        val allowed = if (token.length >= 8) 2 else 1
        if (kotlin.math.abs(word.length - token.length) <= allowed && distance(word, token, allowed) <= allowed) return true
        return word.length > token.length && distance(word.take(token.length), token, allowed) <= allowed
    }

    /** Lettere da cambiare per passare da [a] a [b] (scambi di due vicine = 1). Si ferma oltre [limit]. */
    internal fun distance(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    /**
     * Dove stanno nel testo vero le parole cercate, per colorarle: le parole esatte e quelle quasi uguali.
     * In ordine, senza sovrapposizioni.
     */
    fun ranges(text: String, query: String): List<IntRange> {
        val tokens = words(query)
        if (tokens.isEmpty() || text.isEmpty()) return emptyList()
        val found = mutableListOf<IntRange>()
        for (tok in tokens) {
            val exact = exactRanges(text, tok)
            if (exact.isNotEmpty()) { found += exact; continue }
            if (tok.length < FUZZY_FROM) continue
            WORD.findAll(text).forEach { w ->
                if (close(plain(w.value), tok)) found += w.range
            }
        }
        return merge(found)
    }

    private fun plain(s: String) = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    /** Ogni volta che [token] (già normalizzato) compare in [text], ignorando accenti e maiuscole. */
    private fun exactRanges(text: String, token: String): List<IntRange> {
        val owner = mutableListOf<Int>()
        val flat = StringBuilder()
        text.forEachIndexed { i, c -> plain(c.toString()).forEach { p -> flat.append(p); owner += i } }
        val out = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val at = flat.indexOf(token, from)
            if (at < 0) break
            out += owner[at]..owner[at + token.length - 1]
            from = at + token.length
        }
        return out
    }

    private fun merge(list: List<IntRange>): List<IntRange> {
        val sorted = list.sortedBy { it.first }
        val out = mutableListOf<IntRange>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..maxOf(last.last, r.last) else out += r
        }
        return out
    }
}
