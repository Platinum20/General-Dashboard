package com.platinum20.dashboard.media.youtube.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.platinum20.dashboard.media.NowPlaying
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cosa sta suonando nel player nativo di YouTube. */
data class YtNow(
    val link: String,
    val stream: YtStream?,
    val playing: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Player nativo di YouTube (ExoPlayer, come la radio e i video del telefono): un solo player
 * per tutta l'app, con notifica e comandi in cuffia dal servizio [YtPlaybackService].
 */
@OptIn(UnstableApi::class)
object YtPlayer {
    private val scope = MainScope()
    private var exo: ExoPlayer? = null
    private var job: Job? = null
    private var usingMuxed = false

    private val _now = MutableStateFlow<YtNow?>(null)
    val now: StateFlow<YtNow?> = _now.asStateFlow()

    private const val PREFS = "yt_player"
    private const val KEY_HEIGHT = "max_height"

    /** Qualità scelta dall'utente (altezza massima, 0 = automatica fino a 1080p), ricordata. */
    fun preferredHeight(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HEIGHT, 0)

    /** Cambia qualità al volo, dal punto in cui si era. */
    fun setQuality(context: Context, height: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_HEIGHT, height).apply()
        val stream = _now.value?.stream ?: return
        val quality = stream.qualities.firstOrNull { it.height == height } ?: return
        if (quality.height == stream.height && !usingMuxed) return
        val position = exo?.currentPosition ?: 0L
        val wasPlaying = exo?.playWhenReady ?: true
        usingMuxed = false
        start(context, stream.copy(videoUrl = quality.url, height = quality.height), muxed = false, positionMs = position, play = wasPlaying)
    }

    fun seekTo(context: Context, positionMs: Long) {
        player(context).seekTo(positionMs)
    }

    /** Avanti o indietro di [deltaMs] (doppio tocco sul video). */
    fun seekBy(context: Context, deltaMs: Long) {
        val p = player(context)
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun setSpeed(context: Context, speed: Float) {
        player(context).setPlaybackSpeed(speed)
    }

    fun speed(): Float = exo?.playbackParameters?.speed ?: 1f

    /** Video da far partire uno dopo l'altro (link), impostati dalla schermata del player. */
    var queue: List<String> = emptyList()

    // Sempre il contesto dell'applicazione: il listener vive quanto il player e non deve tenere
    // in memoria un'Activity (né usarla dopo che è stata chiusa, a fine video).
    fun player(context: Context): ExoPlayer = exo ?: newPlayer(context.applicationContext)

    private fun newPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { player ->
            exo = player
            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) next(context)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Un solo player alla volta: se parte YouTube si fermano Spotify e la radio.
                    if (isPlaying) NowPlaying.pauseOthers(null, radio = true, nativeYouTube = false)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val stream = _now.value?.stream
                    // Se l'unione video+audio non va, si riprova con il flusso unico.
                    if (stream != null && !usingMuxed && stream.muxedUrl != null) {
                        usingMuxed = true
                        start(context, stream, muxed = true, positionMs = exo?.currentPosition ?: 0L)
                    } else {
                        _now.value = _now.value?.copy(loading = false, playing = false, error = "Video non riproducibile, riprova più tardi")
                    }
                }
            })
        }

    /**
     * Apre [link] nel player. Se è il video già caricato non fa nulla: tornando sulla schermata di un
     * video messo in pausa resta in pausa (si riprende con il tasto play).
     */
    fun play(context: Context, link: String) {
        if (isLoaded(_now.value, link)) return
        NowPlaying.pauseOthers(null, radio = true, nativeYouTube = false)
        job?.cancel()
        _now.value = YtNow(link = link, stream = null)
        context.startService(Intent(context, YtPlaybackService::class.java))
        job = scope.launch {
            runCatching { YtResolver.resolve(link, preferredHeight(context)) }
                .onSuccess {
                    usingMuxed = false
                    start(context, it, muxed = false)
                }
                .onFailure { _now.value = YtNow(link, null, loading = false, error = "Non riesco ad aprire il video: ${it.message ?: "errore"}") }
        }
    }

    fun toggle(context: Context) {
        val p = player(context)
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() {
        exo?.takeIf { it.isPlaying }?.pause()
    }

    fun stop(context: Context) {
        job?.cancel()
        exo?.stop()
        exo?.clearMediaItems()
        _now.value = null
        context.stopService(Intent(context, YtPlaybackService::class.java))
    }

    fun next(context: Context) {
        val current = _now.value?.stream?.id
        val index = queue.indexOfFirst { YtResolver.videoId(it) == current }
        queue.getOrNull(index + 1)?.let { play(context, it) }
    }

    /** Il video di [link] è già nel player e senza errori (anche se in pausa o ancora in caricamento). */
    internal fun isLoaded(now: YtNow?, link: String): Boolean {
        val id = YtResolver.videoId(link) ?: return false
        if (now == null || now.error != null) return false
        return now.stream?.id == id || (now.stream == null && YtResolver.videoId(now.link) == id)
    }

    /** Video finto per le foto automatiche delle schermate (solo test). */
    internal fun preview(stream: YtStream) {
        _now.value = YtNow(link = "https://www.youtube.com/watch?v=${stream.id}", stream = stream, playing = true, loading = false)
    }

    internal fun release() {
        job?.cancel()
        exo?.release()
        exo = null
        _now.value = null
    }

    private fun start(context: Context, stream: YtStream, muxed: Boolean, positionMs: Long = 0L, play: Boolean = true) {
        val metadata = MediaMetadata.Builder()
            .setTitle(stream.title)
            .setArtist(stream.channel)
            .setArtworkUri(Uri.parse(stream.thumbnail))
            .build()
        val item = { url: String -> MediaItem.Builder().setUri(url).setMediaId(stream.id).setMediaMetadata(metadata).build() }
        val source: MediaSource = when {
            stream.hlsUrl != null -> HlsMediaSource.Factory(factory(stream.hlsUrl)).createMediaSource(item(stream.hlsUrl))
            !muxed && stream.videoUrl != null && stream.audioUrl != null -> MergingMediaSource(
                true,
                ProgressiveMediaSource.Factory(factory(stream.videoUrl)).createMediaSource(item(stream.videoUrl)),
                ProgressiveMediaSource.Factory(factory(stream.audioUrl)).createMediaSource(item(stream.audioUrl)),
            )
            stream.muxedUrl != null -> ProgressiveMediaSource.Factory(factory(stream.muxedUrl)).createMediaSource(item(stream.muxedUrl))
            else -> ProgressiveMediaSource.Factory(factory(stream.audioUrl!!)).createMediaSource(item(stream.audioUrl))
        }
        _now.value = YtNow(link = _now.value?.link ?: stream.id, stream = stream, loading = true)
        player(context).apply {
            setMediaSource(source, positionMs)
            prepare()
            playWhenReady = play
        }
    }

    private fun publish() {
        val p = exo ?: return
        val current = _now.value ?: return
        _now.value = current.copy(
            playing = p.isPlaying,
            loading = p.playbackState == Player.STATE_BUFFERING || (current.stream == null && current.error == null),
        )
    }

    /** I server dei video di YouTube vogliono lo stesso "client" con cui sono stati chiesti i flussi. */
    private fun factory(url: String): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(YtResolver.userAgentFor(url))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
    }
}
