package com.platinum20.dashboard.media.youtube.player

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
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
    /** Manifest DASH con tutte le qualità: il player sceglie da solo la migliore che la rete regge. */
    val dashManifest: String? = null,
    val subtitles: List<YtSubtitle> = emptyList(),
    /** Video correlati, come li propone YouTube sotto al video. */
    val related: List<YtVideo> = emptyList(),
)

data class YtQuality(val height: Int, val url: String)

data class YtChapter(val title: String, val startSec: Int)

data class YtSubtitle(val language: String, val label: String, val url: String, val auto: Boolean)

data class YtComment(
    val id: String,
    val author: String,
    val text: String,
    val likes: Int,
    val date: String,
    val avatar: String,
    val replies: Int,
    /** Pagina delle risposte (null se non ce ne sono). */
    val repliesPage: Page? = null,
    val pinned: Boolean = false,
    val hearted: Boolean = false,
)

data class YtCommentPage(val comments: List<YtComment>, val next: Page?)

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
            .mapNotNull(::video)
    }

    /**
     * Commenti del video come li ordina YouTube: senza [page] la prima pagina, con [page] la successiva
     * oppure le risposte a un commento (la pagina presa da [YtComment.repliesPage]).
     */
    suspend fun comments(id: String, page: Page? = null): YtCommentPage = withContext(Dispatchers.IO) {
        init()
        val url = "https://www.youtube.com/watch?v=$id"
        if (page == null) {
            val info = CommentsInfo.getInfo(ServiceList.YouTube, url)
            YtCommentPage(info.relatedItems.map(::comment), info.nextPage)
        } else {
            val more = CommentsInfo.getMoreItems(ServiceList.YouTube, url, page)
            YtCommentPage(more.items.map(::comment), more.nextPage)
        }
    }

    private fun comment(c: CommentsInfoItem): YtComment {
        val raw = c.commentText?.content.orEmpty()
        return YtComment(
            id = c.commentId.orEmpty(),
            author = c.uploaderName.orEmpty(),
            text = if ('<' in raw) Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim() else raw,
            likes = c.likeCount.coerceAtLeast(0),
            date = c.textualUploadDate.orEmpty(),
            avatar = c.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(),
            replies = c.replyCount.coerceAtLeast(0),
            repliesPage = c.replies,
            pinned = c.isPinned,
            hearted = c.isHeartedByUploader,
        )
    }

    private fun video(item: StreamInfoItem): YtVideo? {
        val id = videoId(item.url) ?: return null
        return YtVideo(
            id = id,
            title = item.name,
            channel = item.uploaderName.orEmpty(),
            channelId = "",
            published = null,
            isShort = item.isShortFormContent,
            durationSec = item.duration.toInt().coerceAtLeast(0),
            live = item.streamType == StreamType.LIVE_STREAM,
            channelAvatar = item.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty(),
            views = item.viewCount,
            uploadedText = item.textualUploadDate.orEmpty(),
        )
    }

    /** [maxHeight]: qualità massima scelta (0 = la più alta fino a 1080p). */
    suspend fun resolve(link: String, maxHeight: Int = 0): YtStream = withContext(Dispatchers.IO) {
        init()
        val id = videoId(link) ?: throw IllegalArgumentException("Link di YouTube non valido")
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM

        val audios = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // Video con più lingue: la traccia originale, mai un doppiaggio (prima a volte partiva in tedesco).
        val original = audios.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }.ifEmpty { audios }
        val audio = original
            // L'audio M4A (AAC) va ovunque; a parità, il bitrate più alto.
            .sortedWith(compareByDescending<AudioStream> { it.format == MediaFormat.M4A }.thenByDescending { it.averageBitrate })
            .firstOrNull()
        val videos = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && height(it.resolution) in 1..MAX_HEIGHT }
            // MP4 (H.264) prima: lo decodificano tutti i telefoni in hardware.
            .sortedWith(compareByDescending<VideoStream> { height(it.resolution) }.thenByDescending { it.format == MediaFormat.MPEG_4 })
            .distinctBy { height(it.resolution) }
        val video = videos.firstOrNull { maxHeight <= 0 || height(it.resolution) <= maxHeight } ?: videos.lastOrNull()
        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { height(it.resolution) }
        val subtitles = info.subtitles.orEmpty()
            .filter { it.isUrl && it.content.isNotBlank() }
            .map { sub ->
                val name = sub.locale?.getDisplayLanguage(java.util.Locale.ITALIAN)?.takeIf { it.isNotBlank() }
                    ?.replaceFirstChar { it.uppercase() } ?: sub.languageTag
                YtSubtitle(
                    language = sub.languageTag,
                    label = if (sub.isAutoGenerated) "$name (automatici)" else name,
                    url = sub.content.replace(Regex("&fmt=[^&]*"), "") + "&fmt=vtt",
                    auto = sub.isAutoGenerated,
                )
            }
            .sortedWith(compareBy<YtSubtitle> { it.auto }.thenBy { it.label })

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
            dashManifest = if (live) null else dashManifest(info.videoOnlyStreams, audio, info.duration),
            subtitles = subtitles,
            related = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::video),
        ).also {
            if (it.hlsUrl == null && it.muxedUrl == null && (it.videoUrl == null || it.audioUrl == null) && it.audioUrl == null) {
                throw IllegalStateException("Nessun flusso riproducibile per questo video")
            }
        }
    }

    /**
     * Manifest DASH costruito dai flussi MP4 (H.264) di ogni qualità più l'audio scelto: con questo
     * ExoPlayer parte dalla qualità più alta e scende solo se la rete non ce la fa (come l'app di YouTube).
     * Null se mancano gli indici dei file (servono per saltare nel video) o c'è una sola qualità.
     */
    private fun dashManifest(videoOnly: List<VideoStream>, audio: AudioStream?, durationSec: Long): String? {
        if (audio == null || durationSec <= 0 || audio.format != MediaFormat.M4A || !hasIndex(audio.initEnd, audio.indexEnd)) return null
        val videos = videoOnly
            .filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 &&
                    height(it.resolution) in 1..MAX_HEIGHT && hasIndex(it.initEnd, it.indexEnd) && it.bitrate > 0
            }
            .sortedByDescending { height(it.resolution) }
            .distinctBy { height(it.resolution) }
        if (videos.size < 2) return null
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        out.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\"")
        out.append(" type=\"static\" minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"PT${durationSec}S\"><Period>")
        out.append("<AdaptationSet id=\"0\" contentType=\"video\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\">")
        videos.forEach { v ->
            val h = height(v.resolution)
            val w = v.width.takeIf { it > 0 } ?: (h * 16 / 9)
            out.append("<Representation id=\"v${v.itag}\" codecs=\"${xml(v.codec ?: "avc1.4d401f")}\" bandwidth=\"${v.bitrate}\" width=\"$w\" height=\"$h\"")
            if (v.fps > 0) out.append(" frameRate=\"${v.fps}\"")
            out.append(">").append(segment(v.content, v.initStart, v.initEnd, v.indexStart, v.indexEnd)).append("</Representation>")
        }
        out.append("</AdaptationSet>")
        out.append("<AdaptationSet id=\"1\" contentType=\"audio\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\">")
        out.append("<Representation id=\"a${audio.itag}\" codecs=\"${xml(audio.codec ?: "mp4a.40.2")}\" bandwidth=\"${audio.bitrate.takeIf { it > 0 } ?: 128_000}\">")
        out.append(segment(audio.content, audio.initStart, audio.initEnd, audio.indexStart, audio.indexEnd)).append("</Representation>")
        out.append("</AdaptationSet></Period></MPD>")
        return out.toString()
    }

    private fun segment(url: String, initStart: Int, initEnd: Int, indexStart: Int, indexEnd: Int) =
        "<BaseURL>${xml(url)}</BaseURL><SegmentBase indexRange=\"$indexStart-$indexEnd\">" +
            "<Initialization range=\"$initStart-$initEnd\"/></SegmentBase>"

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun hasIndex(initEnd: Int, indexEnd: Int) = initEnd > 0 && indexEnd > initEnd

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
