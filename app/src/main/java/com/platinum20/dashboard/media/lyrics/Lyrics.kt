package com.platinum20.dashboard.media.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una riga del testo: da quando si canta (ms) e cosa. Senza tempi, [atMs] è -1. */
data class LyricLine(val atMs: Long, val text: String)

/** Il testo di un brano: righe a tempo se ci sono, altrimenti il testo semplice. */
data class SongLyrics(val lines: List<LyricLine>, val synced: Boolean, val instrumental: Boolean = false)

/**
 * Testi da LRCLIB (lrclib.net: gratis, senza chiave, testi a tempo "LRC"). Si cercano per titolo, artista e
 * durata; si tengono in memoria finché l'app è aperta, anche quando non ci sono (niente seconda richiesta).
 */
object Lyrics {
    private const val BASE = "https://lrclib.net/api/"

    private val _cache = MutableStateFlow<Map<String, SongLyrics?>>(emptyMap())
    val cache: StateFlow<Map<String, SongLyrics?>> = _cache.asStateFlow()
    private val asked = HashSet<String>()

    fun keyOf(title: String, artist: String): String = (artist + "|" + title).lowercase()

    /** Chiede il testo una volta sola per brano; il risultato arriva in [cache]. */
    suspend fun load(title: String, artist: String, durationMs: Long) {
        val key = keyOf(title, artist)
        if (title.isBlank() || !asked.add(key)) return
        val found = runCatching { fetch(title, artist, durationMs) }.getOrNull()
        _cache.update { it + (key to found) }
    }

    private suspend fun fetch(title: String, artist: String, durationMs: Long): SongLyrics? = withContext(Dispatchers.IO) {
        val name = Lrc.cleanTitle(title)
        val q = "track_name=" + enc(name) + "&artist_name=" + enc(artist)
        val list = get("search?$q")?.let { runCatching { JSONArray(it) }.getOrNull() }
        val best = Lrc.pick(list, durationMs / 1000) ?: return@withContext null
        Lrc.from(best)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(path: String): String? {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            // LRCLIB chiede di dire chi fa le richieste.
            setRequestProperty("User-Agent", "Canvas (app personale Android)")
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }
}

/** Le parti pure dei testi: lettura del formato LRC, scelta del risultato, riga di adesso. */
object Lrc {
    private val TIME = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    /** "[01:02.50] parole" → righe in ordine di tempo; le righe con più tempi valgono per ognuno. */
    fun parse(text: String): List<LyricLine> = text.lines().flatMap { raw ->
        val times = TIME.findAll(raw).toList()
        if (times.isEmpty()) return@flatMap emptyList()
        val words = raw.substring(times.last().range.last + 1).trim()
        times.map { m ->
            val (min, sec, frac) = m.destructured
            val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
            LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, words)
        }
    }.sortedBy { it.atMs }

    /** La riga che si canta a [positionMs]: l'ultima già iniziata, -1 prima della prima. */
    fun index(lines: List<LyricLine>, positionMs: Long): Int {
        var found = -1
        for (i in lines.indices) if (lines[i].atMs <= positionMs) found = i else break
        return found
    }

    /** Tra i risultati, quello con la durata più vicina (entro 5 s se la sappiamo), preferendo quelli a tempo. */
    fun pick(results: JSONArray?, durationSec: Long): JSONObject? {
        if (results == null) return null
        val all = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
            .filter { it.optString("syncedLyrics").isNotBlank() || it.optString("plainLyrics").isNotBlank() || it.optBoolean("instrumental") }
        val near = if (durationSec > 0) all.filter { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSec) <= 5 } else all
        val pool = near.ifEmpty { if (durationSec > 0) emptyList() else all }
        return pool.firstOrNull { it.optString("syncedLyrics").isNotBlank() } ?: pool.firstOrNull()
    }

    fun from(json: JSONObject): SongLyrics? {
        if (json.optBoolean("instrumental")) return SongLyrics(emptyList(), synced = false, instrumental = true)
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let(::parse).orEmpty()
        if (synced.isNotEmpty()) return SongLyrics(synced, synced = true)
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return SongLyrics(plain.lines().map { LyricLine(-1, it.trim()) }, synced = false)
    }

    /** "Titolo - Remastered 2011" o "Titolo (feat. X)": LRCLIB lo conosce col titolo semplice. */
    fun cleanTitle(title: String): String = title
        .replace(Regex("\\s+-\\s+.*(remaster|version|live|edit|mix|mono|stereo).*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\((feat|with|ft)\\.?[^)]*\\)", RegexOption.IGNORE_CASE), "")
        .trim()
}
