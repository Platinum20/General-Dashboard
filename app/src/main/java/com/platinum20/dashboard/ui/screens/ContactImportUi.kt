package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.platinum20.dashboard.notes.ContactCandidate
import com.platinum20.dashboard.notes.ContactDates
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Compleanni dai contatti": chiede il permesso della rubrica (solo qui), legge compleanni e anniversari
 * e li mostra con le caselle; quelli già in Date restano spenti. [onImported] riceve le date create.
 */
@Composable
fun ContactImportDialog(existing: List<Countdown>, onDismiss: () -> Unit, onImported: (List<Countdown>) -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<ContactCandidate>?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.READ_CONTACTS) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val found = withContext(Dispatchers.IO) { ContactDates.read(context) }
        val list = ContactDates.candidates(found, existing)
        picked.clear()
        picked.addAll(list.filter { !it.already }.map { it.date.key })
        candidates = list
    }

    val list = candidates
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compleanni dai contatti") },
        text = {
            when {
                denied -> Text(
                    "Senza il permesso dei contatti Canvas non vede i compleanni. Puoi darlo dalle impostazioni dell'app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                list == null -> Text("Cerco nella rubrica…", style = MaterialTheme.typography.bodyMedium)
                list.isEmpty() -> Text(
                    "Nessun compleanno nella rubrica. Si aggiungono nella scheda del contatto, alla voce \"Evento\" o \"Compleanno\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(list, key = { it.date.key }) { c ->
                        val on = c.date.key in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !c.already) {
                                    haptic(if (on) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                                    if (on) picked.remove(c.date.key) else picked.add(c.date.key)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on || c.already, onCheckedChange = null, enabled = !c.already)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(c.date.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    contactDateLine(c),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                denied -> TextButton(onClick = { openAppInfo(context); onDismiss() }) { Text("Impostazioni") }
                !list.isNullOrEmpty() -> TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        haptic(Haptic.CONFIRM)
                        val chosen = list.filter { !it.already && it.date.key in picked }.map { it.date }
                        onImported(NotesStore.importContactDates(chosen))
                        onDismiss()
                    },
                ) { Text(if (picked.size == 1) "Importa 1" else "Importa ${picked.size}") }
                else -> TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        },
        dismissButton = {
            if (!list.isNullOrEmpty() || denied) TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}

/** "12 marzo · classe 1990" oppure "12 marzo · già in Date". */
internal fun contactDateLine(c: ContactCandidate, locale: Locale = Locale.ITALIAN): String {
    val md = c.date.monthDay
    val day = "${md.dayOfMonth} ${md.month.getDisplayName(TextStyle.FULL, locale)}"
    return listOfNotNull(
        day,
        if (c.already) "già in Date" else c.date.year?.let { if (c.date.anniversary) "dal $it" else "classe $it" },
    ).joinToString(" · ")
}

/** Da quante date "ogni anno" in su l'invito a importare dalla rubrica sparisce. */
internal const val CONTACT_IMPORT_UNTIL = 3

/** L'invito a importare i compleanni: solo finché le date ogni anno sono poche. */
internal fun showContactImport(countdowns: List<Countdown>): Boolean =
    countdowns.count { it.yearly } < CONTACT_IMPORT_UNTIL
