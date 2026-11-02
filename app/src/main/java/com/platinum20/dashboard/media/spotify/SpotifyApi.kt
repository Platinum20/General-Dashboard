package com.platinum20.dashboard.media.spotify

import kotlinx.coroutines.Dispatchers
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

    suspend fun topTracks(token: String): List<SpItem> =
        request(token, "me/top/tracks?time_range=short_term&limit=30")?.optJSONArray("items").objects().mapNotNull { it.toItem() }

    suspend fun topArtists(token: String): List<SpItem> =
        request(token, "me/top/artists?time_range=medium_term&limit=20")?.optJSONArray("items").objects().mapNotNull { it.toItem() }

    suspend fun playlists(token: String): List<SpItem> =
        request(token, "me/playlists?limit=50")?.optJSONArray("items").objects().mapNotNull { it.toItem() }

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
