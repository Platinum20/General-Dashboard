package com.platinum20.dashboard.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import java.time.LocalDateTime

/**
 * Widget "Oggi" (il primo widget di Canvas, il nome della classe resta per non toglierlo dalla Home).
 * Giro 2 (Antonio, 2026-11-21): come la card Adesso, il meteo con le prossime ore e sotto la prossima cosa.
 * Le righe "Ancora da fare: 2 abitudini" non dicevano cosa: le abitudini ora stanno nel widget Da fare.
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
            val zone = java.time.ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            views.setOnClickPendingIntent(android.R.id.background, Widgets.open(context, OpenRequest(OpenRequest.HOME), 100))
            views.setOnClickPendingIntent(R.id.today_hero, Widgets.open(context, OpenRequest(OpenRequest.WEATHER), 101))

            val forecast = WeatherStore.cached(context)
            views.setTextViewText(R.id.today_date, WidgetRows.todayLabel(now.toLocalDate(), forecast?.place))
            views.removeAllViews(R.id.today_hours)
            views.setViewVisibility(R.id.today_hours, View.GONE)
            val parts = WidgetRows.todayParts(size)
            if (forecast != null) {
                val c = forecast.current
                val today = forecast.today
                views.setTextViewText(R.id.today_icon, WeatherCodes.emoji(c.code, c.isDay))
                views.setTextViewText(R.id.today_temp, c.temperature.asTemp())
                views.setTextViewText(
                    R.id.today_desc,
                    WidgetRows.weatherLine(WeatherCodes.description(c.code), today?.max?.asTemp(), today?.min?.asTemp(), today?.precipitationProbability),
                )
                // Le prossime ore come nella card Adesso della Home (HourStrip), se c'è spazio.
                // La prima colonna è "Ora", come nella Home.
                val hours = WidgetRows.hours(forecast.hourly, now, parts.hours - 1, step = 1)
                if (parts.hours > 0) {
                    views.setViewVisibility(R.id.today_hours, View.VISIBLE)
                    views.addView(R.id.today_hours, RemoteViews(context.packageName, R.layout.widget_hour).apply {
                        setTextViewText(R.id.hour_time, "Ora")
                        setTextViewText(R.id.hour_icon, WeatherCodes.emoji(c.code, c.isDay))
                        setTextViewText(R.id.hour_temp, c.temperature.asTemp())
                    })
                    hours.forEach { h ->
                        views.addView(R.id.today_hours, RemoteViews(context.packageName, R.layout.widget_hour).apply {
                            setTextViewText(R.id.hour_time, WidgetRows.hourLabel(h.time))
                            setTextViewText(R.id.hour_icon, WeatherCodes.emoji(h.code, h.isDay))
                            setTextViewText(R.id.hour_temp, h.temperature.asTemp())
                        })
                    }
                }
            }

            // Sotto, una riga sola: la prossima cosa vera del calendario (le abitudini hanno il widget Da fare).
            val next = if (!parts.next) null else runCatching {
                AgendaRows.next(TasksWidget.loadDays(context, zone), System.currentTimeMillis(), zone)
            }.getOrNull()
            views.setViewVisibility(R.id.today_next, if (next == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.today_next_text, next.orEmpty())
            views.setOnClickPendingIntent(R.id.today_next, Widgets.open(context, OpenRequest(OpenRequest.NOTES), 102))
            views
        }
    }
}
