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
    val stops: List<TrainStop> = emptyList(),
    /**
     * L'arrivo previsto con questo ritardo (arrivo del biglietto + ritardo); 0 = non saputo (letture vecchie).
     * Passato quello il viaggio è chiuso: niente più letture e il ritardo resta per sempre (Antonio, 2026-11-20).
     */
    val until: Long = 0L,
) {
    /** Il treno è arrivato (col ritardo): quest'ultima lettura è quella che resta. */
    fun closed(now: Long): Boolean = until > 0L && now >= until
}

/** Una fermata del treno con l'orario programmato (senza ritardo), per i punti sulla linea della notifica. */
data class TrainStop(val name: String, val at: Long)

/**
 * Il ritardo dal vivo dei treni (Il viaggio vivo, 1.467): i dati pubblici e gratuiti di ViaggiaTreno
 * (Trenitalia), in JSON, disegnati da noi. Nessun sito mostrato. La lettura è pura, per i test.
 */
object TrainLive {
    internal const val BASE = "https://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno/"
    /** Fino a quando una lettura si dice "appena fatta"; dopo resta valida ma dice quanto è vecchia. */
    const val FRESH_MS = 10 * 60_000L
    /**
     * Quanto teniamo l'ultima lettura (Antonio, 2026-11-20): se la rete cade a metà viaggio gli orari restano
     * quelli col ritardo dell'ultimo rilevamento, non tornano quelli del biglietto. Basta per un viaggio lungo.
     */
    const val KEEP_MS = 12 * 60 * 60_000L
    /** Quanto teniamo i viaggi chiusi: come i giorni passati del calendario, con margine. */
    const val KEEP_PAST_MS = 400L * 24 * 60 * 60_000L

    /** Da tenere: lettura recente, o viaggio chiuso (arrivato) da meno di [KEEP_PAST_MS]. */
    fun kept(s: TrainStatus, now: Long): Boolean =
        if (s.closed(now)) now - s.until < KEEP_PAST_MS else now - s.readAt < KEEP_MS

    private val _statuses = MutableStateFlow<Map<Long, TrainStatus>>(emptyMap())
    /** L'ultimo stato letto per ogni evento-treno (id dell'evento), salvato su file: resta anche se l'app si chiude. */
    val statuses: StateFlow<Map<Long, TrainStatus>> = _statuses.asStateFlow()
    @Volatile private var file: java.io.File? = null

    /** Carica le ultime letture salvate (una volta per processo: app o giro della notifica). */
    fun init(context: android.content.Context) {
        if (file != null) return
        val f = java.io.File(context.filesDir, "train-live.json")
        file = f
        val now = System.currentTimeMillis()
        val saved = runCatching { decode(f.readText()) }.getOrDefault(emptyMap()).filterValues { kept(it, now) }
        _statuses.update { saved + it }
    }

    /**
     * L'ultima lettura dell'evento, anche se non è di adesso (al massimo [KEEP_MS], per sempre se il treno è
     * arrivato): gli orari restano spostati dal ritardo e la riga dice quando l'abbiamo letto (regola 4).
     * Null se non l'abbiamo mai letto.
     */
    fun known(eventId: Long, now: Long = System.currentTimeMillis()): TrainStatus? =
        _statuses.value[eventId]?.takeIf { kept(it, now) }

    /**
     * Va riletto a viaggio finito (dopo l'arrivo del biglietto [arrival]): non sappiamo com'è finito ([status] null,
     * per esempio perso con un aggiornamento) o la lettura non è chiusa. ViaggiaTreno tiene il treno per la giornata.
     */
    fun needsLastRead(status: TrainStatus?, arrival: Long, now: Long): Boolean =
        now > arrival && now - arrival < KEEP_MS && status?.closed(now) != true

    /**
     * Chiude le letture senza [TrainStatus.until] (salvate prima della 1.540): passato l'arrivo col ritardo restano per
     * sempre. Senza rete, basta l'orario del biglietto.
     */
    fun close(eventId: Long, arrival: Long, now: Long = System.currentTimeMillis()) {
        val s = _statuses.value[eventId]?.takeIf { it.until == 0L && kept(it, now) } ?: return
        _statuses.update { it + (eventId to s.copy(until = arrival + shiftMs(s))) }
        save()
    }

    /** Letto da meno di [FRESH_MS]. */
    fun isFresh(s: TrainStatus, now: Long): Boolean = now - s.readAt < FRESH_MS

    /** Le letture in JSON, per il file: ritardo, binario, soppresso, quando, fermate. */
    fun encode(map: Map<Long, TrainStatus>): String = org.json.JSONObject().apply {
        map.forEach { (id, s) ->
            put(id.toString(), org.json.JSONObject().apply {
                put("delay", s.delay); s.platform?.let { put("platform", it) }; put("cancelled", s.cancelled); put("readAt", s.readAt)
                if (s.until > 0L) put("until", s.until)
                put("stops", org.json.JSONArray().apply { s.stops.forEach { put(org.json.JSONObject().put("name", it.name).put("at", it.at)) } })
            })
        }
    }.toString()

    fun decode(json: String): Map<Long, TrainStatus> {
        val root = JSONObject(json)
        return root.keys().asSequence().mapNotNull { key ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val o = root.optJSONObject(key) ?: return@mapNotNull null
            val stops = o.optJSONArray("stops")
            id to TrainStatus(
                delay = o.optInt("delay"),
                platform = o.optString("platform").takeIf { o.has("platform") && it.isNotEmpty() },
                cancelled = o.optBoolean("cancelled"),
                readAt = o.optLong("readAt"),
                until = o.optLong("until"),
                stops = (0 until (stops?.length() ?: 0)).mapNotNull { i -> stops!!.optJSONObject(i)?.let { TrainStop(it.optString("name"), it.optLong("at")) } },
            )
        }.toMap()
    }

