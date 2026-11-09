package com.platinum20.dashboard.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.bank.RevolutStore
import com.platinum20.dashboard.news.NewsStory
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import java.time.LocalDate
import java.time.LocalDateTime

/** Widget "News": le storie nuove dall'ultima visita, con quante testate ne parlano. */
class NewsWidget : CanvasWidget(Renderer, WidgetSize(250, 220)) {
    companion object {
        private const val PREFS = "widget"
        private const val KEY_STORIES = "headlines"
        private const val KEY_FRESH = "fresh"

        /** Salva le storie del widget (Home, aggiornamento orario, notifica del mattino); [fresh] = quante nuove, null se non si sa. */
        fun save(context: Context, stories: List<NewsStory>, fresh: Int? = null) {
            val saved = stories.map {
                SavedStory(it.headline.title, it.headline.link, it.headline.source.name, it.sourceCount, it.headline.publishedAt?.toEpochMilli() ?: 0)
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = NewsWidgetRows.encode(saved)
            if (prefs.getString(KEY_STORIES, null) == raw && (fresh == null || prefs.getInt(KEY_FRESH, -1) == fresh)) return
            prefs.edit().putString(KEY_STORIES, raw).apply { if (fresh != null) putInt(KEY_FRESH, fresh) }.apply()
            Widgets.refresh(context, NewsWidget::class.java)
        }

        fun stories(context: Context): List<SavedStory> = NewsWidgetRows.decode(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STORIES, null),
        )

        private val Renderer: WidgetRenderer = { context, size ->
            val views = RemoteViews(context.packageName, R.layout.widget_news)
            views.setOnClickPendingIntent(android.R.id.background, Widgets.open(context, OpenRequest(OpenRequest.RECAP), 300))
            views.setOnClickPendingIntent(R.id.news_header, Widgets.open(context, OpenRequest(OpenRequest.RECAP), 300))
            val fresh = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FRESH, -1)
            val count = NewsWidgetRows.countLabel(fresh)
            views.setViewVisibility(R.id.news_count, if (count == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.news_count, count.orEmpty())

            val now = System.currentTimeMillis()
            val stories = stories(context).take(NewsWidgetRows.storiesForHeight(size.heightDp))
            views.removeAllViews(R.id.news_rows)
            if (stories.isEmpty()) {
                views.addView(R.id.news_rows, RemoteViews(context.packageName, R.layout.widget_row_note).apply {
                    setTextViewText(R.id.note_text, "Apri Canvas per le notizie")
                })
            }
            stories.forEachIndexed { i, story ->
                if (i > 0) views.addView(R.id.news_rows, RemoteViews(context.packageName, R.layout.widget_row_divider))
                val row = RemoteViews(context.packageName, R.layout.widget_row_story)
                row.setTextViewText(R.id.story_title, story.title)
                row.setTextViewText(R.id.story_meta, NewsWidgetRows.meta(story, now))
                row.setOnClickPendingIntent(
                    R.id.story_root,
                    Widgets.open(context, OpenRequest(OpenRequest.ARTICLE, story.link, story.source, story.title), ("news:" + story.link).hashCode()),
                )
                views.addView(R.id.news_rows, row)
            }
            views
        }
    }
}

