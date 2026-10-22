package com.platinum20.dashboard.capture

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.calendar.QuickEventDraft
import com.platinum20.dashboard.notes.Carrier
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.ui.screens.ChipRow
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.QuickPreview
import com.platinum20.dashboard.ui.screens.dueLabel
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import com.platinum20.dashboard.work.MorningWorker

/**
 * Cattura al volo: una finestrella sopra qualunque app per salvare un promemoria
 * ("domani alle 9 dentista") o una nota. Si apre dal riquadro delle impostazioni rapide,
 * dalle scorciatoie dell'icona e da "Salva in Canvas" nel menu Condividi.
 */
class QuickCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        NotesStore.init(this)

        val shared = sharedText(intent)
        val mode = when {
            intent.action == ACTION_NEW_NOTE -> CaptureMode.NOTE
            intent.action == ACTION_NEW_EXPENSE -> CaptureMode.EXPENSE
            // Una mail di spedizione condivisa: c'è un codice di tracciamento.
            shared != null && Carrier.findCode(shared) != null -> CaptureMode.PARCEL
            // Un testo lungo o su più righe è più una nota che un promemoria.
            shared != null && (shared.contains('\n') || shared.length > 120) -> CaptureMode.NOTE
            else -> CaptureMode.TASK
        }

        setContent {
            val theme by AppSettings.theme.collectAsState()
            GeneralDashboardTheme(theme = theme) {
                CaptureDialog(initial = shared.orEmpty(), initialMode = mode, onClose = ::finish)
            }
        }
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        // Molte app mettono il titolo nell'oggetto e il link nel testo: teniamo entrambi.
        return listOf(subject, text).filter { it.isNotEmpty() }.distinct().joinToString("\n").ifEmpty { null }
    }

    companion object {
        const val ACTION_NEW_TASK = "com.platinum20.dashboard.NEW_TASK"
        const val ACTION_NEW_NOTE = "com.platinum20.dashboard.NEW_NOTE"
        const val ACTION_NEW_EXPENSE = "com.platinum20.dashboard.NEW_EXPENSE"

        fun intent(context: Context, note: Boolean = false): Intent =
            Intent(context, QuickCaptureActivity::class.java)
                .setAction(if (note) ACTION_NEW_NOTE else ACTION_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

internal enum class CaptureMode(val label: String, val title: String, val hint: String) {
    TASK("Promemoria", "Promemoria al volo", "Es. domani alle 9 chiamare il dentista"),
    NOTE("Nota", "Nuova nota", "Scrivi… la prima riga fa da titolo"),
    EXPENSE("Spesa", "Nuova spesa", "Es. 12,50 pizza"),
    PARCEL("Pacco", "Pacco in arrivo", "Cosa arriva e il codice di tracciamento"),
    EVENT("Evento", "Nuovo evento", "Es. venerdì alle 20 cena da Luca"),
}

@Composable
internal fun CaptureDialog(initial: String, initialMode: CaptureMode, onClose: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(initial) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    val focus = remember { FocusRequester() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onClose() }
    val scope = rememberCoroutineScope()
    val event = remember(text, mode) { if (mode == CaptureMode.EVENT) QuickEvent.parse(text) else null }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // L'evento va nel calendario principale del telefono: lo vedono Oggi, Adesso, il widget e Calendario.
    fun saveEvent() {
        val draft = event ?: return toast("Scrivi anche quando: es. domani alle 15")
        scope.launch {
            val calendar = CalendarRepository.writableCalendars(context).firstOrNull()
            if (calendar == null) {
                toast("Nessun calendario in cui scrivere")
                return@launch
            }
            val id = CalendarRepository.save(context, draft.toDraft(calendar.id))
            toast(if (id != null) "Evento: ${eventLine(draft)}" else "Il calendario non ha accettato l'evento")
            if (id != null) onClose()
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) saveEvent() else toast("Serve il permesso del calendario per salvare l'evento")
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val expense = remember(text, mode) { if (mode == CaptureMode.EXPENSE) Expenses.parse(text) else null }

    val save = save@{
        if (text.isBlank()) return@save onClose()
        when (mode) {
            CaptureMode.NOTE -> {
                NotesStore.saveNote(null, text.trim())
                toast("Nota salvata")
            }
            CaptureMode.EXPENSE -> {
                val saved = NotesStore.addExpense(text) ?: return@save toast("Manca l'importo")
                toast("Spesa: ${Expenses.format(saved.cents)} · ${saved.category.label}")
            }
            CaptureMode.PARCEL -> {
                val code = Carrier.findCode(text).orEmpty()
                // Il titolo è la prima riga senza il codice, accorciata.
                val title = text.lineSequence().map { it.replace(code, "", ignoreCase = true).trim() }
                    .firstOrNull { it.isNotEmpty() }.orEmpty().take(60)
                NotesStore.saveParcel(null, title.ifEmpty { "Pacco" }, code, null)
                toast("Pacco aggiunto" + if (code.isNotEmpty()) " · ${Carrier.detect(code).label}" else "")
            }
            CaptureMode.EVENT -> {
                if (CalendarRepository.canWrite(context)) {
                    saveEvent()
                } else {
                    calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }
                // Si chiude da solo quando l'evento è salvato.
                return@save
            }
            CaptureMode.TASK -> {
                val due = NotesStore.addTask(text)?.dueAt
                toast(if (due != null) "Promemoria: ${dueLabel(due)}" else "Promemoria salvato")
                // Senza permesso l'avviso non suonerebbe: lo chiediamo qui, una volta.
                if (due != null && Build.VERSION.SDK_INT >= 33 && !MorningWorker.canNotify(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@save
                }
            }
        }
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(mode.title) },
        text = {
            Column {
                ChipRow(
                    options = CaptureMode.entries.map { it.label },
                    selected = mode.ordinal,
                    onSelect = { mode = CaptureMode.entries[it] },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(mode.hint) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = if (mode == CaptureMode.NOTE) 8 else 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (mode == CaptureMode.NOTE) 120.dp else 56.dp)
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(6.dp))
                when (mode) {
                    CaptureMode.TASK -> QuickPreview(text)
                    CaptureMode.EXPENSE -> if (text.isNotBlank()) {
                        Text(
                            expense?.let { "${Expenses.format(it.cents)} · ${it.category.label}" } ?: "Scrivi anche l'importo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    CaptureMode.PARCEL -> Carrier.findCode(text)?.let {
                        Text("$it · ${Carrier.detect(it).label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    CaptureMode.EVENT -> if (text.isNotBlank()) {
                        Text(
                            event?.let { "${eventLine(it)} · ${it.title}" } ?: "Scrivi anche quando: es. domani alle 15",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    CaptureMode.NOTE -> Unit
                }
            }
        },
        confirmButton = { TextButton(onClick = save, enabled = text.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } },
    )
}

private val eventDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val eventHour = DateTimeFormatter.ofPattern("H:mm")

/** "Ven 24 ott 20:00–21:00", oppure "Sab 25 ott · tutto il giorno". */
internal fun eventLine(e: QuickEventDraft): String {
    val day = e.start.format(eventDay).replaceFirstChar { it.uppercase() }
    return if (e.allDay) "$day · tutto il giorno" else "$day ${e.start.format(eventHour)}–${e.end.format(eventHour)}"
}

/** Riquadro "Promemoria al volo" nelle impostazioni rapide (la tendina in alto). */
class QuickCaptureTile : TileService() {
    override fun onClick() {
        super.onClick()
        val open = {
            val intent = QuickCaptureActivity.intent(this)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
