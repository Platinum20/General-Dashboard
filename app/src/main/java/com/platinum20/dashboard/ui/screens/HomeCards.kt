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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.platinum20.dashboard.today.BriefKind
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
 * Il calendario in Home (Antonio): un'unica card con il mese, poi Oggi sempre in cima
 * (gli impegni, le cose in ritardo, i promemoria e le note senza data), poi le
 * cose di domani. Oggi si mostra tutto, anche se è tanto. Toccando la card si apre il calendario intero.
 */
@Composable
internal fun CalendarCard(
    groups: List<DueDay>,
    today: LocalDate,
    onOpenAll: () -> Unit,
    habits: List<com.platinum20.dashboard.notes.Habit> = emptyList(),
    /** I giorni passati ([pastDays]): ci si arriva tenendo premuto e trascinando in giù. */
    past: List<DueDay> = emptyList(),
) {
    val open = rememberDueOpen()
    // Oggi come nella pagina, niente nascosto: anche le cose spuntate oggi (Antonio, 1.400 e 2026-11-10).
    val pending = groups.filter { it.rows.isNotEmpty() }
    // Niente segno del mese in cima: il mese è già scritto in alto nella Home (Antonio, 2026-11-10). Il mese dopo sì.
    // Il passato a parte, così da oggi in giù le righe restano quelle di sempre.
    // Le cose da spuntare non stanno nei giorni: hanno la loro sezione in fondo alla card (Antonio, 2026-11-11).
    val all = remember(past, pending, today) { timelineEntries(past.withoutTicks(), today) + timelineEntries(pending.withoutTicks(), today) }
    val todo = remember(pending, today) { cardTodo(pending, today) }
    val days = remember(all, today) { cardDays(all.map { it.second.day to it.first.past }, today) }
    // Tenendo premuto e trascinando si va avanti e indietro di un giorno alla volta (Antonio, 2026-11-11).
    // Lasciata ferma per un po' torna da sola a oggi, e lasciando la Home riparte da oggi.
    var shift by remember { mutableIntStateOf(0) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(shift, touched) {
        if (shift != 0) {
            delay(CARD_BACK_MS)
            shift = 0
        }
    }
    val window = days.window(shift)
    // Dopo oggi solo domani (Antonio, 2026-11-11); il resto del mese è a un tocco, "Altre N a novembre".
    val anchor = window.anchor ?: today
    val month = java.time.YearMonth.from(anchor)
    val more = all.drop(window.to).count { (_, row) -> row.day?.let { it.isAfter(anchor) && java.time.YearMonth.from(it) == month } == true }
    val shown = all.subList(window.from, window.to)
    // Le abitudini sono una riga sola in fondo a OGGI, come nella pagina (punto 19): il tocco apre il fumetto per segnarle.
    val habitAt = if (habits.isNotEmpty() && window.todayEnd != null) window.todayEnd - window.from else -1
    val habitRow = TimelineEntry(
        key = "habits", kind = BriefKind.HABIT, text = habitsLine(habits, today), dayLabel = null,
    )
    fun <T> withHabit(list: List<T>, item: T): List<T> =
        if (habitAt < 0) list else list.toMutableList().apply { add(habitAt.coerceAtMost(size), item) }
    val monthName = anchor.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN)
    TimelineCard(
        // Senza titolo né mese: si apre direttamente su OGGI, una riga in meno (Antonio, 2026-11-10).
        null,
        withHabit(shown.map { it.first }, habitRow),
        onOpenAll,
        empty = "Niente in programma.",
        // La card si allarga fino a diventare la pagina Calendario (e ci torna chiudendola).
        modifier = Modifier.sharedItem(SharedKeys.CALENDAR, rounded = true).captureTarget(CaptureMode.TASK).captureTarget(CaptureMode.DATE).captureTarget(CaptureMode.PARCEL)
            .captureTarget(CaptureMode.EVENT).captureTarget(CaptureMode.NOTE),
        // Anche le cose fatte: toccando di nuovo la spunta tornano da fare.
        // Si spuntano solo le cose senza data: promemoria e date passano da soli, come gli eventi (1.376).
        ticks = withHabit(shown.map { (_, row) -> row.task?.takeIf { it.tickable }?.let { tickTask(it.id) } }, null),
        // Toccando una riga si apre proprio quella cosa (le sue azioni sono nel suo "Altro"); il resto della card apre il calendario intero.
        rows = withHabit(shown.map { (_, row) -> { if (row.today) onOpenAll() else open(row) } }, { ActionBubble.show(*habitsBubble(habits, today)) }),
        // Tenere premuto è solo per scorrere i giorni (Antonio): lasciando senza muovere si apre il calendario intero.
        onLongOpen = onOpenAll,
        onScrub = { steps ->
            touched++
            val next = days.clamp(shift + steps)
            val moved = next != shift
            shift = next
            moved
        },
        footer = if (more > 0) (if (more == 1) "Un'altra cosa a $monthName" else "Altre $more a $monthName") else null,
        bottom = if (todo.isEmpty()) null else ({
            val haptic = rememberHaptic()
            CardTitle("Da fare", trailing = {
                Text("${todo.count { !it.first.done }}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
            })
            todo.forEachIndexed { i, (entry, row) ->
                TimelineRow(
                    entry, top = i > 0, bottom = i < todo.lastIndex,
                    onClick = { haptic(Haptic.TICK); open(row) },
                    onIcon = row.task?.let { tickTask(it.id) },
                    monthTick = false, sharedScope = SharedRows.HOME,
                )
            }
        }),
    )
}

/** Quanto resta spostata la card Calendario prima di tornare da sola a oggi. */
private const val CARD_BACK_MS = 20_000L

/**
 * Che cosa apre il tocco su una riga del calendario, uguale in Home e nella pagina intera: la scheda della cosa,
 * la nota (o il video salvato, che parte).
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
    /** null = niente riga del titolo: la card comincia dalle righe (il Calendario in Home). */
    title: String?,
    entries: List<TimelineEntry>,
    onOpen: () -> Unit,
    empty: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    /** Per ogni riga, la spunta: le cose da fare si fanno dalla card, senza entrare (Antonio). */
    ticks: List<(() -> Unit)?> = emptyList(),
    /** Per ogni riga, che cosa apre toccarla (la sua scheda); null = come il resto della card, la pagina intera. */
    rows: List<(() -> Unit)?> = emptyList(),
    /** Tenendo premuta la card fuori dalle righe: la pagina intera. */
    onLongOpen: (() -> Unit)? = null,
    /** Per ogni riga, tenendola premuta: il fumetto delle sue azioni (uguale alla pagina intera). */
    rowLongs: List<(() -> Unit)?> = emptyList(),
    /** L'ultima riga, "Altre 8 a novembre": quello che non sta nella card; toccandola si apre la pagina. */
    footer: String? = null,
    /** Accanto al titolo, a destra; se c'è vince su [count]. */
    titleTrailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    /**
     * Tenendo premuto e trascinando: quanti giorni avanti (+) o indietro (-); true se si è mossa davvero.
     * Con questo tenere premuto non apre più le azioni delle righe: lasciando senza muovere vale [onLongOpen].
     */
    onScrub: ((Int) -> Boolean)? = null,
    /** In fondo alla card, dopo [footer]: una sezione sua (le cose da spuntare del Calendario). */
    bottom: (@Composable () -> Unit)? = null,
) {
    val haptic = rememberHaptic()
    val scrub by androidx.compose.runtime.rememberUpdatedState(onScrub)
    val longOpen by androidx.compose.runtime.rememberUpdatedState(onLongOpen)
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { SCRUB_STEP.toPx() }
    // Il titolo dentro la card, non più un'etichetta fuori (1.404, punto 7 B).
    CanvasCard(modifier.fillMaxWidth()) {
        if (title != null) CardTitle(title, trailing = titleTrailing ?: {
            if (count != null) Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        })
        Column(Modifier.then(
            if (onScrub == null) Modifier else Modifier.holdToScroll(
                stepPx,
                onHold = { haptic(Haptic.LONG_PRESS) },
                onStep = { steps -> if (scrub?.invoke(steps) == true) haptic(Haptic.TICK) },
                onRelease = { longOpen?.invoke() },
            ),
        ).combinedClickable(onClick = onOpen, onLongClick = onLongOpen?.takeIf { onScrub == null }?.let { open -> { haptic(Haptic.LONG_PRESS); open() } }).padding(top = if (title == null) 4.dp else 0.dp, bottom = 6.dp)) {
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
                TimelineRow(entry, top = index > 0, bottom = index < entries.lastIndex, onClick = row?.let { open -> { haptic(Haptic.TICK); open() } } ?: onOpen, onIcon = ticks.getOrNull(index), monthTick = false, onLongClick = if (onScrub != null) null else rowLongs.getOrNull(index) ?: onLongOpen, sharedScope = SharedRows.HOME)
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
            bottom?.invoke()
        }
    }
}

