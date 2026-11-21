package com.platinum20.dashboard.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.ui.screens.LooseItem
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.habitFlame
import com.platinum20.dashboard.ui.screens.habitTodoLine
import com.platinum20.dashboard.ui.screens.looseItems
import com.platinum20.dashboard.ui.screens.pending
import com.platinum20.dashboard.ui.screens.todos
import java.time.LocalDateTime

/** Una riga del widget Da fare: un'abitudine (torna ogni giorno) o una cosa da fare senza data. */
data class TodoRow(
    val kind: Kind,
    val id: Long,
    val title: String,
    /** Al più una riga sotto il nome: "Manca oggi" (arancione se [urgent]), "Fatta oggi · torna domani", la nota. */
    val line: String? = null,
    val urgent: Boolean = false,
    /** Abitudine già fatta oggi: cerchio pieno e testo spento fino a domani, come nella card. */
    val done: Boolean = false,
    /** Giorni (o settimane) di fila, la fiamma a destra; null = niente serie. */
    val flame: Int? = null,
) {
    enum class Kind { HABIT, TASK }
}

/** Cosa disegna il widget: le righe che ci stanno, il conto accanto al titolo e quante restano fuori. */
data class TodoModel(val rows: List<TodoRow>, val count: Int, val more: Int)

/**
 * Il widget Da fare, separato da RemoteViews per poterlo provare. Stesso contenuto e stesso ordine della card "Da fare"
 * della Home (1.532/1.535): in cima le abitudini, poi le cose da fare ancora aperte; il conto è lo stesso della card.
 */
object TodoWidgetRows {
    const val HEADER_DP = 64
    const val ROW_DP = 44
    const val NOTE_DP = 28

    internal fun build(habits: List<Habit>, todos: List<LooseItem>, now: LocalDateTime, heightDp: Int): TodoModel {
        val today = now.toLocalDate()
        val all = habits.map { h ->
            val line = habitTodoLine(h, now)
            TodoRow(TodoRow.Kind.HABIT, h.id, h.title, line?.text, line?.urgent == true, h.isDone(today), habitFlame(h, today))
        } + todos.mapNotNull { item ->
            val task = item.row?.task ?: return@mapNotNull null
            TodoRow(TodoRow.Kind.TASK, task.id, item.title, item.line)
        }
        val count = habits.count { !it.isDone(today) } + all.count { it.kind == TodoRow.Kind.TASK }
        val budget = heightDp - HEADER_DP
        val fit = (budget / ROW_DP).coerceAtLeast(1)
        if (all.size <= fit) return TodoModel(all, count, 0)
        // Non ci stanno tutte: l'ultima riga dice quante ne restano (si vedono toccando il titolo).
        val shown = ((budget - NOTE_DP) / ROW_DP).coerceAtLeast(1)
        return TodoModel(all.take(shown), count, all.size - shown)
    }

    fun moreText(n: Int): String = if (n == 1) "+ un'altra" else "+ altre $n"

    /** Niente da fare e nessuna abitudine: una riga sola, come la card vuota. */
    const val EMPTY = "Niente da fare. Tocca + per aggiungere"
}

/**
 * Widget "Da fare" (giro 2, Antonio 2026-11-21): dalla 1.532 le cose da fare e le abitudini non stavano in nessun
 * widget. Il cerchio si spunta senza aprire l'app; il nome apre la sua pagina; il titolo apre la pagina di Da fare.
 */
