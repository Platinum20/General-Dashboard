package com.platinum20.dashboard.media.songid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.sqrt

/** Una canzone riconosciuta, con il brano di Spotify se c'è. */
data class Song(val title: String, val artist: String, val spotifyUri: String?)

/**
 * "Che canzone è?": Canvas ascolta qualche secondo di musica dal microfono, la riconosce (AudD,
 * con il token di Antonio salvato solo sul telefono), la fa partire su Spotify nel player
 * dell'app e la aggiunge alla playlist "Sentite da Canvas".
 */
object SongId {
    const val PLAYLIST_NAME = "Sentite da Canvas"
    private const val PREFS = "song_id"
    private const val KEY_TOKEN = "audd_token"
    private const val KEY_PLAYLIST = "playlist_id"
    private const val KEY_ADDED = "added"
    private const val RATE = 16_000
    private const val SECONDS = 7

    private lateinit var prefs: SharedPreferences
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    /** Sta ascoltando la musica: il fumetto sopra il + mostra le barre come per la voce. */
    var listening by mutableStateOf(false)
        private set
    var level by mutableFloatStateOf(0f)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _token.value = prefs.getString(KEY_TOKEN, null).orEmpty()
    }

    fun setToken(value: String) {
        _token.value = value.trim()
        prefs.edit().putString(KEY_TOKEN, _token.value).apply()
    }

    val ready: Boolean get() = _token.value.isNotBlank()

    sealed interface Outcome {
        data class Found(val song: Song, val playing: Boolean, val inPlaylist: Boolean) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Ascolta, riconosce, fa partire e salva nella playlist. */
    suspend fun identify(context: Context): Outcome {
        init(context)
        if (!ready) return Outcome.Failed("Per riconoscere le canzoni metti il token AudD in Impostazioni › Media")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return Outcome.Failed("Serve il permesso del microfono")
        }
        val wav = try {
            listening = true
            record()
        } catch (e: Exception) {
            return Outcome.Failed("Il microfono è occupato, riprova")
        } finally {
            listening = false
            level = 0f
        }
        val song = runCatching { recognize(wav) }.getOrElse { return Outcome.Failed("Riconoscimento non riuscito, controlla la connessione") }
            ?: return Outcome.Failed("Non ho riconosciuto la canzone")
        SpotifyAuth.init(context)
        val spToken = runCatching { SpotifyAuth.token() }.getOrNull()
        val uri = song.spotifyUri ?: spToken?.let { t ->
            runCatching { SpotifyApi.search(t, "${song.title} ${song.artist}") }.getOrNull()
                ?.firstOrNull { it.uri.startsWith("spotify:track:") }?.uri
        }
        val found = song.copy(spotifyUri = uri)
        if (uri == null || spToken == null) return Outcome.Found(found, playing = false, inPlaylist = false)
        val playing = SpotifyPlayer.playLink(context, uri)
        val saved = runCatching { addToPlaylist(spToken, uri) }.getOrDefault(false)
        return Outcome.Found(found, playing, saved)
    }

    @SuppressLint("MissingPermission")
    private suspend fun record(): ByteArray = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED)
        val pcm = ByteArrayOutputStream(RATE * 2 * SECONDS)
        val buffer = ShortArray(RATE / 10)
        try {
            recorder.startRecording()
            var samples = 0
            while (isActive && samples < RATE * SECONDS) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buffer[i].toInt()
                    sum += s * s
                    pcm.write(s and 0xff)
                    pcm.write((s shr 8) and 0xff)
                }
                level = (sqrt(sum / n) / 6_000.0).toFloat().coerceIn(0f, 1f)
                samples += n
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        wav(pcm.toByteArray())
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xff) }
        fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xff) }
        out.write("RIFF".toByteArray()); int(36 + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
        out.write("data".toByteArray()); int(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    private suspend fun recognize(wav: ByteArray): Song? = withContext(Dispatchers.IO) {
        val boundary = "canvas" + System.currentTimeMillis()
        val conn = (URL("https://api.audd.io/").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                field("api_token", _token.value)
                field("return", "spotify")
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"canvas.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
                out.write(wav)
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("status") != "success") throw IllegalStateException(json.optJSONObject("error")?.optString("error_message"))
            parse(json)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(json: JSONObject): Song? {
        val result = json.optJSONObject("result") ?: return null
        val title = result.optString("title").ifBlank { return null }
        val uri = result.optJSONObject("spotify")?.optString("uri")?.takeIf { it.startsWith("spotify:track:") }
        return Song(title, result.optString("artist"), uri)
    }

    /** Aggiunge il brano alla playlist "Sentite da Canvas", creandola la prima volta. Una volta sola per brano. */
    private suspend fun addToPlaylist(token: String, uri: String): Boolean {
        val added = prefs.getStringSet(KEY_ADDED, emptySet()).orEmpty()
        if (uri in added) return true
        val id = playlistId(token) ?: return false
        val body = JSONObject().put("uris", JSONArray().put(uri))
        val ok = call(token, "playlists/$id/items", "POST", body) != null || call(token, "playlists/$id/tracks", "POST", body) != null
        if (ok) prefs.edit().putStringSet(KEY_ADDED, added + uri).apply()
        return ok
    }

    private suspend fun playlistId(token: String): String? {
        prefs.getString(KEY_PLAYLIST, null)?.let { return it }
        var next: String? = "me/playlists?limit=50"
        var pages = 0
        while (next != null && pages++ < 4) {
            val page = call(token, next, "GET") ?: break
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val p = items.optJSONObject(i) ?: continue
                if (p.optString("name") == PLAYLIST_NAME) return p.optString("id").also { save(it) }
            }
            next = page.optString("next").takeIf { it.startsWith("http") }?.substringAfter("/v1/")
        }
        val body = JSONObject()
            .put("name", PLAYLIST_NAME)
            .put("public", false)
            .put("description", "Le canzoni che Canvas ha riconosciuto intorno a te")
        val created = call(token, "me/playlists", "POST", body)
            ?: call(token, "me", "GET")?.optString("id")?.let { me -> call(token, "users/${URLEncoder.encode(me, "UTF-8")}/playlists", "POST", body) }
        return created?.optString("id")?.takeIf { it.isNotBlank() }?.also { save(it) }
    }

    private fun save(id: String) = prefs.edit().putString(KEY_PLAYLIST, id).apply()

    /** Una chiamata alla Web API di Spotify; null se non va (permesso mancante, endpoint cambiato). */
    private suspend fun call(token: String, path: String, method: String, body: JSONObject? = null): JSONObject? =
        withContext(Dispatchers.IO) {
            val conn = (URL("https://api.spotify.com/v1/$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode !in 200..299) return@withContext null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }
}
