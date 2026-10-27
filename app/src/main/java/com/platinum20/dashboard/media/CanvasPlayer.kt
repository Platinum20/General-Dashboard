package com.platinum20.dashboard.media

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.media.spotify.SpotifyPlayer.asQueueItem
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.news.RecapSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

/** Quello che il player unico sta suonando, uguale per tutte le fonti. */
data class EngineNow(
    /** La voce della coda che suona. */
    val entryId: Long,
    val item: ResumeItem,
    val playing: Boolean = false,
    val loading: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    /** Radio: il brano annunciato dalla stazione, se lo manda. */
    val streamTitle: String = "",
    /** Una diretta di YouTube: niente barra del tempo. */
    val liveStream: Boolean = false,
) {
    /** Ha un'immagine in movimento: il player la mostra al posto della copertina. */
    val hasVideo: Boolean get() = item.kind == ResumeKind.YOUTUBE || item.kind == ResumeKind.VIDEO
    val live: Boolean get() = item.kind == ResumeKind.RADIO || liveStream
}

/** Lo stato di un riproduttore fuori da ExoPlayer (Spotify, che è protetto e suona nel suo). */
data class ExternalState(
    val playing: Boolean,
    val loading: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val error: String? = null,
)

/**
 * Un riproduttore che non è ExoPlayer: il player unico gli passa la voce, lo comanda e ascolta il
 * suo stato. Chiama [CanvasPlayer.externalEnded] quando il brano finisce da solo.
 */
interface ExternalBackend {
    val state: StateFlow<ExternalState?>
    fun play(context: Context, item: ResumeItem, startMs: Long, autoplay: Boolean)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
}

/**
 * Il player unico dell'app. Un solo motore (ExoPlayer) e una sola coda per YouTube, la musica, la
 * radio, i podcast e i video del telefono: la coda non è una copia accanto ai player, è quella che
 * il motore suona. Ogni voce è prima di tutto audio; se ha anche il video, il player lo mostra
 * quando è aperto (nel mini player e a schermo spento continua solo l'audio).
 * Spotify è protetto e suona nel suo riproduttore ([ExternalBackend]), ma è una voce della stessa
 * coda: il player unico lo fa partire, lo comanda e passa alla voce dopo quando finisce.
 */
@OptIn(UnstableApi::class)
object CanvasPlayer {
    private const val PREFS = "play_queue"
    private const val KEY = "queue"
    private const val KEY_CURRENT = "queue_current"
    private const val KEY_AUTOFILL = "queue_autofill"

    private val scope = MainScope()
    private var exo: ExoPlayer? = null
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var nextId = 1L
    private var loadJob: Job? = null
    private var ticker: Job? = null
    private var externalJob: Job? = null
    /** Voce già chiusa per "finito": un secondo avviso dello stesso finale non fa saltare due voci. */
    private var endedId = -1L
    private var lastSaved = 0L

    /** Chi suona le voci di Spotify (sostituibile nei test). */
    internal var spotify: ExternalBackend? = SpotifyPlayer

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val _now = MutableStateFlow<EngineNow?>(null)
    val now: StateFlow<EngineNow?> = _now.asStateFlow()

