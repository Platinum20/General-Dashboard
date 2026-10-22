package com.platinum20.dashboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.backup.Backup
import com.platinum20.dashboard.backup.BackupContent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Impostazioni › Dati: esporta tutto in un file e ripristina da un backup. */
@Composable
internal fun BackupSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var last by remember { mutableStateOf(Backup.lastExport(context)) }
    var pending by remember { mutableStateOf<BackupContent?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (Backup.export(context, uri)) {
                haptic(Haptic.CONFIRM)
                last = Backup.lastExport(context)
                toast("Backup salvato")
            } else {
                toast("Non è stato possibile salvare il backup")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Prima si legge e si controlla tutto: un file sbagliato non tocca i dati.
            val content = Backup.read(context, uri)
            if (content == null) toast("Questo file non è un backup di Canvas") else pending = content
        }
    }

    SettingsItem(
        icon = Icons.Outlined.SaveAlt,
        accent = TileAccent.GREEN,
        title = "Esporta backup",
        subtitle = "Promemoria, abitudini, spese, note, pacchi, date e impostazioni in un file",
        value = last?.let { relativeTime(Instant.ofEpochMilli(it)) } ?: "Mai",
        onClick = { exportLauncher.launch(Backup.fileName(LocalDate.now())) },
    )
    RowDivider()
    SettingsItem(
        icon = Icons.Outlined.Restore,
        accent = TileAccent.NEUTRAL,
        title = "Ripristina da backup",
        subtitle = "Sostituisce i dati di adesso con quelli del file",
        showChevron = true,
        onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )

    pending?.let { content ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Ripristinare il backup?") },
            text = {
                Text(
                    "Nel file: ${Backup.summary(content.notes)}" +
                        (if (content.createdAt > 0) ", salvato ${relativeTime(Instant.ofEpochMilli(content.createdAt))}" else "") +
                        ".\n\nI dati e le impostazioni di adesso verranno sostituiti.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Backup.restore(context, content)
                    pending = null
                    haptic(Haptic.CONFIRM)
                    toast("Backup ripristinato")
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annulla") } },
        )
    }
}
