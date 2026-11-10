package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.bank.RevolutStore
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.shifts.ShiftStore
import com.platinum20.dashboard.sim.SimStore

/**
 * Impostazioni › Strumenti (punto 49): le letture di SIM, Revolut e turni da mandarmi quando un numero non
 * torna. Prima stavano in mezzo agli account, allo stesso livello delle scelte vere.
 */
@Composable
internal fun CaptureFileRows() {
    val context = LocalContext.current
    androidx.compose.runtime.remember { SimStore.init(context); RevolutStore.init(context); ShiftStore.init(context) }
    val sources = listOf(
        Triple("SIM WindTre", "windtre-dati-letti") { SimStore.capturesText() },
        Triple("Revolut", "revolut-dati-letti") { RevolutStore.capturesText() },
        Triple("Turni", "turni-dati-letti") { ShiftStore.capturesText() },
    )
    sources.forEachIndexed { i, (title, file, text) ->
        if (i > 0) RowDivider()
        SettingsItem(
            icon = Icons.Outlined.Share,
            accent = TileAccent.NEUTRAL,
            title = title,
            subtitle = "Salva in Download o condividi",
            onClick = {
                val where = saveTextToDownloads(context, stampedName(file), text())
                CaptureFeedback.show(
                    CaptureNotice(
                        CaptureMode.NOTE,
                        if (where != null) "Salvato in " + where.substringAfterLast('/') else "Non sono riuscito a salvarlo",
                        undo = null,
                        icon = Icons.Outlined.Share,
                        fly = false,
                    ),
                )
            },
            trailing = { TextButton(onClick = { shareTextFile(context, "$file.txt", text()) }) { Text("Condividi") } },
        )
    }
}
