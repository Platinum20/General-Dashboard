package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.bank.RevolutLink
import com.platinum20.dashboard.bank.RevolutStore

/**
 * Il riepilogo delle spese di un mese a schermo intero: ci si arriva toccando il segno del mese (o la spesa
 * di oggi) nel calendario (1.314). Il mese, le categorie, le fisse e la lista; di lato si cambia mese.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(onBack: () -> Unit, bottomPadding: PaddingValues = PaddingValues(), month: java.time.YearMonth = java.time.YearMonth.now()) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    RevolutStore.init(context)
    val linked by RevolutStore.linked.collectAsStateWithLifecycle()
    val expired by RevolutStore.expired.collectAsStateWithLifecycle()
    val balance by RevolutStore.balance.collectAsStateWithLifecycle()
    // Aperta la pagina, i movimenti si rileggono se l'ultima lettura è vecchia.
    AutoRefresh(everyMs = RevolutStore.STALE_MS) { if (linked && !expired && RevolutStore.isStale()) RevolutLink.refresh(context) }

    // La card Revolut della Home si allarga fino a diventare questa pagina (1.357).
    Scaffold(modifier = Modifier.sharedItem(SharedKeys.BANK, rounded = true), topBar = { TopLevelBar(title = "Spese", subtitle = if (linked && expired) revolutLine(linked, expired, balance) else null, onBack = onBack) }) { inner ->
        ExpensesTab(
            data,
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 12.dp,
                bottom = maxOf(inner.calculateBottomPadding(), bottomPadding.calculateBottomPadding()) + 24.dp,
            ),
            initialMonth = month,
            // Il saldo di adesso sta nel riepilogo del mese, sotto il totale, non più piccolo nel sottotitolo (1.357).
            bankLine = balance?.takeIf { linked && !expired }?.let { "Saldo Revolut adesso · " + com.platinum20.dashboard.notes.Expenses.format(it) },
            onDeleted = { e ->
                // Eliminare è immediato, ma per qualche secondo si può tornare indietro.
                CaptureFeedback.show(
                    CaptureNotice(CaptureMode.EXPENSE, "Spesa eliminata", undo = { NotesStore.restoreExpense(e) }, icon = Icons.Outlined.Delete, fly = false),
                )
            },
        )
    }
}
