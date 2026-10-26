package com.platinum20.dashboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.capture.CaptureDialog
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.ReceiptReader
import kotlinx.coroutines.launch

/**
 * Spese, una pagina sua dalla 1.186: ci si arriva dalla card Spese di Oggi (prima stava in Note).
 * Il mese, il budget, le fisse e lo scontrino da fotografare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Lo scontrino fotografato apre lo stesso pannello del +, già scritto, da controllare e salvare.
    var receiptText by remember { mutableStateOf<String?>(null) }
    receiptText?.let { CaptureDialog(initial = it, initialMode = CaptureMode.EXPENSE, onClose = { receiptText = null }) }
    var readingReceipt by remember { mutableStateOf(false) }
    val receiptPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        readingReceipt = true
        scope.launch {
            val guess = ReceiptReader.read(context, ReceiptReader.photoUri(context))
            readingReceipt = false
            if (guess != null) receiptText = guess.captureText
            else Toast.makeText(context, "Non trovo il totale: riprova con lo scontrino dritto e ben illuminato", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(topBar = { TopLevelBar(title = "Spese", onBack = onBack) }) { inner ->
        ExpensesTab(
            data,
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 12.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            onDeleted = { e ->
                // Eliminare è immediato, ma per qualche secondo si può tornare indietro.
                CaptureFeedback.show(
                    CaptureNotice(CaptureMode.EXPENSE, "Spesa eliminata", undo = { NotesStore.restoreExpense(e) }, icon = Icons.Outlined.Delete, fly = false),
                )
            },
            readingReceipt = readingReceipt,
            onReceipt = { runCatching { receiptPhoto.launch(ReceiptReader.photoUri(context)) } },
        )
    }
}