/** Quanto dito per un giorno, trascinando la card Calendario tenuta premuta. */
private val SCRUB_STEP = 36.dp

/**
 * Tieni premuto, poi trascina: i giorni scorrono a scatti ([DayDrag]). Il gesto si prende tutto solo dopo la
 * pressione lunga, così prima il dito scorre la Home come sempre; una volta preso, né la Home né le righe
 * ricevono più nulla (nemmeno il tocco al rilascio).
 */
private fun Modifier.holdToScroll(
    stepPx: Float,
    onHold: () -> Unit,
    onStep: (Int) -> Unit,
    onRelease: () -> Unit,
): Modifier = pointerInput(stepPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        // null = è passato il tempo della pressione lunga con il dito fermo.
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var gone: Boolean? = null
            while (gone == null) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > slop
                ) gone = true
            }
            gone
        }
        if (ended != null) return@awaitEachGesture
        onHold()
        val drag = DayDrag(stepPx)
        var moved = false
        var total = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() }
            if (change == null || !change.pressed) break
            val dy = change.position.y - change.previousPosition.y
            total += dy
            if (!moved && kotlin.math.abs(total) > slop) moved = true
            if (moved) drag.move(dy).takeIf { it != 0 }?.let(onStep)
        }
        if (!moved) onRelease()
    }
}

/** La spunta di una cosa da fare dentro una card: la segna fatta (la vibrazione la dà la riga, subito). */
@Composable
internal fun tickTask(id: Long): () -> Unit = { com.platinum20.dashboard.notes.NotesStore.toggleTask(id) }
