package com.platinum20.dashboard.media.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sezioni della pagina "Per te". */
data class SpHome(
    val profile: SpProfile,
    val recent: List<SpItem>,
    val topTracks: List<SpItem>,
    val topArtists: List<SpItem>,
)

data class SpLibrary(val liked: List<SpItem>, val albums: List<SpItem>, val shows: List<SpItem>)

/** Dati Spotify dell'utente e comandi della riproduzione. */
object SpotifyStore {
    private val scope = MainScope()
    private const val MAX_AGE = 10 * 60_000L

    private val _home = MutableStateFlow(Loadable<SpHome>())
    val home: StateFlow<Loadable<SpHome>> = _home.asStateFlow()

    private val _playlists = MutableStateFlow(Loadable<List<SpItem>>())
    val playlists: StateFlow<Loadable<List<SpItem>>> = _playlists.asStateFlow()

    private val _library = MutableStateFlow(Loadable<SpLibrary>())
    val library: StateFlow<Loadable<SpLibrary>> = _library.asStateFlow()

    private val _children = MutableStateFlow<Map<String, Loadable<List<SpItem>>>>(emptyMap())
    val children: StateFlow<Map<String, Loadable<List<SpItem>>>> = _children.asStateFlow()

    private val _search = MutableStateFlow(Loadable<List<SpItem>>())
    val search: StateFlow<Loadable<List<SpItem>>> = _search.asStateFlow()

    private val _playback = MutableStateFlow<SpPlayback?>(null)
    val playback: StateFlow<SpPlayback?> = _playback.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** Avviso breve da mostrare (es. serve Premium, nessun dispositivo). */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshHome(force: Boolean = false) = load(_home, force) { token ->
        SpHome(
            profile = SpotifyApi.profile(token),
            recent = SpotifyApi.recentlyPlayed(token),
            topTracks = runCatching { SpotifyApi.topTracks(token) }.getOrDefault(emptyList()),
            topArtists = runCatching { SpotifyApi.topArtists(token) }.getOrDefault(emptyList()),
        )
    }

    fun refreshPlaylists(force: Boolean = false) = load(_playlists, force) { token -> SpotifyApi.playlists(token) }

    fun refreshLibrary(force: Boolean = false) = load(_library, force) { token ->
        SpLibrary(
            liked = SpotifyApi.likedTracks(token),
            albums = runCatching { SpotifyApi.savedAlbums(token) }.getOrDefault(emptyList()),
            shows = runCatching { SpotifyApi.savedShows(token) }.getOrDefault(emptyList()),
        )
    }

    fun loadChildren(parent: SpItem, force: Boolean = false) {
        val state = _children.value[parent.id]
        if (!force && state != null && (state.loading || state.data != null)) return
        _children.update { it + (parent.id to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = call { token -> SpotifyApi.children(token, parent) }
            _children.update { it + (parent.id to result) }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch { _search.value = call { token -> SpotifyApi.search(token, query.trim()) } }
    }

    fun refreshPlayback() {
        scope.launch {
            val token = SpotifyAuth.token() ?: return@launch
            _playback.value = runCatching { SpotifyApi.playback(token) }.getOrNull()
        }
    }

    fun control(action: suspend (String) -> Unit) {
        scope.launch {
            val token = SpotifyAuth.token() ?: return@launch
            runCatching { action(token) }.onFailure { _message.value = it.message }
            delay(500)
            refreshPlayback()
        }
    }

    fun clear() {
        _home.value = Loadable()
        _playlists.value = Loadable()
        _library.value = Loadable()
        _children.value = emptyMap()
        _search.value = Loadable()
        _playback.value = null
    }

    private fun <T> load(flow: MutableStateFlow<Loadable<T>>, force: Boolean, block: suspend (String) -> T) {
        val state = flow.value
        val fresh = state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE
        if (state.loading || (!force && fresh)) return
        flow.update { it.copy(loading = true, error = null) }
        scope.launch { flow.value = call(block).let { if (it.data == null) it.copy(data = state.data) else it } }
    }

    private suspend fun <T> call(block: suspend (String) -> T): Loadable<T> = try {
        val token = SpotifyAuth.token() ?: return Loadable(error = "Ricollega Spotify")
        val data = try {
            block(token)
        } catch (e: SpotifyApiException) {
            if (e.code != 401) throw e
            block(SpotifyAuth.token(forceRefresh = true) ?: throw e)
        }
        Loadable(data = data, loadedAt = System.currentTimeMillis())
    } catch (e: Exception) {
        Loadable(error = e.userMessage("Spotify non è raggiungibile, riprova tra poco."))
    }
}
