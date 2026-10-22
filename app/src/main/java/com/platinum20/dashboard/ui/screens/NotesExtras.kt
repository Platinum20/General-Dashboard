package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.Routes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
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
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Parcel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val dayHeader = DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN)
private val fixedNext = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

/**
 * Spese: si scrivono come un messaggio ("12,50 pizza"), la categoria si indovina dalle parole.
 * In alto il totale del mese, il budget e dove vanno i soldi.
 */
@Composable
fun ExpensesTab(data: NotesData, padding: PaddingValues, onDeleted: (Expense) -> Unit = {}) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var draft by rememberSaveable { mutableStateOf("") }
    var editingBudget by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val fixedDraft = remember(draft) { Expenses.parseFixed(draft) }
    val parsed = remember(draft) { if (fixedDraft != null) null else Expenses.parse(draft) }
    var stopping by remember { mutableStateOf<FixedExpense?>(null) }
    val haptic = rememberHaptic()
    val add = {
        // "ogni mese il 5 affitto 650" è una spesa fissa; il resto una spesa di oggi.
        val saved = if (fixedDraft != null) NotesStore.addFixedExpense(draft) else NotesStore.addExpense(draft)
        if (saved != null) {
            haptic(Haptic.CONFIRM)
            draft = ""
        }
    }
    val total = Expenses.total(data.expenses, month)
    val previous = Expenses.total(data.expenses, month.minusMonths(1))
    val inMonth = data.expenses.filter { YearMonth.from(it.date) == month }.sortedByDescending { it.date }

    LazyColumn(state = rememberReselectListState(Routes.NOTES), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Es. 12,50 pizza · ogni mese il 5 affitto 650") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    if (parsed != null || fixedDraft != null) IconButton(onClick = add) { Icon(Icons.Outlined.Add, contentDescription = "Aggiungi") }
                },
                shape = MaterialTheme.shapes.large,
                colors = canvasFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.isNotBlank()) {
                Text(
                    fixedDraft?.let { "Ogni mese il ${it.day} · ${Expenses.format(it.cents)} · ${it.category.label}" + if (it.text.isNotEmpty()) " · ${it.text}" else "" }
                        ?: parsed?.let { "${Expenses.format(it.cents)} · ${it.category.label}" + if (it.text.isNotEmpty()) " · ${it.text}" else "" }
                        ?: "Scrivi anche l'importo",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (parsed != null || fixedDraft != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            CanvasCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mese prima")
                        }
                        Text(
                            month.format(monthFormat).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mese dopo")
                        }
                    }
                    Text(Expenses.format(total), style = MaterialTheme.typography.displaySmall)
                    if (previous > 0) {
                        val diff = total - previous
                        Text(
                            "Il mese prima ${Expenses.format(previous)}" +
                                if (month == YearMonth.now()) "" else if (diff > 0) " · +${Expenses.format(diff)}" else " · -${Expenses.format(-diff)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val (fixedCents, variableCents) = Expenses.split(data.expenses, month)
                    if (fixedCents > 0) {
                        Text(
                            "Fisse ${Expenses.format(fixedCents)} · variabili ${Expenses.format(variableCents)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val budget = data.budgetCents
                    Row(
                        Modifier.fillMaxWidth().bouncyPress(0.98f).clickable { editingBudget = true }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (budget == null) {
                            Text("Imposta un budget mensile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                val over = total > budget
                                LinearProgressIndicator(
                                    progress = { (total.toFloat() / budget).coerceIn(0f, 1f) },
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (over) "Budget ${Expenses.format(budget)} superato di ${Expenses.format(total - budget)}"
                                    else "Budget ${Expenses.format(budget)} · restano ${Expenses.format(budget - total)}" +
                                        // Le fisse ancora da arrivare sono già impegnate: il "al giorno" conta solo il resto.
                                        if (month == YearMonth.now()) " (${dailyLeft(budget - total - Expenses.fixedStillDue(data.fixedExpenses, LocalDate.now()))} al giorno)" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    val split = Expenses.byCategory(data.expenses, month)
                    if (split.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        split.forEach { (category, cents) ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(category.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
                                LinearProgressIndicator(
                                    progress = { cents.toFloat() / total.coerceAtLeast(1) },
                                    modifier = Modifier.weight(1f),
                                    drawStopIndicator = {},
                                )
                                Text(
                                    Expenses.format(cents),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 8.dp).width(72.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (data.fixedExpenses.isNotEmpty()) {
            item(key = "fixed") {
                Column(Modifier.padding(bottom = 8.dp)) {
                    SectionHeader("Spese fisse · ${Expenses.format(data.fixedExpenses.sumOf { it.cents })} al mese")
                    CanvasCard(Modifier.fillMaxWidth()) {
                        data.fixedExpenses.sortedBy { it.day }.forEachIndexed { i, f ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().bouncyPress(0.98f).clickable { stopping = f }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
        // Un giorno = un'etichetta e una card con le sue spese, come le altre liste dell'app.
        inMonth.groupBy { it.date }.forEach { (day, spent) ->
            item(key = "day-$day") {
                Column {
                    Text(
                        when (day) {
                            LocalDate.now() -> "Oggi"
                            LocalDate.now().minusDays(1) -> "Ieri"
                            else -> day.format(dayHeader).replaceFirstChar { it.uppercase() }
                        }.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp),
                    )
                    CanvasCard(modifier = Modifier.fillMaxWidth()) {
                        spent.forEachIndexed { i, expense ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = expense }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(expense.text.ifEmpty { expense.category.label }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(expense.category.label + if (expense.fixedId != null) " · fissa" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Expenses.format(expense.cents), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
        if (inMonth.isEmpty()) {
            item {
                Text(
                    "Nessuna spesa in questo mese. Scrivila come un messaggio, \"12,50 pizza\" o \"benzina 40€\": " +
                        "la categoria si sceglie da sola. Con un budget, Oggi ti avvisa quando ti avvicini al limite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (editingBudget) BudgetDialog(data.budgetCents, onDismiss = { editingBudget = false })
    stopping?.let { f ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            title = { Text(f.text.ifEmpty { "Spesa fissa" }) },
            text = { Text("Smettere di aggiungerla ogni mese? Quelle già aggiunte restano nei mesi passati.") },
            confirmButton = {
                TextButton(onClick = {
                    NotesStore.deleteFixedExpense(f.id)
                    stopping = null
                }) { Text("Smetti") }
            },
            dismissButton = { TextButton(onClick = { stopping = null }) { Text("Annulla") } },
        )
    }
    editing?.let { ExpenseDialog(it, onDismiss = { editing = null }, onDeleted = onDeleted) }
}

private fun dailyLeft(cents: Long): String {
    val today = LocalDate.now()
    val days = today.lengthOfMonth() - today.dayOfMonth + 1
    return Expenses.format((cents / days).coerceAtLeast(0))
}

private fun centsOf(text: String): Long? {
    val clean = text.replace("€", "").filterNot { it.isWhitespace() }
    // "1.234,50" (come lo scrive l'app sopra i mille euro): il punto separa le migliaia.
    val number = if (',' in clean) clean.replace(".", "").replace(',', '.') else clean
    return number.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
}

@Composable
private fun BudgetDialog(current: Long?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.let { (it / 100).toString() }.orEmpty()) }
    val save = {
        NotesStore.setBudget(centsOf(text))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget mensile") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Es. 600") },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.autoFocus(enabled = current == null),
            )
        },
        confirmButton = {
            TextButton(onClick = save) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                NotesStore.setBudget(null)
                onDismiss()
            }) { Text("Togli") }
        },
    )
}

@Composable
private fun ExpenseDialog(expense: Expense, onDismiss: () -> Unit, onDeleted: (Expense) -> Unit) {
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
    AlertDialog(
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
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text(longDate(date), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = cents != null) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
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

/** Pacchi in arrivo: cosa aspetti, quando arriva e il tracciamento a un tocco. */
@Composable
fun ParcelsTab(parcels: List<Parcel>, padding: PaddingValues, onDeleted: (Parcel) -> Unit = {}) {
    var editing by remember { mutableStateOf<Parcel?>(null) }
    val inApp = LocalInAppNav.current
    val today = LocalDate.now()
    val incoming = parcels.filter { !it.delivered }.sortedBy { it.expected ?: LocalDate.MAX }
    val delivered = parcels.filter { it.delivered }.sortedByDescending { it.deliveredOn }
    val haptic = rememberHaptic()

    LazyColumn(state = rememberReselectListState(Routes.NOTES), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(incoming + delivered, key = { it.id }) { parcel ->
            CanvasCard(onClick = { editing = parcel }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().bouncyPress(0.98f)) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = parcel.delivered, onCheckedChange = {
                        haptic(if (it) Haptic.CONFIRM else Haptic.TICK)
                        NotesStore.toggleDelivered(parcel.id)
                    })
                    Column(Modifier.weight(1f)) {
                        Text(
                            parcel.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = if (parcel.delivered) TextDecoration.LineThrough else null,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val expected = parcel.expected
                        val whenText = when {
                            parcel.delivered -> "Consegnato"
                            expected == null -> "Data non nota"
                            expected.isBefore(today) -> "Atteso ${dueDay(expected, today)}: in ritardo"
                            else -> "Arriva ${dueDay(expected, today)}"
                        }
                        Text(
                            whenText + if (parcel.code.isNotEmpty()) " · ${parcel.carrier.label}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (!parcel.delivered && expected != null && expected.isBefore(today)) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    parcel.trackingUrl()?.let { url ->
                        // La pagina del corriere si apre dentro Canvas, non nel browser.
                        IconButton(onClick = { inApp?.openWeb(url, parcel.carrier.label) }) {
                            Icon(Icons.Outlined.LocalShipping, contentDescription = "Traccia")
                        }
                    }
                }
            }
        }
        if (parcels.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nessun pacco in arrivo. Aggiungilo con + (o condividi la mail di spedizione verso \"Salva in Canvas\"): " +
                                "il giorno della consegna compare in Oggi e il tracciamento si apre con un tocco.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    editing?.let { ParcelDialog(it, onDismiss = { editing = null }, onDeleted = onDeleted) }
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
    AlertDialog(
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
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
            TextButton(onClick = {
                NotesStore.saveParcel(parcel?.id, title, code, expected)
                onDismiss()
            }, enabled = title.isNotBlank() || code.isNotBlank()) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
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
