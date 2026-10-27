package com.platinum20.dashboard.media

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.media.radio.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** VIDEO: video del telefono o da link; YOUTUBE: video; MUSIC: YouTube in solo audio; SPOTIFY; RADIO; PODCAST. */
enum class ResumeKind { VIDEO, YOUTUBE, MUSIC, SPOTIFY, RADIO, PODCAST }

/** Una cosa vista o ascoltata di recente, da riprendere in un tocco. */
data class ResumeItem(
    val kind: ResumeKind,
    /** Video: uri; YouTube e musica: id del video; Spotify: uri del brano; radio: id della stazione; podcast: id dell'episodio. */
    val key: String,
    val title: String,
    val subtitle: String = "",
    val image: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val at: Long,
    /** Solo radio: la stazione, per farla ripartire senza cercarla. */
    val station: RadioStation? = null,
    /** Spotify: l'album o la playlist da cui veniva il brano; podcast: l'audio dell'episodio. */
    val link: String = "",
    /** Solo podcast: il feed, per ritrovare l'episodio. */
    val feed: String = "",
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Lasciato a metà: c'è un punto da cui ripartire. */
    val unfinished: Boolean get() = kind != ResumeKind.RADIO && positionMs > 0

    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind.name).put("key", key).put("title", title).put("subtitle", subtitle).put("image", image)
        .put("position", positionMs).put("duration", durationMs).put("at", at)
        .apply { station?.let { put("station", it.toJson()) } }
        .put("link", link).put("feed", feed)

    companion object {
        fun fromJson(o: JSONObject) = ResumeItem(
            kind = ResumeKind.valueOf(o.getString("kind")),
            key = o.getString("key"),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            image = o.optString("image"),
            positionMs = o.optLong("position"),
            durationMs = o.optLong("duration"),
            at = o.optLong("at"),
            station = o.optJSONObject("station")?.let(RadioStation::fromJson),
            link = o.optString("link"),
            feed = o.optString("feed"),
        )

        fun fromVideo(v: VideoEntry) = ResumeItem(
            kind = ResumeKind.VIDEO,
            key = v.uri,
            title = v.title.ifBlank { "Video" },
            subtitle = if (v.isLocal) "Video del telefono" else "Video da link",
            positionMs = v.positionMs,
            durationMs = v.durationMs,
            at = v.openedAt,
        )
    }
}

/**
 * Cronologia di YouTube (player nativo) e radio per "Riprendi" in Media e in Adesso.
 * I video del telefono e i link hanno già la loro in [VideoLibrary].
 */
object MediaHistory {
    private const val PREFS = "media_history"
    private const val KEY_ITEMS = "items"
    /** Voci tenute per ogni fonte: una sessione di Spotify (una voce a brano) non spinge fuori i video di YouTube. */
    internal const val PER_KIND = 12

    private var prefs: SharedPreferences? = null
    private val _items = MutableStateFlow<List<ResumeItem>>(emptyList())
    val items: StateFlow<List<ResumeItem>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value = runCatching {
            val array = JSONArray(p.getString(KEY_ITEMS, "[]"))
            // Una voce illeggibile (es. di una versione futura) si salta, non svuota tutta la cronologia.
            List(array.length()) { runCatching { ResumeItem.fromJson(array.getJSONObject(it)) }.getOrNull() }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    fun record(item: ResumeItem) {
        save(trim(listOf(item) + _items.value.filterNot { it.kind == item.kind && it.key == item.key }))
    }

    /** Toglie le voci di un tipo (es. YouTube quando si svuota la sua cronologia) e restituisce le tolte. */
    fun removeKind(kind: ResumeKind): List<ResumeItem> {
        val (removed, kept) = _items.value.partition { it.kind == kind }
        save(kept)
        return removed
    }

    /** Rimette le voci tolte con [removeKind], dal più recente, senza doppioni. */
    fun restore(items: List<ResumeItem>) {
        save(trim((_items.value + items).distinctBy { it.kind to it.key }.sortedByDescending { it.at }))
    }

    /** Le più recenti [PER_KIND] voci di ogni fonte, nell'ordine dato: funzione pura, provata nei test. */
    internal fun trim(items: List<ResumeItem>): List<ResumeItem> {
        val seen = mutableMapOf<ResumeKind, Int>()
        return items.filter { item -> seen.merge(item.kind, 1, Int::plus)!! <= PER_KIND }
    }

    private fun save(list: List<ResumeItem>) {
        _items.value = list
        prefs?.edit()?.putString(KEY_ITEMS, JSONArray(list.map { it.toJson() }).toString())?.apply()
    }

    /** Dati finti per i test. */
    internal fun preview(items: List<ResumeItem>) {
        _items.value = items
    }

    fun recordRadio(station: RadioStation, at: Long = System.currentTimeMillis()) = record(
        ResumeItem(ResumeKind.RADIO, station.id, station.name, "Radio", station.logo, at = at, station = station),
    )
}

/** Sceglie cosa proporre di riprendere: funzione pura, facile da provare. */
object Resume {
    /** Oltre questa quota un video è considerato finito. */
    const val FINISHED = 0.95f
    const val ADESSO_MS = 24 * 60 * 60 * 1000L

    /** Dal più recente; niente doppioni, niente video finiti. */
    fun pick(items: List<ResumeItem>): List<ResumeItem> = items
        .filterNot { it.durationMs > 0 && it.progress >= FINISHED }
        .sortedByDescending { it.at }
        .distinctBy { it.kind to it.key }

    /** La riga di "Adesso": solo un video lasciato a metà nelle ultime 24 ore. */
    fun forAdesso(items: List<ResumeItem>, now: Long): ResumeItem? =
        pick(items).firstOrNull { it.unfinished && now - it.at in 0 until ADESSO_MS }

    /** "12:30 di 45:00", "1:02:03 di 2:00:00"; vuoto se non c'è un punto da cui ripartire. */
    fun progressLabel(item: ResumeItem): String = when {
        !item.unfinished -> ""
        item.durationMs > 0 -> "${clock(item.positionMs)} di ${clock(item.durationMs)}"
        else -> "da ${clock(item.positionMs)}"
    }

    /** Link del player YouTube che riparte dal punto lasciato. */
    fun youtubeLink(item: ResumeItem): String =
        "https://www.youtube.com/watch?v=${item.key}" + if (item.unfinished) "&t=${item.positionMs / 1000}s" else ""

    fun clock(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
