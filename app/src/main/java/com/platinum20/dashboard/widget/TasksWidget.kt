package com.platinum20.dashboard.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.sim.SimStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Widget "Calendario" (era "Da fare": il nome della classe resta per non toglierlo dalla Home).
 * Come la card della Home: lo stesso Oggi del calendario (tutto, anche le cose finite), poi i prossimi giorni.
 */
class TasksWidget : CanvasWidget(Renderer, WidgetSize(250, 250)) {

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

        /** Il tocco sul cerchio: stessa spunta della notifica, poi NotesStore ridisegna i widget. */
        internal fun markDone(context: Context, id: Long) {
            NotesStore.init(context)
            if (NotesStore.data.value.tasks.any { it.id == id && !it.done }) NotesStore.setTaskDone(id)
        }

        /** Broadcast esplicito verso questo widget, uno per promemoria (l'id fa da codice). */
        internal fun doneIntent(context: Context, taskId: Long): PendingIntent =
            Widgets.broadcast(context, TasksWidget::class.java, ACTION_DONE, taskId.hashCode()) { putExtra(EXTRA_TASK, taskId) }

        /**
         * Gli stessi giorni del calendario dell'app (Home e pagina): [dueDays] con Oggi, così il widget non ha
         * più un "oggi" suo (Antonio, 2026-11-10). Usato anche dal widget Oggi.
         */
        internal suspend fun loadDays(context: Context, zone: ZoneId): List<com.platinum20.dashboard.ui.screens.DueDay> {
            NotesStore.init(context)
            SimStore.init(context)
            val data = NotesStore.data.value
            val today = LocalDate.now(zone)
            val events = runCatching { CalendarRepository.eventsFrom(context, today, AgendaRows.DAYS) }.getOrDefault(emptyMap())
            return com.platinum20.dashboard.ui.screens.dueDays(
                data.tasks, data.parcels, events.values.flatten(),
                now = LocalDateTime.now(zone), zone = zone,
                simRenewal = SimStore.status.value?.renewal?.takeIf { SimStore.linked.value },
                withToday = true, notes = data.notes,
            )
        }

        private val Renderer: WidgetRenderer = { context, size ->
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val openCalendar = Widgets.open(context, OpenRequest(OpenRequest.NOTES), 200)
            views.setOnClickPendingIntent(android.R.id.background, openCalendar)
            views.setOnClickPendingIntent(R.id.cal_header, openCalendar)
            views.setOnClickPendingIntent(R.id.cal_add, Widgets.open(context, OpenRequest(OpenRequest.WRITE), 201))
            views.setTextViewText(R.id.cal_title, WidgetRows.dayTitle(today))

            val rows = AgendaRows.build(loadDays(context, zone), System.currentTimeMillis(), zone, size.heightDp)
            views.removeAllViews(R.id.cal_rows)
            rows.forEach { row -> views.addView(R.id.cal_rows, rowView(context, row, openCalendar)) }
            views
        }

        private fun rowView(context: Context, row: AgendaRow, openCalendar: PendingIntent): RemoteViews = when (row) {
            // Come la linea del tempo dell'app (1.528, Antonio scelta B): OGGI nel colore di "adesso", gli altri giorni in inchiostro.
            is AgendaRow.Day -> RemoteViews(context.packageName, R.layout.widget_row_day).apply {
                val today = row.label == AgendaRows.TODAY
                setTextViewText(R.id.day_text, if (today) row.label.uppercase() else row.label)
                val color = context.getColor(if (today) R.color.widget_accent else R.color.widget_text)
                setTextColor(R.id.day_text, color)
                setInt(R.id.day_dot, "setColorFilter", color)
            }
            is AgendaRow.Note -> RemoteViews(context.packageName, R.layout.widget_row_cal_note).apply {
                setTextViewText(R.id.note_text, row.text)
                setOnClickPendingIntent(R.id.note_text, openCalendar)
            }
            is AgendaRow.Item -> RemoteViews(context.packageName, R.layout.widget_row_item).apply {
                setTextViewText(R.id.item_text, row.title)
                setTextViewText(R.id.item_time, row.time.orEmpty())
                setViewVisibility(R.id.item_time, if (row.time == null) View.GONE else View.VISIBLE)
                setTextColor(R.id.item_time, context.getColor(if (row.late) R.color.widget_late else R.color.widget_text_secondary))
                // Le cose spuntate oggi restano, più tenui (come nel calendario dell'app).
                if (row.done) setTextColor(R.id.item_text, context.getColor(R.color.widget_text_secondary))
                // Niente spunta (Antonio, 1.376): eventi, promemoria e date sono la stessa cosa, con la barretta colorata.
                setViewVisibility(R.id.item_mark, View.VISIBLE)
                val color = when {
                    row.late -> context.getColor(R.color.widget_late)
                    else -> row.color?.takeIf { it != 0 }?.let { it or 0xFF000000.toInt() } ?: context.getColor(R.color.widget_accent)
                }
                setInt(R.id.item_mark, "setColorFilter", color)
                // Toccando la riga si apre la sua scheda, come nel calendario dell'app.
                val request = when (row.kind) {
                    AgendaRow.Kind.EVENT -> OpenRequest(OpenRequest.EVENT, url = "${row.id}/${row.begin}/${row.end}")
                    AgendaRow.Kind.PARCEL -> OpenRequest(OpenRequest.ITEM, url = row.id.toString(), source = "PARCEL")
                    AgendaRow.Kind.SIM -> OpenRequest(OpenRequest.SIM)
                    AgendaRow.Kind.NOTE -> OpenRequest(OpenRequest.NOTES)
                    else -> OpenRequest(OpenRequest.ITEM, url = row.id.toString(), source = "TASK")
                }
                setOnClickPendingIntent(R.id.item_root, Widgets.open(context, request, ("cal:" + row.key).hashCode()))
            }
        }
    }
}
