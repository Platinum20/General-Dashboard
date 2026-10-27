package com.platinum20.dashboard.media.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import com.platinum20.dashboard.net.userMessage

/**
 * Accesso alle API di Spotify con il flusso PKCE: il login si fa nel browser sul sito di Spotify,
 * l'app riceve solo un codice e lo scambia con i token. Serve il Client ID dell'app creata su
 * developer.spotify.com (pubblico, nessun segreto nell'app).
 */
object SpotifyAuth {
    const val REDIRECT_URI = "com.platinum20.dashboard://spotify-callback"
    private const val PREFS = "spotify_api"
    // App "Canvas" registrata da Antonio su developer.spotify.com (il Client ID è pubblico, non è un segreto).
    const val DEFAULT_CLIENT_ID = "802a1bc467c041538598820b98e20f63"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_VERIFIER = "pkce_verifier"
    private const val KEY_STATE = "pkce_state"

    private val SCOPES = listOf(
        "user-read-private", "user-read-recently-played", "user-top-read",
        "playlist-read-private", "playlist-read-collaborative", "playlist-modify-private", "playlist-modify-public", "user-library-read", "user-follow-read",
        "user-read-playback-state", "user-modify-playback-state", "user-read-currently-playing",
        // Per ascoltare dentro l'app (Web Playback SDK, serve Premium).
        "streaming", "user-read-email",
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val refreshLock = Mutex()
    private val scope = MainScope()

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Ultimo errore del collegamento, da mostrare nella schermata di Spotify. */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _clientId.value = prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
        _connected.value = prefs.getString(KEY_REFRESH, null) != null
    }

    /** Indirizzo del login di Spotify, da aprire dentro l'app (nessun browser esterno). */
    fun loginUrl(): Uri {
        _error.value = null
        val verifier = randomString(64)
        val state = randomString(16)
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", _clientId.value)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()
        return url
    }

    fun isRedirect(uri: Uri): Boolean = uri.toString().startsWith(REDIRECT_URI)

    /** Ritorno dal login: scambia il codice con i token. */
    fun handleRedirect(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (error != null) {
            _error.value = if (error == "access_denied") "Accesso annullato" else "Spotify ha risposto: $error"
            return
        }
        val code = uri.getQueryParameter("code") ?: return
        if (uri.getQueryParameter("state") != prefs.getString(KEY_STATE, null)) {
            _error.value = "Risposta di Spotify non valida, riprova"
            return
        }
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return
        scope.launch {
            runCatching {
                tokenRequest(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to REDIRECT_URI,
                        "client_id" to _clientId.value,
                        "code_verifier" to verifier,
                    )
                )
            }.onFailure { _error.value = "Collegamento non riuscito. " + it.userMessage("Riprova tra poco.") }
        }
    }

    /**
     * Token valido per le chiamate, rinnovato quando scade. Senza il collegamento ufficiale si usa
     * quello del web player con l'accesso fatto nell'app. Null se nessuno dei due è disponibile.
     */
    suspend fun token(forceRefresh: Boolean = false): String? {
        if (!_connected.value) return SpotifyWebSession.token(appContext, forceRefresh)
        return officialToken(forceRefresh)
    }

    private suspend fun officialToken(forceRefresh: Boolean): String? = refreshLock.withLock {
        val access = prefs.getString(KEY_ACCESS, null)
        if (!forceRefresh && access != null && System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES, 0)) {
            return access
        }
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return try {
            tokenRequest(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to _clientId.value))
            prefs.getString(KEY_ACCESS, null)
        } catch (e: SpotifyRevokedException) {
            disconnect()
            _error.value = "Il collegamento con Spotify è scaduto, ricollegati"
            null
        }
    }

    /**
     * Ripulisce il Client ID incollato: vuoto = quello predefinito, altrimenti 32 caratteri
     * esadecimali (spazi e maiuscole tollerati). Null se non è un Client ID valido.
     */
    fun normalizeClientId(input: String): String? {
        val id = input.filterNot { it.isWhitespace() }.lowercase()
        if (id.isEmpty()) return DEFAULT_CLIENT_ID
        return id.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /** Cambia la chiave (Client ID): con un'app diversa i vecchi token non valgono più. */
    fun setClientId(id: String) {
        if (id == _clientId.value) return
        prefs.edit().apply { if (id == DEFAULT_CLIENT_ID) remove(KEY_CLIENT_ID) else putString(KEY_CLIENT_ID, id) }.apply()
        _clientId.value = id
        disconnect()
    }

    fun disconnect() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).apply()
        _connected.value = false
    }

    private class SpotifyRevokedException : IOException("invalid_grant")

    private suspend fun tokenRequest(form: Map<String, String>) = withContext(Dispatchers.IO) {
        val body = form.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        val conn = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) {
                if (json.optString("error") == "invalid_grant") throw SpotifyRevokedException()
                throw IOException(json.optString("error_description").ifBlank { "HTTP $code" })
            }
            val editor = prefs.edit()
                .putString(KEY_ACCESS, json.getString("access_token"))
                .putLong(KEY_EXPIRES, System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 120) * 1000)
            // Al rinnovo Spotify a volte non manda un nuovo refresh token: si tiene quello vecchio.
            json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { editor.putString(KEY_REFRESH, it) }
            editor.apply()
            _connected.value = true
        } finally {
            conn.disconnect()
        }
    }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val random = SecureRandom()
        return String(CharArray(length) { chars[random.nextInt(chars.length)] })
    }
}

/** Riceve il ritorno dal login di Spotify nel browser e riporta all'app. */
class SpotifyAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpotifyAuth.init(this)
        intent?.data?.let { SpotifyAuth.handleRedirect(it) }
        // Torna alla schermata da cui era partito il login, senza crearne una nuova.
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        finish()
    }
}
