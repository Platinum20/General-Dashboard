package com.platinum20.dashboard.trips

import com.platinum20.dashboard.calendar.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Com'è il treno adesso, letto da ViaggiaTreno: ritardo in minuti (negativo = in anticipo), binario di partenza
 * dalla stazione dove sali, soppresso. [readAt] = quando l'abbiamo letto, per non mostrare dati vecchi come nuovi.
 */
data class TrainStatus(
    val delay: Int,
    val platform: String? = null,
    val cancelled: Boolean = false,
    val readAt: Long = 0L,
)

/**
 * Il ritardo dal vivo dei treni (Il viaggio vivo, 1.467): i dati pubblici e gratuiti di ViaggiaTreno
 * (Trenitalia), in JSON, disegnati da noi. Nessun sito mostrato. La lettura è pura, per i test.
 */
object TrainLive {
    internal const val BASE = "https://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/"
    /** Dopo quanto un ritardo letto non vale più: la riga torna a dire solo gli orari. */
    const val FRESH_MS = 10 * 60_000L

    private val _statuses = MutableStateFlow<Map<Long, TrainStatus>>(emptyMap())
    /** Lo stato letto per ogni evento-treno (id dell'evento), finché l'app resta aperta. */
    val statuses: StateFlow<Map<Long, TrainStatus>> = _statuses.asStateFlow()

    /** Lo stato dell'evento se letto da poco, altrimenti null (regola 4: un ritardo vecchio non si spaccia per vero). */
    fun fresh(eventId: Long, now: Long = System.currentTimeMillis()): TrainStatus? =
        _statuses.value[eventId]?.takeIf { now - it.readAt < FRESH_MS }

    /** Legge il treno ora; null se ViaggiaTreno non risponde o non lo conosce. */
    suspend fun refresh(eventId: Long, trip: Trip, departure: Long, zone: ZoneId = ZoneId.systemDefault()): TrainStatus? {
        val number = number(trip.train) ?: return null
        val day = Instant.ofEpochMilli(departure).atZone(zone).toLocalDate()
        val status = runCatching {
            val found = origin(get(BASE + "cercaNumeroTrenoTrenoAutocomplete/$number"), trip.from) ?: return null
            val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
            parse(get(BASE + "andamentoTreno/${found}/$number/$midnight"), trip.from, System.currentTimeMillis())
        }.getOrNull() ?: return null
        _statuses.update { it + (eventId to status) }
        return status
    }

    /**
     * GET che segue i rimandi da sé (ViaggiaTreno rimanda fra https e http, che HttpURLConnection non segue).
     * L'http è permesso solo per viaggiatreno.it (network_security_config).
     */
    internal suspend fun get(url: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var address = url
        repeat(6) {
            val conn = (java.net.URL(address).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) Canvas")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    address = java.net.URL(java.net.URL(address), conn.getHeaderField("Location") ?: throw java.io.IOException("HTTP $code")).toString()
                    return@repeat
                }
                if (code !in 200..299) throw java.io.IOException("HTTP $code da $address")
                return@withContext conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw java.io.IOException("Troppi rimandi da $url")
    }

    /** Il numero del treno: "Intercity 612" → "612". */
    fun number(train: String?): String? = train?.let { Regex("""(\d{2,5})""").find(it)?.value }

    /**
     * Dalla risposta di "cercaNumeroTrenoTrenoAutocomplete" (righe "612 - TARANTO - 11/11/26|612-S11119-1762815600000")
     * il codice della stazione d'origine. Con più treni dallo stesso numero sceglie quello che parte da [from], se c'è.
     */
    fun origin(text: String, from: String?): String? {
        val rows = text.lines().map { it.trim() }.filter { '|' in it }.mapNotNull { line ->
            val label = line.substringBefore('|')
            val code = line.substringAfter('|').split('-').getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            label to code
        }
        if (rows.isEmpty()) return null
        val wanted = from?.let(::plain)
        return (rows.firstOrNull { (label, _) -> wanted != null && plain(label).contains(wanted.substringBefore(' ')) } ?: rows.first()).second
    }

    /**
     * Da "andamentoTreno": ritardo generale, binario (effettivo, altrimenti programmato) della fermata [from],
     * soppresso se "provvedimento" vale 1. Null se il JSON non è quello di un treno.
     */
    fun parse(json: String, from: String?, now: Long): TrainStatus? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (!root.has("ritardo") && !root.has("fermate")) return null
        val stops = root.optJSONArray("fermate")
        val wanted = from?.let(::plain)
        var platform: String? = null
        if (stops != null && wanted != null) {
            for (i in 0 until stops.length()) {
                val stop = stops.optJSONObject(i) ?: continue
                val name = plain(stop.optString("stazione"))
                if (name.isEmpty() || !(name.contains(wanted.substringBefore(' ')) || wanted.contains(name))) continue
                platform = listOf("binarioEffettivoPartenzaDescrizione", "binarioProgrammatoPartenzaDescrizione")
                    .firstNotNullOfOrNull { k -> stop.optString(k).trim().takeIf { it.isNotEmpty() && it != "null" } }
                break
            }
        }
        return TrainStatus(
            delay = root.optInt("ritardo", 0),
            platform = platform,
            cancelled = root.optInt("provvedimento", 0) == 1,
            readAt = now,
        )
    }

    /** "Ritardo" per la riga e la notifica: "In orario", "+5 min", "2 min in anticipo", "Soppresso". */
    fun delayText(s: TrainStatus): String = when {
        s.cancelled -> "Soppresso"
        s.delay > 0 -> "+${s.delay} min"
        s.delay < 0 -> "${-s.delay} min in anticipo"
        else -> "In orario"
    }

    /** "+5 min · binario 4", per la riga del treno nel Calendario. */
    fun line(s: TrainStatus): String =
        listOfNotNull(delayText(s), s.platform?.takeIf { !s.cancelled }?.let { "binario $it" }).joinToString(" · ")

    private fun plain(s: String): String = s.uppercase().replace(Regex("""[^A-Z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()
}
