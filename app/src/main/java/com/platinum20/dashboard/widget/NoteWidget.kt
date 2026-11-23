package com.platinum20.dashboard.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.notes.Checklist
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NotesStore

/** Una riga del widget Nota: una voce da spuntare ([index] = riga nel testo della nota) o una riga di testo. */
data class NoteWidgetLine(val index: Int, val text: String, val check: Boolean, val done: Boolean = false)

/** Cosa disegna il widget Nota: il titolo, quante voci restano, le righe che ci stanno e quante restano fuori. */
data class NoteWidgetModel(val noteId: Long?, val title: String, val open: Int, val lines: List<NoteWidgetLine>, val more: Int)

/**
 * Il widget Nota, separato da RemoteViews per poterlo provare (Antonio, 2026-11-23, idea 8). Mostra la nota scelta
 * con "Mostra nel widget" (nell'editor e tenendo premuta la lista in Da fare); se non ce n'è una, la lista fissata o
 * l'ultima lista toccata, se no l'ultima nota.
 */
object NoteWidgetRows {
    const val HEADER_DP = 60
    const val CHECK_DP = 44
    const val TEXT_DP = 30
    const val MORE_DP = 30

    /** Quale nota: quella scelta, se c'è ancora; se no la lista più utile. Le note archiviate no. */
    fun pick(notes: List<Note>, chosen: Long?): Note? {
        notes.firstOrNull { it.id == chosen }?.let { return it }
        val active = notes.filter { it.archivedAt == null && (it.text.isNotBlank()) }
        val lists = active.filter { Checklist.hasItems(it.text) }
        return lists.filter { it.pinned }.maxByOrNull { it.updatedAt }
            ?: lists.maxByOrNull { it.updatedAt }
            ?: active.maxByOrNull { it.updatedAt }
    }

    /** Le righe dopo il titolo: le voci con la loro spunta, il testo senza segni; niente righe vuote, foto, audio. */
    fun lines(text: String): List<NoteWidgetLine> = text.lines().mapIndexedNotNull { i, line ->
        if (i == 0) return@mapIndexedNotNull null
        when (val parsed = Checklist.parseLine(i, line)) {
            is Checklist.Item -> NoteFormat.plain(parsed.text).trim().takeIf { it.isNotEmpty() }?.let { NoteWidgetLine(i, it, true, parsed.done) }
            is Checklist.Plain -> when (NoteLines.kindOf(line).first) {
                NoteLines.Kind.IMAGE, NoteLines.Kind.AUDIO, NoteLines.Kind.DIVIDER -> null
                NoteLines.Kind.BULLET -> NoteLines.plainLine(line).takeIf { it.isNotEmpty() }?.let { NoteWidgetLine(i, "• $it", false) }
                else -> NoteLines.plainLine(line).takeIf { it.isNotEmpty() }?.let { NoteWidgetLine(i, it, false) }
            }
        }
    }

    fun build(note: Note?, heightDp: Int): NoteWidgetModel {
        if (note == null) return NoteWidgetModel(null, "Nota", 0, emptyList(), 0)
        val all = lines(note.text)
        val open = all.count { it.check && !it.done }
        val budget = heightDp - HEADER_DP
        fun height(l: NoteWidgetLine) = if (l.check) CHECK_DP else TEXT_DP
        var used = 0
        val shown = mutableListOf<NoteWidgetLine>()
        for ((i, l) in all.withIndex()) {
            val rest = all.size - i - 1
            // Se dopo questa ne restano altre, serve anche il posto per "+ altre N".
            val need = height(l) + if (rest > 0) MORE_DP else 0
            if (used + need > budget && shown.isNotEmpty()) break
            used += height(l)
            shown += l
        }
        return NoteWidgetModel(note.id, note.title.ifBlank { "Nota" }, open, shown, all.size - shown.size)
    }

    /** "SPESA · 3 DA FARE ›": il titolo in maiuscolo come le card, con le voci che mancano. */
    fun header(model: NoteWidgetModel): String =
        (model.title + if (model.open > 0) " · ${model.open} da fare" else "").uppercase() + " ›"

    fun moreText(n: Int): String = if (n == 1) "+ un'altra riga" else "+ altre $n righe"

    const val EMPTY = "Nessuna nota. Tieni premuta una lista in Da fare: Mostra nel widget"
}

/**
 * Widget "Nota" (Antonio, 2026-11-23): la spesa, i codici, i numeri utili sulla schermata del telefono. Le voci si
 * spuntano senza aprire l'app; il titolo apre la nota nell'editor.
 */
