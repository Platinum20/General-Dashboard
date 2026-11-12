package com.platinum20.dashboard.media.lastfm

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un brano come lo dà Last.fm: solo nome e artista, su Spotify si cerca dopo. */
data class FmTrack(val title: String, val artist: String) {
    /** Lo stesso brano scritto in modi diversi ("Feat.", maiuscole) resta uno. */
    val key: String get() = LastFmParse.norm(artist) + "|" + LastFmParse.norm(title)
}

/**
 * Last.fm (gratis, con la chiave che Antonio crea su last.fm/api): è l'unica fonte aperta per i brani e gli
 * artisti simili e per le classifiche, che Spotify non dà più alle app personali. La chiave resta sul telefono.
 */
object LastFm {
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    private const val PREFS = "lastfm"
    private const val KEY = "api_key"

    private var prefs: SharedPreferences? = null
    private val _key = MutableStateFlow("")
    val key: StateFlow<String> = _key.asStateFlow()
    val ready: Boolean get() = _key.value.isNotBlank()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            _key.value = Vault.read(it, KEY).orEmpty()
        }
    }

    fun setKey(value: String) {
        _key.value = value.trim()
        prefs?.let { Vault.put(it.edit(), KEY, _key.value).apply() }
    }

    private suspend fun call(method: String, vararg params: Pair<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val key = _key.value.ifBlank { throw IOException("Manca la chiave Last.fm: mettila in Impostazioni › Media") }
        val query = (params.toList() + listOf("method" to method, "api_key" to key, "format" to "json", "autocorrect" to "1"))
            .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val conn = (URL("$BASE?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Canvas/1.0")
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull() ?: throw IOException("Last.fm non risponde ($code)")
            if (json.has("error")) {
                throw IOException(if (json.optInt("error") == 10) "La chiave Last.fm non è valida" else "Last.fm: " + json.optString("message"))
            }
            json
        } finally {
            conn.disconnect()
        }
    }

    suspend fun similarTracks(track: FmTrack, limit: Int = 30): List<FmTrack> =
        LastFmParse.tracks(call("track.getsimilar", "artist" to track.artist, "track" to track.title, "limit" to "$limit"), "similartracks")

    suspend fun similarArtists(artist: String, limit: Int = 12): List<String> =
        LastFmParse.artists(call("artist.getsimilar", "artist" to artist, "limit" to "$limit"))

    /** I generi di un artista (i tag più usati su Last.fm), quando Spotify non li dà. */
    suspend fun artistTags(artist: String): List<String> =
        LastFmParse.tags(call("artist.gettoptags", "artist" to artist))

    suspend fun topItaly(limit: Int = 50): List<FmTrack> =
        LastFmParse.tracks(call("geo.gettoptracks", "country" to "italy", "limit" to "$limit"), "tracks")

    suspend fun topWorld(limit: Int = 50): List<FmTrack> =
        LastFmParse.tracks(call("chart.gettoptracks", "limit" to "$limit"), "tracks")
}

/** Lettura delle risposte di Last.fm, separata per i test. */
object LastFmParse {
    /** Tag che non sono generi: si saltano. */
    private val NOT_GENRES = setOf("seen live", "favorites", "favourite", "italian", "italia", "italy", "female vocalists", "male vocalists", "usa", "uk", "british", "american")

    fun tracks(json: JSONObject?, root: String): List<FmTrack> =
        json?.optJSONObject(root)?.optJSONArray("track").objects().mapNotNull { t ->
            val title = t.optString("name").trim()
            val artist = t.optJSONObject("artist")?.let { it.optString("name").ifBlank { it.optString("#text") } }
                ?: t.optString("artist")
            if (title.isBlank() || artist.isBlank()) null else FmTrack(title, artist.trim())
        }.distinctBy { it.key }

    fun artists(json: JSONObject?): List<String> =
        json?.optJSONObject("similarartists")?.optJSONArray("artist").objects().map { it.optString("name").trim() }.filter { it.isNotBlank() }

    fun tags(json: JSONObject?): List<String> =
        json?.optJSONObject("toptags")?.optJSONArray("tag").objects().map { it.optString("name").trim().lowercase() }
            .filter { it.isNotBlank() && it !in NOT_GENRES && !it.any(Char::isDigit) }
            .take(5)

    /** Minuscole, senza "feat." e parentesi: per riconoscere lo stesso brano o artista. */
    fun norm(text: String): String = text.lowercase()
        .replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
        .replace(Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s.*"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
