package com.platinum20.dashboard.media.youtube

import android.content.Context
import com.platinum20.dashboard.media.Loadable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Feed di YouTube dell'utente, tenuti in memoria finché l'app è aperta. */
object YouTubeStore {
    private val scope = MainScope()
    private const val MAX_AGE = 15 * 60_000L

    private val _subscriptions = MutableStateFlow(Loadable<List<YtVideo>>())
    /** Nuovi video dei canali seguiti, i più recenti prima. */
    val subscriptions: StateFlow<Loadable<List<YtVideo>>> = _subscriptions.asStateFlow()

    private val _playlists = MutableStateFlow(Loadable<List<YtPlaylist>>())
    val playlists: StateFlow<Loadable<List<YtPlaylist>>> = _playlists.asStateFlow()

    private val _liked = MutableStateFlow(Loadable<List<YtVideo>>())
    val liked: StateFlow<Loadable<List<YtVideo>>> = _liked.asStateFlow()

    private val _playlistItems = MutableStateFlow<Map<String, Loadable<List<YtVideo>>>>(emptyMap())
    val playlistItems: StateFlow<Map<String, Loadable<List<YtVideo>>>> = _playlistItems.asStateFlow()

    private val _search = MutableStateFlow(Loadable<List<YtVideo>>())
    val search: StateFlow<Loadable<List<YtVideo>>> = _search.asStateFlow()

    fun refreshSubscriptions(context: Context, force: Boolean = false) =
        load(context, _subscriptions, force) { token ->
            YouTubeApi.uploads(YouTubeApi.subscriptions(token)).take(300)
        }

    fun refreshPlaylists(context: Context, force: Boolean = false) =
        load(context, _playlists, force) { token -> YouTubeApi.playlists(token) }

    fun refreshLiked(context: Context, force: Boolean = false) =
        load(context, _liked, force) { token -> YouTubeApi.liked(token) }

    fun loadPlaylist(context: Context, playlistId: String, force: Boolean = false) {
        val state = _playlistItems.value[playlistId]
        if (!force && state != null && (state.loading || fresh(state))) return
        _playlistItems.update { it + (playlistId to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = call(context) { token -> YouTubeApi.playlistItems(token, playlistId) }
            _playlistItems.update { it + (playlistId to result) }
        }
    }

    fun search(context: Context, query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch { _search.value = call(context) { token -> YouTubeApi.search(token, query.trim()) } }
    }

    fun clear() {
        _subscriptions.value = Loadable()
        _playlists.value = Loadable()
        _liked.value = Loadable()
        _playlistItems.value = emptyMap()
        _search.value = Loadable()
    }

    private fun fresh(state: Loadable<*>) = state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE

    private fun <T> load(
        context: Context,
        flow: MutableStateFlow<Loadable<T>>,
        force: Boolean,
        block: suspend (String) -> T,
    ) {
        val state = flow.value
        if (state.loading || (!force && fresh(state))) return
        flow.update { it.copy(loading = true, error = null) }
        scope.launch { flow.value = call(context, block).let { if (it.data == null) it.copy(data = state.data) else it } }
    }

    /** Esegue la chiamata con un token valido; se è scaduto lo rinnova una volta e riprova. */
    private suspend fun <T> call(context: Context, block: suspend (String) -> T): Loadable<T> {
        val app = context.applicationContext
        return try {
            val token = YouTubeAuth.token(app) ?: return Loadable(error = "Ricollega il tuo YouTube")
            val data = try {
                block(token)
            } catch (e: YouTubeAuthException) {
                block(YouTubeAuth.token(app, forceRefresh = true) ?: throw e)
            }
            Loadable(data = data, loadedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            Loadable(error = e.message ?: "Errore di rete")
        }
    }
}
