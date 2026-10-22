package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.platinum20.dashboard.work.MorningWorker
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.AppTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.BuildConfig
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.spotify.SpotifyWebSession
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.autofill.AutoFill
import androidx.compose.material.icons.outlined.AutoAwesome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUpdates: () -> Unit,
    onCustomize: () -> Unit,
    onLogin: (MediaService) -> Unit,
    onOpenAutoFill: () -> Unit = {},
) {
    val context = LocalContext.current
    val theme by AppSettings.theme.collectAsState()
    val haptic = rememberHaptic()
    val loggedIn by Accounts.loggedIn.collectAsState()
    AutoFill.init(context)
    val autoFillOn by AutoFill.enabled.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
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
            item { SettingsHeader("Aspetto") }
            item {
                SettingsGroup {
                    // Tre scelte in una riga sola, con la descrizione di quella attiva sotto.
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            AppTheme.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = theme == option,
                                    onClick = { if (theme != option) haptic(Haptic.TICK); AppSettings.setTheme(option) },
                                    shape = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size),
                                    icon = {},
                                ) {
                                    Text(option.label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Text(
                            theme.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                }
            }

            item { SettingsHeader("Oggi") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        title = "Card della schermata Oggi",
                        subtitle = "Quali mostrare e in che ordine",
                        showChevron = true,
                        onClick = onCustomize,
                    )
                    MorningSettings()
                }
            }

            item { SettingsHeader("Dati automatici") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Compila da solo",
                        subtitle = if (autoFillOn) "Acceso · pacchi, spese e scadenze dalle notifiche" else "Pacchi, spese e scadenze presi da notifiche, mail e SMS",
                        showChevron = true,
                        onClick = onOpenAutoFill,
                    )
                }
            }

            item { SettingsHeader("Account e Media") }
            item {
                SettingsGroup {
                    // Un solo accesso per servizio: serve sia al player sia al feed nell'app.
                    MediaService.entries.forEach { service ->
                        val connected = service in loggedIn
                        SettingsItem(
                            icon = Icons.Outlined.AccountCircle,
                            title = service.label,
                            subtitle = if (connected) "Collegato · player e feed nella sezione Media" else "Non collegato",
                            trailing = {
                                if (connected) {
                                    TextButton(onClick = {
                                        Accounts.logout(service)
                                        when (service) {
                                            MediaService.YOUTUBE -> YouTubeStore.clear()
                                            MediaService.SPOTIFY -> {
                                                SpotifyWebSession.clear()
                                                SpotifyStore.clear()
                                            }
                                        }
                                    }) { Text("Esci") }
                                } else {
                                    FilledTonalButton(onClick = { onLogin(service) }) { Text("Accedi") }
                                }
                            },
                        )
                    }
                }
            }

            item { SettingsHeader("Informazioni") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "Aggiornamenti",
                        subtitle = "Canvas ${BuildConfig.VERSION_NAME} · novità e aggiornamento",
                        showChevron = true,
                        onClick = onOpenUpdates,
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Code,
                        title = "Codice sorgente",
                        subtitle = BuildConfig.GITHUB_REPO,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsHeader(text: String) {
    SectionHeader(text, Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = if (showChevron) {
            { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
        } else null,
    )
}

/** Riga compatta delle impostazioni: icona, titolo, una riga di descrizione e un comando a destra. */
@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyPress(0.98f).clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorningSettings() {
    val context = LocalContext.current
    val morning by AppSettings.morning.collectAsState()
    var pickTime by remember { mutableStateOf(false) }

    fun enable(on: Boolean) {
        AppSettings.setMorning(morning.copy(enabled = on))
        MorningWorker.schedule(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable(true)
    }
    // Il permesso chiesto dal tasto "Invia una prova": appena concesso parte la prova.
    val testPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) MorningWorker.sendTest(context)
    }

    Column {
        val toggle = {
            if (!morning.enabled && !MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enable(!morning.enabled)
            }
        }
        SettingsItem(
            icon = Icons.Outlined.NotificationsActive,
            title = "Notifica del mattino",
            subtitle = if (morning.enabled) "Ogni giorno alle ${morning.timeLabel}" else "Meteo, impegni, cose da fare e notizie",
            onClick = toggle,
            trailing = { Switch(checked = morning.enabled, onCheckedChange = hapticToggle { toggle() }) },
        )
        if (morning.enabled) {
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = "Orario",
                subtitle = morning.timeLabel,
                onClick = { pickTime = true },
            )
            SettingsRow(
                icon = Icons.Outlined.Send,
                title = "Invia una prova",
                subtitle = "Ricevi subito la notifica di oggi",
                onClick = {
                    if (!MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                        testPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        MorningWorker.sendTest(context)
                    }
                },
            )
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = morning.hour, initialMinute = morning.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Orario della notifica") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    AppSettings.setMorning(morning.copy(hour = state.hour, minute = state.minute))
                    MorningWorker.schedule(context)
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickTime = false }) { Text("Annulla") }
            },
        )
    }
}
