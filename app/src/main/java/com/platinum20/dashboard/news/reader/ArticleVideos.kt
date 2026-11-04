package com.platinum20.dashboard.news.reader

/**
 * I video dentro gli articoli si guardano nel player di Canvas, non nel riquadro del sito:
 * da un indirizzo di YouTube (anche "embed" o "nocookie") ricava la pagina del video.
 */
object ArticleVideos {
    private val patterns = listOf(
        Regex("""youtube(?:-nocookie)?\.com/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/watch\?(?:.*&)?v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    )

    fun youtubeId(url: String): String? = patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

    fun youtubeWatchUrl(url: String): String? = youtubeId(url)?.let { "https://m.youtube.com/watch?v=$it" }

    /** Come si guarda questo video in Canvas; null = nessun modo nativo, il riquadro non si mostra. */
    fun playable(video: ReaderBlock.Video): String? = when {
        youtubeId(video.url) != null -> youtubeWatchUrl(video.url)
        !video.embed && (video.url.startsWith("https://") || video.url.startsWith("http://")) -> video.url
        else -> null
    }
}
