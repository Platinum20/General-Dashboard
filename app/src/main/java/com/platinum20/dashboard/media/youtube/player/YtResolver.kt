package com.platinum20.dashboard.media.youtube.player

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
)

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

    suspend fun resolve(link: String): YtStream = withContext(Dispatchers.IO) {
        init()
        val id = videoId(link) ?: throw IllegalArgumentException("Link di YouTube non valido")
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM

        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // L'audio M4A (AAC) va ovunque; a parità, il bitrate più alto.
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format == MediaFormat.M4A }.thenByDescending { it.averageBitrate })
            .firstOrNull()
        val video = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && height(it.resolution) in 1..MAX_HEIGHT }
            // MP4 (H.264) prima: lo decodificano tutti i telefoni in hardware.
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { height(it.resolution) }.thenByDescending { it.format == MediaFormat.MPEG_4 })
            .firstOrNull()
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
