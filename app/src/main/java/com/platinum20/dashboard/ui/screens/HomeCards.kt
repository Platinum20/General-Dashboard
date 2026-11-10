package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.calendar.CalendarAsk
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.Briefing
import com.platinum20.dashboard.today.Upcoming
import com.platinum20.dashboard.today.UpcomingItem
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.captureTarget
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.platinum20.dashboard.autofill.AutoFill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.PaddingValues

/** Gli impegni di oggi e di domani, condivisi tra "Adesso" e l'agenda. */
class AgendaState(
    val granted: Boolean,
    /** null finché non sono stati letti (o manca il permesso). */
    val today: List<CalendarEvent>?,
    val tomorrow: List<CalendarEvent>?,
    val requestPermission: () -> Unit,
) {
    /**
     * Gli impegni di oggi; vuoto se non c'è niente da dire. La sera, finiti tutti, l'agenda sparisce:
     * domani e i giorni dopo li elenca "Prossimi giorni".
     */
    fun shown(now: LocalDateTime = LocalDateTime.now()): List<CalendarEvent> {
        val list = today.orEmpty()
        if (now.hour < Briefing.EVENING_HOUR) return list
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return if (list.none { it.allDay || it.end > nowMs }) emptyList() else list
    }

    /** Con il permesso e nessun impegno la card sparisce: non serve leggere "nessun impegno". */
    val hasContent: Boolean get() = !granted || shown().isNotEmpty()
}

@Composable
fun rememberAgenda(refreshKey: Int = 0): AgendaState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var today by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var tomorrow by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        reload++
        if (!it && CalendarAsk.shouldHide(CalendarAsk.recordDenial(context))) hideAgenda(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = CalendarRepository.hasPermission(context)
        reload++
    }
    LaunchedEffect(granted, reload, refreshKey) {
        if (granted) {
            val day = LocalDate.now()
            today = CalendarRepository.eventsOn(context, day)
            tomorrow = CalendarRepository.eventsOn(context, day.plusDays(1))
        }
    }
    return AgendaState(granted, today, tomorrow) { launcher.launch(Manifest.permission.READ_CALENDAR) }
}

/** Niente più inviti per il calendario: si può sempre consentire da Info app. */
internal fun hideAgenda(context: Context) {
    CalendarAsk.setOff(context)
    CaptureFeedback.tell(context, "Ok, niente calendario: lo puoi consentire da Info app", actionLabel = "Info app", action = { openAppInfo(context) })
}

