package com.platinum20.dashboard.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.platinum20.dashboard.calendar.CalendarDays
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarInfo
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.calendar.EventDetail
import com.platinum20.dashboard.calendar.EventDraft
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Quanti giorni mostra l'agenda a partire dal giorno scelto. */
private const val AGENDA_DAYS = 60

private val agendaDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val shortDayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** "Oggi · giovedì 22 ottobre", "Domani · …", poi solo il giorno. */
internal fun agendaDayTitle(day: LocalDate, today: LocalDate): String {
    val text = day.format(agendaDayFormat)
    return when (day) {
        today -> "Oggi · $text"
        today.plusDays(1) -> "Domani · $text"
        today.minusDays(1) -> "Ieri · $text"
        else -> text
    }
}

/** Il calendario del telefono dentro Canvas: gli impegni dei prossimi giorni, giorno per giorno. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEvent: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var first by remember { mutableStateOf(today) }
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var days by remember { mutableStateOf<Map<LocalDate, List<CalendarEvent>>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        reload++
    }
    // Tornando dalla scheda o dalla modifica di un evento l'agenda si rilegge.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = CalendarRepository.hasPermission(context)
        reload++
    }
    LaunchedEffect(granted, first, reload) {
        if (granted) days = CalendarRepository.eventsFrom(context, first, AGENDA_DAYS)
    }

    Scaffold(
        topBar = {
            TopLevelBar(
                title = "Calendario",
                onBack = onBack,
                actions = {
                    if (granted) {
                        RoundIconButton(Icons.Outlined.CalendarMonth, "Vai a un giorno") { picking = true }
                        RoundIconButton(Icons.Outlined.Add, "Nuovo evento") { onNewEvent(first) }
                    }
                },
            )
        },
    ) { inner ->
        val list = days
        when {
            !granted -> CalendarMessage(
                modifier = Modifier.padding(inner),
                text = "Per vedere qui i tuoi impegni Canvas deve poter leggere il calendario del telefono.",
                action = "Consenti",
                onAction = { launcher.launch(Manifest.permission.READ_CALENDAR) },
            )
            list == null -> Box(Modifier.padding(inner))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 4.dp,
                    bottom = inner.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (first != today) {
                    item {
                        CanvasChip(selected = false, onClick = { first = today }, label = "Torna a oggi")
                    }
                }
                if (list.isEmpty()) {
                    item {
                        CalendarMessage(
                            text = "Nessun impegno nei prossimi $AGENDA_DAYS giorni.",
                            action = "Nuovo evento",
                            onAction = { onNewEvent(first) },
                        )
                    }
                }
                items(list.entries.toList(), key = { it.key.toEpochDay() }) { (day, events) ->
                    Section(title = agendaDayTitle(day, today)) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            val now = System.currentTimeMillis()
                            events.forEach { event ->
                                AgendaRow(
                                    event = event,
                                    time = CalendarDays.timeLabel(event.begin, event.end, event.allDay, day, ZoneId.systemDefault()),
                                    past = !event.allDay && event.end < now,
                                    onClick = { onOpenEvent(event) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        DatePickDialog(
            initial = first,
            onDismiss = { picking = false },
            onConfirm = { first = it; picking = false },
        )
    }
}

@Composable
private fun AgendaRow(event: CalendarEvent, time: String, past: Boolean, onClick: () -> Unit) {
    val alpha = if (past) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(event.color, alpha)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (event.location.isNotBlank()) "$time · ${event.location}" else time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColorDot(color: Int, alpha: Float = 1f) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (color != 0) Color(color).copy(alpha = alpha) else MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun CalendarMessage(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

/** Orario di una scheda evento: "Giovedì 22 ottobre · 10:00 – 11:30" o i due giorni se cambia data. */
internal fun eventWhen(begin: Long, end: Long, allDay: Boolean, zone: ZoneId): String {
    if (allDay) {
        val first = Instant.ofEpochMilli(begin).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val last = Instant.ofEpochMilli(end).atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(1)
        val day = first.format(agendaDayFormat).replaceFirstChar { it.uppercase() }
        return if (last <= first) "$day · tutto il giorno" else "$day – ${last.format(agendaDayFormat)}"
    }
    val from = Instant.ofEpochMilli(begin).atZone(zone)
    val to = Instant.ofEpochMilli(end).atZone(zone)
    val day = from.toLocalDate().format(agendaDayFormat).replaceFirstChar { it.uppercase() }
    return if (from.toLocalDate() == to.toLocalDate() || to.toLocalDateTime() == from.toLocalDate().plusDays(1).atStartOfDay()) {
        "$day · ${from.format(clockFormat)} – ${to.format(clockFormat)}"
    } else {
        "$day ${from.format(clockFormat)} – ${to.toLocalDate().format(shortDayFormat)} ${to.format(clockFormat)}"
    }
}

