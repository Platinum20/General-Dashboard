package com.platinum20.dashboard.media.radio

import com.platinum20.dashboard.media.MediaHistory
import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/** Stazioni e preferite; si ascoltano nel player unico ([CanvasPlayer]). */
object RadioStore {
    private const val PREFS = "radio"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_RECENT = "recent"

    private val scope = MainScope()
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _popular = MutableStateFlow(Loadable<List<RadioStation>>())
    val popular: StateFlow<Loadable<List<RadioStation>>> = _popular.asStateFlow()

    private val _search = MutableStateFlow(Loadable<List<RadioStation>>())
    val search: StateFlow<Loadable<List<RadioStation>>> = _search.asStateFlow()

    private val _favorites = MutableStateFlow<List<RadioStation>>(emptyList())
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow<List<RadioStation>>(emptyList())
    val recent: StateFlow<List<RadioStation>> = _recent.asStateFlow()

    /** La stazione nel player unico, se è una radio a suonare. */
    val now: StateFlow<RadioNow?> = CanvasPlayer.now.map { n ->
        n?.item?.station?.takeIf { n.item.kind == ResumeKind.RADIO }?.let {
            RadioNow(it, n.playing, n.loading, n.streamTitle, n.error)
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        CanvasPlayer.init(appContext)
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
        val recent = (listOf(station) + _recent.value.filter { it.id != station.id }).take(10)
        _recent.value = recent
        write(KEY_RECENT, recent)
        MediaHistory.recordRadio(station)
        scope.launch { RadioApi.click(station) }
        CanvasPlayer.play(appContext, station.asItem(), autoplay)
    }

    fun RadioStation.asItem() = ResumeItem(ResumeKind.RADIO, id, name, "Radio", logo, at = System.currentTimeMillis(), station = this)

    private fun isCurrent() = CanvasPlayer.now.value?.item?.kind == ResumeKind.RADIO

    fun togglePause() {
        if (isCurrent()) CanvasPlayer.toggle()
    }

    fun pause() {
        if (isCurrent()) CanvasPlayer.pause()
    }

    /** Volume del player (0-1), per la sveglia che parte piano. */
    fun setVolume(volume: Float) {
        CanvasPlayer.setVolume(volume)
    }

    fun stop() {
        if (isCurrent()) CanvasPlayer.stop()
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