/** Scadenza di un promemoria, in rosso se già passata. */
@Composable
fun DueText(dueAt: Long, repeat: Repeat? = null) {
    val overdue = dueAt < System.currentTimeMillis()
    Text(
        (if (overdue) "Scaduto · " else "") + dueLabel(dueAt) + (repeat?.let { " · ${it.label}" } ?: ""),
        style = MaterialTheme.typography.labelMedium,
        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Il calendario in Home (Antonio): un'unica card con il mese e quanto hai speso, poi Oggi sempre in cima
 * (la spesa di oggi, anche 0, gli impegni, le cose in ritardo, i promemoria e le note senza data), poi le
 * prossime [Upcoming.MAX] cose. Oggi si mostra tutto, anche se è tanto. Toccando la card si apre il calendario intero.
 */
@Composable
internal fun CalendarCard(groups: List<DueDay>, expenses: List<com.platinum20.dashboard.notes.Expense>, today: LocalDate, onOpenAll: () -> Unit) {
    val inApp = LocalInAppNav.current
    val open = rememberDueOpen()
    val actions = rememberRowActions { inApp?.openEvent?.invoke(it) }
    // Le cose già spuntate solo nel calendario intero, non nella card (Antonio). Le spese di oggi invece sì,
    // una per una come nella pagina: tutto quello di oggi sta sotto OGGI (Antonio, 1.400).
    val pending = groups.map { g -> g.copy(rows = g.rows.filter { it.task?.done != true }) }
        .filter { it.rows.isNotEmpty() }
    val all = timelineEntries(pending, today, months = true, expenses = expenses)
    val todayCount = all.count { it.second.day == today && !it.first.past }
    // Dopo oggi il resto della settimana (almeno [Upcoming.MAX]); il resto del mese è a un tocco, "Altre N a novembre".
    val (later, more) = calendarCardCut(all.drop(todayCount).map { it.second.day }, today, Upcoming.MAX)
    val shown = all.take(todayCount + later)
    val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN)
    TimelineCard(
        "Calendario",
        shown.map { it.first },
        onOpenAll,
        empty = "Niente in programma.",
        // La card si allarga fino a diventare la pagina Calendario (e ci torna chiudendola).
        modifier = Modifier.sharedItem(SharedKeys.CALENDAR, rounded = true).captureTarget(CaptureMode.TASK).captureTarget(CaptureMode.DATE).captureTarget(CaptureMode.PARCEL)
            .captureTarget(CaptureMode.EVENT).captureTarget(CaptureMode.NOTE).captureTarget(CaptureMode.EXPENSE),
        // Anche le cose fatte: toccando di nuovo la spunta tornano da fare.
        // Si spuntano solo le cose senza data: promemoria e date passano da soli, come gli eventi (1.376).
        ticks = shown.map { (_, row) -> row.task?.takeIf { it.tickable }?.let { tickTask(it.id) } },
        // Toccando una riga si apre proprio quella cosa; il resto della card apre il calendario intero.
        rows = shown.map { (_, row) -> { open(row) } },
        // Tenendo premuta una riga: le sue azioni, come nella pagina intera.
        rowLongs = shown.map { (_, row) -> { actions(row) } },
        onMonth = { inApp?.openRoute?.invoke(Routes.expenses(it)) },
        onLongOpen = onOpenAll,
        footer = if (more > 0) (if (more == 1) "Un'altra cosa a $monthName" else "Altre $more a $monthName") else null,
    )
}

/**
 * Che cosa apre il tocco su una riga del calendario, uguale in Home e nella pagina intera: la scheda della cosa,
 * la nota (o il video salvato, che parte), il riepilogo del mese per la spesa di oggi.
 */
@Composable
internal fun rememberDueOpen(): (DueRow) -> Unit {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val openItem = rememberOpenItem()
    return { row ->
        when {
            row.event != null -> inApp?.openEvent?.invoke(row.event)
            row.task != null -> openItem(ItemKind.TASK, row.task.id)
            row.parcel != null -> openItem(ItemKind.PARCEL, row.parcel.id)
            row.charge != null -> openItem(ItemKind.FIXED, row.charge.id)
            row.expense != null -> openItem(ItemKind.EXPENSE, row.expense.id)
            row.spend != null -> inApp?.openRoute?.invoke(Routes.expenses(YearMonth.from(row.day ?: LocalDate.now())))
            row.note != null -> {
                val saved = com.platinum20.dashboard.capture.SavedLinks.find(row.note.text)
                if (saved != null) com.platinum20.dashboard.capture.SavedLinks.open(context, saved) { inApp?.openRoute?.invoke(it) }
                else inApp?.openRoute?.invoke(Routes.noteEdit(row.note.id))
            }
            row.simRenewal -> inApp?.openRoute?.invoke(Routes.SIM)
        }
    }
}

/**
 * La card con la linea del tempo, uguale in Oggi e nella pagina Note (Antonio): il titolo sopra, a destra
 * quanto c'è, dentro le prime righe. Si tocca ovunque per aprire la sezione, senza link in fondo.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineCard(
    title: String,
    entries: List<TimelineEntry>,
    onOpen: () -> Unit,
    empty: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    /** Per ogni riga, la spunta: le cose da fare si fanno dalla card, senza entrare (Antonio). */
    ticks: List<(() -> Unit)?> = emptyList(),
    /** Per ogni riga, che cosa apre toccarla (la sua scheda); null = come il resto della card, la pagina intera. */
    rows: List<(() -> Unit)?> = emptyList(),
    /** Toccando il segno di un mese: il riepilogo delle spese di quel mese. */
    onMonth: ((YearMonth) -> Unit)? = null,
    /** Tenendo premuta la card fuori dalle righe: la pagina intera. */
    onLongOpen: (() -> Unit)? = null,
    /** Per ogni riga, tenendola premuta: il fumetto delle sue azioni (uguale alla pagina intera). */
    rowLongs: List<(() -> Unit)?> = emptyList(),
    /** L'ultima riga, "Altre 8 a novembre": quello che non sta nella card; toccandola si apre la pagina. */
    footer: String? = null,
) {
    val haptic = rememberHaptic()
    Section(
        title = title,
        modifier = modifier,
        trailing = {
            count?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp)) }
        },
    ) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongOpen?.let { open -> { haptic(Haptic.LONG_PRESS); open() } }).padding(vertical = 6.dp)) {
            if (entries.isEmpty()) {
                Text(
                    empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            FlashNewRows(entries.map { it.key })
            entries.forEachIndexed { index, entry ->
                val row = rows.getOrNull(index)
                TimelineRow(entry, top = index > 0, bottom = index < entries.lastIndex, onClick = row?.let { open -> { haptic(Haptic.TICK); open() } } ?: onOpen, onIcon = ticks.getOrNull(index), monthTick = false, onMonth = onMonth, onLongClick = rowLongs.getOrNull(index) ?: onLongOpen, sharedScope = SharedRows.HOME)
            }
            footer?.let { text ->
                Text(
                    "$text ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptic(Haptic.TICK); onOpen() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** La spunta di una cosa da fare dentro una card: la segna fatta (la vibrazione la dà la riga, subito). */
@Composable
internal fun tickTask(id: Long): () -> Unit = { com.platinum20.dashboard.notes.NotesStore.toggleTask(id) }
