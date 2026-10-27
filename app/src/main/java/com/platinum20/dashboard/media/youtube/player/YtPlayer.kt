package com.platinum20.dashboard.media.youtube.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtPremiere
import com.platinum20.dashboard.media.youtube.YtUpcomingException
import com.platinum20.dashboard.media.youtube.YtWatched
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import kotlinx.coroutines.MainScope
import com.platinum20.dashboard.media.youtube.SponsorBlock
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
    /** Solo audio (la musica di YouTube in Musica): niente video da scaricare né da mostrare. */
    val audioOnly: Boolean = false,
    /** Avviso breve da mostrare sotto il video (es. sottotitoli non disponibili). */
    val notice: String? = null,
    /** Punto e durata in millisecondi, per la barra del tempo del player unico. */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Anteprima non ancora iniziata: la schermata mostra l'attesa invece dell'errore. */
    val upcoming: YtUpcomingException? = null,
)

/**
 * YouTube nel player unico ([CanvasPlayer]): trova i flussi del video, li mette nel motore e tiene i
 * dettagli che servono alla schermata del video (qualità, sottotitoli, velocità). Il motore, la coda,
 * la notifica e il "finito, parte il prossimo" sono del player unico.
 */
@OptIn(UnstableApi::class)
object YtPlayer {
    private val scope = MainScope()
    private val exo: ExoPlayer? get() = CanvasPlayer.current()
    /** Come si sta riproducendo: in caso di errore si scende al modo successivo. */
    private enum class Mode { AUTO, FIXED, MUXED }
    private var mode = Mode.AUTO
    /** Lingua dei sottotitoli scelta: resta accesa anche per i video successivi, se ce l'hanno. */
    private var subtitleLanguage: String? = null
    private var lastSaved = 0L
    private var appContext: Context? = null

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

    /**
     * Accende i sottotitoli in quella lingua (null = spenti), dal punto in cui si era. Il file si
     * scarica prima: se YouTube non lo dà il video continua senza e compare un avviso.
     */
    fun setSubtitle(context: Context, subtitle: YtSubtitle?) {
        val stream = _now.value?.stream ?: return
        if (subtitle == null) {
            subtitleLanguage = null
            restart(context, stream, mode, null)
            return
        }
        scope.launch {
            if (prepareCaption(context, subtitle) == null) {
                _now.value = _now.value?.copy(notice = "Sottotitoli non disponibili per questo video")
                return@launch
            }
            subtitleLanguage = subtitle.language
            if (_now.value?.stream?.id == stream.id) restart(context, stream, mode, subtitle)
        }
    }

    fun consumeNotice() {
        _now.value = _now.value?.copy(notice = null)
    }

    /** I sottotitoli in un file locale (scaricati una volta per video e lingua), o null se mancano. */
    private suspend fun prepareCaption(context: Context, subtitle: YtSubtitle): Uri? {
        captionFiles[subtitle.url]?.let { return it }
        val text = YtCaptions.fetchVtt(subtitle) ?: return null
        val file = java.io.File(context.cacheDir, "yt-sub-${subtitle.url.hashCode().toUInt()}.vtt")
        runCatching { file.writeText(text) }.getOrElse { return null }
        return Uri.fromFile(file).also { captionFiles[subtitle.url] = it }
    }

