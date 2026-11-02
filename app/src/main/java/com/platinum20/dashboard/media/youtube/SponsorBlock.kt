package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.settings.AppSettings
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Cosa fare di un tipo di segmento: le stesse quattro scelte dell'estensione ufficiale. */
enum class SbAction(val key: String) {
    AUTO("auto"),
    MANUAL("manual"),
    SHOW("show"),
    OFF("off"),
}

/**
 * I tipi di segmento di SponsorBlock, con i colori ufficiali della barra. [actions] sono le scelte che
 * hanno senso per quel tipo: il momento clou si raggiunge invece di saltarlo, l'etichetta del video
 * intero si può solo mostrare o ignorare.
 */
enum class SbCategory(
    val key: String,
    val label: String,
    val detail: String,
    val color: Long,
    val default: SbAction,
    val actions: List<SbAction> = SbAction.entries,
) {
    SPONSOR("sponsor", "Sponsor", "Pubblicità pagata, link e codici sconto", 0xFF00D400, SbAction.AUTO),
    SELFPROMO("selfpromo", "Autopromozione", "Merch, donazioni, Patreon, altri canali", 0xFFFFFF00, SbAction.MANUAL),
    INTERACTION("interaction", "Richiesta di interazione", "«Iscriviti», «metti mi piace», «commenta»", 0xFFCC00FF, SbAction.MANUAL),
    HIGHLIGHT(
        "poi_highlight", "Momento clou", "Il punto che quasi tutti cercano", 0xFFFF1684, SbAction.MANUAL,
    ),
    INTRO("intro", "Sigla e intermezzi", "Animazioni o pause senza contenuto", 0xFF00FFFF, SbAction.MANUAL),
    OUTRO("outro", "Titoli di coda", "Saluti, schermate finali e crediti", 0xFF0202ED, SbAction.MANUAL),
    PREVIEW("preview", "Anteprima e riassunto", "Spezzoni di cosa arriva o di cosa è successo", 0xFF008FD6, SbAction.SHOW),
    HOOK("hook", "Gancio iniziale", "Il teaser all'inizio per farti restare", 0xFF395699, SbAction.SHOW),
    FILLER("filler", "Divagazioni", "Battute e parti non necessarie al video", 0xFF7300FF, SbAction.SHOW),
    MUSIC_OFFTOPIC(
        "music_offtopic", "Musica: parti senza musica", "Nei video musicali, ciò che non è la canzone", 0xFFFF9900, SbAction.MANUAL,
    ),
    EXCLUSIVE_ACCESS(
        "exclusive_access", "Accesso esclusivo", "Tutto il video mostra un prodotto avuto dal marchio", 0xFF008A5C, SbAction.SHOW,
        listOf(SbAction.SHOW, SbAction.OFF),
    );

    /** Il nome della scelta per questo tipo (il momento clou si raggiunge, l'etichetta si mostra). */
    fun actionLabel(action: SbAction): String = when (this) {
        HIGHLIGHT -> when (action) {
            SbAction.AUTO -> "Vai lì da solo"
            SbAction.MANUAL -> "Mostra il tasto per andarci"
            SbAction.SHOW -> "Solo nella barra"
            SbAction.OFF -> "Ignora"
        }
        EXCLUSIVE_ACCESS -> if (action == SbAction.OFF) "Ignora" else "Mostra l'etichetta"
        else -> when (action) {
            SbAction.AUTO -> "Salta da solo"
            SbAction.MANUAL -> "Mostra il tasto Salta"
            SbAction.SHOW -> "Solo nella barra"
            SbAction.OFF -> "Ignora"
        }
    }

    /** La scelta in breve, a destra nella riga delle impostazioni. */
    fun shortLabel(action: SbAction): String = when (action) {
        SbAction.AUTO -> "Da solo"
        SbAction.MANUAL -> "Tasto"
        SbAction.SHOW -> if (this == EXCLUSIVE_ACCESS) "Etichetta" else "Barra"
        SbAction.OFF -> "Ignora"
    }

    /** Cosa succede con questa scelta, una riga sotto il nome nel menu. */
    fun actionDetail(action: SbAction): String = when (action) {
        SbAction.AUTO -> if (this == HIGHLIGHT) "All'inizio il video va lì da solo" else "Il video lo salta senza chiedere"
        SbAction.MANUAL -> "Compare un tasto sul video, decidi tu"
        SbAction.SHOW -> if (this == EXCLUSIVE_ACCESS) "Un'etichetta sotto il titolo del video" else "Segnato a colori sulla barra del tempo"
        SbAction.OFF -> "Come se non ci fosse"
    }

    companion object {
        fun of(key: String): SbCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Come va trattato un segmento: saltato, messo muto, raggiunto (momento clou) o etichetta del video intero. */
enum class SbType { SKIP, MUTE, POI, FULL }

data class SbSegment(
    val uuid: String,
    val category: SbCategory,
    val type: SbType,
    val startMs: Long,
    val endMs: Long,
)

/** Cosa fare adesso, deciso da [SponsorBlock.step]: funzione pura, provata nei test. */
data class SbStep(
    /** Dove saltare da soli (null = niente salto). */
    val skipTo: Long? = null,
    val skipped: SbSegment? = null,
    /** Dentro un segmento da mettere muto. */
    val mute: Boolean = false,
    /** Il segmento per cui mostrare il tasto sul video. */
    val offer: SbSegment? = null,
)

/** Il video di adesso per SponsorBlock: i segmenti trovati e il tasto da mostrare sul video. */
data class SbState(
    val videoId: String,
    val segments: List<SbSegment> = emptyList(),
    val offer: SbSegment? = null,
)

/** Quanto ha già saltato SponsorBlock, per la riga nelle impostazioni. */
data class SbStats(val skips: Int = 0, val savedMs: Long = 0)

/**
 * SponsorBlock nel player unico: chiede al database pubblico (sponsor.ajay.app) i segmenti del video
 * e mentre suona li salta, li mette muti o mostra il tasto, secondo le scelte per ogni tipo in
 * Impostazioni. La richiesta usa solo i primi 4 caratteri dell'hash del video, come l'estensione:
 * il server non sa quale video stai guardando.
 */
object SponsorBlock {
    private const val API = "https://sponsor.ajay.app/api"
    /** Un segmento si salta solo se ne manca almeno questo: alla fine è inutile. */
    private const val TAIL_MS = 500L
    /** Il tasto del momento clou resta solo all'inizio del video. */
    private const val HIGHLIGHT_OFFER_MS = 20_000L
    private const val PREFS = "sponsorblock"

    private val scope = MainScope()
    private var job: Job? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    /** Segmenti già usati (momento clou raggiunto) o annullati: non si risaltano. */
    private val done = mutableSetOf<String>()
    private var muted = false

    private val _state = MutableStateFlow<SbState?>(null)
    val state: StateFlow<SbState?> = _state.asStateFlow()
    private val _stats = MutableStateFlow(SbStats())
    val stats: StateFlow<SbStats> = _stats.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            _stats.value = SbStats(it.getInt("skips", 0), it.getLong("saved_ms", 0))
        }
    }

    /** È partito il video [videoId] (lo chiama YtPlayer): si cercano i segmenti e si segue il player. */
    fun attach(context: Context, videoId: String, live: Boolean) {
        init(context)
        detach()
        if (live) return
        _state.value = SbState(videoId)
        job = scope.launch {
            if (!AppSettings.sponsorBlock.value) return@launch
            val segments = runCatching { withContext(Dispatchers.IO) { fetch(videoId) } }.getOrDefault(emptyList())
            if (segments.isEmpty() || _state.value?.videoId != videoId) return@launch
            _state.value = SbState(videoId, segments)
            // Cinque controlli al secondo: un salto arriva entro un quinto di secondo dall'inizio.
            while (isActive) {
                delay(200)
                if (YtPlayer.now.value?.stream?.id != videoId) break
                tick(videoId, segments)
            }
            setMuted(false)
        }
    }

    /** Suona altro: niente più salti né tasti. */
    fun detach() {
        job?.cancel()
        job = null
        setMuted(false)
        done.clear()
        _state.value = null
    }

    private fun tick(videoId: String, segments: List<SbSegment>) {
        val p = CanvasPlayer.current() ?: return
        if (p.currentMediaItem?.mediaId != videoId) return
        if (!AppSettings.sponsorBlock.value) {
            setMuted(false)
            _state.value = _state.value?.copy(offer = null)
            return
        }
        val position = p.currentPosition
        val s = step(segments, AppSettings.sponsorActions.value, position, done)
        val target = s.skipTo
        val skipped = s.skipped
        if (target != null && skipped != null && p.playWhenReady) {
            if (skipped.type == SbType.POI) {
                done += skipped.uuid
                CanvasPlayer.seekTo(target)
            } else {
                skip(skipped, position, target)
            }
            return
        }
        setMuted(s.mute)
        if (_state.value?.offer != s.offer) _state.value = _state.value?.copy(offer = s.offer)
    }

    /** Tocco sul tasto sul video: salta il segmento (o va al momento clou). */
    fun takeOffer() {
        val offer = _state.value?.offer ?: return
        val p = CanvasPlayer.current() ?: return
        _state.value = _state.value?.copy(offer = null)
        if (offer.type == SbType.POI) {
            done += offer.uuid
            CanvasPlayer.seekTo(offer.startMs)
        } else {
            skip(offer, p.currentPosition, offer.endMs)
        }
    }

    private fun skip(segment: SbSegment, from: Long, to: Long) {
        CanvasPlayer.seekTo(to)
        record(to - from)
        report(segment.uuid)
        val context = appContext ?: return
        CaptureFeedback.tell(context, "Saltato: ${segment.category.label}", Icons.Outlined.FastForward) {
            // Annulla: si torna all'inizio del segmento e questo non si salta più.
            done += segment.uuid
            CanvasPlayer.seekTo(segment.startMs)
        }
    }

    private fun setMuted(on: Boolean) {
        if (on == muted) return
        muted = on
        CanvasPlayer.setVolume(if (on) 0f else 1f)
    }

    private fun record(savedMs: Long) {
        val stats = SbStats(_stats.value.skips + 1, _stats.value.savedMs + savedMs.coerceAtLeast(0))
        _stats.value = stats
        prefs?.edit()?.putInt("skips", stats.skips)?.putLong("saved_ms", stats.savedMs)?.apply()
    }

    /** Come l'estensione: dice al database che il segmento è servito (conta per chi l'ha segnato). */
    private fun report(uuid: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val connection = URL("$API/viewedVideoSponsorTime?UUID=${URLEncoder.encode(uuid, "UTF-8")}").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.responseCode
                connection.disconnect()
            }
        }
    }

    private fun fetch(videoId: String): List<SbSegment> {
        val connection = URL(url(videoId)).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        return try {
            // 404 = nessun segmento per questi 4 caratteri di hash.
            if (connection.responseCode != 200) emptyList()
            else parse(connection.inputStream.bufferedReader().use { it.readText() }, videoId)
        } finally {
            connection.disconnect()
        }
    }

    /** La richiesta per i segmenti di [videoId], con il solo prefisso dell'hash. */
    internal fun url(videoId: String): String {
        val categories = SbCategory.entries.joinToString(",", "[", "]") { "\"${it.key}\"" }
        val types = "[\"skip\",\"mute\",\"poi\",\"full\"]"
        return "$API/skipSegments/${hashPrefix(videoId)}" +
            "?categories=${URLEncoder.encode(categories, "UTF-8")}&actionTypes=${URLEncoder.encode(types, "UTF-8")}"
    }

    internal fun hashPrefix(videoId: String): String =
        MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(4)

    /** Dalla risposta (tutti i video col prefisso) i segmenti di [videoId], in ordine. */
    internal fun parse(json: String, videoId: String): List<SbSegment> {
        val videos = JSONArray(json)
        val out = mutableListOf<SbSegment>()
        for (i in 0 until videos.length()) {
            val video = videos.optJSONObject(i) ?: continue
            if (video.optString("videoID") != videoId) continue
            val segments = video.optJSONArray("segments") ?: continue
            for (j in 0 until segments.length()) {
                val o = segments.optJSONObject(j) ?: continue
                val category = SbCategory.of(o.optString("category")) ?: continue
                val type = when (o.optString("actionType", "skip")) {
                    "skip" -> SbType.SKIP
                    "mute" -> SbType.MUTE
                    "poi" -> SbType.POI
                    "full" -> SbType.FULL
                    else -> continue
                }
                val range = o.optJSONArray("segment") ?: continue
                val start = (range.optDouble(0, 0.0) * 1000).toLong()
                val end = (range.optDouble(1, 0.0) * 1000).toLong()
                if (type == SbType.SKIP || type == SbType.MUTE) if (end <= start) continue
                out += SbSegment(o.optString("UUID"), category, type, start, end)
            }
        }
        return out.sortedBy { it.startMs }
    }

    /**
     * Cosa fare a [positionMs]. Un salto automatico arriva in fondo anche ai segmenti da saltare che
     * si accavallano o si toccano (sponsor seguito da autopromozione = un salto solo).
     */
    internal fun step(
        segments: List<SbSegment>,
        actions: Map<SbCategory, SbAction>,
        positionMs: Long,
        done: Set<String> = emptySet(),
    ): SbStep {
        fun action(s: SbSegment) = actions[s.category] ?: s.category.default
        fun inside(s: SbSegment) = positionMs >= s.startMs && positionMs < s.endMs - TAIL_MS
        val auto = segments.filter { it.type == SbType.SKIP && action(it) == SbAction.AUTO && it.uuid !in done }
        auto.firstOrNull { inside(it) }?.let { first ->
            var end = first.endMs
            var grew = true
            while (grew) {
                grew = false
                for (s in auto) if (s.startMs <= end + 1_000 && s.endMs > end) {
                    end = s.endMs
                    grew = true
                }
            }
            return SbStep(skipTo = end, skipped = first)
        }
        val highlight = segments.firstOrNull { it.type == SbType.POI && it.uuid !in done && positionMs < it.startMs }
        if (highlight != null && action(highlight) == SbAction.AUTO && positionMs < 3_000) {
            return SbStep(skipTo = highlight.startMs, skipped = highlight)
        }
        val mute = segments.any { it.type == SbType.MUTE && action(it) == SbAction.AUTO && it.uuid !in done && inside(it) }
        val offer = segments.firstOrNull {
            (it.type == SbType.SKIP || it.type == SbType.MUTE) && action(it) == SbAction.MANUAL && it.uuid !in done && inside(it)
        } ?: highlight?.takeIf { action(it) == SbAction.MANUAL && positionMs < HIGHLIGHT_OFFER_MS }
        return SbStep(mute = mute, offer = offer)
    }

    /** I segmenti da segnare sulla barra del tempo: tutti tranne quelli ignorati e l'etichetta del video intero. */
    internal fun marks(segments: List<SbSegment>, actions: Map<SbCategory, SbAction>): List<SbSegment> =
        segments.filter { it.type != SbType.FULL && (actions[it.category] ?: it.category.default) != SbAction.OFF }

    /** L'etichetta del video intero (es. «Accesso esclusivo»), se va mostrata. */
    internal fun label(segments: List<SbSegment>, actions: Map<SbCategory, SbAction>): SbCategory? =
        segments.firstOrNull { it.type == SbType.FULL && (actions[it.category] ?: it.category.default) != SbAction.OFF }?.category
}
