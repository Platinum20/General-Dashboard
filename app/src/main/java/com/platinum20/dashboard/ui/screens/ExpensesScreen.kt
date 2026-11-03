package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.notes.NotesStore

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

    Scaffold(topBar = { TopLevelBar(title = "Spese", onBack = onBack) }) { inner ->
        ExpensesTab(
            data,
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 12.dp,
                bottom = maxOf(inner.calculateBottomPadding(), bottomPadding.calculateBottomPadding()) + 24.dp,
            ),
            initialMonth = month,
            onDeleted = { e ->
                // Eliminare è immediato, ma per qualche secondo si può tornare indietro.
                CaptureFeedback.show(
                    CaptureNotice(CaptureMode.EXPENSE, "Spesa eliminata", undo = { NotesStore.restoreExpense(e) }, icon = Icons.Outlined.Delete, fly = false),
                )
            },
        )
    }
}
