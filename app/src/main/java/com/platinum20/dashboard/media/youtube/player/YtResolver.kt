package com.platinum20.dashboard.media.youtube.player

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfo
import com.platinum20.dashboard.media.youtube.YtVideo

/** Un video di YouTube pronto per il player nativo: indirizzi dei flussi e dati da mostrare. */
data class YtStream(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    /** Video senza audio (qualità alta) da unire a [audioUrl]; null se c'è solo [muxedUrl]. */
    val videoUrl: String?,
    val audioUrl: String?,
    /** Video con l'audio incluso (qualità più bassa): riserva se l'unione non va. */
    val muxedUrl: String?,
    /** Dirette: flusso HLS. */
    val hlsUrl: String?,
    val height: Int,
    /** Qualità disponibili (altezza → video senza audio), dalla più alta. */
    val qualities: List<YtQuality> = emptyList(),
    val durationSec: Long = 0,
    /** Capitoli del video (i "minutaggi" della descrizione). */
    val chapters: List<YtChapter> = emptyList(),
    val views: Long = -1,
    val uploaded: String = "",
    val channelAvatar: String = "",
    val subscribers: Long = -1,
    val likes: Long = -1,
    val description: String = "",
)

data class YtQuality(val height: Int, val url: String)

data class YtChapter(val title: String, val startSec: Int)

data class YtComment(val author: String, val text: String, val likes: Int, val date: String, val avatar: String, val replies: Int)

/**
 * Ricava i flussi di un video con NewPipe Extractor (lo stesso di NewPipe): niente pagina web,
 * niente pubblicità, il video si riproduce con il player di Android.
 */
object YtResolver {
    private const val MAX_HEIGHT = 1080
    @Volatile private var ready = false

    private fun init() {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(NewPipeDownloader(), Localization("it", "IT"), ContentCountry("IT"))
                ready = true
            }
        }
    }

    fun videoId(link: String): String? =
        Regex("""(?:v=|youtu\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})""").find(link)?.groupValues?.get(1)

    /** Ricerca dei video senza API e senza quota: stessi risultati della ricerca di YouTube. */
    suspend fun search(query: String): List<YtVideo> = withContext(Dispatchers.IO) {
        init()
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = videoId(item.url) ?: return@mapNotNull null
                YtVideo(
                    id = id,
                    title = item.name,
                    channel = item.uploaderName.orEmpty(),
                    channelId = "",
                    published = null,
                    isShort = item.isShortFormContent,
                )
            }
    }

    /** Primi commenti del video, come li ordina YouTube. */
    suspend fun comments(id: String): List<YtComment> = withContext(Dispatchers.IO) {
        init()
        CommentsInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id").relatedItems.map { c ->
            val raw = c.commentText?.content.orEmpty()
            YtComment(
                author = c.uploaderName.orEmpty(),
                text = if ('<' in raw) Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim() else raw,
                likes = c.likeCount.coerceAtLeast(0),
                date = c.textualUploadDate.orEmpty(),
                avatar = c.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(),
                replies = c.replyCount.coerceAtLeast(0),
            )
        }
    }

    /** [maxHeight]: qualità massima scelta (0 = la più alta fino a 1080p). */
    suspend fun resolve(link: String, maxHeight: Int = 0): YtStream = withContext(Dispatchers.IO) {
        init()
        val id = videoId(link) ?: throw IllegalArgumentException("Link di YouTube non valido")
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM

        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // L'audio M4A (AAC) va ovunque; a parità, il bitrate più alto.
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format == MediaFormat.M4A }.thenByDescending { it.averageBitrate })
            .firstOrNull()
        val videos = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && height(it.resolution) in 1..MAX_HEIGHT }
            // MP4 (H.264) prima: lo decodificano tutti i telefoni in hardware.
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { height(it.resolution) }.thenByDescending { it.format == MediaFormat.MPEG_4 })
            .distinctBy { height(it.resolution) }
        val video = videos.firstOrNull { maxHeight <= 0 || height(it.resolution) <= maxHeight } ?: videos.lastOrNull()
        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { height(it.resolution) }

        YtStream(
            id = id,
            title = info.name.orEmpty(),
            channel = info.uploaderName.orEmpty(),
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            videoUrl = video?.content,
            audioUrl = audio?.content,
            muxedUrl = muxed?.content,
            hlsUrl = info.hlsUrl?.takeIf { live && it.isNotBlank() },
            height = video?.let { height(it.resolution) } ?: muxed?.let { height(it.resolution) } ?: 0,
            qualities = videos.map { YtQuality(height(it.resolution), it.content) },
            durationSec = info.duration,
            chapters = info.streamSegments.orEmpty().map { YtChapter(it.title.orEmpty(), it.startTimeSeconds) },
            views = info.viewCount,
            uploaded = info.textualUploadDate.orEmpty(),
            channelAvatar = info.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(),
            subscribers = info.uploaderSubscriberCount,
            likes = info.likeCount,
            description = info.description?.content.orEmpty().let { raw ->
                if ('<' in raw) Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim() else raw
            },
        ).also {
            if (it.hlsUrl == null && it.muxedUrl == null && (it.videoUrl == null || it.audioUrl == null) && it.audioUrl == null) {
                throw IllegalStateException("Nessun flusso riproducibile per questo video")
            }
        }
    }

    /** I server dei video vogliono lo stesso "client" (parametro c=) con cui sono stati chiesti i flussi. */
    fun userAgentFor(url: String): String {
        val client = Regex("""[?&]c=([A-Za-z_]+)""").find(url)?.groupValues?.get(1).orEmpty().uppercase()
        return when {
            client.startsWith("ANDROID") -> "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip"
            client.startsWith("IOS") -> "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            else -> NewPipeDownloader.USER_AGENT
        }
    }

    private fun height(resolution: String?): Int = resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
}
