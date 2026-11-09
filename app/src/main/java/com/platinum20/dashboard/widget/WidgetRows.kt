package com.platinum20.dashboard.widget

import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.weather.HourlyPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una riga di "Adesso" nel widget Oggi, con l'azione rapida se si può fare dalla schermata Home. */
data class WidgetBrief(val text: String, val urgent: Boolean = false, val action: BriefAction? = null, val targetId: Long? = null)

/** Le parti di testo dei widget, separate da RemoteViews per poterle provare. */
object WidgetRows {
    private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
    private val HOUR = DateTimeFormatter.ofPattern("HH")

    /** Sotto la temperatura: "Sereno · 22°/10° · pioggia 40%" (la pioggia solo dal 30%). */
    fun weatherLine(description: String, max: String?, min: String?, rainChance: Int?): String =
        description +
            (if (max != null && min != null) " · $max/$min" else "") +
            (rainChance?.takeIf { it >= 30 }?.let { " · pioggia $it%" } ?: "")

    /** "LUNEDÌ 9 NOVEMBRE": l'etichetta in cima al widget Oggi. */
    fun dateLabel(day: LocalDate): String = day.format(LONG_DATE).uppercase(Locale.ITALIAN)

    /** "Lunedì 9": il titolo del widget Calendario. */
    fun dayTitle(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN)).replaceFirstChar { it.titlecase(Locale.ITALIAN) }

    /** Azioni che il widget sa fare da solo: spunta, pacco arrivato, abitudine fatta. */
    private val WIDGET_ACTIONS = setOf(BriefAction.TASK_DONE, BriefAction.PARCEL_ARRIVED, BriefAction.HABIT_DONE)

    /** Le righe di "Adesso" del widget Oggi, con il tasto Fatto quando agisce su una cosa sola. */
    fun briefEntries(lines: List<BriefLine>, max: Int): List<WidgetBrief> =
        lines.take(max.coerceAtLeast(0)).map { line ->
            val id = line.targetIds.singleOrNull()
            if (line.action in WIDGET_ACTIONS && id != null) WidgetBrief(line.text, line.urgent, line.action, id)
            else WidgetBrief(line.text, line.urgent)
        }

    /** Quante righe di Adesso stanno sotto il blocco del meteo: 34dp l'una, niente se il widget è basso. */
    fun linesForHeight(heightDp: Int): Int = ((heightDp - HERO_DP) / LINE_DP).coerceIn(0, 5)

    private const val HERO_DP = 112
    private const val LINE_DP = 34

    /** Le prossime ore del widget Meteo: una ogni [step], a partire dall'ora dopo quella di adesso. */
    fun hours(hourly: List<HourlyPoint>, now: LocalDateTime, count: Int, step: Int = 2): List<HourlyPoint> {
        val next = hourly.filter { it.time.isAfter(now) }
        return next.filterIndexed { i, _ -> i % step == 0 }.take(count.coerceAtLeast(0))
    }

    /** "15": l'ora di una colonna del widget Meteo. */
    fun hourLabel(time: LocalDateTime): String = time.format(HOUR)

    /** Quante ore stanno nel widget Meteo: servono spazio in altezza e almeno 56dp a colonna. */
    fun hourColumns(size: WidgetSize): Int = if (size.heightDp < 170) 0 else (size.widthDp / 56).coerceIn(3, 6)
}
