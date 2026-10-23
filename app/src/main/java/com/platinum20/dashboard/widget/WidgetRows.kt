package com.platinum20.dashboard.widget

import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import org.json.JSONArray
import org.json.JSONObject

/** Una riga di "Adesso" nel widget, con l'azione rapida se si può fare dalla schermata Home. */
data class WidgetBrief(val text: String, val action: BriefAction? = null, val targetId: Long? = null)

/** Le righe di testo del widget, separate da RemoteViews per poterle provare. */
object WidgetRows {
    /** Sotto la temperatura: "Sereno · 22°/10° · pioggia 40%" (la pioggia solo dal 30%). */
    fun weatherLine(description: String, max: String?, min: String?, rainChance: Int?): String =
        description +
            (if (max != null && min != null) " · $max/$min" else "") +
            (rainChance?.takeIf { it >= 30 }?.let { " · pioggia $it%" } ?: "")

    /** Quante notizie stanno nel widget: tre se non c'è altro, due se sopra ci sono le righe di Adesso o i promemoria. */
    fun newsCount(briefLines: Int, hasTasks: Boolean): Int = if (briefLines == 0 && !hasTasks) 3 else 2

    /** Azioni che il widget sa fare da solo: pacco arrivato e abitudine fatta (i promemoria hanno il loro widget). */
    private val WIDGET_ACTIONS = setOf(BriefAction.PARCEL_ARRIVED, BriefAction.HABIT_DONE)

    /** Le due righe di "Adesso" del widget, senza i promemoria (hanno la loro riga). */
    fun briefEntries(lines: List<BriefLine>): List<WidgetBrief> =
        lines.filter { it.kind != BriefKind.TASK }.take(2).map { line ->
            val id = line.targetIds.singleOrNull()
            if (line.action in WIDGET_ACTIONS && id != null) WidgetBrief(line.text, line.action, id) else WidgetBrief(line.text)
        }

    fun encodeBrief(entries: List<WidgetBrief>): String = JSONArray().apply {
        entries.forEach { e ->
            put(JSONObject().put("t", e.text).apply {
                if (e.action != null && e.targetId != null) put("a", e.action.name).put("id", e.targetId)
            })
        }
    }.toString()

    /** Legge le righe salvate; le versioni prima della 1.111 salvavano solo i testi. */
    fun decodeBrief(raw: String?): List<WidgetBrief> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull array.optString(i).takeIf { it.isNotBlank() }?.let { WidgetBrief(it) }
                val action = runCatching { BriefAction.valueOf(o.optString("a")) }.getOrNull()
                val id = if (o.has("id")) o.optLong("id") else null
                WidgetBrief(o.optString("t"), action.takeIf { id != null }, id.takeIf { action != null })
            }
        }.getOrDefault(emptyList())
    }

    /** Dopo l'azione la riga sparisce subito, senza aspettare il prossimo aggiornamento. */
    fun withoutTarget(entries: List<WidgetBrief>, action: BriefAction, id: Long): List<WidgetBrief> =
        entries.filterNot { it.action == action && it.targetId == id }
}
