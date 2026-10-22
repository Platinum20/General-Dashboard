package com.platinum20.dashboard.media.radio

import com.platinum20.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logo: String,
    val tags: String,
    val country: String,
    val bitrate: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("url", streamUrl).put("logo", logo)
        .put("tags", tags).put("country", country).put("bitrate", bitrate)

    companion object {
        fun fromJson(o: JSONObject) = RadioStation(
            id = o.optString("id"),
            name = o.optString("name"),
            streamUrl = o.optString("url"),
            logo = o.optString("logo"),
            tags = o.optString("tags"),
            country = o.optString("country"),
            bitrate = o.optInt("bitrate"),
        )
    }
}

/** Elenco pubblico e gratuito di radio online (radio-browser.info), senza chiavi. */
object RadioApi {
    // Il servizio ha più server equivalenti: se uno non risponde si prova il successivo.
    private val servers = listOf("de1", "de2", "fi1", "at1").map { "https://$it.api.radio-browser.info/json/" }

    suspend fun popular(countryCode: String = "IT"): List<RadioStation> =
        stations("stations/search?countrycode=$countryCode&order=clickcount&reverse=true&limit=80&hidebroken=true")

    suspend fun search(query: String): List<RadioStation> =
        stations(
            "stations/search?name=" + URLEncoder.encode(query, "UTF-8") +
                "&order=clickcount&reverse=true&limit=60&hidebroken=true"
        )

    /** Segnala l'ascolto al servizio, che lo usa per la classifica (come fanno le altre app). */
    suspend fun click(station: RadioStation) {
        runCatching { get("url/${station.id}") }
    }

    private suspend fun stations(path: String): List<RadioStation> {
        val array = JSONArray(get(path))
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isBlank()) null else RadioStation(
                id = o.optString("stationuuid"),
                name = o.optString("name").trim(),
                streamUrl = url,
                logo = o.optString("favicon"),
                tags = o.optString("tags").split(',').filter { it.isNotBlank() }.take(3).joinToString(", "),
                country = o.optString("country"),
                bitrate = o.optInt("bitrate"),
            )
        }.distinctBy { it.name.lowercase() }
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (server in servers) {
            try {
                val conn = (URL(server + path).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "GeneralDashboard/${BuildConfig.VERSION_NAME}")
                }
                try {
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    return@withContext conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("Elenco delle radio non raggiungibile", last)
    }
}
