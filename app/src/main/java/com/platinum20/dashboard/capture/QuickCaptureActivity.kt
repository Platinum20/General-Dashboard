package com.platinum20.dashboard.capture

import com.platinum20.dashboard.ui.screens.CanvasDialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
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
    HABIT("Abitudine", "Nuova abitudine", "Es. ogni giorno leggere 20 pagine"),
    DATE("Data", "Data da ricordare", "Es. compleanno di Marta 12 marzo"),
    PARCEL("Pacco", "Pacco in arrivo", "Cosa arriva e il codice di tracciamento"),
    EVENT("Evento", "Nuovo evento", "Es. venerdì alle 20 cena da Luca"),
}

@Composable
internal fun CaptureDialog(initial: String, initialMode: CaptureMode, onClose: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(initial) }
    // Niente da scegliere: partendo da "Promemoria" il tipo lo decide Canvas da quello che si scrive
    // (o si detta). Solo le scorciatoie dirette (nuova nota, nuova spesa, evento dalle Note) lo fissano.
    val auto = initialMode == CaptureMode.TASK
    val mode = remember(text) { if (auto) CaptureGuess.guess(text) else initialMode }
    val focus = remember { FocusRequester() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onClose() }
    val scope = rememberCoroutineScope()
    val event = remember(text, mode) { if (mode == CaptureMode.EVENT) QuickEvent.parse(text) else null }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    // Salvato: nell'app il fumetto di Canvas che vola dove è finito; fuori (condivisione) un avviso normale.
    fun saved(mode: CaptureMode, message: String, undo: (() -> Unit)? = null) {
        val notice = CaptureNotice(mode, message, undo?.let { u -> suspend { u() } })
        if (!CaptureFeedback.show(notice)) toast(message)
    }

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
            if (id != null) {
                val app = context.applicationContext
                val notice = CaptureNotice(CaptureMode.EVENT, "Evento: ${eventLine(draft)}", undo = { CalendarRepository.delete(app, id) })
                if (!CaptureFeedback.show(notice)) toast(notice.message)
                onClose()
            } else {
                toast("Il calendario non ha accettato l'evento")
            }
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) saveEvent() else toast("Serve il permesso del calendario per salvare l'evento")
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val save = save@{
        if (text.isBlank()) return@save onClose()
        when (mode) {
            CaptureMode.NOTE, CaptureMode.EXPENSE, CaptureMode.PARCEL, CaptureMode.HABIT, CaptureMode.DATE -> {
                val result = CaptureSave.save(mode, text)
                if (!result.ok) return@save toast(result.message)
                saved(mode, result.message, result.undo)
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
                val result = CaptureSave.save(mode, text)
                val due = result.dueAt
                saved(mode, result.message, result.undo)
                // Senza permesso l'avviso non suonerebbe: lo chiediamo qui, una volta.
                if (due != null && Build.VERSION.SDK_INT >= 33 && !MorningWorker.canNotify(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@save
                }
            }
        }
        onClose()
    }

    CanvasDialog(
        onDismissRequest = onClose,
        title = { Text(if (auto) "Aggiungi" else mode.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(if (auto) AUTO_HINT else mode.hint) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = if (mode == CaptureMode.NOTE) 8 else 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (mode == CaptureMode.NOTE) 120.dp else 56.dp)
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                // Non una scelta ma una risposta: che cosa diventa quello che hai scritto.
                if (text.isNotBlank()) CaptureKindTag(mode)
                Spacer(Modifier.height(4.dp))
                CapturePreview(mode, text)
            }
        },
        confirmButton = { TextButton(onClick = save, enabled = text.isNotBlank()) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } },
    )
}

/** L'icona di ogni tipo: la stessa nell'etichetta del + e nel messaggio dopo il salvataggio. */
internal val CaptureMode.icon: ImageVector
    get() = when (this) {
        CaptureMode.TASK -> Icons.Outlined.TaskAlt
        CaptureMode.NOTE -> Icons.AutoMirrored.Outlined.StickyNote2
        CaptureMode.EXPENSE -> Icons.Outlined.Payments
        CaptureMode.HABIT -> Icons.Outlined.Autorenew
        CaptureMode.DATE -> Icons.Outlined.Cake
        CaptureMode.PARCEL -> Icons.Outlined.LocalShipping
        CaptureMode.EVENT -> Icons.Outlined.Event
    }

/** Che cosa ha capito Canvas: la scadenza, l'importo, il giorno dell'evento. */
@Composable
internal fun CapturePreview(mode: CaptureMode, text: String) {
    when (mode) {
        CaptureMode.TASK -> QuickPreview(text)
        CaptureMode.EXPENSE, CaptureMode.PARCEL, CaptureMode.HABIT, CaptureMode.DATE -> CaptureSave.preview(mode, text)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        CaptureMode.EVENT -> if (text.isNotBlank()) {
            val event = remember(text) { QuickEvent.parse(text) }
            Text(
                event?.let { "${eventLine(it)} · ${it.title}" } ?: "Scrivi anche quando: es. domani alle 15",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        CaptureMode.NOTE -> Unit
    }
}

/** "Diventa: Spesa", con l'icona: cambia da solo mentre si scrive. */
@Composable
internal fun CaptureKindTag(mode: CaptureMode) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(mode.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Diventa: " + mode.label.lowercase().let { if (mode == CaptureMode.EVENT) "evento in calendario" else it },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Con il tipo automatico il suggerimento mostra che si può scrivere di tutto. */
internal const val AUTO_HINT = "Scrivi, Canvas capisce cos'è"

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
