package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.BriefLine
import java.time.LocalDate

/** Quello che si può annullare dopo un'azione rapida di "Adesso". */
data class BriefUndo(val message: String, val undo: () -> Unit)

object BriefActions {
    /**
     * Esegue l'azione di una riga di "Adesso" e restituisce come annullarla.
     * null se la cosa non c'è più (già chiusa altrove) o se l'azione apre un elenco ([BriefAction.HABITS]).
     */
    fun perform(line: BriefLine, data: NotesData, today: LocalDate = LocalDate.now()): BriefUndo? {
        val id = line.targetIds.firstOrNull() ?: return null
        return when (line.action) {
            BriefAction.TASK_DONE -> {
                val task = data.tasks.firstOrNull { it.id == id } ?: return null
                NotesStore.setTaskDone(id)
                BriefUndo(if (task.repeat != null) "Spostato alla prossima volta: ${task.text}" else "Fatto: ${task.text}") {
                    NotesStore.restoreTask(task)
                }
            }
            BriefAction.PARCEL_ARRIVED -> {
                val parcel = data.parcels.firstOrNull { it.id == id && !it.delivered } ?: return null
                NotesStore.toggleDelivered(id)
                BriefUndo("Arrivato: ${parcel.title}") { NotesStore.toggleDelivered(id) }
            }
            BriefAction.HABIT_DONE -> {
                val habit = data.habits.firstOrNull { it.id == id && !it.isDone(today) } ?: return null
                NotesStore.toggleHabit(id, today)
                BriefUndo("Fatto: ${habit.title}") { NotesStore.toggleHabit(id, today) }
            }
            BriefAction.HABITS, null -> null
        }
    }

    fun label(action: BriefAction): String = when (action) {
        BriefAction.TASK_DONE -> "Segna fatto"
        BriefAction.PARCEL_ARRIVED -> "Arrivato"
        BriefAction.HABIT_DONE -> "Abitudine fatta"
        BriefAction.HABITS -> "Spunta le abitudini"
    }
}

/** Il bottoncino tondo a destra di una riga di "Adesso". */
@Composable
fun BriefActionButton(action: BriefAction, onClick: () -> Unit) {
    val content = LocalContentColor.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .bouncyPress(0.9f)
            .clip(CircleShape)
            .background(content.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (action == BriefAction.HABITS) Icons.Outlined.Checklist else Icons.Outlined.Done,
            contentDescription = BriefActions.label(action),
            tint = content,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Le abitudini che mancano oggi, da spuntare senza aprire Note. */
@Composable
fun HabitsTodayDialog(habits: List<Habit>, today: LocalDate, onToggle: (Habit) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abitudini di oggi") },
        text = {
            Column {
                habits.forEach { habit ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(habit) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = habit.isDone(today), onCheckedChange = { onToggle(habit) })
                        Text(habit.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } },
    )
}
