package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tenendo premuta una riga del calendario: il fumetto di vetro con le sue azioni. Uguale nella card
 * della Home e nella pagina intera, così tenere premuto vuol dire sempre la stessa cosa.
 */
@Composable
internal fun rememberRowActions(onOpenEvent: (CalendarEvent) -> Unit = {}): (DueRow) -> Unit {
    val context = LocalContext.current
    val openItem = rememberOpenItem()
    val haptic = rememberHaptic()
    val openEvent by rememberUpdatedState(onOpenEvent)
    fun offerUndo(message: String, restore: () -> Unit) {
        CaptureFeedback.show(
            CaptureNotice(CaptureMode.NOTE, message, undo = { restore() }, icon = Icons.Outlined.Delete, fly = false),
        )
    }
    fun offerUndo(removed: List<TaskItem>, message: String) {
        if (removed.isNotEmpty()) offerUndo(message) { NotesStore.restoreTasks(removed) }
    }
    fun share(text: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, text)
        context.startActivity(android.content.Intent.createChooser(send, null))
    }
    fun taskActions(t: TaskItem): List<BubbleAction> = listOfNotNull(
        BubbleAction(Icons.Outlined.Check, if (t.done) "Segna da fare" else "Fatto", detail = t.text) {
            haptic(Haptic.CONFIRM)
            NotesStore.toggleTask(t.id)
        },
        t.dueAt?.takeIf { !t.done }?.let { due ->
            BubbleAction(Icons.Outlined.Update, "Rimanda a domani") {
                val zone = ZoneId.systemDefault()
                val time = java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalTime()
                NotesStore.snoozeTask(t.id, LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli())
            }
        },
        BubbleAction(Icons.Outlined.Share, "Condividi") { share(t.text) },
        BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
            NotesStore.deleteTask(t.id)
            offerUndo(listOf(t), "Promemoria eliminato")
        },
    )
    fun dueActions(row: DueRow) {
        val actions = when {
            row.task != null -> taskActions(row.task)
            row.event != null -> listOf(
                BubbleAction(Icons.Outlined.Event, "Apri", detail = row.event.title) { openEvent(row.event) },
                BubbleAction(Icons.Outlined.Share, "Condividi") {
                    share(row.event.title + " · " + com.platinum20.dashboard.today.Upcoming.dayLabel(java.time.Instant.ofEpochMilli(row.event.begin).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()))
                },
            )
            row.parcel != null -> listOf(
                BubbleAction(Icons.Outlined.LocalShipping, "Apri", detail = row.parcel.title) { openItem(ItemKind.PARCEL, row.parcel.id) },
                BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                    NotesStore.deleteParcel(row.parcel.id)
                    offerUndo("Pacco eliminato") { NotesStore.restoreParcel(row.parcel) }
                },
            )
            row.charge != null -> listOf(
                BubbleAction(Icons.Outlined.Event, "Apri", detail = row.charge.text.ifEmpty { row.charge.category.label }) { openItem(ItemKind.FIXED, row.charge.id) },
                BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                    NotesStore.deleteFixedExpense(row.charge.id)
                    offerUndo("Spesa fissa eliminata") { NotesStore.restoreFixedExpense(row.charge) }
                },
            )
            else -> emptyList()
        }
        if (actions.isNotEmpty()) ActionBubble.show(*actions.toTypedArray())
    }
    fun expenseActions(e: com.platinum20.dashboard.notes.Expense) {
        ActionBubble.show(
            BubbleAction(Icons.Outlined.Event, "Apri", detail = com.platinum20.dashboard.notes.Expenses.displayName(e)) { openItem(ItemKind.EXPENSE, e.id) },
            BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                NotesStore.deleteExpense(e.id)
                offerUndo("Spesa eliminata") { NotesStore.restoreExpense(e) }
            },
        )
    }
    fun noteActions(n: Note) {
        ActionBubble.show(
            BubbleAction(
                if (n.pinned) Icons.Filled.PushPinFilled else Icons.Outlined.PushPin,
                if (n.pinned) "Togli da in alto" else "Fissa in alto",
                detail = n.title,
            ) { NotesStore.togglePin(n.id) },
            BubbleAction(Icons.Outlined.Share, "Condividi") { share(n.text) },
            BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                NotesStore.deleteNote(n.id)
                offerUndo("Nota eliminata") { NotesStore.restoreNote(n) }
            },
        )
    }
    return { row ->
        when {
            row.note != null -> noteActions(row.note)
            row.expense != null -> expenseActions(row.expense)
            else -> dueActions(row)
        }
    }
}
