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
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.work.RefreshWorker
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

/** Widget della schermata Home: meteo, le righe di "Adesso", i promemoria di oggi e i temi del giorno. */
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
        // Il widget "Da fare" usa lo stesso aggiornamento: si ferma solo se non c'è più nessuno dei due.
        if (!TasksWidget.hasWidgets(context)) RefreshWorker.cancel(context)
    }

    companion object {
        private const val PREFS = "widget"
        private const val KEY_HEADLINES = "headlines"
        private const val KEY_BRIEF = "brief"

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

        /** Le righe di "Adesso" mostrate sotto il meteo: è la stessa domanda della schermata Oggi. */
        fun saveBriefing(context: Context, lines: List<BriefLine>) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // I promemoria hanno già la loro riga (sempre aggiornata): qui solo il resto.
            val raw = JSONArray(lines.filter { it.kind != BriefKind.TASK }.take(2).map { it.text }).toString()
            if (prefs.getString(KEY_BRIEF, null) == raw) return
            prefs.edit().putString(KEY_BRIEF, raw).apply()
            updateAll(context)
        }

        private fun briefing(context: Context): List<String> {
            val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BRIEF, null) ?: return emptyList()
            return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
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
                    WidgetRows.weatherLine(WeatherCodes.description(c.code), today?.max?.asTemp(), today?.min?.asTemp(), today?.precipitationProbability),
                )
            }

            // "Adesso" senza i promemoria: quelli hanno la loro riga, che si aggiorna a ogni modifica.
            val brief = briefing(context)
            listOf(R.id.widget_brief1, R.id.widget_brief2).forEachIndexed { i, id ->
                val line = brief.getOrNull(i)
                views.setViewVisibility(id, if (line == null) View.GONE else View.VISIBLE)
                if (line != null) views.setTextViewText(id, line)
            }

            val tasks = tasksLine(context)
            views.setViewVisibility(R.id.widget_tasks, if (tasks == null) View.GONE else View.VISIBLE)
            if (tasks != null) {
                views.setTextViewText(R.id.widget_tasks, tasks)
                views.setOnClickPendingIntent(R.id.widget_tasks, openApp(context, OpenRequest(OpenRequest.NOTES), 3))
            }

            // Con le righe sopra le notizie scendono a due, per stare nello stesso spazio.
            val news = headlines(context).take(WidgetRows.newsCount(brief.size, tasks != null))
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

        /** Riga con i promemoria scaduti o in scadenza oggi, null se non ce ne sono. */
        private fun tasksLine(context: Context): String? {
            NotesStore.init(context)
            return tasksLine(NotesStore.data.value.openTasks, System.currentTimeMillis(), ZoneId.systemDefault())
        }

        /** La riga dei promemoria del widget, calcolata dai promemoria aperti: separata per poterla provare. */
        internal fun tasksLine(openTasks: List<TaskItem>, now: Long, zone: ZoneId): String? {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val due = openTasks.filter { (it.dueAt ?: Long.MAX_VALUE) < endOfToday }.sortedBy { it.dueAt }
            if (due.isEmpty()) return null
            val late = due.count { (it.dueAt ?: 0) < now }
            val next = due.firstOrNull { (it.dueAt ?: 0) >= now } ?: due.first()
            val count = when {
                late == due.size -> if (late == 1) "1 promemoria scaduto" else "$late promemoria scaduti"
                late > 0 -> "${due.size} promemoria oggi ($late ${if (late == 1) "scaduto" else "scaduti"})"
                due.size == 1 -> "1 promemoria oggi"
                else -> "${due.size} promemoria oggi"
            }
            return "📌 $count · ${next.text} ${timeOf(next, today, zone)}".trim()
        }

        private fun timeOf(task: TaskItem, today: LocalDate, zone: ZoneId): String {
            val due = task.dueAt ?: return ""
            val time = Instant.ofEpochMilli(due).atZone(zone)
            return if (time.toLocalDate() == today) "alle " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
            else ""
        }

        internal fun openApp(context: Context, request: OpenRequest, code: Int): PendingIntent {
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
