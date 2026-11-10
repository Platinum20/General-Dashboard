package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.Routes
import android.content.ClipboardManager
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.autofill.AutoFill
import com.platinum20.dashboard.notes.ParcelHint
import com.platinum20.dashboard.notes.ParcelHints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Parcel
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val fixedNext = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

/**
 * Spese: il totale del mese e dove vanno i soldi. Si aggiungono solo dal +
 * ("12,50 pizza", "ogni mese il 5 affitto 650"), la categoria si indovina dalle parole.
 */
@Composable
fun ExpensesTab(
    data: NotesData,
    padding: PaddingValues,
    onDeleted: (Expense) -> Unit = {},
    initialMonth: YearMonth = YearMonth.now(),
    /** "Saldo Revolut adesso · 1.240 €": solo nel riepilogo del mese in corso, sotto il totale (1.357). */
    bankLine: String? = null,
) {
    var month by remember { mutableStateOf(initialMonth) }
    // Ogni spesa si apre a schermo intero, come gli eventi del calendario.
    val openItem = rememberOpenItem()
    val total = Expenses.total(data.expenses, month)
    val inMonth = data.expenses.filter { YearMonth.from(it.date) == month }.sortedWith(Expenses.byTime)
    val listState = rememberReselectListState(Routes.EXPENSES)
    // La pagina si apre in cima al mese (Antonio), non su "OGGI": il mese si legge dall'inizio.
    androidx.compose.runtime.LaunchedEffect(month) { runCatching { listState.scrollToItem(0) } }

    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    Column(
        Modifier.padding(
            start = padding.calculateStartPadding(direction),
            end = padding.calculateEndPadding(direction),
            top = padding.calculateTopPadding(),
        ),
    ) {
        // Il riepilogo all'apertura fa un piccolo giro a sinistra e a destra (Antonio): si capisce che si trascina
        // di lato per cambiare mese. Mentre lo trascini segue il dito con un po' di elastico; quando lasciandolo
        // cambierebbe mese vibra, e il nuovo mese entra dal lato opposto a quello verso cui l'hai spinto.
        val nudge = remember { androidx.compose.animation.core.Animatable(0f) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val nudgePx = with(density) { 14.dp.toPx() }
        val threshold = with(density) { 56.dp.toPx() }
        var cardWidth by remember { mutableStateOf(0f) }
        var shownMonth by remember { mutableStateOf(month) }
        // Velocità con cui il mese vecchio è uscito, passata a quello che entra.
        var carry by remember { mutableStateOf(0f) }
        val monthSpring = remember {
            androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
        }
        val exitSpring = remember {
            androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium)
        }
        val fling = with(density) { 400.dp.toPx() }
        val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
        androidx.compose.runtime.LaunchedEffect(month) {
            if (month == shownMonth) {
                kotlinx.coroutines.delay(Motion.ENTER_MS.toLong())
                nudge.animateTo(-nudgePx, androidx.compose.animation.core.tween(Motion.ENTER_MS / 2, easing = Motion.Standard))
                nudge.animateTo(nudgePx, androidx.compose.animation.core.tween(Motion.ENTER_MS, easing = Motion.Standard))
                nudge.animateTo(0f, Motion.settle())
            } else {
                // Il nuovo mese entra con la stessa velocità con cui è uscito il vecchio: un solo movimento continuo.
                val width = cardWidth.takeIf { it > 0f } ?: (4 * threshold)
                nudge.snapTo(if (month > shownMonth) width else -width)
                shownMonth = month
                nudge.animateTo(0f, monthSpring, initialVelocity = carry)
                carry = 0f
            }
        }
        val haptic = rememberHaptic()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var drag by remember { mutableStateOf(0f) }
        var armed by remember { mutableStateOf(false) }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                // Il mese si cambia trascinando di lato tutta la pagina (Antonio), non solo il riepilogo. Il gesto sta
                // fuori dai graphicsLayer: la card e la lista che si spostano non falsano il trascinamento.
                .pointerInput(threshold) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f; armed = false; tracker.resetTracking() },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val target = MonthSwipe.target(month, drag, threshold, YearMonth.now(), velocity, fling)
                            val out = drag
                            // Un colpo veloce che cambia mese senza aver passato la soglia vibra lo stesso.
                            if (target != null && !armed) haptic(Haptic.THRESHOLD)
                            drag = 0f
                            armed = false
                            scope.launch {
                                if (target != null) {
                                    // Esce dal lato verso cui l'hai spinto continuando alla velocità del dito (prima
                                    // ripartiva da fermo e sembrava tornare indietro), poi il nuovo mese entra dall'altro.
                                    val width = cardWidth.takeIf { it > 0f } ?: (4 * threshold)
                                    val sign = if (out < 0) -1f else 1f
                                    val start = sign * maxOf(kotlin.math.abs(velocity), fling)
                                    // Mira oltre il bordo con il limite sul bordo: appena è fuori si passa al mese nuovo,
                                    // senza aspettare la coda della molla, e la velocità di quel momento passa a chi entra.
                                    nudge.updateBounds(-width, width)
                                    val result = try {
                                        nudge.animateTo(sign * width * 1.5f, exitSpring, initialVelocity = start)
                                    } finally {
                                        nudge.updateBounds(null, null)
                                    }
                                    carry = result.endState.velocity.takeIf { it * sign > 0f } ?: (sign * fling)
                                    month = target
                                } else {
                                    nudge.animateTo(0f, Motion.settle(), initialVelocity = velocity)
                                }
                            }
                        },
                        onDragCancel = {
                            drag = 0f
                            armed = false
                            scope.launch { nudge.animateTo(0f, Motion.settle()) }
                        },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        drag += amount
                        val can = MonthSwipe.target(month, drag, threshold, YearMonth.now()) != null
                        if (can != armed) {
                            armed = can
                            if (can) haptic(Haptic.THRESHOLD)
                        }
                        val blocked = drag < 0 && month >= YearMonth.now()
                        scope.launch { nudge.snapTo(MonthSwipe.follow(drag, threshold, blocked)) }
                    }
                }
        ) {
            // Il riepilogo scorre con la lista (Antonio): tutta la pagina si muove insieme.
            item(key = "summary") {
                Column {
                    CanvasCard(
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { cardWidth = it.width.toFloat() }
                            .graphicsLayer { translationX = nudge.value },
                    ) {
                        // Il mese si cambia trascinando il riepilogo di lato (Antonio): a destra il mese prima, a sinistra il dopo.
                        Column(
                            Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    month.format(monthFormat).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.weight(1f))
                                RollingText(Expenses.format(total), style = MaterialTheme.typography.headlineSmall)
                            }
                            // Sotto il totale solo fisse e variabili di questo mese: niente mese prima, né confronti
                            // (Antonio, più volte). Il mese prima si vede trascinando il riepilogo.
                            monthSubline(data.expenses, month)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (bankLine != null && month == YearMonth.now()) {
                                Text(
                                    bankLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val categories = Expenses.byCategory(data.expenses, month)
                            if (categories.isNotEmpty()) CategoryBar(categories, Modifier.padding(top = 10.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (data.fixedExpenses.isNotEmpty()) {
                item(key = "fixed") {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        SectionHeader("Spese fisse · ${Expenses.format(data.fixedExpenses.sumOf { it.cents })} al mese")
                        CanvasCard(Modifier.fillMaxWidth()) {
                            data.fixedExpenses.sortedBy { it.day }.forEachIndexed { i, f ->
                                if (i > 0) RowDivider()
                                Row(
                                    Modifier.fillMaxWidth().bouncyPress(0.98f).clickable { openItem(ItemKind.FIXED, f.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(f.text.ifEmpty { f.category.label }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "Il ${f.day} di ogni mese · prossima ${f.nextDate(LocalDate.now()).format(fixedNext)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(Expenses.format(f.cents), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
            // Le spese del mese sulla stessa linea del tempo di Prossimi giorni e Note.
            if (inMonth.isNotEmpty()) {
                item(key = "timeline") {
                    val entries = expenseEntries(inMonth, LocalDate.now(), header = month)
                    // "OGGI" solo nel mese di oggi: prima delle spese di oggi, o in fondo.
                    val nowAt = if (month == YearMonth.now()) nowIndex(entries.map { it.first }) else -1
                    val nowMarker = Modifier.padding(start = 16.dp)
                    // Le spese del mese si spostano insieme al riepilogo (Antonio): cambiando mese cambia tutta la pagina.
                    Column(Modifier.fillMaxWidth().graphicsLayer { translationX = nudge.value }.padding(top = 8.dp)) {
                        entries.forEachIndexed { index, (entry, expense) ->
                            if (index == nowAt && index > 0) TimelineNowMarker(nowMarker)
                            TimelineRow(
                                entry,
                                top = index > 0,
                                bottom = index < entries.lastIndex,
                                onClick = { openItem(ItemKind.EXPENSE, expense.id) },
                                sharedScope = SharedRows.EXPENSES,
                                onLongClick = {
                                    ActionBubble.show(
                                        BubbleAction(Icons.Outlined.Event, "Apri", detail = entry.text) { openItem(ItemKind.EXPENSE, expense.id) },
                                        BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                                            NotesStore.deleteExpense(expense.id)
                                            onDeleted(expense)
                                        },
                                    )
                                },
                            )
                        }
                        if (nowAt == entries.size) TimelineNowMarker(nowMarker)
                    }
                }
            }
            if (inMonth.isEmpty()) {
                item(key = "empty") {
                    // Mese senza spese: "Nessuna spesa" al centro dello spazio che resta (Antonio).
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().graphicsLayer { translationX = nudge.value }, contentAlignment = Alignment.Center) {
                        Text(
                            "Nessuna spesa",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** La riga sotto il totale del riepilogo: fisse e variabili del solo [month], null senza fisse. Logica pura. */
internal fun monthSubline(expenses: List<Expense>, month: YearMonth): String? {
    val (fixedCents, variableCents) = Expenses.split(expenses, month)
    return if (fixedCents > 0) "fisse ${Expenses.format(fixedCents)} · variabili ${Expenses.format(variableCents)}" else null
}

internal fun centsOf(text: String): Long? {
    val clean = text.replace("€", "").filterNot { it.isWhitespace() }
    // "1.234,50" (come lo scrive l'app sopra i mille euro): il punto separa le migliaia.
    val number = if (',' in clean) clean.replace(".", "").replace(',', '.') else clean
    return number.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
}

@Composable
internal fun ExpenseDialog(expense: Expense, onDismiss: () -> Unit, onDeleted: (Expense) -> Unit) {
    var text by remember { mutableStateOf(expense.text) }
    var amount by remember { mutableStateOf(Expenses.format(expense.cents).replace("€", "").trim()) }
    var category by remember { mutableStateOf(expense.category) }
    var date by remember { mutableStateOf(expense.date) }
    var picking by remember { mutableStateOf(false) }
    val cents = centsOf(amount)?.takeIf { it > 0 }
    val save = {
        if (cents != null) {
            NotesStore.saveExpense(expense.copy(cents = cents, text = text.trim(), category = category, date = date))
            onDismiss()
        }
    }
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spesa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    suffix = { Text("€") },
                    // Importo non valido: lo diciamo invece di chiudere senza salvare.
                    isError = cents == null,
                    supportingText = if (cents == null) ({ Text("Scrivi un importo, es. 12,50") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Descrizione") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipRow(
                    options = com.platinum20.dashboard.notes.ExpenseCategory.entries.map { it.label },
                    selected = category.ordinal,
                    onSelect = { category = com.platinum20.dashboard.notes.ExpenseCategory.entries[it] },
                )
                Row(
                    Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    Text(longDate(date), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            ConfirmButton(onClick = save, enabled = cents != null) { Text("Salva") }
        },
        dismissButton = {
            DismissButton(danger = true, onClick = {
                NotesStore.deleteExpense(expense.id)
                onDismiss()
                onDeleted(expense)
            }) { Text("Elimina") }
        },
    )
    if (picking) {
        DatePickDialog(initial = date, onDismiss = { picking = false }, onConfirm = {
            date = it
            picking = false
        })
    }
}

/** Una fila di chip che va a capo da sola. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            CanvasChip(selected = i == selected, onClick = { onSelect(i) }, label = label)
        }
    }
}

/**
 * Un pacco in "In arrivo", come un conto alla rovescia: cosa arriva, il corriere e a destra quanto manca
 * ("Domani", "3 giorni"). Il camioncino apre il tracciamento; tenendo premuto si segna arrivato (o non ancora).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParcelCard(parcel: Parcel, onClick: () -> Unit) {
    val inApp = LocalInAppNav.current
    val today = LocalDate.now()
    val haptic = rememberHaptic()
    val expected = parcel.expected
    val late = !parcel.delivered && expected != null && expected.isBefore(today)
    val days = expected?.let { ChronoUnit.DAYS.between(today, it) }
    CanvasCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic(if (parcel.delivered) Haptic.TICK else Haptic.CONFIRM)
                    NotesStore.toggleDelivered(parcel.id)
                },
            ),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    parcel.title.trimEnd('"', '“', '”', ' '),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (parcel.delivered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        "Pacco",
                        parcel.carrier.label.takeIf { parcel.code.isNotEmpty() },
                        when {
                            parcel.delivered -> "arrivato"
                            late -> "atteso ${dueDay(expected!!, today)}, in ritardo"
                            expected != null && days!! > 1 -> longDate(expected).replaceFirstChar { it.lowercase() }
                            expected == null -> "data non nota"
                            else -> null
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            parcel.trackingUrl()?.takeIf { !parcel.delivered }?.let { url ->
                // La pagina del corriere si apre dentro Canvas, non nel browser.
                IconButton(onClick = { inApp?.openWeb(url, parcel.carrier.label) }) {
                    Icon(Icons.Outlined.LocalShipping, contentDescription = "Traccia", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // A destra quanto manca, come per le date: il numero grande e "giorni" sotto.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                val big = when {
                    parcel.delivered -> "✓"
                    days == null -> "?"
                    late -> "In ritardo"
                    days == 0L -> "Oggi"
                    days == 1L -> "Domani"
                    else -> "$days"
                }
                Text(
                    big,
                    style = if (big.length > 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = when {
                        late -> MaterialTheme.colorScheme.error
                        parcel.delivered || days == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (!parcel.delivered && days != null && days > 1) {
                    Text("giorni", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun dueDay(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "oggi"
    1L -> "domani"
    -1L -> "ieri"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN))
}

/** Nuovo pacco (parcel null) o modifica. [initialCode] arriva da un testo condiviso. */
@Composable
fun ParcelDialog(
    parcel: Parcel?,
    onDismiss: () -> Unit,
    initialTitle: String = "",
    initialCode: String = "",
    onDeleted: (Parcel) -> Unit = {},
) {
    var title by remember { mutableStateOf(parcel?.title ?: initialTitle) }
    var code by remember { mutableStateOf(parcel?.code ?: initialCode) }
    var expected by remember { mutableStateOf(parcel?.expected) }
    var picking by remember { mutableStateOf(false) }
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parcel == null) "Nuovo pacco" else "Pacco") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Cosa arriva (es. Cuffie)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().autoFocus(enabled = parcel == null && initialTitle.isBlank()),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("Codice di tracciamento (facoltativo)") },
                    supportingText = { if (code.isNotBlank()) Text(Carrier.detect(code).label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        expected?.let { "Arriva " + longDate(it) } ?: "Quando arriva?",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (expected != null) {
                        IconButton(onClick = { expected = null }) { Icon(Icons.Outlined.Close, contentDescription = "Togli data") }
                    }
                }
            }
        },
        confirmButton = {
            ConfirmButton(onClick = {
                NotesStore.saveParcel(parcel?.id, title, code, expected)
                onDismiss()
            }, enabled = title.isNotBlank() || code.isNotBlank()) { Text("Salva") }
        },
        dismissButton = {
            DismissButton(danger = parcel != null, onClick = {
                onDismiss()
                parcel?.let {
                    NotesStore.deleteParcel(it.id)
                    onDeleted(it)
                }
            }) { Text(if (parcel == null) "Annulla" else "Elimina") }
        },
    )
    if (picking) {
        DatePickDialog(initial = expected ?: LocalDate.now().plusDays(2), onDismiss = { picking = false }, onConfirm = {
            expected = it
            picking = false
        })
    }
}

/**
 * Come si divide il mese: una barra con un pezzo per categoria, nei colori delle icone sulla linea, e
 * sotto una riga per categoria con il suo pallino.
 */
@Composable
private fun CategoryBar(split: List<Pair<com.platinum20.dashboard.notes.ExpenseCategory, Long>>, modifier: Modifier = Modifier) {
    val sum = split.sumOf { it.second }.coerceAtLeast(1L)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)) {
            split.forEach { (category, cents) ->
                Box(
                    Modifier.weight((cents.toFloat() / sum).coerceAtLeast(0.01f)).fillMaxHeight()
                        .background(androidx.compose.ui.graphics.Color(category.color())),
                )
            }
        }
        // Una riga per categoria, l'importo per primo.
        split.sortedByDescending { it.second }.forEach { (category, cents) ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(category.color())))
                Spacer(Modifier.width(10.dp))
                Text(Expenses.format(cents), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
                Text(category.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Trascinando il riepilogo di Spese: oltre [threshold] px si cambia mese, mai oltre quello di [now]. Logica pura. */
internal object MonthSwipe {
    fun target(month: YearMonth, drag: Float, threshold: Float, now: YearMonth, velocity: Float = 0f, fling: Float = Float.MAX_VALUE): YearMonth? {
        // Un colpo veloce cambia mese anche se corto, come le pagine (Antonio: lasciando il dito non deve tornare indietro);
        // un colpo nel verso opposto al trascinamento invece lo annulla.
        val push = when {
            velocity >= fling && drag > 0f -> 1
            velocity <= -fling && drag < 0f -> -1
            kotlin.math.abs(velocity) >= fling -> 0
            drag >= threshold -> 1
            drag <= -threshold -> -1
            else -> 0
        }
        return when {
            push > 0 -> month.minusMonths(1)
            push < 0 && month < now -> month.plusMonths(1)
            else -> null
        }
    }

    /**
     * Quanto si sposta la card trascinando di [drag] px: segue il dito con elastico, sempre meno oltre la soglia.
     * Verso un mese che non c'è ancora ([blocked]) resiste molto di più.
     */
    fun follow(drag: Float, threshold: Float, blocked: Boolean = false): Float {
        val limit = threshold * (if (blocked) 0.5f else 2.2f)
        return (limit * kotlin.math.tanh(drag / limit)).toFloat()
    }
}
