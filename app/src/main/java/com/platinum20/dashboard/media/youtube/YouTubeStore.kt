package com.platinum20.dashboard.media.youtube

import android.content.Context
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.net.userMessage
import com.platinum20.dashboard.media.youtube.player.YtResolver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    private val _watchLater = MutableStateFlow(Loadable<List<YtVideo>>())
    /** "Guarda più tardi": c'è solo leggendo il sito, l'API ufficiale non lo espone. */
    val watchLater: StateFlow<Loadable<List<YtVideo>>> = _watchLater.asStateFlow()

    fun refreshSubscriptions(context: Context, force: Boolean = false) =
        load(context, _subscriptions, force, web = { YouTubeApi.uploads(YouTubeWeb.subscriptions(it)).take(300) }) { token ->
            YouTubeApi.withDetails(token, YouTubeApi.uploads(YouTubeApi.subscriptions(token)).take(300))
        }

    private val _home = MutableStateFlow(Loadable<List<YtVideo>>())
    /** La Home di YouTube (video consigliati): c'è solo con l'accesso a YouTube fatto nell'app. */
    val home: StateFlow<Loadable<List<YtVideo>>> = _home.asStateFlow()

    fun refreshHome(context: Context, force: Boolean = false) =
        load(context, _home, force, web = { YouTubeWeb.home(it) }, api = null)

    fun refreshPlaylists(context: Context, force: Boolean = false) =
        load(context, _playlists, force, web = { YouTubeWeb.playlists(it) }) { token -> YouTubeApi.playlists(token) }

    fun refreshLiked(context: Context, force: Boolean = false) =
        load(context, _liked, force, web = { YouTubeWeb.liked(it) }) { token -> YouTubeApi.liked(token) }

    fun refreshWatchLater(context: Context, force: Boolean = false) =
        load(context, _watchLater, force, web = { YouTubeWeb.watchLater(it) }, api = null)

    fun loadPlaylist(context: Context, playlistId: String, force: Boolean = false) {
        val state = _playlistItems.value[playlistId]
        if (!force && state != null && (state.loading || fresh(state))) return
        _playlistItems.update { it + (playlistId to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = call(context, web = { YouTubeWeb.playlistItems(it, playlistId) }) { token ->
                YouTubeApi.playlistItems(token, playlistId)
            }
            _playlistItems.update { it + (playlistId to result) }
        }
    }

    fun search(context: Context, query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch {
            // Prima la ricerca nativa (niente quota dell'API); se YouTube la blocca, la strada di prima.
            val native = runCatching { YtResolver.search(query.trim()) }.getOrNull()?.takeIf { it.isNotEmpty() }
            _search.value = if (native != null) {
                Loadable(data = native, loadedAt = System.currentTimeMillis())
            } else {
                call(context, web = { YouTubeWeb.search(it, query.trim()) }) { token -> YouTubeApi.search(token, query.trim()) }
            }
        }
    }

    fun clear() {
        _subscriptions.value = Loadable()
        _playlists.value = Loadable()
        _liked.value = Loadable()
        _watchLater.value = Loadable()
        _playlistItems.value = emptyMap()
        _search.value = Loadable()
        _home.value = Loadable()
    }

    /** Dati finti per le foto automatiche delle schermate (solo test). */
    internal fun preview(home: List<YtVideo>, subscriptions: List<YtVideo>) {
        val now = System.currentTimeMillis()
        _home.value = Loadable(data = home, loadedAt = now)
        _subscriptions.value = Loadable(data = subscriptions, loadedAt = now)
    }

    private fun fresh(state: Loadable<*>) = state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE

    private fun <T> load(
        context: Context,
        flow: MutableStateFlow<Loadable<T>>,
        force: Boolean,
        web: suspend (Context) -> T,
        api: (suspend (String) -> T)?,
    ) {
        val state = flow.value
        if (state.loading || (!force && fresh(state))) return
        flow.update { it.copy(loading = true, error = null) }
        scope.launch { flow.value = call(context, web, api).let { if (it.data == null) it.copy(data = state.data) else it } }
    }

    /**
     * Con l'API ufficiale collegata usa un token valido (se è scaduto lo rinnova una volta e riprova),
     * altrimenti legge il sito con l'accesso fatto nell'app.
     */
    private suspend fun <T> call(context: Context, web: suspend (Context) -> T, api: (suspend (String) -> T)?): Loadable<T> {
        val app = context.applicationContext
        return try {
            val token = if (api != null && YouTubeAuth.connected.value) YouTubeAuth.token(app) else null
            val data = when {
                // Senza API ufficiale si legge il sito: mai più di un minuto, poi un errore chiaro.
                api == null || token == null -> withTimeoutOrNull(60_000) { web(app) }
                    ?: throw java.io.IOException(
                        "YouTube non risponde. Tocca la chiave in alto e collega l'API ufficiale di Google: è più veloce e affidabile."
                    )
                else -> try {
                    api(token)
                } catch (e: YouTubeAuthException) {
                    api(YouTubeAuth.token(app, forceRefresh = true) ?: throw e)
                }
            }
            Loadable(data = data, loadedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            Loadable(error = e.userMessage("YouTube non è raggiungibile, riprova tra poco."))
        }
    }
}
