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
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.NowPlaying
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtWatched
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
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
    /** Qualità che si sta vedendo davvero (in automatico cambia con la rete). */
    val playingHeight: Int = 0,
    /** Lingua dei sottotitoli accesi, null se spenti. */
    val subtitle: String? = null,
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
    /** Come si sta riproducendo: in caso di errore si scende al modo successivo. */
    private enum class Mode { AUTO, FIXED, MUXED }
    private var mode = Mode.AUTO
    /** Lingua dei sottotitoli scelta: resta accesa anche per i video successivi, se ce l'hanno. */
    private var subtitleLanguage: String? = null
    private var appContext: Context? = null
    private var lastSaved = 0L
    private var lastRemembered = 0L

    private val _now = MutableStateFlow<YtNow?>(null)
    val now: StateFlow<YtNow?> = _now.asStateFlow()

    private const val PREFS = "yt_player"
    private const val KEY_HEIGHT = "max_height"

    /** Qualità scelta dall'utente (altezza massima), ricordata; 0 = automatica, il valore iniziale. */
    fun preferredHeight(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HEIGHT, 0)

    /** In automatico il player sceglie la qualità più alta che la rete regge, senza fermarsi a caricare. */
    fun isAuto(): Boolean = mode == Mode.AUTO

    /** Cambia qualità al volo, dal punto in cui si era; 0 = automatica. */
    fun setQuality(context: Context, height: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_HEIGHT, height).apply()
        val stream = _now.value?.stream ?: return
        if (height == 0) {
            if (mode == Mode.AUTO || stream.dashManifest == null) return
            restart(context, stream, Mode.AUTO)
            return
        }
        val quality = stream.qualities.firstOrNull { it.height == height } ?: return
        if (quality.height == stream.height && mode == Mode.FIXED) return
        restart(context, stream.copy(videoUrl = quality.url, height = quality.height), Mode.FIXED)
    }

    /** Accende i sottotitoli in quella lingua (null = spenti), dal punto in cui si era. */
    fun setSubtitle(context: Context, subtitle: YtSubtitle?) {
        subtitleLanguage = subtitle?.language
        val stream = _now.value?.stream ?: return
        restart(context, stream, mode, subtitle)
    }

    private fun restart(context: Context, stream: YtStream, newMode: Mode, subtitle: YtSubtitle? = pickSubtitle(stream)) {
        val position = exo?.currentPosition ?: 0L
        val wasPlaying = exo?.playWhenReady ?: true
        start(context, stream, newMode, positionMs = position, play = wasPlaying, subtitle = subtitle)
    }

    /** I sottotitoli scelti, se il video li ha: prima quelli scritti a mano, poi gli automatici. */
    private fun pickSubtitle(stream: YtStream): YtSubtitle? {
        val language = subtitleLanguage ?: return null
        return stream.subtitles.filter { it.language.substringBefore('-') == language.substringBefore('-') }.minByOrNull { it.auto }
    }

    fun loop(): Boolean = exo?.repeatMode == Player.REPEAT_MODE_ONE

    fun setLoop(context: Context, on: Boolean) {
        player(context).repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
        // Si parte dalla qualità più alta e si scende solo se la rete non regge (stima iniziale alta).
        .setBandwidthMeter(DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(20_000_000).build())
        .setTrackSelector(
            DefaultTrackSelector(
                context,
                AdaptiveTrackSelection.Factory(
                    5_000,
                    AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                    AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                    0.85f,
                ),
            )
        )
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { player ->
            exo = player
            appContext = context.applicationContext
            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) next(context)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Un solo player alla volta: se parte YouTube si fermano Spotify e la radio.
                    if (isPlaying) NowPlaying.pauseOthers(null, radio = true, nativeYouTube = false)
                    // In pausa (o finito) si ricorda il punto, per "Riprendi" in Media e in Adesso.
                    else remember(context)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val stream = _now.value?.stream
                    val position = exo?.currentPosition ?: 0L
                    // Se l'automatico non va si passa alla qualità fissa, poi al flusso unico video+audio.
                    if (stream != null && mode == Mode.AUTO && stream.videoUrl != null && stream.audioUrl != null) {
                        start(context, stream, Mode.FIXED, positionMs = position, subtitle = pickSubtitle(stream))
                    } else if (stream != null && mode != Mode.MUXED && stream.muxedUrl != null) {
                        start(context, stream, Mode.MUXED, positionMs = position, subtitle = pickSubtitle(stream))
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
        // Il video di prima va in "Riprendi" con il suo punto, prima che _now parli del nuovo.
        remember(context)
        _now.value = YtNow(link = link, stream = null)
        context.startService(Intent(context, YtPlaybackService::class.java))
        job = scope.launch {
            runCatching { YtResolver.resolve(link, preferredHeight(context)) }
                .onSuccess {
                    val auto = preferredHeight(context) == 0 && it.dashManifest != null
                    YtHistory.init(context)
                    // Un link con t=... (anche quello di "Riprendi") parte da quel punto, se no da dove eri rimasto.
                    val from = YtResolver.startSeconds(link) * 1000
                    start(
                        context, it, if (auto) Mode.AUTO else Mode.FIXED,
                        positionMs = if (from > 0) from else YtHistory.resumeMs(it.id),
                        subtitle = pickSubtitle(it),
                    )
                    remember(context)
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
        remember(context)
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

    private fun start(
        context: Context,
        stream: YtStream,
        newMode: Mode,
        positionMs: Long = 0L,
        play: Boolean = true,
        subtitle: YtSubtitle? = null,
    ) {
        mode = newMode
        val metadata = MediaMetadata.Builder()
            .setTitle(stream.title)
            .setArtist(stream.channel)
            .setArtworkUri(Uri.parse(stream.thumbnail))
            .build()
        val item = { url: String -> MediaItem.Builder().setUri(url).setMediaId(stream.id).setMediaMetadata(metadata).build() }
        val muxed = newMode == Mode.MUXED
        val video: MediaSource = when {
            stream.hlsUrl != null -> HlsMediaSource.Factory(factory(stream.hlsUrl)).createMediaSource(item(stream.hlsUrl))
            newMode == Mode.AUTO && stream.dashManifest != null && stream.videoUrl != null -> {
                val base = Uri.parse("https://www.youtube.com/watch?v=${stream.id}")
                val manifest = DashManifestParser().parse(base, stream.dashManifest.byteInputStream())
                DashMediaSource.Factory(factory(stream.videoUrl)).createMediaSource(manifest, item(base.toString()))
            }
            !muxed && stream.videoUrl != null && stream.audioUrl != null -> MergingMediaSource(
                true,
                ProgressiveMediaSource.Factory(factory(stream.videoUrl)).createMediaSource(item(stream.videoUrl)),
                ProgressiveMediaSource.Factory(factory(stream.audioUrl)).createMediaSource(item(stream.audioUrl)),
            )
            stream.muxedUrl != null -> ProgressiveMediaSource.Factory(factory(stream.muxedUrl)).createMediaSource(item(stream.muxedUrl))
            else -> ProgressiveMediaSource.Factory(factory(stream.audioUrl!!)).createMediaSource(item(stream.audioUrl))
        }
        val source = if (subtitle == null) video else MergingMediaSource(
            video,
            SingleSampleMediaSource.Factory(factory(subtitle.url)).createMediaSource(
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(subtitle.language)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
                C.TIME_UNSET,
            ),
        )
        _now.value = YtNow(link = _now.value?.link ?: stream.id, stream = stream, loading = true, subtitle = subtitle?.language)
        player(context).apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredTextLanguage(subtitle?.language).build()
            setMediaSource(source, positionMs)
            prepare()
            playWhenReady = play
        }
    }

    private fun remember(context: Context) {
        val stream = _now.value?.stream ?: return
        // Il player sta ancora caricando questo video: la posizione è quella del video di prima.
        if (exo?.currentMediaItem?.mediaId != stream.id) return
        MediaHistory.init(context)
        lastRemembered = System.currentTimeMillis()
        MediaHistory.record(resumeEntry(stream, exo?.currentPosition ?: 0, exo?.duration ?: 0, lastRemembered))
    }

    /** La voce di "Riprendi" per [stream] arrivato a [positionMs]: funzione pura, provata nei test. */
    internal fun resumeEntry(stream: YtStream, positionMs: Long, playerDurationMs: Long, at: Long): ResumeItem {
        val live = stream.hlsUrl != null
        val duration = playerDurationMs.takeIf { it > 0 } ?: (stream.durationSec * 1000)
        val position = if (live) 0 else positionMs.coerceAtLeast(0)
        return ResumeItem(
            kind = ResumeKind.YOUTUBE,
            key = stream.id,
            title = stream.title,
            subtitle = stream.channel,
            image = stream.thumbnail,
            // Arrivato quasi in fondo: la prossima volta riparte dall'inizio.
            positionMs = if (duration > 0 && duration - position < 5_000) duration else position,
            durationMs = if (live) 0 else duration,
            at = at,
        )
    }

    private fun publish() {
        val p = exo ?: return
        val current = _now.value ?: return
        saveProgress(p, current.stream)
        // Anche mentre suona, ogni 30 secondi: se il telefono chiude l'app il punto non si perde.
        val context = appContext
        if (context != null && p.isPlaying && System.currentTimeMillis() - lastRemembered > 30_000) remember(context)
        _now.value = current.copy(
            playingHeight = p.videoFormat?.height ?: current.playingHeight,
            playing = p.isPlaying,
            loading = p.playbackState == Player.STATE_BUFFERING || (current.stream == null && current.error == null),
        )
    }

    /** Segna nella cronologia dove sei arrivato: ogni 5 secondi mentre guardi e a ogni pausa. */
    private fun saveProgress(p: ExoPlayer, stream: YtStream?) {
        val context = appContext ?: return
        if (stream == null || stream.hlsUrl != null || p.duration <= 0 || p.currentPosition <= 0) return
        val now = System.currentTimeMillis()
        if (p.isPlaying && now - lastSaved < 5_000) return
        lastSaved = now
        YtHistory.save(
            context,
            YtWatched(stream.id, stream.title, stream.channel, stream.channelAvatar, p.currentPosition, p.duration, now),
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
