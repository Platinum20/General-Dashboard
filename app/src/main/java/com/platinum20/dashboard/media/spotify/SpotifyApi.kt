package com.platinum20.dashboard.media.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SpKind { TRACK, ALBUM, PLAYLIST, ARTIST, SHOW, EPISODE }

/** Un elemento di Spotify da mostrare in lista: brano, album, playlist, artista o podcast. */
data class SpItem(
    val id: String,
    val uri: String,
    val kind: SpKind,
    val title: String,
    val subtitle: String,
    val image: String,
    /** Durata del brano o dell'episodio: la barra del tempo c'è da subito, prima che Spotify risponda. */
    val durationMs: Long = 0,
) {
    val webUrl: String get() = "https://open.spotify.com/${kind.name.lowercase()}/$id"
}

data class SpProfile(val name: String, val premium: Boolean)

data class SpPlayback(
    val item: SpItem?,
    val playing: Boolean,
    val device: String,
    val progressMs: Long,
    val durationMs: Long,
)

/** Un episodio di un podcast salvato su Spotify, con quello che serve per metterlo tra gli altri. */
data class SpEpisode(
    val id: String,
    val uri: String,
    val title: String,
    /** "2026-10-21" (a volte solo l'anno). */
    val releaseDate: String,
    val durationMs: Long,
    val description: String,
    val image: String,
)

class SpotifyApiException(message: String, val code: Int) : IOException(message)

/**
 * Spotify ha risposto 403 all'elenco dei brani: succede con le playlist che non hai creato tu, ma
 * anche quando l'account non è tra gli utenti dell'app (User Management). Si può comunque ascoltare.
 */
class PlaylistLockedException : IOException(
    "Spotify non mostra a Canvas i brani di questa playlist: succede con quelle che non hai creato tu. Puoi comunque ascoltarla: tocca Riproduci."
)

/** Web API di Spotify: libreria, ascolti e, con Premium, comandi della riproduzione. */
object SpotifyApi {
    private const val BASE = "https://api.spotify.com/v1/"

    private suspend fun request(
        token: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject? = withContext(Dispatchers.IO) {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept-Language", "it-IT,it;q=0.9")
            if (body != null || method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (conn.doOutput) conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
            val code = conn.responseCode
            if (code == 204) return@withContext null
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = runCatching { JSONObject(text).getJSONObject("error") }.getOrNull()
                val reason = error?.optString("reason").orEmpty()
                throw SpotifyApiException(
                    when {
                        code == 401 -> "Accesso a Spotify scaduto"
                        code == 403 && reason == "PREMIUM_REQUIRED" -> "Per comandare la riproduzione serve Spotify Premium"
                        code == 403 -> "Spotify non permette questa richiesta alle app personali"
                        code == 404 && reason == "NO_ACTIVE_DEVICE" -> "Nessun dispositivo Spotify attivo"
                        code == 429 -> "Troppe richieste a Spotify, riprova tra poco"
                        else -> "Spotify non risponde ($code), riprova più tardi"
                    },
                    code,
                )
            }
            // Alcuni comandi (pausa, avanti) rispondono "ok" con un testo che non è JSON: non è un errore.
            if (text.isBlank()) null else runCatching { JSONObject(text) }.getOrNull()
        } finally {
            conn.disconnect()
        }
    }

    suspend fun profile(token: String): SpProfile {
        val me = request(token, "me") ?: JSONObject()
        return SpProfile(me.optString("display_name").ifBlank { me.optString("id") }, me.optString("product") == "premium")
    }

    suspend fun recentlyPlayed(token: String): List<SpItem> =
        request(token, "me/player/recently-played?limit=50")?.optJSONArray("items")
            .objects().mapNotNull { it.optJSONObject("track")?.toItem() }
            .distinctBy { it.id }

    /** Gli ultimi 50 ascolti così come arrivano, con l'ora: servono alle storie di Media ([SpotifyArtist.listened]). */
    suspend fun recentlyPlayedRaw(token: String): JSONObject? = request(token, "me/player/recently-played?limit=50")

