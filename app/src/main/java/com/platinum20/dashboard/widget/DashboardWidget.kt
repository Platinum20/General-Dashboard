package com.platinum20.dashboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.news.RecapTheme
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.work.RefreshWorker
import org.json.JSONArray
import org.json.JSONObject

/** Widget della schermata Home: meteo e i primi tre temi del giorno. */
class DashboardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, render(context)) }
        RefreshWorker.schedule(context)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        RefreshWorker.runNow(context)
    }

    override fun onDisabled(context: Context) {
        RefreshWorker.cancel(context)
    }

    companion object {
        private const val PREFS = "widget"
        private const val KEY_HEADLINES = "headlines"

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DashboardWidget::class.java))
        }.getOrDefault(IntArray(0))

        /** Ridisegna tutti i widget con i dati salvati. */
        fun updateAll(context: Context) {
            val app = context.applicationContext
            val ids = ids(app)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(app)
            ids.forEach { manager.updateAppWidget(it, render(app)) }
        }

        /** Salva i temi del giorno mostrati dal widget (e dalla notifica del mattino). */
        fun saveRecap(context: Context, themes: List<RecapTheme>) {
            val array = JSONArray()
            themes.take(3).forEach {
                array.put(
                    JSONObject()
                        .put("title", it.headline.title)
                        .put("link", it.headline.link)
                        .put("source", it.headline.source.name)
                )
            }
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_HEADLINES, array.toString()).apply()
            updateAll(context)
        }

        data class Headline(val title: String, val link: String, val source: String)

        fun headlines(context: Context): List<Headline> {
            val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_HEADLINES, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    Headline(o.optString("title"), o.optString("link"), o.optString("source"))
                }
            }.getOrDefault(emptyList())
        }

        private fun render(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, OpenRequest(OpenRequest.HOME), 0))
            views.setOnClickPendingIntent(R.id.widget_weather, openApp(context, OpenRequest(OpenRequest.WEATHER), 1))
            views.setOnClickPendingIntent(R.id.widget_recap_title, openApp(context, OpenRequest(OpenRequest.RECAP), 2))

            val forecast = WeatherStore.cached(context)
            if (forecast != null) {
                val c = forecast.current
                views.setTextViewText(R.id.widget_emoji, WeatherCodes.emoji(c.code, c.isDay))
                views.setTextViewText(R.id.widget_temp, c.temperature.asTemp())
                views.setTextViewText(R.id.widget_place, forecast.place)
                val today = forecast.today
                views.setTextViewText(
                    R.id.widget_desc,
                    WeatherCodes.description(c.code) +
                        (today?.let { " · ${it.max.asTemp()}/${it.min.asTemp()}" } ?: "") +
                        (today?.takeIf { it.precipitationProbability >= 30 }?.let { " · pioggia ${it.precipitationProbability}%" } ?: ""),
                )
            }

            val news = headlines(context)
            val rows = listOf(R.id.widget_news1, R.id.widget_news2, R.id.widget_news3)
            views.setViewVisibility(R.id.widget_recap_title, if (news.isEmpty()) View.GONE else View.VISIBLE)
            rows.forEachIndexed { i, id ->
                val item = news.getOrNull(i)
                if (item == null) {
                    views.setViewVisibility(id, View.GONE)
                } else {
                    views.setViewVisibility(id, View.VISIBLE)
                    views.setTextViewText(id, "• " + item.title)
                    views.setOnClickPendingIntent(
                        id,
                        openApp(context, OpenRequest(OpenRequest.ARTICLE, item.link, item.source, item.title), 10 + i),
                    )
                }
            }
            return views
        }

        private fun openApp(context: Context, request: OpenRequest, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            request.writeTo(intent)
            return PendingIntent.getActivity(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
