package com.platinum20.dashboard.widget

import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.ui.screens.euroLabel
import com.platinum20.dashboard.ui.screens.gbLabel
import com.platinum20.dashboard.ui.screens.meterText
import com.platinum20.dashboard.ui.screens.renewalLine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una storia salvata per il widget News. */
data class SavedStory(val title: String, val link: String, val source: String, val sources: Int = 1, val at: Long = 0)

/** Il widget News, separato da RemoteViews per poterlo provare. */
object NewsWidgetRows {
    const val HEADER_DP = 40
    const val STORY_DP = 76

    fun storiesForHeight(heightDp: Int): Int = ((heightDp - HEADER_DP) / STORY_DP).coerceIn(1, 5)

    /** "3 NUOVE" accanto a NEWS; niente se non ce ne sono (o non si sa). */
    fun countLabel(fresh: Int?): String? = when {
        fresh == null || fresh <= 0 -> null
        fresh == 1 -> "1 NUOVA"
        else -> "$fresh NUOVE"
    }

    /** "ANSA · 4 fonti · 2 h fa": chi lo dice, quanti ne parlano, da quanto. */
    fun meta(story: SavedStory, now: Long): String = listOfNotNull(
        story.source.takeIf { it.isNotBlank() },
        story.sources.takeIf { it > 1 }?.let { "$it fonti" },
        story.at.takeIf { it > 0 }?.let { ago(it, now) },
    ).joinToString(" · ")

    fun ago(at: Long, now: Long): String {
        val minutes = (now - at) / 60_000
        return when {
            minutes < 1 -> "adesso"
            minutes < 60 -> "$minutes min fa"
            minutes < 24 * 60 -> "${minutes / 60} h fa"
            minutes < 48 * 60 -> "ieri"
            else -> "${minutes / (24 * 60)} giorni fa"
        }
    }

    fun encode(stories: List<SavedStory>): String = JSONArray().apply {
        stories.forEach { put(JSONObject().put("title", it.title).put("link", it.link).put("source", it.source).put("n", it.sources).put("at", it.at)) }
    }.toString()

    /** Legge le storie salvate; quelle di prima della 1.362 non avevano fonti e ora. */
    fun decode(raw: String?): List<SavedStory> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SavedStory(o.optString("title"), o.optString("link"), o.optString("source"), o.optInt("n", 1), o.optLong("at", 0))
            }.filter { it.title.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}

/** Cosa mostra il widget SIM: [progress] da 0 a 1000, null se non si sa il totale. */
data class SimModel(val big: String, val of: String, val progress: Int?, val line1: String, val line2: String)

object SimWidgetRows {
    fun model(status: SimStatus?, linked: Boolean, today: LocalDate): SimModel {
        if (!linked || status == null || !status.hasAny) {
            return SimModel("–", "", null, "Collega la SIM", "Impostazioni › Account")
        }
        val data = status.data
        val big = when {
            data == null -> status.credit?.let { euroLabel(it) } ?: "–"
            data.unlimited -> "∞"
            else -> gbLabel(data.left)
        }
        val of = when {
            data == null -> if (status.credit != null) "di credito" else ""
            data.unlimited -> "giga illimitati"
            data.total != null -> "di " + gbLabel(data.total)
            else -> "rimasti"
        }
        val progress = data?.fraction?.let { (it * 1000).toInt() }
        val line1 = status.renewal?.let { renewalLine(it, today) } ?: status.offer.orEmpty()
        val line2 = listOfNotNull(
            status.minutes?.let { "Minuti " + meterText(it, "") },
            status.credit?.takeIf { data != null }?.let { "credito " + euroLabel(it) },
        ).joinToString(" · ")
        return SimModel(big, of, progress, line1, line2)
    }
}