class TodoWidget : CanvasWidget(Renderer, WidgetSize(250, 250)) {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TICK) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            val kind = runCatching { TodoRow.Kind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty()) }.getOrNull()
            if (id >= 0 && kind != null) tick(context, kind, id)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TICK = "com.platinum20.dashboard.widget.TODO_TICK"
        const val EXTRA_ID = "todo_id"
        const val EXTRA_KIND = "todo_kind"

        /**
         * Il tocco sul cerchio, come nella card: l'abitudine si accende o si spegne per oggi, la cosa da fare va in "Fatte".
         * Una cosa già fatta non si rimette (dal widget è sparita: un doppio tocco non deve riaprirla).
         */
        internal fun tick(context: Context, kind: TodoRow.Kind, id: Long) {
            NotesStore.init(context)
            when (kind) {
                TodoRow.Kind.HABIT -> NotesStore.toggleHabit(id)
                TodoRow.Kind.TASK -> if (NotesStore.data.value.tasks.any { it.id == id && !it.done }) NotesStore.toggleTask(id)
            }
            // NotesStore ridisegna già i widget a ogni modifica.
        }

        /** Le stesse cose da fare della card della Home ([looseItems] → aperte → solo da spuntare). */
        internal fun todos(context: Context, now: LocalDateTime): List<LooseItem> {
            NotesStore.init(context)
            val data = NotesStore.data.value
            val days = dueDays(data.tasks, data.parcels, emptyList(), now = now, withToday = true, notes = data.notes)
            return looseItems(days, data.notes, now.toLocalDate()).pending().todos()
        }

        private val Renderer: WidgetRenderer = { context, size ->
            val views = RemoteViews(context.packageName, R.layout.widget_todo)
            val now = LocalDateTime.now()
            val openPage = Widgets.open(context, OpenRequest(OpenRequest.LATER), 800)
            views.setOnClickPendingIntent(android.R.id.background, openPage)
            views.setOnClickPendingIntent(R.id.todo_header, openPage)
            views.setOnClickPendingIntent(R.id.todo_add, Widgets.open(context, OpenRequest(OpenRequest.WRITE), 801))

            NotesStore.init(context)
            val model = TodoWidgetRows.build(NotesStore.data.value.habits, todos(context, now), now, size.heightDp)
            views.setTextViewText(R.id.todo_count, if (model.count > 0) model.count.toString() else "")
            views.setViewVisibility(R.id.todo_count, if (model.count > 0) View.VISIBLE else View.GONE)

            views.removeAllViews(R.id.todo_rows)
            if (model.rows.isEmpty()) {
                views.addView(R.id.todo_rows, RemoteViews(context.packageName, R.layout.widget_row_cal_note).apply {
                    setTextViewText(R.id.note_text, TodoWidgetRows.EMPTY)
                    setOnClickPendingIntent(R.id.note_text, openPage)
                })
            }
            model.rows.forEach { row -> views.addView(R.id.todo_rows, rowView(context, row)) }
            if (model.more > 0) {
                views.addView(R.id.todo_rows, RemoteViews(context.packageName, R.layout.widget_row_cal_note).apply {
                    setTextViewText(R.id.note_text, TodoWidgetRows.moreText(model.more))
                    setOnClickPendingIntent(R.id.note_text, openPage)
                })
            }
            views
        }

        private fun rowView(context: Context, row: TodoRow): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_row_todo).apply {
                setTextViewText(R.id.todo_title, row.title)
                setTextColor(R.id.todo_title, context.getColor(if (row.done) R.color.widget_text_secondary else R.color.widget_text))
                setViewVisibility(R.id.todo_line, if (row.line == null) View.GONE else View.VISIBLE)
                setTextViewText(R.id.todo_line, row.line.orEmpty())
                setTextColor(R.id.todo_line, context.getColor(if (row.urgent) R.color.widget_late else R.color.widget_text_secondary))
                setImageViewResource(R.id.todo_check, if (row.done) R.drawable.widget_check_done else R.drawable.widget_check)
                setContentDescription(R.id.todo_check, if (row.done) "Da fare" else "Fatto")
                val flame = row.flame != null
                setViewVisibility(R.id.todo_flame, if (flame) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.todo_streak, if (flame) View.VISIBLE else View.GONE)
                setTextViewText(R.id.todo_streak, row.flame?.toString().orEmpty())
                val code = ("todo:" + row.kind.name + row.id).hashCode()
                setOnClickPendingIntent(
                    R.id.todo_check,
                    Widgets.broadcast(context, TodoWidget::class.java, ACTION_TICK, code) {
                        putExtra(EXTRA_ID, row.id)
                        putExtra(EXTRA_KIND, row.kind.name)
                    },
                )
                // Il nome apre la sua pagina, come toccando la riga nella card.
                val source = if (row.kind == TodoRow.Kind.HABIT) "HABIT" else "TASK"
                setOnClickPendingIntent(R.id.todo_root, Widgets.open(context, OpenRequest(OpenRequest.ITEM, url = row.id.toString(), source = source), code + 1))
            }
    }
}
