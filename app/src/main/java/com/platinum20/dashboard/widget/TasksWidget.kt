package com.platinum20.dashboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.capture.QuickCaptureActivity
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.work.RefreshWorker
import java.time.ZoneId

/**
 * Widget "Da fare": i promemoria scaduti e di oggi, ognuno con un cerchio che li spunta
 * senza aprire l'app (i ricorrenti passano alla volta dopo, come in Note).
 */
class TasksWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, render(context, manager.getAppWidgetOptions(it))) }
    }

    override fun onEnabled(context: Context) {
        // Lo stesso aggiornamento orario del widget principale ridisegna le righe dopo mezzanotte.
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        if (!DashboardWidget.hasWidgets(context)) RefreshWorker.cancel(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Ridimensionato: le righe si adattano alla nuova altezza.
        manager.updateAppWidget(id, render(context, options))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DONE) {
            val id = intent.getLongExtra(EXTRA_TASK, -1L)
            if (id >= 0) markDone(context, id)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_DONE = "com.platinum20.dashboard.widget.TASK_DONE"
        const val EXTRA_TASK = "task_id"

        private val ROWS = listOf(R.id.tasks_row1, R.id.tasks_row2, R.id.tasks_row3, R.id.tasks_row4, R.id.tasks_row5, R.id.tasks_row6)
        private val CHECKS = listOf(R.id.tasks_check1, R.id.tasks_check2, R.id.tasks_check3, R.id.tasks_check4, R.id.tasks_check5, R.id.tasks_check6)
        private val TEXTS = listOf(R.id.tasks_text1, R.id.tasks_text2, R.id.tasks_text3, R.id.tasks_text4, R.id.tasks_text5, R.id.tasks_text6)
        private val TIMES = listOf(R.id.tasks_time1, R.id.tasks_time2, R.id.tasks_time3, R.id.tasks_time4, R.id.tasks_time5, R.id.tasks_time6)

        /** Il tocco sul cerchio: stessa spunta della notifica, poi NotesStore ridisegna i widget. */
        internal fun markDone(context: Context, id: Long) {
            NotesStore.init(context)
            NotesStore.setTaskDone(id)
            updateAll(context)
        }

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TasksWidget::class.java))
        }.getOrDefault(IntArray(0))

        /** Ridisegna tutti i widget "Da fare" con i promemoria di adesso. */
        fun updateAll(context: Context) {
            val app = context.applicationContext
            val ids = ids(app)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(app)
            ids.forEach { manager.updateAppWidget(it, render(app, manager.getAppWidgetOptions(it))) }
        }

        private fun render(context: Context, options: Bundle?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            NotesStore.init(context)
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: 180
            val content = TasksWidgetRows.build(
                NotesStore.data.value.openTasks,
                System.currentTimeMillis(),
                ZoneId.systemDefault(),
                TasksWidgetRows.rowsForHeight(height),
            )

            val openNotes = DashboardWidget.openApp(context, OpenRequest(OpenRequest.NOTES), 40)
            views.setOnClickPendingIntent(R.id.tasks_title, openNotes)
            views.setOnClickPendingIntent(R.id.tasks_add, addIntent(context))
            views.setTextViewText(
                R.id.tasks_title,
                if (content.rows.isEmpty()) "DA FARE" else "DA FARE · ${content.rows.size + content.more}",
            )

            ROWS.forEachIndexed { i, rowId ->
                val row = content.rows.getOrNull(i)
                views.setViewVisibility(rowId, if (row == null) View.GONE else View.VISIBLE)
                if (row == null) return@forEachIndexed
                views.setTextViewText(TEXTS[i], row.text)
                views.setTextViewText(TIMES[i], row.time.orEmpty())
                views.setViewVisibility(TIMES[i], if (row.time == null) View.GONE else View.VISIBLE)
                views.setTextColor(TIMES[i], context.getColor(if (row.late) R.color.widget_late else R.color.widget_text_secondary))
                views.setImageViewResource(CHECKS[i], if (row.late) R.drawable.widget_check_late else R.drawable.widget_check)
                views.setOnClickPendingIntent(CHECKS[i], doneIntent(context, row.id))
                views.setOnClickPendingIntent(TEXTS[i], openNotes)
            }

            views.setViewVisibility(R.id.tasks_empty, if (content.empty == null) View.GONE else View.VISIBLE)
            content.empty?.let {
                views.setTextViewText(R.id.tasks_empty, it)
                views.setOnClickPendingIntent(R.id.tasks_empty, openNotes)
            }
            val more = TasksWidgetRows.moreText(content.more)
            views.setViewVisibility(R.id.tasks_more, if (more == null) View.GONE else View.VISIBLE)
            if (more != null) {
                views.setTextViewText(R.id.tasks_more, more)
                views.setOnClickPendingIntent(R.id.tasks_more, openNotes)
            }
            return views
        }

        /** Broadcast esplicito verso questo widget, uno per promemoria (l'id fa da codice). */
        internal fun doneIntent(context: Context, taskId: Long): PendingIntent {
            val intent = Intent(context, TasksWidget::class.java)
                .setAction(ACTION_DONE)
                .putExtra(EXTRA_TASK, taskId)
            return PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun addIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            41,
            QuickCaptureActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
