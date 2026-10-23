package com.platinum20.dashboard.media.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.platinum20.dashboard.media.FeedCache
import com.platinum20.dashboard.media.FeedCodec
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // Le parti di ogni pagina si scaricano insieme, non una dopo l'altra.
    fun refreshHome(force: Boolean = false) = load(_home, force, FeedCache.spHome) { token ->
        coroutineScope {
            val profile = async { SpotifyApi.profile(token) }
            val recent = async { SpotifyApi.recentlyPlayed(token) }
            val topTracks = async { runCatching { SpotifyApi.topTracks(token) }.getOrDefault(emptyList()) }
            val topArtists = async { runCatching { SpotifyApi.topArtists(token) }.getOrDefault(emptyList()) }
            SpHome(profile.await(), recent.await(), topTracks.await(), topArtists.await())
        }
    }

    fun refreshPlaylists(force: Boolean = false) =
        load(_playlists, force, FeedCache.spItems("sp-playlist.json")) { token -> SpotifyApi.playlists(token) }

    fun refreshLibrary(force: Boolean = false) = load(_library, force, FeedCache.spLibrary) { token ->
        coroutineScope {
            val liked = async { SpotifyApi.likedTracks(token) }
            val albums = async { runCatching { SpotifyApi.savedAlbums(token) }.getOrDefault(emptyList()) }
            val shows = async { runCatching { SpotifyApi.savedShows(token) }.getOrDefault(emptyList()) }
            SpLibrary(liked.await(), albums.await(), shows.await())
        }
    }

    fun loadChildren(parent: SpItem, force: Boolean = false) {
        val state = _children.value[parent.id]
        if (!force && state != null && (state.loading || state.data != null)) return
        _children.update { it + (parent.id to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            // L'avviso "playlist bloccata" vale solo per questa chiamata: niente ricordi di quelle prima.
            var locked: String? = null
            val result = call { token ->
                try {
                    SpotifyApi.children(token, parent)
                } catch (e: PlaylistLockedException) {
                    locked = e.message.orEmpty()
                    emptyList()
                }
            }
            _children.update { it + (parent.id to childrenState(state?.data, result, locked)) }
        }
    }

    /**
     * Stato della lista dopo una chiamata: se la chiamata fallisce si tiene l'elenco di prima (così
     * copertina, titolo e "Riproduci" restano); con la playlist bloccata si mostra l'avviso sopra
     * un elenco vuoto, ma il titolo e "Riproduci" restano perché la playlist suona lo stesso.
     */
    internal fun childrenState(previous: List<SpItem>?, result: Loadable<List<SpItem>>, locked: String?): Loadable<List<SpItem>> = when {
        locked != null && result.error == null -> result.copy(error = locked)
        result.data == null -> result.copy(data = previous)
        else -> result
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
        FeedCache.clear("sp-")
        _home.value = Loadable()
        _playlists.value = Loadable()
        _library.value = Loadable()
        _children.value = emptyMap()
        _search.value = Loadable()
        _playback.value = null
    }

    private fun fresh(state: Loadable<*>) = state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE

    private fun <T> load(flow: MutableStateFlow<Loadable<T>>, force: Boolean, codec: FeedCodec<T>, block: suspend (String) -> T) {
        val state = flow.value
        if (state.loading || (!force && fresh(state))) return
        flow.update { it.copy(loading = true, error = null) }
        scope.launch {
            var previous = state.data
            // All'avvio si vede subito l'ultima pagina salvata, intanto si scarica quella nuova.
            if (previous == null) {
                FeedCache.restore(codec)?.let { saved ->
                    previous = saved.data
                    flow.update { it.copy(data = saved.data, loadedAt = saved.loadedAt) }
                    if (!force && fresh(saved)) {
                        flow.update { it.copy(loading = false) }
                        return@launch
                    }
                }
            }
            val result = call(block)
            flow.value = if (result.data == null) result.copy(data = previous) else result
            result.data?.let { FeedCache.save(codec, it, result.loadedAt) }
        }
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