    private val captionFiles = mutableMapOf<String, Uri>()

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
        CanvasPlayer.init(context)
        CanvasPlayer.seekTo(positionMs)
    }

    /** Avanti o indietro di [deltaMs] (doppio tocco sul video). */
    fun seekBy(context: Context, deltaMs: Long) {
        CanvasPlayer.init(context)
        CanvasPlayer.seekBy(deltaMs)
    }

    fun setSpeed(context: Context, speed: Float) {
        player(context).setPlaybackSpeed(speed)
    }

    fun speed(): Float = exo?.playbackParameters?.speed ?: 1f

    /** I video proposti dopo quello che si guarda (link): finita la coda, si continua con questi. */
    var queue: List<String> = emptyList()

    /** Il proposto dopo il video [id], se c'è. */
    internal fun suggestedAfter(id: String): String? {
        val index = queue.indexOfFirst { YtResolver.videoId(it) == id }
        return queue.getOrNull(index + 1)?.takeIf { index >= 0 }
    }

    /** Il motore del player unico (lo stesso per tutte le fonti). */
    fun player(context: Context): ExoPlayer = CanvasPlayer.player(context)

    /** Il motore di adesso, senza crearlo: chi lo mostra lo rilegge, perché dopo una chiusura ne nasce uno nuovo. */
    fun current(): ExoPlayer? = CanvasPlayer.current()

    /**
     * Fa partire [link] nel player unico. Se è il video già caricato non fa nulla: tornando sulla
     * schermata di un video messo in pausa resta in pausa. Se cambia solo il modo (video o solo audio)
     * continua dal punto in cui era. Con [autoplay] falso si carica fermo e aspetta il play.
     */
    fun play(context: Context, link: String, audioOnly: Boolean = false, autoplay: Boolean = true, title: String = "", subtitle: String = "", image: String = "") {
        val id = YtResolver.videoId(link) ?: return
        CanvasPlayer.init(context)
        val loaded = _now.value
        if (isLoaded(loaded, link) && CanvasPlayer.now.value != null) {
            val stream = loaded?.stream
            if (stream != null && loaded.audioOnly != audioOnly) {
                _now.value = loaded.copy(audioOnly = audioOnly)
                CanvasPlayer.updateCurrent { it.copy(kind = if (audioOnly) ResumeKind.MUSIC else ResumeKind.YOUTUBE) }
                restart(context, stream, mode)
            }
            return
        }
        val from = YtResolver.startSeconds(link) * 1000
        CanvasPlayer.play(
            context,
            ResumeItem(
                if (audioOnly) ResumeKind.MUSIC else ResumeKind.YOUTUBE, id, title, subtitle, image,
                positionMs = from, at = System.currentTimeMillis(),
            ),
            autoplay,
        )
    }

    /**
     * Mette il video [id] nel motore [p] (lo chiama il player unico). Restituisce i flussi trovati;
     * se YouTube non li dà, lancia l'errore (e la schermata del video lo mostra).
     */
    internal suspend fun load(context: Context, p: ExoPlayer, id: String, audioOnly: Boolean, autoplay: Boolean, startMs: Long?): YtStream {
        val link = "https://www.youtube.com/watch?v=$id"
        appContext = context.applicationContext
        _now.value = YtNow(link = link, stream = null, audioOnly = audioOnly)
        val stream = runCatching { YtResolver.resolve(link, preferredHeight(context)) }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Un'anteprima non ancora iniziata non è un errore: si aspetta col conto alla rovescia.
            val upcoming = YtPremiere.check(id)
            _now.value = YtNow(
                link, null, loading = false, audioOnly = audioOnly, upcoming = upcoming,
                error = upcoming?.message ?: "Non riesco ad aprire il video: ${e.message ?: "errore"}",
            )
            throw upcoming ?: e
        }
        val auto = preferredHeight(context) == 0 && stream.dashManifest != null
        YtHistory.init(context)
        if (!audioOnly) pickSubtitle(stream)?.let { sub -> prepareCaption(context, sub) }
        // Si riparte da dove eri rimasto (i video; la musica dall'inizio), se non è chiesto un punto preciso.
        val from = startMs?.takeIf { it > 0 } ?: if (audioOnly) 0 else YtHistory.resumeMs(stream.id)
        start(context, stream, if (auto) Mode.AUTO else Mode.FIXED, positionMs = from, play = autoplay, subtitle = pickSubtitle(stream), player = p)
        remember(p)
        SponsorBlock.attach(context, stream.id, live = stream.hlsUrl != null)
        return stream
    }

    /** Il motore ha dato errore: si prova il modo successivo di scaricare lo stesso video. */
    internal fun retry(context: Context, p: ExoPlayer): Boolean {
        val stream = _now.value?.stream ?: return false
        val position = p.currentPosition
        // Se l'automatico non va si passa alla qualità fissa, poi al flusso unico video+audio.
        return if (mode == Mode.AUTO && stream.videoUrl != null && stream.audioUrl != null) {
            start(context, stream, Mode.FIXED, positionMs = position, subtitle = pickSubtitle(stream))
            true
        } else if (mode != Mode.MUXED && stream.muxedUrl != null) {
            start(context, stream, Mode.MUXED, positionMs = position, subtitle = pickSubtitle(stream))
            true
        } else {
            _now.value = _now.value?.copy(loading = false, playing = false, error = "Video non riproducibile, riprova più tardi")
            false
        }
    }

    /** Suona altro: i dettagli del video non servono più. */
    internal fun detach() {
        SponsorBlock.detach()
        _now.value = null
    }

    fun toggle(context: Context) {
        CanvasPlayer.init(context)
        CanvasPlayer.toggle()
    }

    /** Avanti: la voce dopo nella coda, se no il primo video proposto. */
    fun next(context: Context) {
        CanvasPlayer.init(context)
        if (CanvasPlayer.next()) return
        val current = _now.value?.stream?.id ?: return
        suggestedAfter(current)?.let { play(context, it) }
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

    private fun start(
        context: Context,
        stream: YtStream,
        newMode: Mode,
        positionMs: Long = 0L,
        play: Boolean = true,
        subtitle: YtSubtitle? = null,
        player: ExoPlayer = player(context),
    ) {
        mode = newMode
        val metadata = MediaMetadata.Builder()
            .setTitle(stream.title)
            .setArtist(stream.channel)
            .setArtworkUri(Uri.parse(stream.thumbnail))
            .build()
        val item = { url: String -> MediaItem.Builder().setUri(url).setMediaId(stream.id).setMediaMetadata(metadata).build() }
        val muxed = newMode == Mode.MUXED
        val audioOnly = _now.value?.audioOnly == true
        val video: MediaSource = when {
            audioOnly && stream.hlsUrl == null && stream.audioUrl != null ->
                ProgressiveMediaSource.Factory(factory(stream.audioUrl)).createMediaSource(item(stream.audioUrl))
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
        // Solo il file già scaricato: un sottotitolo che non si carica non deve fermare il video.
        val caption = subtitle?.let { captionFiles[it.url] }?.takeIf { !audioOnly }
        val source = if (subtitle == null || caption == null) video else MergingMediaSource(
            video,
            SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context))
                .setTreatLoadErrorsAsEndOfStream(true)
                .createMediaSource(
                MediaItem.SubtitleConfiguration.Builder(caption)
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(subtitle.language)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
                C.TIME_UNSET,
            ),
        )
        _now.value = YtNow(
            link = _now.value?.link ?: stream.id,
            stream = stream,
            loading = true,
            subtitle = subtitle?.language?.takeIf { caption != null },
            audioOnly = audioOnly,
        )
        player.apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage(subtitle?.language?.takeIf { caption != null })
                // In solo audio il player non scarica il video.
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
                .build()
            setMediaSource(source, positionMs)
            prepare()
            playWhenReady = play
        }
    }

    /** Segna il video in "Continua" con il punto a cui si è (lo chiama il player unico). */
    internal fun remember(p: ExoPlayer) {
        val stream = _now.value?.stream ?: return
        // Il player sta ancora caricando questo video: la posizione è quella del video di prima.
        if (p.currentMediaItem?.mediaId != stream.id) return
        MediaHistory.record(resumeEntry(stream, p.currentPosition, p.duration, System.currentTimeMillis(), _now.value?.audioOnly == true))
    }

    /** La voce di "Riprendi" per [stream] arrivato a [positionMs]: funzione pura, provata nei test. */
    internal fun resumeEntry(stream: YtStream, positionMs: Long, playerDurationMs: Long, at: Long, audioOnly: Boolean = false): ResumeItem {
        val live = stream.hlsUrl != null
        val duration = playerDurationMs.takeIf { it > 0 } ?: (stream.durationSec * 1000)
        val position = if (live) 0 else positionMs.coerceAtLeast(0)
        return ResumeItem(
            // Ascoltato come musica: in "Continua" riparte in solo audio, nel player della musica.
            kind = if (audioOnly) ResumeKind.MUSIC else ResumeKind.YOUTUBE,
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

    /** Il motore è cambiato: si aggiornano i dettagli del video (lo chiama il player unico). */
    internal fun publish(p: ExoPlayer) {
        val current = _now.value ?: return
        saveProgress(p, current.stream)
        _now.value = current.copy(
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 } ?: current.durationMs,
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
