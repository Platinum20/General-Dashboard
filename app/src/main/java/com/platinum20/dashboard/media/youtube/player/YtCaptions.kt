package com.platinum20.dashboard.media.youtube.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sottotitoli di YouTube scaricati prima di darli al player. Se il player li chiede da solo e
 * YouTube risponde vuoto o con un errore, l'intera riproduzione si blocca: così invece il video
 * parte sempre, e senza sottotitoli validi resta solo un avviso.
 */
object YtCaptions {
    /** Il testo WebVTT dei sottotitoli, oppure null se YouTube non li dà. */
    suspend fun fetchVtt(subtitle: YtSubtitle): String? = withContext(Dispatchers.IO) {
        val base = subtitle.url.replace(Regex("&fmt=[^&]*"), "")
        runCatching { download("$base&fmt=vtt") }.getOrNull()?.takeIf(::isVtt)
            ?: runCatching { download("$base&fmt=json3") }.getOrNull()?.let(::json3ToVtt)
    }

    internal fun isVtt(text: String): Boolean = text.trimStart('﻿', ' ', '\n', '\r').startsWith("WEBVTT") && text.contains("-->")

    /** Il formato json3 di YouTube (eventi con inizio, durata e pezzi di testo) in WebVTT; null se vuoto. */
    internal fun json3ToVtt(json: String): String? {
        val events = runCatching { JSONObject(json).optJSONArray("events") }.getOrNull() ?: return null
        val out = StringBuilder("WEBVTT\n\n")
        var cues = 0
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val segs = e.optJSONArray("segs") ?: continue
            val text = (0 until segs.length()).joinToString("") { segs.optJSONObject(it)?.optString("utf8").orEmpty() }.trim()
            if (text.isBlank()) continue
            val start = e.optLong("tStartMs")
            val end = start + e.optLong("dDurationMs").coerceAtLeast(500)
            out.append(time(start)).append(" --> ").append(time(end)).append('\n').append(text).append("\n\n")
            cues++
        }
        return if (cues > 0) out.toString() else null
    }

    private fun time(ms: Long): String =
        "%02d:%02d:%02d.%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", NewPipeDownloader.USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
