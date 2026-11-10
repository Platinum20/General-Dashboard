package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice

/**
 * "Salva i dati letti" sotto l'account (WindTre, turni): quello che il servizio ha mandato, per
 * correggere la lettura quando un numero non torna. Sta di nuovo nel suo account (Antonio, 10/11: niente Strumenti).
 */
@Composable
internal fun CaptureFileRow(title: String, file: String, text: () -> String) {
    val context = LocalContext.current
    SettingsItem(
        icon = Icons.Outlined.Share,
        accent = TileAccent.NEUTRAL,
        title = title,
        subtitle = "Un file in Download da allegare",
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
        trailing = { androidx.compose.material3.FilledTonalButton(onClick = { shareTextFile(context, "$file.txt", text()) }) { Text("Condividi") } },
    )
}