    private fun save() {
        val f = file ?: return
        val now = System.currentTimeMillis()
        runCatching { f.writeText(encode(_statuses.value.filterValues { kept(it, now) })) }
    }

    /**
     * Legge il treno ora; null se ViaggiaTreno non risponde o non lo conosce. [arrival] = arrivo del biglietto:
     * a viaggio chiuso (arrivato col ritardo) non legge più e rende l'ultima lettura, che resta così.
     */
    suspend fun refresh(eventId: Long, trip: Trip, departure: Long, arrival: Long = 0L, zone: ZoneId = ZoneId.systemDefault()): TrainStatus? {
        _statuses.value[eventId]?.takeIf { it.closed(System.currentTimeMillis()) }?.let { return it }
        val number = number(trip.train) ?: return null
        val day = Instant.ofEpochMilli(departure).atZone(zone).toLocalDate()
        val status = runCatching {
            val found = origin(get(BASE + "cercaNumeroTrenoTrenoAutocomplete/$number"), trip.from) ?: return null
            val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
            parse(get(BASE + "andamentoTreno/${found}/$number/$midnight"), trip.from, System.currentTimeMillis())
        }.getOrNull()?.let { if (arrival > 0L) it.copy(until = arrival + shiftMs(it)) else it } ?: return null
        _statuses.update { it + (eventId to status) }
        save()
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
        val all = mutableListOf<TrainStop>()
        if (stops != null) for (i in 0 until stops.length()) {
            val stop = stops.optJSONObject(i) ?: continue
            val name = stop.optString("stazione").trim().takeIf { it.isNotEmpty() && it != "null" } ?: continue
            val time = listOf("programmata", "partenza_teorica", "arrivo_teorico")
                .firstNotNullOfOrNull { k -> stop.optLong(k, 0L).takeIf { it > 0L } } ?: continue
            all += TrainStop(name, time)
        }
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
            stops = all,
        )
    }

    /**
     * Le fermate fra dove sali ([from]) e dove scendi ([to]), esclusi questi due: i punti sulla linea della
     * notifica. Se dove sali non si trova si parte dall'inizio; se dove scendi non si trova si va fino in fondo.
     */
    fun between(stops: List<TrainStop>, from: String?, to: String?): List<TrainStop> {
        fun index(name: String?): Int {
            val w = name?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return -1
            return stops.indexOfFirst { s -> val n = plain(s.name); n.isNotEmpty() && (n.contains(w.substringBefore(' ')) || w.contains(n)) }
        }
        val start = index(from)
        val end = index(to).takeIf { it > start } ?: stops.size
        return stops.subList((start + 1).coerceAtMost(end), end)
    }

    /** "BARI CENTRALE" → "Bari Centrale", come lo scriveresti tu. */
    fun pretty(name: String): String = name.trim().lowercase().split(Regex("""\s+""")).joinToString(" ") { w ->
        w.split('-').joinToString("-") { it.replaceFirstChar(Char::titlecase) }
    }

    /** "Ritardo" per la riga e la notifica: "In orario", "+5 min", "2 min in anticipo", "Soppresso". */
    fun delayText(s: TrainStatus): String = when {
        s.cancelled -> "Soppresso"
        s.delay > 0 -> "+${s.delay} min"
        s.delay < 0 -> "${-s.delay} min in anticipo"
        else -> "In orario"
    }

    /**
     * Di quanto spostare partenza e arrivo: solo un ritardo vero su un treno che viaggia. In orario, in anticipo
     * o soppresso restano gli orari programmati (Antonio, 2026-11-20). Passare solo uno stato fresco ([fresh]).
     */
    fun shiftMinutes(s: TrainStatus?): Int = if (s == null || s.cancelled) 0 else s.delay.coerceAtLeast(0)

    fun shiftMs(s: TrainStatus?): Long = shiftMinutes(s) * 60_000L

    private val clock = java.time.format.DateTimeFormatter.ofPattern("H:mm")

    /**
     * La riga dell'ultima lettura sulla pagina del viaggio (regola 4): "Ultimo rilevamento 14:44" se fresca,
     * "Ultimo rilevamento 14:44 · 35 min fa" se vecchia (gli orari restano quelli di quella lettura),
     * "Ritardo non disponibile" se ViaggiaTreno non ha mai risposto; null se non l'abbiamo ancora chiesto.
     */
    fun readLine(last: TrainStatus?, tried: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (last == null || last.readAt <= 0L) return if (tried) "Ritardo non disponibile" else null
        val at = Instant.ofEpochMilli(last.readAt).atZone(zone).format(clock)
        return if (isFresh(last, now)) "Ultimo rilevamento $at" else "Ultimo rilevamento $at · ${ago(now - last.readAt)}"
    }

    /** "35 min fa", "2 h fa": quanto è vecchia una lettura. */
    fun ago(ms: Long): String {
        val m = ms / 60_000L
        return if (m < 60) "$m min fa" else "${m / 60} h fa"
    }

    /** Lo stato letto per ultimo, anche vecchio: solo per dire quando l'abbiamo letto. */
    fun last(eventId: Long): TrainStatus? = _statuses.value[eventId]

    /** "+5 min · binario 4", per la riga del treno nel Calendario. */
    fun line(s: TrainStatus): String =
        listOfNotNull(delayText(s), s.platform?.takeIf { !s.cancelled }?.let { "binario $it" }).joinToString(" · ")

    private fun plain(s: String): String = s.uppercase().replace(Regex("""[^A-Z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()
}
