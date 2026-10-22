package com.platinum20.dashboard.net

import com.platinum20.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Richieste GET semplici per le API JSON (meteo, località). */
object Http {
    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "GeneralDashboard/${BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "it-IT,it;q=0.9")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Messaggio leggibile per un errore, senza testi tecnici in inglese per i problemi di rete. */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        "Nessuna connessione a Internet. Controlla la rete e riprova."
    is SocketTimeoutException -> "La connessione è troppo lenta, riprova tra poco."
    is SSLException -> "Connessione sicura non riuscita, riprova più tardi."
    else -> message?.takeIf { it.isNotBlank() }?.let {
        if (it.startsWith("HTTP ")) "Il servizio non risponde (${it.removePrefix("HTTP ")}), riprova più tardi." else it
    } ?: fallback
}
