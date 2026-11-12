package com.platinum20.dashboard.media.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.platinum20.dashboard.media.FeedCache
import com.platinum20.dashboard.media.FeedCodec
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.net.DiskCache
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate

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
    private const val RELEASES_MAX_AGE = 24 * 3_600_000L
    // Inizia con "sp-": si cancella insieme agli altri dati quando si scollega Spotify.
    private const val SEEN_FILE = "sp-releases-seen.json"

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

    private val _releases = MutableStateFlow(Loadable<List<SpRelease>>())
    /** Album e singoli usciti nelle ultime 4 settimane dagli artisti che segui. */
    val releases: StateFlow<Loadable<List<SpRelease>>> = _releases.asStateFlow()

    private val _seenReleases = MutableStateFlow<Set<String>>(emptySet())
    /** Le uscite già aperte: le altre hanno il pallino "nuovo". */
    val seenReleases: StateFlow<Set<String>> = _seenReleases.asStateFlow()
    private var seenLoaded = false

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

    private val _artists = MutableStateFlow(Loadable<List<SpArtistLatest>>())
    /** Gli artisti che segui con la loro ultima uscita: le storie in cima a Media (1.481). */
    val artists: StateFlow<Loadable<List<SpArtistLatest>>> = _artists.asStateFlow()

    private val _listened = MutableStateFlow(Loadable<List<SpListened>>())
    /** Gli artisti ascoltati di recente, dal più recente, con la foto: le storie di Media (1.485, Antonio). */
    val listened: StateFlow<Loadable<List<SpListened>>> = _listened.asStateFlow()
    private val photos = HashMap<String, String>()
    private val _played = MutableStateFlow<List<Pair<SpItem, java.time.Instant>>>(emptyList())
    /** Gli ultimi brani ascoltati con l'ora, dalla stessa richiesta delle storie: entrano nella Cronologia di Media. */
    val played: StateFlow<List<Pair<SpItem, java.time.Instant>>> = _played.asStateFlow()

    /**
     * Gli ultimi ascolti e la foto di ogni artista: prima da quelli che segui e dai tuoi preferiti (già scaricati),
     * poi una richiesta per artista che manca, a gruppi di 5. Al massimo [LISTENED_MAX] artisti.
     */
    fun refreshListened(force: Boolean = false) {
        val state = _listened.value
        if (state.loading || (!force && state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE)) return
        _listened.update { it.copy(loading = true) }
        scope.launch {
            val result = call { token ->
                val raw = SpotifyApi.recentlyPlayedRaw(token)
                _played.value = SpotifyArtist.played(raw)
                val list = SpotifyArtist.listened(raw).take(LISTENED_MAX)
                val known = (_artists.value.data.orEmpty().map { it.artist } + _home.value.data?.topArtists.orEmpty())
                known.forEach { if (it.image.isNotBlank()) photos[it.id] = it.image }
                val missing = list.map { it.artist.id }.filter { it !in photos }
                missing.chunked(5).forEach { group ->
                    coroutineScope {
                        group.map { id -> async { id to runCatching { SpotifyApi.artistPhoto(token, id) }.getOrDefault("") } }.awaitAll()
                    }.forEach { (id, url) -> if (url.isNotBlank()) photos[id] = url }
                }
                list.map { l -> l.copy(artist = l.artist.copy(image = photos[l.artist.id] ?: l.artist.image)) }
            }
            _listened.value = if (result.data == null) result.copy(data = state.data) else result
        }
    }

    private const val LISTENED_MAX = 20

    private val _discover = MutableStateFlow(Loadable<List<SpDiscoverRow>>())
    /** "Scopri" della musica (1.488): per ogni tuo genere gli artisti che non ascolti ancora e le sue playlist. */
    val discover: StateFlow<Loadable<List<SpDiscoverRow>>> = _discover.asStateFlow()

    fun refreshDiscover(force: Boolean = false) = load(_discover, force, FeedCache.spDiscover, maxAge = RELEASES_MAX_AGE) { token ->
        val genres = SpotifyDiscover.genres(SpotifyApi.topGenres(token))
        val known = (SpotifyApi.topArtists(token) + _listened.value.data.orEmpty().map { it.artist })
            .flatMap { listOf(it.id, it.title.lowercase()) }.toSet()
        if (genres.isEmpty()) {
            // Spotify non dice i generi: le playlist che parlano dei tuoi artisti più ascoltati.
            _listened.value.data.orEmpty().map { it.artist }.distinctBy { it.id }.take(SpotifyDiscover.GENRES).mapNotNull { a ->
                runCatching { SpotifyApi.searchKind(token, a.title, "playlist") }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?.let { SpDiscoverRow("Perché ascolti ${a.title}", "Playlist con i suoi brani", it) }
            }
        } else genres.flatMap { (genre, artist) ->
            val label = SpotifyDiscover.label(genre)
            val artists = runCatching { SpotifyApi.searchKind(token, "genre:\"$genre\"", "artist", 20) }.getOrNull().orEmpty()
            val playlists = runCatching { SpotifyApi.searchKind(token, genre, "playlist") }.getOrNull().orEmpty()
            listOfNotNull(
                SpotifyDiscover.newArtists(artists, known).takeIf { it.isNotEmpty() }?.let { SpDiscoverRow("Artisti simili: $label", "Perché ascolti $artist", it) },
                playlists.takeIf { it.isNotEmpty() }?.let { SpDiscoverRow("Il tuo genere: $label", "Playlist da scoprire", it) },
            )
        }
    }

    private val _artistPages = MutableStateFlow<Map<String, Loadable<SpArtistPage>>>(emptyMap())
    /** Le pagine degli artisti aperte, per id. */
    val artistPages: StateFlow<Map<String, Loadable<SpArtistPage>>> = _artistPages.asStateFlow()

    /**
     * Le nuove uscite si cercano al massimo una volta al giorno: una richiesta per artista seguito. Dalle stesse
     * risposte esce anche l'ultima uscita di ogni artista (le storie), così non si chiede niente due volte.
     */
    fun refreshReleases(force: Boolean = false) {
        loadSeen()
        scope.launch {
            if (_artists.value.data == null) FeedCache.restore(FeedCache.spArtists)?.let { saved -> _artists.update { saved } }
            // Senza storie salvate (la prima volta) si rilegge anche se le uscite sono fresche.
            val missing = _artists.value.data == null
            load(_releases, force || missing, FeedCache.spReleases, maxAge = RELEASES_MAX_AGE) { token ->
                val artists = SpotifyApi.followedArtistItems(token)
                val responses = coroutineScope {
                    // A gruppi di 6, per non farsi rispondere "troppe richieste".
                    artists.chunked(6).flatMap { group ->
                        group.map { a -> async { runCatching { SpotifyApi.artistAlbumsRaw(token, a.id) }.getOrNull() } }.awaitAll()
                    }
                }
                val latest = artists.zip(responses).map { (a, r) -> SpArtistLatest(a, SpotifyArtist.latestRelease(r)) }
                val now = System.currentTimeMillis()
                _artists.value = Loadable(data = latest, loadedAt = now)
                FeedCache.save(FeedCache.spArtists, latest, now)
                NewReleases.pick(responses, LocalDate.now())
            }
        }
    }

    /** La pagina di un artista: si scarica alla prima apertura e si tiene finché l'app è aperta. */
    fun loadArtist(artist: SpItem, force: Boolean = false) {
        val state = _artistPages.value[artist.id]
        if (!force && state != null && (state.loading || state.data != null)) return
        _artistPages.update { it + (artist.id to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = call { token -> SpotifyApi.artistPage(token, artist) }
            _artistPages.update { it + (artist.id to if (result.data == null) result.copy(data = state?.data) else result) }
        }
    }

    /** Aperta un'uscita, il pallino "nuovo" sparisce (anche alle prossime aperture dell'app). */
    fun markReleaseSeen(id: String) {
        if (id in _seenReleases.value) return
        _seenReleases.update { it + id }
        val text = JSONArray(_seenReleases.value.toList()).toString()
        scope.launch { DiskCache.write(SEEN_FILE, text) }
    }

    private fun loadSeen() {
        if (seenLoaded) return
        seenLoaded = true
        scope.launch {
            val saved = runCatching {
                DiskCache.read(SEEN_FILE)?.let { raw -> JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }
            }.getOrNull().orEmpty()
            _seenReleases.update { it + saved }
        }
    }

    fun refreshPlaylists(force: Boolean = false) =
        load(_playlists, force, FeedCache.spItems("sp-playlist.json")) { token -> SpotifyApi.playlists(token) }

    /** L'ultima playlist dove hai messo un brano: la prima chip la prossima volta. */
    var lastPlaylist: String? = null
        private set

    /** Le playlist dove si può scrivere (le tue e le collaborative), lette ogni volta: sono poche e cambiano. */
    suspend fun editablePlaylists(): Loadable<List<SpItem>> = modifyCall { token -> SpotifyApi.editablePlaylists(token) }

    /**
     * Mette [track] in [playlist]. Risponde col testo da mostrare: "Aggiunto a …", "È già in …" o l'errore.
     * Dopo, la pagina della playlist e l'elenco si rileggono, così il brano si vede subito.
     */
    suspend fun addToPlaylist(playlist: SpItem, track: SpItem): String {
        if (SpotifyPlaylists.alreadyIn(_children.value[playlist.id]?.data, track.uri)) return "È già in ${playlist.title}"
        val result = modifyCall { token ->
            // Senza i brani in memoria si leggono (i primi 100): Spotify altrimenti lo mette due volte.
            val known = runCatching { SpotifyApi.children(token, playlist) }.getOrNull()
            if (SpotifyPlaylists.alreadyIn(known, track.uri)) false
            else true.also { SpotifyApi.addToPlaylist(token, playlist.id, listOf(track.uri)) }
        }
        if (result.error != null) return result.error
        if (result.data == false) return "È già in ${playlist.title}"
        lastPlaylist = playlist.id
        _children.update { it - playlist.id }
        refreshPlaylists(force = true)
        return "Aggiunto a ${playlist.title}"
    }

    /** Una playlist nuova chiamata [name], con [track] dentro se c'è. Risponde la playlist o l'errore. */
    suspend fun createPlaylist(name: String, track: SpItem?): Result<SpItem> {
        val result = modifyCall { token ->
            val made = SpotifyApi.createPlaylist(token, name)
            if (track != null) SpotifyApi.addToPlaylist(token, made.id, listOf(track.uri))
            made
        }
        val made = result.data ?: return Result.failure(IllegalStateException(result.error ?: "Spotify non risponde, riprova"))
        lastPlaylist = made.id
        refreshPlaylists(force = true)
        return Result.success(made)
    }

    /**
     * Come [call], ma un 403 qui vuol dire quasi sempre che l'accesso è vecchio e non ha il permesso di
     * modificare le playlist: lo dice, con dove rientrare.
     */
    private suspend fun <T> modifyCall(block: suspend (String) -> T): Loadable<T> = try {
        val token = SpotifyAuth.token() ?: return Loadable(error = "Ricollega Spotify da Impostazioni › Account")
        val data = try {
            block(token)
        } catch (e: SpotifyApiException) {
            if (e.code != 401) throw e
            block(SpotifyAuth.token(forceRefresh = true) ?: throw e)
        }
        Loadable(data = data, loadedAt = System.currentTimeMillis())
    } catch (e: SpotifyApiException) {
        Loadable(error = if (e.code == 403) "Spotify non dà il permesso: rientra da Impostazioni › Account › Spotify" else e.message.orEmpty())
    } catch (e: Exception) {
        Loadable(error = e.userMessage("Spotify non è raggiungibile, riprova tra poco."))
    }

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

    /**
     * Mette in pausa Spotify se suona fuori dall'app (app di Spotify, casse, computer): quando
     * parte qualcos'altro nell'app suona una cosa sola. Chiede lo stato fresco, non quello vecchio.
     */
    fun pauseRemote() {
        scope.launch {
            val token = runCatching { SpotifyAuth.token() }.getOrNull() ?: return@launch
            val state = runCatching { SpotifyApi.playback(token) }.getOrNull() ?: return@launch
            if (!state.playing) return@launch
            runCatching { SpotifyApi.pause(token) }
            // Solo se il player dell'app lo mostrava già: altrimenti non deve comparire all'improvviso.
            if (_playback.value != null) _playback.value = state.copy(playing = false)
        }
    }

    /** Chiude dal player dell'app lo Spotify che suona altrove: pausa e il mini player lo dimentica. */
    fun closeRemote() {
        _playback.value = null
        scope.launch {
            val token = runCatching { SpotifyAuth.token() }.getOrNull() ?: return@launch
            runCatching { SpotifyApi.pause(token) }
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
        MadeStore.clear()
        _home.value = Loadable()
        _playlists.value = Loadable()
        _library.value = Loadable()
        _children.value = emptyMap()
        _search.value = Loadable()
        _releases.value = Loadable()
        _artists.value = Loadable()
        _listened.value = Loadable()
        _played.value = emptyList()
        _artistPages.value = emptyMap()
        _seenReleases.value = emptySet()
        _playback.value = null
    }

    private fun fresh(state: Loadable<*>, maxAge: Long = MAX_AGE) = state.data != null && System.currentTimeMillis() - state.loadedAt < maxAge

    private fun <T> load(
        flow: MutableStateFlow<Loadable<T>>,
        force: Boolean,
        codec: FeedCodec<T>,
        maxAge: Long = MAX_AGE,
        block: suspend (String) -> T,
    ) {
        val state = flow.value
        if (state.loading || (!force && fresh(state, maxAge))) return
        flow.update { it.copy(loading = true, error = null) }
        scope.launch {
            var previous = state.data
            // All'avvio si vede subito l'ultima pagina salvata, intanto si scarica quella nuova.
            if (previous == null) {
                FeedCache.restore(codec)?.let { saved ->
                    previous = saved.data
                    flow.update { it.copy(data = saved.data, loadedAt = saved.loadedAt) }
                    if (!force && fresh(saved, maxAge)) {
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