/** La scheda di un evento, con modifica ed eliminazione quando il calendario lo permette. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    /** Inizio e fine di questa volta (per gli eventi che si ripetono); 0 per usare quelli dell'evento. */
    begin: Long,
    end: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var event by remember { mutableStateOf<EventDetail?>(null) }
    var missing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) askWrite?.invoke()
        askWrite = null
    }
    fun withWrite(action: () -> Unit) {
        if (CalendarRepository.canWrite(context)) action() else {
            askWrite = action
            writeLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload++ }
    LaunchedEffect(eventId, reload) {
        val found = CalendarRepository.event(context, eventId)
        event = found
        missing = found == null
    }

    val current = event
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (current?.writable == true) {
                        RoundIconButton(Icons.Outlined.Edit, "Modifica") { withWrite { onEdit(current.id) } }
                        RoundIconButton(Icons.Outlined.Delete, "Elimina") { confirmDelete = true }
                        Spacer(Modifier.width(8.dp))
                    }
                },
            )
        },
    ) { inner ->
        if (current == null) {
            if (missing) {
                Column(
                    Modifier.padding(inner).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Questo evento non c'è più.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val zone = ZoneId.systemDefault()
            val from = if (begin > 0) begin else current.begin
            val to = if (begin > 0 && end > 0) end else current.end
            Column(
                Modifier
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    ColorDot(current.color)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        current.title.ifBlank { "(Senza titolo)" },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                CanvasCard(Modifier.fillMaxWidth()) {
                    DetailRow(Icons.Outlined.Schedule, eventWhen(from, to, current.allDay, zone))
                    if (current.recurring) {
                        RowDivider()
                        DetailRow(Icons.Outlined.Repeat, "Si ripete")
                    }
                    if (current.location.isNotBlank()) {
                        RowDivider()
                        DetailRow(Icons.Outlined.Place, current.location)
                    }
                    if (current.calendarName.isNotBlank()) {
                        RowDivider()
                        DetailRow(Icons.Outlined.CalendarMonth, current.calendarName)
                    }
                }
                if (current.description.isNotBlank()) {
                    Section(title = "Note") {
                        DetailRow(Icons.AutoMirrored.Outlined.Notes, current.description.trim(), maxLines = Int.MAX_VALUE)
                    }
                }
                if (!current.writable) {
                    Text(
                        "Questo calendario è di sola lettura: l'evento si può guardare ma non modificare.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare l'evento?") },
            text = {
                Text(
                    if (current.recurring) "Si ripete: verranno eliminate tutte le volte, anche dal calendario del telefono."
                    else "Sparisce anche dal calendario del telefono."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    withWrite {
                        scope.launch {
                            if (CalendarRepository.delete(context, current.id)) {
                                haptic(Haptic.CONFIRM)
                                onBack()
                            }
                        }
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String, maxLines: Int = 3) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Nuovo evento ([eventId] null) o modifica, salvato nel calendario del telefono. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    eventId: Long?,
    day: LocalDate,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val zone = ZoneId.systemDefault()
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<EventDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context) && CalendarRepository.canWrite(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it } || (CalendarRepository.hasPermission(context) && CalendarRepository.canWrite(context))
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        calendars = CalendarRepository.writableCalendars(context)
        if (draft == null) {
            draft = if (eventId != null) {
                CalendarRepository.event(context, eventId)?.let { EventDraft.from(it, zone) }
            } else {
                calendars.firstOrNull()?.let { EventDraft.new(it.id, day) }
            }
        }
        loaded = true
    }

    fun save() {
        val current = draft ?: return
        val problem = current.problem()
        if (problem != null) {
            error = problem
            return
        }
        saving = true
        scope.launch {
            val id = CalendarRepository.save(context, current, zone)
            saving = false
            if (id != null) {
                haptic(Haptic.CONFIRM)
                onDone()
            } else {
                error = "Il calendario non ha accettato l'evento. Riprova."
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (eventId == null) "Nuovo evento" else "Modifica evento") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Chiudi") }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = draft != null && !saving) { Text("Salva") }
                },
            )
        },
    ) { inner ->
        val current = draft
        when {
            !granted && asked -> CalendarMessage(
                modifier = Modifier.padding(inner),
                text = "Per salvare gli eventi Canvas deve poter scrivere nel calendario del telefono.",
                action = "Consenti",
                onAction = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
            )
            loaded && current == null -> Text(
                if (eventId != null) "Questo evento non c'è più."
                else "Sul telefono non c'è un calendario in cui scrivere: aggiungi un account (per esempio Google) nelle impostazioni.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(inner).padding(24.dp),
            )
            current != null -> EventForm(
                draft = current,
                calendars = calendars,
                error = error,
                isNew = eventId == null,
                onChange = { draft = it; error = null },
                onSave = ::save,
                modifier = Modifier.padding(inner),
            )
        }
    }
}

@Composable
private fun EventForm(
    draft: EventDraft,
    calendars: List<CalendarInfo>,
    error: String?,
    isNew: Boolean,
    onChange: (EventDraft) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }
    val toggle = hapticToggle { onChange(draft.copy(allDay = it)) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            label = { Text("Titolo") },
            singleLine = true,
            isError = error != null && draft.title.isBlank(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = canvasFieldColors(),
            modifier = Modifier.fillMaxWidth().autoFocus(isNew),
        )
        CanvasCard(Modifier.fillMaxWidth()) {
            if (draft.recurring) {
                DetailRow(Icons.Outlined.Repeat, "Si ripete: qui cambi titolo, luogo e note di tutta la serie; gli orari restano quelli del calendario.")
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { toggle(!draft.allDay) }
                        .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tutto il giorno", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = draft.allDay, onCheckedChange = toggle)
                }
                RowDivider()
                TimeRow("Inizio", draft.start, draft.allDay) { pickStart = true }
                RowDivider()
                TimeRow("Fine", draft.end, draft.allDay) { pickEnd = true }
            }
            if (calendars.size > 1 && isNew) {
                RowDivider()
                Box {
                    val chosen = calendars.firstOrNull { it.id == draft.calendarId }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { calendarMenu = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(chosen?.color ?: 0)
                        Spacer(Modifier.width(14.dp))
                        Text(chosen?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("Calendario", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = calendarMenu, onDismissRequest = { calendarMenu = false }) {
                        calendars.forEach { cal ->
                            DropdownMenuItem(
                                text = { Text(if (cal.account.isNotBlank() && cal.account != cal.name) "${cal.name} · ${cal.account}" else cal.name) },
                                leadingIcon = { ColorDot(cal.color) },
                                onClick = { onChange(draft.copy(calendarId = cal.id)); calendarMenu = false },
                            )
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft.location,
            onValueChange = { onChange(draft.copy(location = it)) },
            label = { Text("Luogo") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
            colors = canvasFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            label = { Text("Note") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = canvasFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Salva nel calendario") }
        Spacer(Modifier.height(16.dp))
    }

    fun LocalDateTime.millis() = atZone(zone).toInstant().toEpochMilli()
    fun Long.local() = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
    if (pickStart) {
        val close = { pickStart = false }
        if (draft.allDay) {
            DatePickDialog(draft.start.toLocalDate(), onDismiss = close) { date ->
                // Spostando l'inizio la durata resta la stessa.
                val days = java.time.temporal.ChronoUnit.DAYS.between(draft.start.toLocalDate(), draft.end.toLocalDate())
                onChange(draft.copy(start = date.atStartOfDay(), end = date.plusDays(days).atStartOfDay()))
                close()
            }
        } else {
            DateTimePickDialog(draft.start.millis(), onDismiss = close) { picked ->
                val length = java.time.Duration.between(draft.start, draft.end)
                val start = picked.local()
                onChange(draft.copy(start = start, end = start.plus(length)))
                close()
            }
        }
    }
    if (pickEnd) {
        val close = { pickEnd = false }
        if (draft.allDay) {
            DatePickDialog(draft.end.toLocalDate(), onDismiss = close) { date ->
                onChange(draft.copy(end = date.atStartOfDay()))
                close()
            }
        } else {
            DateTimePickDialog(draft.end.millis(), onDismiss = close) { picked ->
                onChange(draft.copy(end = picked.local()))
                close()
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, value: LocalDateTime, allDay: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value.toLocalDate().format(shortDayFormat) + if (allDay) "" else "  ${value.format(clockFormat)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
