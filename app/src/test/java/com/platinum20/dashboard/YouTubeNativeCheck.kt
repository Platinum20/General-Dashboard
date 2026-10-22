package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.YtResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Controllo dal vivo del player nativo di YouTube: ricava i flussi di alcuni video e prova a
 * scaricarne l'inizio con lo stesso user agent dell'app. Gira solo con -Plive=true.
 */
class YouTubeNativeCheck {
    @Test
    fun flussi() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")
        val out = StringBuilder("## Player nativo YouTube\n\n")
        for (link in listOf("https://www.youtube.com/watch?v=jNQXAC9IVRw", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")) {
            val result = runCatching { YtResolver.resolve(link) }
            val stream = result.getOrNull()
            if (stream == null) {
                out.append("- ❌ $link: ${result.exceptionOrNull()}\n")
                result.exceptionOrNull()?.printStackTrace()
                continue
            }
            out.append("- ✅ $link: \"${stream.title}\" di ${stream.channel}, ${stream.height}p\n")
            out.append("  - qualità automatica (DASH): ${if (stream.dashManifest != null) "sì" else "no"}, " +
                "sottotitoli: ${stream.subtitles.size}, correlati: ${stream.related.size}\n")
            for ((name, url) in listOf("video" to stream.videoUrl, "audio" to stream.audioUrl, "unico" to stream.muxedUrl)) {
                if (url == null) {
                    out.append("  - $name: assente\n")
                    continue
                }
                val code = runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", YtResolver.userAgentFor(url))
                    conn.setRequestProperty("Range", "bytes=0-65535")
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 15_000
                    val c = conn.responseCode
                    conn.disconnect()
                    c
                }.getOrElse { -1 }
                val client = Regex("""[?&]c=([A-Za-z_]+)""").find(url)?.groupValues?.get(1)
                out.append("  - $name (client $client): HTTP $code\n")
            }
        }
        // Video con l'audio doppiato in più lingue: deve restare la traccia originale.
        runCatching { YtResolver.resolve("https://www.youtube.com/watch?v=0e3GPea1Tyg") }
            .onSuccess { out.append("- audio multilingua: ${Regex("[?&]xtags=[^&]*").find(it.audioUrl.orEmpty())?.value ?: "nessun tag"}\n") }
            .onFailure { out.append("- ❌ audio multilingua: $it\n") }
        val comments = runCatching { YtResolver.comments("dQw4w9WgXcQ") }
        comments.getOrNull()?.let { page ->
            val withReplies = page.comments.firstOrNull { it.repliesPage != null }
            val replies = withReplies?.let { runCatching { YtResolver.comments("dQw4w9WgXcQ", it.repliesPage) }.getOrNull() }
            out.append("- ✅ commenti: ${page.comments.size}, altra pagina: ${page.next != null}, risposte caricate: ${replies?.comments?.size}\n")
        } ?: out.append("- ❌ commenti: ${comments.exceptionOrNull()}\n")
        val found = runCatching { YtResolver.search("lofi hip hop") }
        found.getOrNull()?.let { out.append("- ✅ ricerca: ${it.size} video, primo \"${it.firstOrNull()?.title}\"\n") }
            ?: out.append("- ❌ ricerca: ${found.exceptionOrNull()}\n")
        println(out)
        val dir = File(System.getProperty("live.dir") ?: "build/live").apply { mkdirs() }
        File(dir, "youtube.md").writeText(out.toString())
    }
}