    private val _autoFill = MutableStateFlow(true)
    /** "Riempi in automatico": a coda di Spotify quasi finita si aggiungono altri tuoi brani. */
    val autoFill: StateFlow<Boolean> = _autoFill.asStateFlow()
    private var filling: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            val entries = runCatching {
                val array = JSONArray(p.getString(KEY, "[]"))
                (0 until array.length()).mapNotNull { i -> runCatching { ResumeItem.fromJson(array.getJSONObject(i)) }.getOrNull() }
            }.getOrDefault(emptyList()).mapIndexed { i, it -> QueueEntry(i + 1L, it) }
            nextId = entries.size + 1L
            _queue.value = QueueState(entries, p.getInt(KEY_CURRENT, -1).coerceIn(-1, entries.size - 1))
            _autoFill.value = p.getBoolean(KEY_AUTOFILL, true)
        }
    }

    private fun context(): Context = appContext ?: error("CanvasPlayer.init non chiamato")

    // ---------------------------------------------------------------- coda

    /** Suona [item] adesso: entra subito dopo quella che suonava e lo diventa. */
    fun play(context: Context, item: ResumeItem, autoplay: Boolean = true) {
        init(context)
        setQueue(QueueOps.playNow(_queue.value, item, id()))
        load(autoplay, item.positionMs.takeIf { item.unfinished })
    }

    /** Una lista (album, playlist, risultati) da [start]: sostituisce quello che veniva dopo. */
    fun playList(context: Context, items: List<ResumeItem>, start: Int = 0) {
        if (items.isEmpty()) return
        init(context)
        val first = nextId
        nextId += items.size
        setQueue(QueueOps.playList(_queue.value, items, start, first))
        load(true, null)
    }

    /** In fondo alla coda. Se non suona niente (o la coda era finita), parte subito. */
    fun add(item: ResumeItem) {
        setQueue(QueueOps.add(_queue.value, item, id()))
        val n = _now.value
        if (appContext != null && (n == null || endedId == n.entryId)) next()
    }

    /** Subito dopo quella che suona. */
    fun addNext(item: ResumeItem) {
        setQueue(QueueOps.addNext(_queue.value, item, id()))
    }

    fun move(id: Long, by: Int) = setQueue(QueueOps.move(_queue.value, id, by))

    /** Sposta la voce [id] al posto della voce [target] (trascinandola nella coda). */
    fun moveTo(id: Long, target: Long) {
        val entries = _queue.value.entries
        val from = entries.indexOfFirst { it.id == id }
        val to = entries.indexOfFirst { it.id == target }
        if (from >= 0 && to >= 0) move(id, to - from)
    }

    fun clearUpcoming() = setQueue(QueueOps.clearUpcoming(_queue.value))

    /** Toglie una voce; se era quella che suonava parte la successiva (o il player si ferma). */
    fun remove(id: Long) {
        val playing = _queue.value.now?.id == id
        setQueue(QueueOps.remove(_queue.value, id))
        if (!playing) return
        if (!next()) stop()
    }

    /** Fa partire una voce della coda: esce da "Dopo", le altre restano dove sono. */
    fun jump(id: Long) {
        setQueue(QueueOps.pick(_queue.value, id))
        load(true, null)
    }

    /** "Annulla" dopo averla tolta: torna al suo posto. */
    fun restore(entry: QueueEntry, index: Int) = setQueue(QueueOps.restore(_queue.value, entry, index))

    fun setAutoFill(enabled: Boolean) {
        _autoFill.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTOFILL, enabled)?.apply()
        if (enabled) topUp() else filling?.cancel()
    }

    /** Coda di Spotify quasi finita: altri tuoi brani (più ascoltati, salvati, recenti) in fondo. */
    private fun topUp() {
        if (!_autoFill.value || filling?.isActive == true || !QueueFill.needed(_queue.value)) return
        if (!SpotifyAuth.connected.value) return
        filling = scope.launch {
            SpotifyStore.refreshHome()
            SpotifyStore.refreshLibrary()
            // Prima volta dall'avvio: si aspetta che arrivino (dalla copia salvata o dalla rete).
            val home = withTimeoutOrNull(15_000) { SpotifyStore.home.first { it.data != null || (!it.loading && it.error != null) } }?.data
            val library = withTimeoutOrNull(5_000) { SpotifyStore.library.first { it.data != null || (!it.loading && it.error != null) } }?.data
            val pool = (home?.topTracks.orEmpty() + library?.liked.orEmpty() + home?.recent.orEmpty()).filter { it.kind == SpKind.TRACK }.map { it.asQueueItem() }
            val state = _queue.value
            if (!_autoFill.value || !QueueFill.needed(state)) return@launch
            var filled = state
            QueueFill.pick(state, pool, System.currentTimeMillis()).forEach { filled = QueueOps.add(filled, it, id()) }
            if (filled === state) return@launch
            setQueue(filled)
            // Arrivati quando la coda era già finita: si riparte da soli.
            val n = _now.value
            if (n != null && endedId == n.entryId) next()
        }
    }

    /** Avanti nella coda, se c'è qualcosa dopo. */
    fun next(): Boolean {
        val s = _queue.value
        if (!s.hasNext) return false
        setQueue(s.copy(current = s.current + 1))
        load(true, null)
        return true
    }

    /** Indietro nella coda, se c'è qualcosa prima. */
    fun previous(): Boolean {
        val s = _queue.value
        if (!s.hasPrevious) return false
        setQueue(s.copy(current = s.current - 1))
        load(true, null)
        return true
    }

    /** Cambia la voce che suona (es. il titolo arrivato da YouTube, o da video a solo audio). */
    internal fun updateCurrent(change: (ResumeItem) -> ResumeItem) {
        val s = _queue.value
        val entry = s.now ?: return
        val item = change(entry.item)
        if (item == entry.item) return
        setQueue(s.copy(entries = s.entries.toMutableList().also { it[s.current] = entry.copy(item = item) }))
        _now.update { it?.takeIf { n -> n.entryId == entry.id }?.copy(item = item) }
    }

    private fun setQueue(state: QueueState) {
        val trimmed = QueueOps.trim(state)
        _queue.value = trimmed
        val array = JSONArray()
        trimmed.entries.forEach { array.put(it.item.toJson()) }
        prefs?.edit()?.putString(KEY, array.toString())?.putInt(KEY_CURRENT, trimmed.current)?.apply()
        topUp()
    }

    private fun id(): Long = nextId++

    // ---------------------------------------------------------------- comandi

    fun player(context: Context): ExoPlayer {
        init(context)
        return exo ?: build(context.applicationContext)
    }

    /** Il motore di adesso, senza crearlo. */
    fun current(): ExoPlayer? = exo

    private fun external(): ExternalBackend? = spotify.takeIf { _now.value?.item?.kind == ResumeKind.SPOTIFY }

    fun toggle() {
        val n = _now.value
        if (n == null) {
            // Niente in riproduzione ma la coda ha una voce: riparte quella.
            if (_queue.value.now != null) load(true, null)
            return
        }
        external()?.let { if (n.playing) it.pause() else { pauseOthers(); it.resume() }; return }
        val p = exo ?: return load(true, null)
        when {
            p.isPlaying -> p.pause()
            n.error != null || p.playbackState == Player.STATE_IDLE -> load(true, n.positionMs.takeIf { it > 0 })
            else -> {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
        }
    }

    fun pause() {
        if (external() != null) return external()!!.pause()
        exo?.takeIf { it.playWhenReady }?.pause()
    }

    fun seekTo(positionMs: Long) {
        external()?.let { return it.seekTo(positionMs) }
        val p = exo ?: return
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo(positionMs.coerceIn(0, duration))
        publish()
    }

    fun seekBy(deltaMs: Long) {
        val position = external()?.state?.value?.positionMs ?: exo?.currentPosition ?: return
        seekTo(position + deltaMs)
    }

    /** Volume del motore (0-1), per la sveglia della radio che parte piano. */
    fun setVolume(volume: Float) {
        exo?.volume = volume.coerceIn(0f, 1f)
    }

    /** Ferma e chiude: il mini player sparisce, la coda resta. */
    fun stop() {
        saveProgress()
        loadJob?.cancel()
        externalJob?.cancel()
        spotify?.takeIf { _now.value?.item?.kind == ResumeKind.SPOTIFY }?.stop()
        YtPlayer.detach()
        exo?.stop()
        exo?.clearMediaItems()
        ticker?.cancel()
        _now.value = null
        appContext?.let { it.stopService(Intent(it, PlayerService::class.java)) }
    }

    /** Il brano di un riproduttore esterno è finito da solo. */
    fun externalEnded() {
        scope.launch { ended() }
    }

    // ---------------------------------------------------------------- caricamento

    /** Fa suonare la voce corrente della coda, da [startMs] (null: dal punto giusto per la fonte). */
    private fun load(autoplay: Boolean, startMs: Long?) {
        val entry = _queue.value.now ?: return stop()
        val context = context()
        saveProgress()
        loadJob?.cancel()
        externalJob?.cancel()
        val wasExternal = external()
        val item = entry.item
        if (wasExternal != null && item.kind != ResumeKind.SPOTIFY) wasExternal.stop()
        if (item.kind != ResumeKind.YOUTUBE && item.kind != ResumeKind.MUSIC) YtPlayer.detach()
        endedId = -1
        if (autoplay) pauseOthers()
        val p = player(context)
        p.stop()
        p.clearMediaItems()
        // "Ripeti" vale per il video in cui lo si accende, non per la coda.
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, item.kind == ResumeKind.MUSIC || item.kind == ResumeKind.PODCAST || item.kind == ResumeKind.RADIO)
            .build()
        _now.value = EngineNow(entry.id, item, loading = true, durationMs = item.durationMs)
        // Il servizio c'è già se qualcosa suonava; da fermi e in sottofondo Android può rifiutarlo.
        runCatching { context.startService(Intent(context, PlayerService::class.java)) }
        MediaHistory.init(context)
        loadJob = scope.launch {
            try {
                when (item.kind) {
                    ResumeKind.YOUTUBE, ResumeKind.MUSIC -> {
                        val stream = YtPlayer.load(context, p, item.key, item.kind == ResumeKind.MUSIC, autoplay, startMs)
                        if (stream.hlsUrl != null) _now.update { it?.takeIf { n -> n.entryId == entry.id }?.copy(liveStream = true) }
                        updateCurrent {
                            it.copy(
                                title = stream.title.ifBlank { it.title },
                                subtitle = stream.channel.removeSuffix(" - Topic").ifBlank { it.subtitle },
                                image = stream.thumbnail.ifBlank { it.image },
                                durationMs = if (stream.hlsUrl != null) 0 else stream.durationSec * 1000,
                            )
                        }
                    }
                    ResumeKind.SPOTIFY -> playExternal(context, entry.id, item, startMs ?: 0, autoplay)
                    ResumeKind.RADIO -> {
                        val station = item.station ?: error("Stazione mancante")
                        // Il titolo resta libero per il brano annunciato dalla stazione (metadati ICY).
                        set(p, station.streamUrl, MediaMetadata.Builder().setArtist(station.name).setStation(station.name).artwork(station.logo), 0, autoplay)
                    }
                    ResumeKind.PODCAST -> {
                        PodcastStore.init(context)
                        val from = startMs ?: PodcastStore.resumeMs(item.key, item.durationMs)
                        set(p, item.link, MediaMetadata.Builder().setTitle(item.title).setArtist(item.subtitle).artwork(item.image), from, autoplay)
                    }
                    ResumeKind.VIDEO -> set(p, item.key, MediaMetadata.Builder().setTitle(item.title).artwork(item.image), startMs ?: 0, autoplay)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _now.update { it?.takeIf { n -> n.entryId == entry.id }?.copy(loading = false, playing = false, error = message(item, e)) }
            }
        }
    }

    private fun MediaMetadata.Builder.artwork(url: String) = apply { if (url.isNotBlank()) setArtworkUri(Uri.parse(url)) }

    private fun set(p: ExoPlayer, uri: String, metadata: MediaMetadata.Builder, startMs: Long, autoplay: Boolean) {
        require(uri.isNotBlank()) { "Indirizzo mancante" }
        p.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(metadata.build()).build(), startMs)
        p.prepare()
        p.playWhenReady = autoplay
    }

    private fun playExternal(context: Context, entryId: Long, item: ResumeItem, startMs: Long, autoplay: Boolean) {
        val backend = spotify ?: error("Spotify non è disponibile")
        backend.play(context, item, startMs, autoplay)
        externalJob = scope.launch {
            backend.state.collect { s ->
                val n = _now.value ?: return@collect
                if (n.entryId != entryId) return@collect
                _now.value = if (s == null) n else n.copy(
                    playing = s.playing,
                    loading = s.loading,
                    positionMs = s.positionMs,
                    durationMs = s.durationMs.takeIf { it > 0 } ?: n.durationMs,
                    error = s.error,
                )
                if (s?.playing == true) pauseOthers()
            }
        }
    }

    private fun message(item: ResumeItem, e: Throwable): String = when (item.kind) {
        ResumeKind.YOUTUBE, ResumeKind.MUSIC ->
            if (e is com.platinum20.dashboard.media.youtube.YtUpcomingException) e.message.orEmpty() else "Non riesco ad aprirlo, riprova più tardi"
        ResumeKind.RADIO -> "Stazione non raggiungibile"
        ResumeKind.PODCAST -> "Episodio non raggiungibile"
        ResumeKind.SPOTIFY -> e.message ?: "Spotify non risponde, riprova"
        ResumeKind.VIDEO -> "Video non riproducibile"
    }

    /** Un solo audio alla volta: la lettura del punto del giorno e i riproduttori web si fermano. */
    private fun pauseOthers() {
        com.platinum20.dashboard.news.RecapSpeaker.pause()
    }

    private fun build(context: Context): ExoPlayer = ExoPlayer.Builder(context)
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
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { player ->
            exo = player
            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) ended()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) pauseOthers() else saveProgress()
                }

                override fun onPlayerError(error: PlaybackException) {
                    val n = _now.value ?: return
                    // YouTube prova prima gli altri modi di scaricare lo stesso video.
                    val youtube = n.item.kind == ResumeKind.YOUTUBE || n.item.kind == ResumeKind.MUSIC
                    if (youtube && YtPlayer.retry(context, player)) return
                    _now.value = n.copy(loading = false, playing = false, error = message(n.item, error))
                }
            })
        }

    /** Finito quello che suonava: parte la voce dopo, di qualunque fonte sia. */
    private fun ended() {
        val n = _now.value ?: return
        if (endedId == n.entryId) return
        endedId = n.entryId
        saveProgress()
        if (next()) return
        // Coda finita su un video di YouTube: si continua con quelli proposti, come su YouTube (se lo vuoi).
        if (!AppSettings.autoplayNext.value) return
        val suggested = if (n.item.kind == ResumeKind.YOUTUBE) YtPlayer.suggestedAfter(n.item.key) else null
        val id = suggested?.let(YtResolver::videoId)
        if (id != null) {
            setQueue(QueueOps.add(_queue.value, ResumeItem(ResumeKind.YOUTUBE, id, "", at = System.currentTimeMillis()), id()))
            next()
        }
    }

    private fun publish() {
        val p = exo ?: return
        val n = _now.value ?: return
        if (n.item.kind == ResumeKind.SPOTIFY) return
        val youtube = n.item.kind == ResumeKind.YOUTUBE || n.item.kind == ResumeKind.MUSIC
        if (youtube) YtPlayer.publish(p)
        val loadingSource = loadJob?.isActive == true && p.currentMediaItem == null
        val error = when {
            youtube -> YtPlayer.now.value?.error
            p.playerError != null -> n.error ?: message(n.item, p.playerError!!)
            else -> null
        } ?: n.error.takeIf { p.playbackState == Player.STATE_IDLE && !loadingSource }
        val ended = p.playbackState == Player.STATE_ENDED
        _now.value = n.copy(
            playing = p.isPlaying,
            loading = error == null && (loadingSource || p.playbackState == Player.STATE_BUFFERING),
            positionMs = if (ended && p.duration > 0) p.duration else p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 && !n.live } ?: n.durationMs,
            error = error,
            streamTitle = if (n.live) p.mediaMetadata.title?.toString()?.trim()?.takeIf { it != n.item.title }.orEmpty() else "",
        )
        if (p.isPlaying) {
            startTicker()
            if (System.currentTimeMillis() - lastSaved > 10_000) saveProgress()
        } else ticker?.cancel()
    }

    // Mentre suona la barra del tempo scorre (una volta al secondo basta).
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                delay(1_000)
                if (exo?.isPlaying != true) break
                publish()
            }
        }
    }

    /** Segna dove si è arrivati, per "Continua" e per riprendere i podcast. */
    private fun saveProgress() {
        val n = _now.value ?: return
        val p = exo ?: return
        lastSaved = System.currentTimeMillis()
        when (n.item.kind) {
            ResumeKind.YOUTUBE, ResumeKind.MUSIC -> YtPlayer.remember(p)
            ResumeKind.PODCAST -> if (p.currentMediaItem != null) {
                val duration = p.duration.takeIf { it > 0 } ?: n.durationMs
                val position = if (p.playbackState == Player.STATE_ENDED) duration else p.currentPosition
                PodcastStore.saveProgress(n.item, position, duration)
            }
            ResumeKind.VIDEO -> if (p.currentMediaItem != null && p.duration > 0) {
                MediaHistory.record(n.item.copy(positionMs = p.currentPosition, durationMs = p.duration, at = lastSaved))
                // I Recenti di "Sul telefono" ripartono dallo stesso punto.
                VideoLibrary.init(context())
                VideoLibrary.updatePosition(n.item.key, p.currentPosition, p.duration)
            }
            ResumeKind.SPOTIFY -> spotify?.state?.value?.let { s ->
                MediaHistory.record(n.item.copy(positionMs = s.positionMs, durationMs = s.durationMs, at = lastSaved))
            }
            ResumeKind.RADIO -> Unit
        }
    }

    /** Il servizio si chiude: il motore se ne va con lui. */
    internal fun release() {
        saveProgress()
        loadJob?.cancel()
        ticker?.cancel()
        YtPlayer.detach()
        exo?.release()
        exo = null
        _now.value = null
    }

    /** Solo per i test: stato finto senza motore. */
    internal fun preview(now: EngineNow?, queue: QueueState = QueueState()) {
        _queue.value = queue
        _now.value = now
    }
}
