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
import com.platinum20.dashboard.today.BriefAction
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_BRIEF) {
            val action = runCatching { BriefAction.valueOf(intent.getStringExtra(EXTRA_ACTION).orEmpty()) }.getOrNull()
            val id = intent.getLongExtra(EXTRA_TARGET, -1L)
            if (action != null && id >= 0) performBrief(context, action, id)
            return
        }
        super.onReceive(context, intent)
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
        const val ACTION_BRIEF = "com.platinum20.dashboard.widget.BRIEF_ACTION"
        const val EXTRA_ACTION = "brief_action"
        const val EXTRA_TARGET = "brief_target"
        private val BRIEF_ROWS = listOf(R.id.widget_brief_row1, R.id.widget_brief_row2)
        private val BRIEF_ACTIONS = listOf(R.id.widget_brief_action1, R.id.widget_brief_action2)

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
            val raw = WidgetRows.encodeBrief(WidgetRows.briefEntries(lines))
            if (prefs.getString(KEY_BRIEF, null) == raw) return
            prefs.edit().putString(KEY_BRIEF, raw).apply()
            updateAll(context)
        }

        private fun briefing(context: Context): List<WidgetBrief> =
            WidgetRows.decodeBrief(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BRIEF, null),
            )

        /** Il bottoncino di una riga di "Adesso": pacco arrivato o abitudine fatta, senza aprire l'app. */
        internal fun performBrief(context: Context, action: BriefAction, id: Long) {
            NotesStore.init(context)
            when (action) {
                BriefAction.PARCEL_ARRIVED ->
                    if (NotesStore.data.value.parcels.any { it.id == id && !it.delivered }) NotesStore.toggleDelivered(id)
                BriefAction.HABIT_DONE -> NotesStore.markHabitDone(id)
                else -> return
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val left = WidgetRows.withoutTarget(briefing(context), action, id)
            prefs.edit().putString(KEY_BRIEF, WidgetRows.encodeBrief(left)).apply()
            updateAll(context)
        }

        private fun briefIntent(context: Context, entry: WidgetBrief, index: Int): PendingIntent {
            val intent = Intent(context, DashboardWidget::class.java)
                .setAction(ACTION_BRIEF)
                .putExtra(EXTRA_ACTION, entry.action?.name)
                .putExtra(EXTRA_TARGET, entry.targetId ?: -1L)
            return PendingIntent.getBroadcast(
                context,
                30 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
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
                val row = BRIEF_ROWS[i]
                val button = BRIEF_ACTIONS[i]
                views.setViewVisibility(row, if (line == null) View.GONE else View.VISIBLE)
                if (line == null) return@forEachIndexed
                views.setTextViewText(id, line.text)
                val actionable = line.action != null && line.targetId != null
                views.setViewVisibility(button, if (actionable) View.VISIBLE else View.GONE)
                if (actionable) views.setOnClickPendingIntent(button, briefIntent(context, line, i))
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
