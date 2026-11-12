package com.platinum20.dashboard.media.spotify

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Un artista che hai ascoltato e quando l'ultima volta (storie in cima a Media, 1.484: il più recente per primo). */
data class SpListened(val artist: SpItem, val at: java.time.Instant)

/** Un artista che segui con la data della sua ultima uscita (per le storie in cima a Media). */
data class SpArtistLatest(val artist: SpItem, val latest: LocalDate?)

/**
 * La pagina di un artista (1.481, Antonio: "come su Spotify"): foto grande, i brani popolari, la discografia
 * divisa in album e singoli, dove appare. Spotify non dà più alle app personali i "brani più ascoltati":
 * i popolari sono i primi brani della ricerca per artista, che Spotify ordina per ascolti.
 */
data class SpArtistPage(
    val artist: SpItem,
    /** La foto più grande, per la testata. */
    val photo: String,
    /** 0 se Spotify non lo dice. */
    val followers: Long,
    val genres: List<String>,
    val popular: List<SpItem>,
    val albums: List<SpItem>,
    val singles: List<SpItem>,
    val appearsOn: List<SpItem>,
)

object SpotifyArtist {
    /** Quanti brani popolari si vedono prima di "Altri". */
    const val POPULAR_SHORT = 5
    const val POPULAR_MAX = 10

    /** La foto più grande di un artista (Spotify le dà dalla più grande). */
    fun photo(json: JSONObject?): String =
        json?.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()

    fun followers(json: JSONObject?): Long = json?.optJSONObject("followers")?.optLong("total") ?: 0L

    fun genres(json: JSONObject?): List<String> =
        json?.optJSONArray("genres").strings().take(3)

    /**
     * I brani popolari dalla ricerca `artist:"Nome"`: solo quelli dove l'artista c'è davvero (la ricerca
     * porta anche omonimi e cover), senza lo stesso titolo due volte (singolo e album).
     */
    fun popular(responses: List<JSONObject?>, artistId: String): List<SpItem> =
        responses.asSequence()
            .flatMap { it?.optJSONObject("tracks")?.optJSONArray("items").objects().asSequence() }
            .filter { track -> track.optJSONArray("artists").objects().any { it.optString("id") == artistId } }
            .mapNotNull { SpotifyApi.parseItem(it) }
            .distinctBy { it.title.lowercase().substringBefore(" - ").trim() }
            .take(POPULAR_MAX)
            .toList()

    /** Gli album di una risposta `artists/{id}/albums`, già nell'ordine di Spotify (dal più nuovo). */
    fun albums(json: JSONObject?): List<SpItem> =
        json?.optJSONArray("items").objects().mapNotNull { SpotifyApi.parseItem(it) }.distinctBy { it.id }

    /** La data dell'uscita più recente tra quelle di una risposta `artists/{id}/albums` (l'anno solo vale il 1° gennaio). */
    fun latestRelease(json: JSONObject?): LocalDate? =
        json?.optJSONArray("items").objects().mapNotNull { album -> date(album.optString("release_date")) }.maxOrNull()

    internal fun date(text: String): LocalDate? = runCatching {
        when (text.length) {
            10 -> LocalDate.parse(text)
            7 -> LocalDate.parse("$text-01")
            4 -> LocalDate.of(text.toInt(), 1, 1)
            else -> null
        }
    }.getOrNull()

    /** Una pagina di `me/following?type=artist` con nome e foto (per le storie), e il cursore della pagina dopo. */
    fun following(json: JSONObject?): Pair<List<SpItem>, String?> {
        val artists = json?.optJSONObject("artists") ?: return emptyList<SpItem>() to null
        val items = artists.optJSONArray("items").objects().mapNotNull { SpotifyApi.parseItem(it) }
        val after = artists.optJSONObject("cursors")?.optString("after")?.takeIf { it.isNotBlank() && it != "null" }
        return items to after
    }

    /**
     * Gli artisti di `me/player/recently-played`, dal più recente: solo il primo artista di ogni brano (gli ospiti
     * riempirebbero le storie), ognuno una volta con l'ultimo ascolto. La foto qui non c'è: la mette [SpotifyStore].
     */
    fun listened(json: JSONObject?): List<SpListened> =
        json?.optJSONArray("items").objects().mapNotNull { item ->
            val at = runCatching { java.time.Instant.parse(item.optString("played_at")) }.getOrNull() ?: return@mapNotNull null
            val first = item.optJSONObject("track")?.optJSONArray("artists")?.optJSONObject(0) ?: return@mapNotNull null
            if (!first.has("type")) first.put("type", "artist")
            SpotifyApi.parseItem(first)?.let { SpListened(it, at) }
        }.groupBy { it.artist.id }.map { (_, l) -> l.maxBy { it.at } }.sortedByDescending { it.at }

    /** I brani di `me/player/recently-played` con l'ora dell'ascolto, dal più recente (Cronologia di Media, 1.484). */
    fun played(json: JSONObject?): List<Pair<SpItem, java.time.Instant>> =
        json?.optJSONArray("items").objects().mapNotNull { item ->
            val at = runCatching { java.time.Instant.parse(item.optString("played_at")) }.getOrNull() ?: return@mapNotNull null
            item.optJSONObject("track")?.let { SpotifyApi.parseItem(it) }?.let { it to at }
        }

    /** "1,2 Mln di follower", "15.300 follower"; vuoto se non si sa. */
    fun followersLine(n: Long): String = when {
        n <= 0 -> ""
        n >= 1_000_000 -> "%.1f Mln di follower".format(java.util.Locale.ITALY, n / 1_000_000.0).replace(",0 ", " ")
        else -> "%,d follower".format(java.util.Locale.ITALY, n)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}