    /** La foto di un artista (gli artisti dentro un brano non ce l'hanno). */
    suspend fun artistPhoto(token: String, artistId: String): String = SpotifyArtist.photo(request(token, "artists/$artistId"))

    suspend fun topTracks(token: String): List<SpItem> =
        request(token, "me/top/tracks?time_range=short_term&limit=30")?.optJSONArray("items").objects().mapNotNull { it.toItem() }

    suspend fun topArtists(token: String): List<SpItem> =
        request(token, "me/top/artists?time_range=medium_term&limit=20")?.optJSONArray("items").objects().mapNotNull { it.toItem() }

    /** I generi dei tuoi artisti più ascoltati, ognuno col nome dell'artista: (genere, artista). Per "Scopri" (1.488). */
    suspend fun topGenres(token: String): List<Pair<String, String>> =
        request(token, "me/top/artists?time_range=medium_term&limit=20")?.optJSONArray("items").objects().flatMap { a ->
            val g = a.optJSONArray("genres")
            (0 until (g?.length() ?: 0)).map { g!!.optString(it) to a.optString("name") }
        }

    /** Una ricerca di un tipo solo (artist, playlist, album): per le file di "Scopri". */
    suspend fun searchKind(token: String, query: String, type: String, limit: Int = 12): List<SpItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        return request(token, "search?q=$q&type=$type&limit=$limit&market=from_token")
            ?.optJSONObject(type + "s")?.optJSONArray("items").objects().mapNotNull { it.toItem() }
    }

    suspend fun playlists(token: String): List<SpItem> =
        request(token, "me/playlists?limit=50")?.optJSONArray("items").objects().mapNotNull { it.toItem() }

    /**
     * Le playlist dove si possono mettere brani: le tue e quelle collaborative. Spotify non lo dice nell'elemento,
     * quindi serve l'id del profilo per confrontarlo col proprietario.
     */
    suspend fun editablePlaylists(token: String): List<SpItem> {
        val me = request(token, "me")?.optString("id").orEmpty()
        return SpotifyPlaylists.editable(request(token, "me/playlists?limit=50"), me)
    }

    /**
     * Una playlist nuova, privata. Da febbraio 2026 si crea su "me/playlists"; se il server risponde ancora col
     * vecchio indirizzo (404/405) si riprova con "users/<id>/playlists".
     */
    suspend fun createPlaylist(token: String, name: String): SpItem {
        val body = JSONObject().put("name", name).put("public", false)
        val json = try {
            request(token, "me/playlists", "POST", body)
        } catch (e: SpotifyApiException) {
            if (e.code != 404 && e.code != 405) throw e
            val me = request(token, "me")?.optString("id").orEmpty()
            request(token, "users/$me/playlists", "POST", body)
        }
        return json?.toItem() ?: throw SpotifyApiException("Spotify non ha creato la playlist, riprova", 0)
    }

    /** Mette i brani in fondo alla playlist: "items" dal 2026, "tracks" prima (stessa regola della lettura). */
    suspend fun addToPlaylist(token: String, playlistId: String, uris: List<String>) {
        val body = JSONObject().put("uris", JSONArray(uris))
        try {
            request(token, "playlists/$playlistId/items", "POST", body)
        } catch (e: SpotifyApiException) {
            if (e.code != 404 && e.code != 405) throw e
            request(token, "playlists/$playlistId/tracks", "POST", body)
        }
    }

    /** Il tuo id di Spotify: serve per sapere quali playlist sono tue. */
    suspend fun myId(token: String): String = request(token, "me")?.optString("id").orEmpty()

    /** Nome, descrizione, privata e proprietario di una playlist (1.502). */
    suspend fun playlistInfo(token: String, playlistId: String): SpPlaylistInfo? =
        SpotifyPlaylists.info(request(token, "playlists/$playlistId?fields=id,name,description,public,collaborative,owner(id)"))

    /** Cambia nome, descrizione o privata: [body] viene da [SpotifyPlaylists.changes]. */
    suspend fun updatePlaylist(token: String, playlistId: String, body: JSONObject) {
        request(token, "playlists/$playlistId", "PUT", body)
    }

    /**
     * "Elimina": Spotify non cancella mai davvero una playlist, la togli dalla tua libreria (anche se è tua). Prima
     * il vecchio indirizzo "followers", poi quello del 2026 "me/library" se il primo non c'è più.
     */
    suspend fun unfollowPlaylist(token: String, playlistId: String) = withNewPath(
        { request(token, "playlists/$playlistId/followers", "DELETE") },
        { request(token, "me/library?uris=" + URLEncoder.encode("spotify:playlist:$playlistId", "UTF-8"), "DELETE") },
    )

    /** Per "Annulla": la playlist torna nella libreria. */
    suspend fun followPlaylist(token: String, playlistId: String) = withNewPath(
        { request(token, "playlists/$playlistId/followers", "PUT") },
        { request(token, "me/library?uris=" + URLEncoder.encode("spotify:playlist:$playlistId", "UTF-8"), "PUT") },
    )

    /** Toglie il brano dalla playlist (tutte le volte che c'è). */
    suspend fun removeFromPlaylist(token: String, playlistId: String, uri: String) {
        val one = JSONArray().put(JSONObject().put("uri", uri))
        withNewPath(
            { request(token, "playlists/$playlistId/items", "DELETE", JSONObject().put("items", one)) },
            { request(token, "playlists/$playlistId/tracks", "DELETE", JSONObject().put("tracks", one)) },
        )
    }

    /** Rimette il brano al suo posto ([position] da 0), per "Annulla". */
    suspend fun insertInPlaylist(token: String, playlistId: String, uri: String, position: Int) {
        val body = JSONObject().put("uris", JSONArray(listOf(uri))).put("position", position)
        withNewPath(
            { request(token, "playlists/$playlistId/items", "POST", body) },
            { request(token, "playlists/$playlistId/tracks", "POST", body) },
        )
    }

    /** Sposta la riga [from] prima della riga [insertBefore] (vedi [SpotifyPlaylists.insertBefore]). */
    suspend fun moveInPlaylist(token: String, playlistId: String, from: Int, insertBefore: Int) {
        val body = JSONObject().put("range_start", from).put("insert_before", insertBefore).put("range_length", 1)
        withNewPath(
            { request(token, "playlists/$playlistId/items", "PUT", body) },
            { request(token, "playlists/$playlistId/tracks", "PUT", body) },
        )
    }

    /**
     * Spotify ha cambiato indirizzi nel 2026 e non tutti rispondono a tutti: se il primo dice che non esiste
     * (404/405/410) si prova l'altro. Gli altri errori (403 = permesso) passano così come sono.
     */
    private suspend fun withNewPath(first: suspend () -> Unit, second: suspend () -> Unit) {
        try {
            first()
        } catch (e: SpotifyApiException) {
            if (e.code != 404 && e.code != 405 && e.code != 410) throw e
            second()
        }
    }

    suspend fun likedTracks(token: String): List<SpItem> =
        request(token, "me/tracks?limit=50")?.optJSONArray("items").objects().mapNotNull { it.optJSONObject("track")?.toItem() }

    suspend fun savedAlbums(token: String): List<SpItem> =
        request(token, "me/albums?limit=50")?.optJSONArray("items").objects().mapNotNull { it.optJSONObject("album")?.toItem() }

    suspend fun savedShows(token: String): List<SpItem> =
        request(token, "me/shows?limit=50")?.optJSONArray("items").objects().mapNotNull { it.optJSONObject("show")?.toItem() }

    /** Contenuto di una playlist, di un album, di un artista (brani più ascoltati) o di un podcast. */
    suspend fun children(token: String, parent: SpItem): List<SpItem> = when (parent.kind) {
        // Da febbraio 2026 l'elenco si chiama "items" e Spotify lo dà alle app personali solo per le
        // playlist tue o collaborative: per le altre (es. quelle create da Spotify) risponde 403.
        SpKind.PLAYLIST -> try {
            parsePlaylistItems(request(token, "playlists/${parent.id}/items?limit=100"))
        } catch (e: SpotifyApiException) {
            if (e.code == 403) throw PlaylistLockedException() else throw e
        }
        SpKind.ALBUM -> {
            val album = request(token, "albums/${parent.id}") ?: JSONObject()
            val image = album.image()
            album.optJSONObject("tracks")?.optJSONArray("items").objects()
                .mapNotNull { it.toItem()?.copy(image = image) }
        }
        // I "brani più ascoltati" dell'artista non ci sono più per le app personali: album e singoli.
        SpKind.ARTIST -> parseArtistAlbums(request(token, "artists/${parent.id}/albums?include_groups=album,single&limit=30"))
        SpKind.SHOW -> request(token, "shows/${parent.id}/episodes?limit=50&market=from_token")?.optJSONArray("items")
            .objects().mapNotNull { it.toItem() }
        else -> emptyList()
    }

    /** Gli ultimi episodi di un podcast di Spotify, dal più nuovo. */
    suspend fun showEpisodes(token: String, showId: String): List<SpEpisode> =
        request(token, "shows/$showId/episodes?limit=20&market=from_token")?.optJSONArray("items").objects().mapNotNull { e ->
            val id = e.optString("id").ifBlank { return@mapNotNull null }
            SpEpisode(
                id = id,
                uri = e.optString("uri").ifBlank { "spotify:episode:$id" },
                title = e.optString("name"),
                releaseDate = e.optString("release_date"),
                durationMs = e.optLong("duration_ms"),
                description = e.optString("description"),
                image = e.image(),
            )
        }

    /** Gli id degli artisti che segui, a pagine da 50 (al massimo [NewReleases.MAX_ARTISTS]). */
    suspend fun followedArtists(token: String): List<String> {
        val ids = mutableListOf<String>()
        var after: String? = null
        do {
            val path = "me/following?type=artist&limit=50" + (after?.let { "&after=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val (page, next) = NewReleases.parseFollowing(request(token, path))
            ids += page
            after = next
        } while (after != null && page.isNotEmpty() && ids.size < NewReleases.MAX_ARTISTS)
        return ids.distinct().take(NewReleases.MAX_ARTISTS)
    }

    /** Gli ultimi album e singoli di un artista, così come arrivano (servono le date di uscita). */
    suspend fun artistAlbumsRaw(token: String, artistId: String): JSONObject? =
        request(token, "artists/$artistId/albums?include_groups=album,single&limit=10")

    /** Gli artisti che segui con nome e foto (le storie in cima a Media), a pagine da 50. */
    suspend fun followedArtistItems(token: String): List<SpItem> {
        val list = mutableListOf<SpItem>()
        var after: String? = null
        do {
            val path = "me/following?type=artist&limit=50" + (after?.let { "&after=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val (page, next) = SpotifyArtist.following(request(token, path))
            list += page
            after = next
        } while (after != null && page.isNotEmpty() && list.size < NewReleases.MAX_ARTISTS)
        return list.distinctBy { it.id }.take(NewReleases.MAX_ARTISTS)
    }

    /** La pagina di un artista: scheda, popolari (dalla ricerca), tutta la discografia e dove appare, insieme. */
    suspend fun artistPage(token: String, artist: SpItem): SpArtistPage = coroutineScope {
        // Con l'accesso scaduto si ferma tutto (e si riprova col token nuovo); le altre parti mancanti restano vuote.
        val info = async {
            try {
                request(token, "artists/${artist.id}")
            } catch (e: SpotifyApiException) {
                if (e.code == 401) throw e else null
            }
        }
        val name = URLEncoder.encode("artist:\"${artist.title}\"", "UTF-8")
        val searches = (0..1).map { page ->
            async { runCatching { request(token, "search?q=$name&type=track&limit=10&offset=${page * 10}&market=from_token") }.getOrNull() }
        }
        // Le richieste in volo insieme, per tutti i gruppi: abbastanza veloce senza farsi rispondere 429.
        val gate = Semaphore(SpotifyArtist.PARALLEL)
        fun group(group: String, max: Int) = async { allReleases(token, artist.id, group, max, gate) }
        val albums = group("album", SpotifyArtist.RELEASES_MAX)
        val singles = group("single", SpotifyArtist.RELEASES_MAX)
        val compilations = group("compilation", SpotifyArtist.RELEASES_MAX)
        val appears = group("appears_on", SpotifyArtist.APPEARS_MAX)
        val json = info.await()
        SpArtistPage(
            artist = json?.let { parseItem(it) } ?: artist,
            photo = SpotifyArtist.photo(json).ifBlank { artist.image },
            followers = SpotifyArtist.followers(json),
            genres = SpotifyArtist.genres(json),
            popular = SpotifyArtist.popular(searches.awaitAll(), artist.id),
            albums = albums.await().map { it.first },
            singles = singles.await().map { it.first },
            compilations = compilations.await().map { it.first },
            appearsOn = appears.await().map { it.first },
            released = listOf(albums, singles, compilations, appears).flatMap { it.await() }
                .mapNotNull { (item, day) -> day?.let { item.id to it } }.toMap(),
        )
    }

    /**
     * Tutte le uscite di un gruppo (album, singoli, raccolte, dove appare), dalla più nuova (1.494, Antonio: "tutto
     * quello che hanno pubblicato"). Spotify le dà a pagine piccole: la prima dice quante sono, le altre si chiedono
     * insieme. Una pagina che non arriva manca e basta; col token scaduto si ferma tutto.
     */
    private suspend fun allReleases(token: String, artistId: String, group: String, max: Int, gate: Semaphore): List<Pair<SpItem, java.time.LocalDate?>> = coroutineScope {
        suspend fun page(offset: Int): JSONObject? = gate.withPermit {
            try {
                request(token, "artists/$artistId/albums?include_groups=$group&limit=${SpotifyArtist.PAGE}&offset=$offset&market=from_token")
            } catch (e: SpotifyApiException) {
                if (e.code == 401) throw e else null
            } catch (e: IOException) {
                null
            }
        }
        val first = page(0) ?: return@coroutineScope emptyList()
        val rest = SpotifyArtist.nextOffsets(first.optInt("total"), SpotifyArtist.PAGE, max).map { async { page(it) } }
        SpotifyArtist.releases(listOf(first) + rest.awaitAll())
    }

    suspend fun search(token: String, query: String): List<SpItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val result = request(token, "search?q=$q&type=track,artist,album,playlist,show&limit=10&market=from_token") ?: return emptyList()
        return listOf("artists", "tracks", "albums", "playlists", "shows").flatMap { key ->
            result.optJSONObject(key)?.optJSONArray("items").objects().mapNotNull { it.toItem() }
        }
    }

    suspend fun playback(token: String): SpPlayback? {
        val state = request(token, "me/player?additional_types=episode") ?: return null
        return SpPlayback(
            item = state.optJSONObject("item")?.toItem(),
            playing = state.optBoolean("is_playing"),
            device = state.optJSONObject("device")?.optString("name").orEmpty(),
            progressMs = state.optLong("progress_ms"),
            durationMs = state.optJSONObject("item")?.optLong("duration_ms") ?: 0,
        )
    }

    /** Fa partire un brano o una raccolta sul dispositivo attivo (Premium). */
    suspend fun play(token: String, item: SpItem, context: SpItem? = null) {
        val body = JSONObject()
        when {
            context != null && item.kind in setOf(SpKind.TRACK, SpKind.EPISODE) && context.kind != SpKind.ARTIST ->
                body.put("context_uri", context.uri).put("offset", JSONObject().put("uri", item.uri))
            item.kind in setOf(SpKind.TRACK, SpKind.EPISODE) -> body.put("uris", JSONArray().put(item.uri))
            else -> body.put("context_uri", item.uri)
        }
        request(token, "me/player/play", "PUT", body)
    }

    /**
     * Fa partire la musica sul dispositivo [deviceId] (il riproduttore dentro l'app): un brano,
     * oppure un album/playlist ([contextUri]) a partire da [offsetUri].
     */
    suspend fun playOn(token: String, deviceId: String, contextUri: String?, uris: List<String>, offsetUri: String? = null, positionMs: Long = 0) {
        val body = JSONObject()
        if (contextUri != null) body.put("context_uri", contextUri) else body.put("uris", JSONArray(uris))
        if (contextUri != null && offsetUri != null) body.put("offset", JSONObject().put("uri", offsetUri))
        if (positionMs > 0) body.put("position_ms", positionMs)
        request(token, "me/player/play?device_id=" + URLEncoder.encode(deviceId, "UTF-8"), "PUT", body)
    }

    suspend fun pause(token: String) { request(token, "me/player/pause", "PUT") }
    suspend fun resume(token: String) { request(token, "me/player/play", "PUT") }
    suspend fun next(token: String) { request(token, "me/player/next", "POST") }
    suspend fun previous(token: String) { request(token, "me/player/previous", "POST") }
    suspend fun seek(token: String, positionMs: Long) { request(token, "me/player/seek?position_ms=${positionMs.coerceAtLeast(0)}", "PUT") }

    /** Brani di una playlist: dal 2026 ogni voce ha il brano in "item", prima era in "track". */
    fun parsePlaylistItems(json: JSONObject?): List<SpItem> =
        json?.optJSONArray("items").objects()
            .mapNotNull { (it.optJSONObject("item") ?: it.optJSONObject("track"))?.toItem() }

    /** Album e singoli di un artista. */
    fun parseArtistAlbums(json: JSONObject?): List<SpItem> =
        json?.optJSONArray("items").objects().mapNotNull { it.toItem() }

    /** Un elemento singolo come arriva dalle API (brano, album, playlist…), o null se non si riconosce. */
    fun parseItem(json: JSONObject): SpItem? = json.toItem()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.image(): String {
        val images = optJSONArray("images") ?: optJSONObject("album")?.optJSONArray("images") ?: return ""
        // Le immagini arrivano dalla più grande: la media basta per le liste.
        val list = images.objects()
        return (list.getOrNull(1) ?: list.firstOrNull())?.optString("url").orEmpty()
    }

    private fun JSONObject.toItem(): SpItem? {
        val id = optString("id")
        if (id.isBlank()) return null
        val kind = when (optString("type")) {
            "track" -> SpKind.TRACK
            "album" -> SpKind.ALBUM
            "playlist" -> SpKind.PLAYLIST
            "artist" -> SpKind.ARTIST
            "show" -> SpKind.SHOW
            "episode" -> SpKind.EPISODE
            else -> return null
        }
        val artists = optJSONArray("artists").objects().joinToString(", ") { it.optString("name") }
        val subtitle = when (kind) {
            SpKind.TRACK -> artists
            SpKind.ALBUM -> listOf(artists, optString("release_date").take(4)).filter { it.isNotBlank() }.joinToString(" · ")
            SpKind.PLAYLIST -> optJSONObject("owner")?.optString("display_name").orEmpty().let { owner ->
                // Con la nuova API il conteggio sta in "items", prima in "tracks".
                val total = (optJSONObject("items") ?: optJSONObject("tracks"))?.optInt("total") ?: 0
                listOf(owner, if (total > 0) "$total brani" else "").filter { it.isNotBlank() }.joinToString(" · ")
            }
            SpKind.ARTIST -> "Artista"
            SpKind.SHOW -> optString("publisher")
            SpKind.EPISODE -> optString("release_date")
        }
        return SpItem(id, optString("uri"), kind, optString("name"), subtitle, image(), optLong("duration_ms"))
    }
}