/** Widget "Spese": il mese in corso, oggi e il saldo del conto se è collegato. */
class SpendWidget : CanvasWidget(Renderer, WidgetSize(110, 110)) {
    companion object {
        private val Renderer: WidgetRenderer = { context, _ ->
            val views = RemoteViews(context.packageName, R.layout.widget_spend)
            views.setOnClickPendingIntent(android.R.id.background, Widgets.open(context, OpenRequest(OpenRequest.EXPENSES), 400))
            val balance = RevolutStore.balance.value.takeIf { RevolutStore.linked.value }
            val m = SpendWidgetRows.model(NotesStore.data.value.expenses, LocalDate.now(), balance)
            views.setTextViewText(R.id.spend_label, m.month)
            views.setTextViewText(R.id.spend_total, m.total)
            views.setTextViewText(R.id.spend_today, m.today)
            views.setViewVisibility(R.id.spend_balance_row, if (m.balance == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.spend_balance, m.balance.orEmpty())
            views
        }
    }
}

/** Widget "SIM": i giga che restano con la barra, il rinnovo e il credito. */
class SimWidget : CanvasWidget(Renderer, WidgetSize(110, 110)) {
    companion object {
        private val Renderer: WidgetRenderer = { context, _ ->
            val views = RemoteViews(context.packageName, R.layout.widget_sim)
            views.setOnClickPendingIntent(android.R.id.background, Widgets.open(context, OpenRequest(OpenRequest.SIM), 500))
            val m = SimWidgetRows.model(SimStore.status.value, SimStore.linked.value, LocalDate.now())
            views.setTextViewText(R.id.sim_big, m.big)
            views.setTextViewText(R.id.sim_of, m.of)
            views.setViewVisibility(R.id.sim_bar, if (m.progress == null) View.GONE else View.VISIBLE)
            views.setProgressBar(R.id.sim_bar, 1000, m.progress ?: 0, false)
            views.setTextViewText(R.id.sim_line1, m.line1)
            views.setTextViewText(R.id.sim_line2, m.line2)
            views.setViewVisibility(R.id.sim_line2, if (m.line2.isEmpty()) View.GONE else View.VISIBLE)
            views
        }
    }
}

/** Widget "Meteo": tutto blocco colorato, la temperatura grande e, se c'è spazio, le prossime ore. */
class WeatherWidget : CanvasWidget(Renderer, WidgetSize(110, 110)) {
    companion object {
        private val Renderer: WidgetRenderer = { context, size ->
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            views.setOnClickPendingIntent(android.R.id.background, Widgets.open(context, OpenRequest(OpenRequest.WEATHER), 600))
            val forecast = WeatherStore.cached(context)
            views.removeAllViews(R.id.w_hours)
            views.setViewVisibility(R.id.w_hours, View.GONE)
            if (forecast != null) {
                val c = forecast.current
                val today = forecast.today
                views.setTextViewText(R.id.w_place, forecast.place.uppercase())
                views.setTextViewText(R.id.w_temp, c.temperature.asTemp())
                views.setTextViewText(R.id.w_icon, WeatherCodes.emoji(c.code, c.isDay))
                views.setTextViewText(R.id.w_desc, WeatherCodes.description(c.code))
                views.setTextViewText(
                    R.id.w_range,
                    listOfNotNull(
                        today?.let { "${it.max.asTemp()} / ${it.min.asTemp()}" },
                        today?.precipitationProbability?.takeIf { it >= 30 }?.let { "pioggia $it%" },
                    ).joinToString(" · "),
                )
                val hours = WidgetRows.hours(forecast.hourly, LocalDateTime.now(), WidgetRows.hourColumns(size))
                if (hours.isNotEmpty()) {
                    views.setViewVisibility(R.id.w_hours, View.VISIBLE)
                    hours.forEach { h ->
                        views.addView(R.id.w_hours, RemoteViews(context.packageName, R.layout.widget_hour).apply {
                            setTextViewText(R.id.hour_time, WidgetRows.hourLabel(h.time))
                            setTextViewText(R.id.hour_icon, WeatherCodes.emoji(h.code, h.isDay))
                            setTextViewText(R.id.hour_temp, h.temperature.asTemp())
                        })
                    }
                }
            }
            views
        }
    }
}

/** Widget "Scrivi": la barra di Canvas sulla schermata Home. Il campo apre la scrittura, il tondo detta. */
class CaptureWidget : CanvasWidget(Renderer, WidgetSize(250, 60)) {
    companion object {
        private val Renderer: WidgetRenderer = { context, _ ->
            val views = RemoteViews(context.packageName, R.layout.widget_capture)
            views.setOnClickPendingIntent(R.id.cap_write, Widgets.open(context, OpenRequest(OpenRequest.WRITE), 700))
            views.setOnClickPendingIntent(R.id.cap_voice, Widgets.open(context, OpenRequest(OpenRequest.VOICE), 701))
            views
        }
    }
}
