package com.platinum20.dashboard.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.Briefing
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import java.time.LocalDate

/**
 * Widget "Oggi" (il primo widget di Canvas, il nome della classe resta per non toglierlo dalla Home):
 * il blocco colorato col meteo e sotto pioggia e abitudini di "Adesso" più tutto Oggi del calendario.
 */
class DashboardWidget : CanvasWidget(Renderer, WidgetSize(250, 180)) {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_BRIEF) {
            val action = runCatching { BriefAction.valueOf(intent.getStringExtra(EXTRA_ACTION).orEmpty()) }.getOrNull()
            val id = intent.getLongExtra(EXTRA_TARGET, -1L)
            if (action != null && id >= 0) performBrief(context, action, id)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_BRIEF = "com.platinum20.dashboard.widget.BRIEF_ACTION"
        const val EXTRA_ACTION = "brief_action"
        const val EXTRA_TARGET = "brief_target"

        /** Il tasto Fatto di una riga: promemoria spuntato, pacco arrivato o abitudine fatta, senza aprire l'app. */
        internal fun performBrief(context: Context, action: BriefAction, id: Long) {
            NotesStore.init(context)
            when (action) {
                BriefAction.TASK_DONE -> NotesStore.setTaskDone(id)
                BriefAction.PARCEL_ARRIVED ->
                    if (NotesStore.data.value.parcels.any { it.id == id && !it.delivered }) NotesStore.toggleDelivered(id)
                BriefAction.HABIT_DONE -> NotesStore.markHabitDone(id)
                else -> return
            }
            // NotesStore ridisegna già i widget a ogni modifica.
        }

        private val Renderer: WidgetRenderer = { context, size ->
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setOnClickPendingIntent(android.R.id.background, Widgets.open(context, OpenRequest(OpenRequest.HOME), 100))
            views.setOnClickPendingIntent(R.id.today_hero, Widgets.open(context, OpenRequest(OpenRequest.WEATHER), 101))
            views.setTextViewText(R.id.today_date, WidgetRows.dateLabel(LocalDate.now()))

            val forecast = WeatherStore.cached(context)
            if (forecast != null) {
                val c = forecast.current
                val today = forecast.today
                views.setTextViewText(R.id.today_icon, WeatherCodes.emoji(c.code, c.isDay))
                views.setTextViewText(R.id.today_temp, c.temperature.asTemp())
                views.setTextViewText(R.id.today_place, forecast.place)
                views.setTextViewText(
                    R.id.today_desc,
                    WidgetRows.weatherLine(WeatherCodes.description(c.code), today?.max?.asTemp(), today?.min?.asTemp(), today?.precipitationProbability),
                )
            }

            // Pioggia e abitudini di Adesso, poi lo stesso Oggi del calendario: ricalcolati adesso a ogni disegno.
            val max = WidgetRows.linesForHeight(size.heightDp)
            val lines = if (max == 0) emptyList() else {
                val zone = java.time.ZoneId.systemDefault()
                val brief = runCatching { Briefing.load(context, max = Int.MAX_VALUE) }.getOrDefault(emptyList())
                val days = runCatching { TasksWidget.loadDays(context, zone) }.getOrDefault(emptyList())
                WidgetRows.todayEntries(brief, AgendaRows.todayItems(days, LocalDate.now(zone), zone), max)
            }
            views.removeAllViews(R.id.today_lines)
            views.setViewVisibility(R.id.today_lines, if (lines.isEmpty()) View.GONE else View.VISIBLE)
            lines.forEachIndexed { i, line ->
                val row = RemoteViews(context.packageName, R.layout.widget_row_line)
                row.setTextViewText(R.id.line_text, line.text)
                row.setInt(R.id.line_dot, "setColorFilter", context.getColor(if (line.urgent) R.color.widget_late else R.color.widget_accent))
                row.setOnClickPendingIntent(R.id.line_root, Widgets.open(context, OpenRequest(OpenRequest.HOME), 100))
                val actionable = line.action != null && line.targetId != null
                row.setViewVisibility(R.id.line_action, if (actionable) View.VISIBLE else View.GONE)
                if (actionable) {
                    row.setOnClickPendingIntent(
                        R.id.line_action,
                        Widgets.broadcast(context, DashboardWidget::class.java, ACTION_BRIEF, 110 + i) {
                            putExtra(EXTRA_ACTION, line.action!!.name)
                            putExtra(EXTRA_TARGET, line.targetId!!)
                        },
                    )
                }
                views.addView(R.id.today_lines, row)
            }
            views
        }
    }
}
