package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.platinum20.dashboard.net.userMessage

/**
 * Accesso alle API di YouTube con l'account Google del telefono (solo lettura).
 * Funziona dopo aver registrato l'app su Google Cloud: pacchetto e firma bastano, nessuna chiave nell'app.
 */
object YouTubeAuth {
    private const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val PREFS = "youtube_api"
    private const val KEY_CONNECTED = "connected"
    const val GUIDE_URL = "https://claude.ai/artifact/MGivavbbvKjLz3kkKAMyHE"

    sealed interface Outcome {
        data class Authorized(val token: String) : Outcome
        /** Serve il consenso dell'utente: va mostrata la schermata di Google. */
        data class NeedsConsent(val sender: IntentSender) : Outcome
        data class Failed(val message: String, val notConfigured: Boolean) : Outcome
    }

    private val _connected = MutableStateFlow(false)
    /** L'utente ha collegato il suo YouTube (e non l'ha scollegato). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var token: String? = null
    private var expiresAt = 0L
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        _connected.value = prefs(context).getBoolean(KEY_CONNECTED, false)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    suspend fun authorize(context: Context): Outcome = try {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pending = result.pendingIntent
        val access = result.accessToken
        when {
            result.hasResolution() && pending != null -> Outcome.NeedsConsent(pending.intentSender)
            access != null -> {
                remember(context, access)
                Outcome.Authorized(access)
            }
            else -> Outcome.Failed("Google non ha restituito l'accesso", notConfigured = false)
        }
    } catch (e: ApiException) {
        Outcome.Failed(
            message = if (e.statusCode == CommonStatusCodes.NETWORK_ERROR) "Nessuna connessione a Internet" else "Google ha rifiutato la richiesta, riprova",
            notConfigured = e.statusCode == CommonStatusCodes.DEVELOPER_ERROR,
        )
    } catch (e: Exception) {
        Outcome.Failed(e.userMessage("Riprova tra poco"), notConfigured = false)
    }

    /** Risultato della schermata di consenso di Google. */
    fun handleConsent(context: Context, data: Intent?): Boolean {
        val access = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        }.getOrNull() ?: return false
        remember(context, access)
        return true
    }

    /** Token valido per le chiamate, rinnovato in silenzio quando scade. Null se serve di nuovo il consenso. */
    suspend fun token(context: Context, forceRefresh: Boolean = false): String? {
        val cached = token
        if (!forceRefresh && cached != null && System.currentTimeMillis() < expiresAt) return cached
        return (authorize(context) as? Outcome.Authorized)?.token
    }

    suspend fun disconnect(context: Context) {
        val old = token
        token = null
        expiresAt = 0
        _connected.value = false
        prefs(context).edit().putBoolean(KEY_CONNECTED, false).apply()
        if (old != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL("https://oauth2.googleapis.com/revoke?token=" + URLEncoder.encode(old, "UTF-8"))
                        .openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.responseCode
                    conn.disconnect()
                }
            }
        }
    }

    private fun remember(context: Context, access: String) {
        token = access
        // I token di Google durano un'ora: lo rinnoviamo qualche minuto prima.
        expiresAt = System.currentTimeMillis() + 50 * 60_000L
        _connected.value = true
        prefs(context).edit().putBoolean(KEY_CONNECTED, true).apply()
    }
}
