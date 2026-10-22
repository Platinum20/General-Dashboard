package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.autofill.AutoFill
import com.platinum20.dashboard.autofill.AutoFillRules
import com.platinum20.dashboard.autofill.Found
import com.platinum20.dashboard.autofill.SmsScanner
import com.platinum20.dashboard.autofill.Suggestion
import com.platinum20.dashboard.autofill.SuggestionState
import com.platinum20.dashboard.autofill.SuggestionText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Found.icon(): ImageVector = when (this) {
    is Found.NewParcel, is Found.Delivered -> Icons.Outlined.Inventory2
    is Found.Spent -> Icons.Outlined.Payments
    is Found.Deadline -> Icons.Outlined.Event
}

/**
 * In Oggi: le cose trovate da sole da confermare (o appena salvate, da annullare).
 * Se la funzione è spenta, una volta sola, l'invito ad accenderla.
 */
@Composable
fun AutoFillCard(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    AutoFill.init(context)
    val items by AutoFill.items.collectAsStateWithLifecycle()
    val enabled by AutoFill.enabled.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val shown = remember(items) { AutoFillRules.visible(items, System.currentTimeMillis()) }

    // L'invito ad attivarla sta in fondo a Oggi (InvitesRow), non in cima.
    if (!enabled) return
    if (shown.isEmpty()) return

    val pending = shown.count { it.state == SuggestionState.PENDING }
    Section(
        title = if (pending > 0) "Trovati per te · $pending" else "Aggiunti da soli",
        action = if (pending > 1) "Aggiungi tutti" else if (pending == 0) "Ok" else null,
        onAction = {
            haptic(Haptic.CONFIRM)
            if (pending > 0) AutoFill.acceptAll() else AutoFill.clearRecent()
        },
    ) {
        Column(Modifier.animateContentSize(Springs.snappy())) {
            shown.take(6).forEachIndexed { index, s ->
                if (index > 0) RowDivider()
                SuggestionRow(
                    s,
                    onAccept = { haptic(Haptic.CONFIRM); AutoFill.accept(s.id) },
                    onDismiss = { haptic(Haptic.TICK); AutoFill.dismiss(s.id) },
                    onUndo = { haptic(Haptic.TICK); AutoFill.undo(s.id) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, onAccept: () -> Unit, onDismiss: () -> Unit, onUndo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(s.found.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(SuggestionText.title(s.found), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                SuggestionText.detail(s),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.state == SuggestionState.PENDING) {
            IconButton(onClick = onDismiss, modifier = Modifier.bouncyPress(0.9f)) {
                Icon(Icons.Outlined.Close, contentDescription = "Ignora", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAccept, modifier = Modifier.bouncyPress(0.9f)) {
                Icon(Icons.Outlined.Check, contentDescription = "Aggiungi", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            TextButton(onClick = onUndo) { Text("Annulla") }
        }
    }
}

private fun openListenerSettings(context: Context) {
    val component = AutoFill.listenerComponent(context)
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= 30) {
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()))
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

internal fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Impostazioni > Compila da solo: accensione, permessi, SMS e cronologia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoFillScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    AutoFill.init(context)
    val enabled by AutoFill.enabled.collectAsStateWithLifecycle()
    val autoSave by AutoFill.autoSave.collectAsStateWithLifecycle()
    val items by AutoFill.items.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(AutoFill.listenerAllowed(context)) }
    var smsAllowed by remember { mutableStateOf(SmsScanner.canRead(context)) }
    var smsResult by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = AutoFill.listenerAllowed(context)
        smsAllowed = SmsScanner.canRead(context)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    fun scanSms() {
        scanning = true
        scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { SmsScanner.scan(context) }.getOrDefault(0) }
            scanning = false
            smsResult = when (n) {
                0 -> "Niente di nuovo negli SMS degli ultimi ${SmsScanner.DAYS} giorni"
                1 -> "Trovata 1 cosa: la vedi in Oggi"
                else -> "Trovate $n cose: le vedi in Oggi"
            }
        }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsAllowed = granted
        if (granted) scanSms() else smsResult = "Permesso negato. Se Android lo blocca: Info app > ⋮ > Consenti impostazioni con limitazioni"
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Compila da solo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Text(
                    "Canvas legge le notifiche che arrivano sul telefono e ci trova pacchi in arrivo (Amazon, Poste, BRT, GLS, DHL), " +
                        "spese (Revolut, banche, Satispay, PayPal) e scadenze o appuntamenti (bollette, prenotazioni, visite, anche da Gmail). " +
                        "Le chat non vengono lette. Niente lascia il telefono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            item { SettingsHeader("Notifiche") }
            item {
                SettingsGroup {
                    val toggle = {
                        val on = !enabled
                        AutoFill.setEnabled(on)
                        if (on && !allowed) openListenerSettings(context)
                        if (on && Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    SettingsItem(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Compila da solo",
                        subtitle = when {
                            !enabled -> "Spento"
                            allowed -> "Acceso · le cose trovate compaiono in Oggi"
                            else -> "Manca l'accesso alle notifiche"
                        },
                        onClick = toggle,
                        trailing = { Switch(checked = enabled, onCheckedChange = hapticToggle { toggle() }) },
                    )
                    if (enabled && !allowed) {
                        RowDivider()
                        SettingsItem(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "Consenti l'accesso alle notifiche",
                            subtitle = "Attiva Canvas nell'elenco che si apre",
                            onClick = { openListenerSettings(context) },
                            trailing = { FilledTonalButton(onClick = { openListenerSettings(context) }) { Text("Apri") } },
                        )
                        RowDivider()
                        SettingsItem(
                            icon = Icons.Outlined.Info,
                            title = "Android dice \"impostazione con limitazioni\"?",
                            subtitle = "Succede alle app installate dall'APK: apri Info app, tocca ⋮ in alto e scegli \"Consenti impostazioni con limitazioni\", poi riprova",
                            onClick = { openAppInfo(context) },
                            trailing = { TextButton(onClick = { openAppInfo(context) }) { Text("Info app") } },
                        )
                    }
                    if (enabled) {
                        RowDivider()
                        SettingsItem(
                            icon = Icons.Outlined.Bolt,
                            title = "Salva senza chiedere",
                            subtitle = if (autoSave) "Salva subito; in Oggi e nella notifica puoi annullare" else "Prima te lo propone in Oggi",
                            onClick = { AutoFill.setAutoSave(!autoSave) },
                            trailing = { Switch(checked = autoSave, onCheckedChange = hapticToggle { AutoFill.setAutoSave(it) }) },
                        )
                    }
                }
            }
            item { SettingsHeader("SMS") }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Sms,
                        title = "Cerca negli SMS",
                        subtitle = smsResult ?: "Pagamenti con carta, corrieri e appuntamenti degli ultimi ${SmsScanner.DAYS} giorni",
                        onClick = {
                            if (!scanning) {
                                if (smsAllowed) scanSms() else smsPermission.launch(Manifest.permission.READ_SMS)
                            }
                        },
                        trailing = {
                            FilledTonalButton(
                                enabled = !scanning,
                                onClick = { if (smsAllowed) scanSms() else smsPermission.launch(Manifest.permission.READ_SMS) },
                            ) { Text(if (scanning) "Cerco…" else "Cerca") }
                        },
                    )
                }
            }
            val recent = items.filter { it.state != SuggestionState.PENDING }.take(15)
            if (recent.isNotEmpty()) {
                item { SettingsHeader("Ultimi trovati") }
                item {
                    SettingsGroup {
                        recent.forEachIndexed { index, s ->
                            if (index > 0) RowDivider()
                            SettingsItem(
                                icon = s.found.icon(),
                                title = SuggestionText.title(s.found),
                                subtitle = (if (s.state == SuggestionState.ADDED) "Aggiunto · " else "Ignorato · ") + SuggestionText.detail(s),
                                trailing = if (s.state == SuggestionState.ADDED && s.ref != null) {
                                    { TextButton(onClick = { AutoFill.undo(s.id) }) { Text("Togli") } }
                                } else null,
                            )
                        }
                        RowDivider()
                        SettingsItem(
                            icon = Icons.Outlined.DeleteSweep,
                            title = "Dimentica la cronologia",
                            subtitle = "Le cose già salvate restano in Note",
                            onClick = { AutoFill.forgetAll() },
                        )
                    }
                }
            }
        }
    }
}