class NoteWidget : CanvasWidget(Renderer, WidgetSize(250, 250)) {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TICK) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            val index = intent.getIntExtra(EXTRA_INDEX, -1)
            if (id >= 0 && index > 0) {
                NotesStore.init(context)
                // NotesStore ridisegna già i widget a ogni modifica.
                NotesStore.editNoteText(id) { Checklist.toggle(it, index) }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TICK = "com.platinum20.dashboard.widget.NOTE_TICK"
        const val EXTRA_ID = "note_id"
        const val EXTRA_INDEX = "note_line"
        private const val PREFS = "note_widget"
        private const val KEY = "note_id"

        /** "Mostra nel widget": la nota che il widget Nota mostra da adesso. */
        fun choose(context: Context, noteId: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, noteId).apply()
            Widgets.refresh(context, NoteWidget::class.java)
        }

        fun chosen(context: Context): Long? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, -1L).takeIf { it >= 0 }

        /** C'è almeno un widget Nota sulla schermata: per dire "è nel widget" o "aggiungi il widget". */
        fun placed(context: Context): Boolean = Widgets.ids(context, NoteWidget::class.java).isNotEmpty()

        private val Renderer: WidgetRenderer = { context, size ->
            val views = RemoteViews(context.packageName, R.layout.widget_note)
            NotesStore.init(context)
            val note = NoteWidgetRows.pick(NotesStore.data.value.notes, chosen(context))
            val model = NoteWidgetRows.build(note, size.heightDp)
            val open = if (model.noteId != null) Widgets.open(context, OpenRequest(OpenRequest.NOTE, url = model.noteId.toString()), 820)
            else Widgets.open(context, OpenRequest(OpenRequest.LATER), 821)
            views.setOnClickPendingIntent(android.R.id.background, open)
            views.setOnClickPendingIntent(R.id.note_w_header, open)
            views.setOnClickPendingIntent(R.id.note_w_open, open)
            views.setTextViewText(R.id.note_w_title, NoteWidgetRows.header(model))
            views.setViewVisibility(R.id.note_w_count, View.GONE)

            views.removeAllViews(R.id.note_w_rows)
            if (model.lines.isEmpty()) {
                views.addView(R.id.note_w_rows, RemoteViews(context.packageName, R.layout.widget_row_note_text).apply {
                    setTextViewText(R.id.note_line, if (model.noteId == null) NoteWidgetRows.EMPTY else "Nota vuota. Tocca per scrivere")
                    setTextColor(R.id.note_line, context.getColor(R.color.widget_text_secondary))
                    setOnClickPendingIntent(R.id.note_line, open)
                })
            }
            model.lines.forEach { line ->
                views.addView(R.id.note_w_rows, if (line.check) checkView(context, model.noteId!!, line, open) else textView(context, line, open))
            }
            if (model.more > 0) {
                views.addView(R.id.note_w_rows, RemoteViews(context.packageName, R.layout.widget_row_note_text).apply {
                    setTextViewText(R.id.note_line, NoteWidgetRows.moreText(model.more))
                    setTextColor(R.id.note_line, context.getColor(R.color.widget_text_secondary))
                    setOnClickPendingIntent(R.id.note_line, open)
                })
            }
            views
        }

        private fun textView(context: Context, line: NoteWidgetLine, open: android.app.PendingIntent) =
            RemoteViews(context.packageName, R.layout.widget_row_note_text).apply {
                setTextViewText(R.id.note_line, line.text)
                setOnClickPendingIntent(R.id.note_line, open)
            }

        private fun checkView(context: Context, noteId: Long, line: NoteWidgetLine, open: android.app.PendingIntent) =
            RemoteViews(context.packageName, R.layout.widget_row_todo).apply {
                setTextViewText(R.id.todo_title, line.text)
                setTextColor(R.id.todo_title, context.getColor(if (line.done) R.color.widget_text_secondary else R.color.widget_text))
                setViewVisibility(R.id.todo_line, View.GONE)
                setViewVisibility(R.id.todo_flame, View.GONE)
                setViewVisibility(R.id.todo_streak, View.GONE)
                setImageViewResource(R.id.todo_check, if (line.done) R.drawable.widget_check_done else R.drawable.widget_check)
                setContentDescription(R.id.todo_check, if (line.done) "Da fare" else "Fatto")
                val code = ("note:$noteId:${line.index}").hashCode()
                setOnClickPendingIntent(
                    R.id.todo_check,
                    Widgets.broadcast(context, NoteWidget::class.java, ACTION_TICK, code) {
                        putExtra(EXTRA_ID, noteId)
                        putExtra(EXTRA_INDEX, line.index)
                    },
                )
                // Il nome spunta anche lui: è la cosa che si tocca col pollice (come nella card Da fare l'intera riga).
                setOnClickPendingIntent(
                    R.id.todo_root,
                    Widgets.broadcast(context, NoteWidget::class.java, ACTION_TICK, code + 1) {
                        putExtra(EXTRA_ID, noteId)
                        putExtra(EXTRA_INDEX, line.index)
                    },
                )
            }
    }
}
