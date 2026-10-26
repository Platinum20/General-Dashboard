package com.platinum20.dashboard.media.radio

import com.platinum20.dashboard.media.MediaHistory
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.NowPlaying
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Cosa sta suonando la radio. */
data class RadioNow(
    val station: RadioStation,
    val playing: Boolean,
    val buffering: Boolean,
    /** Titolo del brano trasmesso dalla stazione, se lo manda. */
    val track: String,
    val error: String?,
)

/** Stazioni, preferite e controllo della riproduzione tramite il servizio in sottofondo. */
object RadioStore {
    private const val PREFS = "radio"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_RECENT = "recent"

    private val scope = MainScope()
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private var controller: MediaController? = null
    private var connecting = false
    private var pending: Pair<RadioStation, Boolean>? = null
    private var current: RadioStation? = null

    private val _popular = MutableStateFlow(Loadable<List<RadioStation>>())
    val popular: StateFlow<Loadable<List<RadioStation>>> = _popular.asStateFlow()

    private val _search = MutableStateFlow(Loadable<List<RadioStation>>())
    val search: StateFlow<Loadable<List<RadioStation>>> = _search.asStateFlow()

    private val _favorites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow<List<RadioStation>>(emptyList())
    val recent: StateFlow<List<RadioStation>> = _recent.asStateFlow()

    private val _now = MutableStateFlow<RadioNow?>(null)
    val now: StateFlow<RadioNow?> = _now.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        MediaHistory.init(appContext)
        _favorites.value = read(KEY_FAVORITES)
        _recent.value = read(KEY_RECENT)
    }

    fun loadPopular(force: Boolean = false) {
        val state = _popular.value
        if (state.loading || (!force && state.data != null)) return
        _popular.update { it.copy(loading = true, error = null) }
        scope.launch {
            _popular.value = runCatching { RadioApi.popular() }
                .fold({ Loadable(data = it, loadedAt = System.currentTimeMillis()) }, { Loadable(data = state.data, error = it.message) })
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch {
            _search.value = runCatching { RadioApi.search(query.trim()) }
                .fold({ Loadable(data = it, loadedAt = System.currentTimeMillis()) }, { Loadable(error = it.message) })
        }
    }

    fun isFavorite(station: RadioStation) = _favorites.value.any { it.id == station.id }

    fun toggleFavorite(station: RadioStation) {
        val list = if (isFavorite(station)) _favorites.value.filter { it.id != station.id } else _favorites.value + station
        _favorites.value = list
        write(KEY_FAVORITES, list)
    }

    /** Con [autoplay] falso la stazione si sintonizza ferma e aspetta il play (es. aperta da una nota). */
    fun play(station: RadioStation, autoplay: Boolean = true) {
        // Un solo player alla volta: YouTube e Spotify si fermano, il podcast lascia il player alla radio.
        NowPlaying.pauseOthers(null, podcast = false)
        com.platinum20.dashboard.media.podcast.PodcastStore.yieldPlayer()
        com.platinum20.dashboard.media.Playback.starting(com.platinum20.dashboard.media.PlaySource.RADIO)
        com.platinum20.dashboard.media.PlayQueue.started(
            com.platinum20.dashboard.media.ResumeItem(
                com.platinum20.dashboard.media.ResumeKind.RADIO, station.id, station.name, "Radio", station.logo,
                at = System.currentTimeMillis(), station = station,
            ),
        )
        current = station
        _now.value = RadioNow(station, playing = false, buffering = true, track = "", error = null)
        val recent = (listOf(station) + _recent.value.filter { it.id != station.id }).take(10)
        _recent.value = recent
        write(KEY_RECENT, recent)
        MediaHistory.recordRadio(station)
        scope.launch { RadioApi.click(station) }
        val ready = controller
        if (ready != null) start(ready, station, autoplay) else {
            pending = station to autoplay
            connect()
        }
    }

    fun togglePause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            NowPlaying.pauseOthers(null)
            c.play()
        }
    }

    fun pause() {
        if (current == null) return
        controller?.takeIf { it.isPlaying }?.pause()
    }

    /** Volume del player (0-1), per la sveglia che parte piano. */
    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    /** Il podcast prende il player: smettiamo di seguirlo, senza comandarlo. */
    fun yieldPlayer() {
        current = null
        _now.value = null
    }

    fun stop() {
        // Se nel player c'è un podcast, non è la radio a fermarlo.
        if (current != null) {
            controller?.stop()
            controller?.clearMediaItems()
        }
        current = null
        _now.value = null
    }

    private fun start(controller: MediaController, station: RadioStation, autoplay: Boolean) {
        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(station.streamUrl)).build())
            .setMediaMetadata(
                // Il titolo resta libero per il brano annunciato dalla stazione (metadati ICY):
                // il nome della radio va come artista, così in notifica si vedono tutti e due.
                MediaMetadata.Builder()
                    .setArtist(station.name)
                    .setStation(station.name)
                    .apply { if (station.logo.isNotBlank()) setArtworkUri(Uri.parse(station.logo)) }
                    .build()
            )
            .build()
        controller.setMediaItem(item)
        controller.prepare()
        if (autoplay) controller.play() else controller.pause()
    }

    private fun connect() {
        if (connecting) return
        connecting = true
        val token = SessionToken(appContext, ComponentName(appContext, RadioService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player)
            })
            pending?.let { (station, autoplay) -> start(c, station, autoplay) }
            pending = null
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun publish(player: Player) {
        val station = current ?: return
        // Il podcast ha messo il suo episodio: la stazione non è più nel player.
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId != null && mediaId != station.id) return
        val meta = player.mediaMetadata
        // Molte radio mandano "Artista - Titolo" nel titolo del flusso.
        val track = meta.title?.toString()?.trim()?.takeIf { it.isNotBlank() && it != station.name }.orEmpty()
        _now.value = RadioNow(
            station = station,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            track = track,
            error = player.playerError?.let { "Stazione non raggiungibile" },
        )
    }

    private fun read(key: String): List<RadioStation> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { RadioStation.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun write(key: String, list: List<RadioStation>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
