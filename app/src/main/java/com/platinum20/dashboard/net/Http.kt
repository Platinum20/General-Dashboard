package com.platinum20.dashboard.net

import com.platinum20.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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
