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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bottomPadding: PaddingValues,
    onOpenUpdates: () -> Unit,
    onCustomize: () -> Unit,
) {
    val context = LocalContext.current
    val theme by AppSettings.theme.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            LargeTopAppBar(
                title = { Text("Impostazioni") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { SectionHeader("Tema") }
            item {
                SettingsGroup {
                    AppTheme.entries.forEach { option ->
                        ListItem(
                            modifier = Modifier.selectable(
                                selected = theme == option,
                                onClick = { AppSettings.setTheme(option) },
                                role = Role.RadioButton,
                            ),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            leadingContent = {
                                Icon(
                                    when (option) {
                                        AppTheme.DYNAMIC -> Icons.Outlined.Palette
                                        AppTheme.SYSTEM -> Icons.Outlined.BrightnessMedium
                                        AppTheme.AMOLED -> Icons.Outlined.DarkMode
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = { Text(option.label) },
                            supportingContent = { Text(option.description) },
                            trailingContent = { RadioButton(selected = theme == option, onClick = null) },
                        )
                    }
                }
            }

            item { SectionHeader("Dashboard") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        title = "Card della Dashboard",
                        subtitle = "Scegli quali mostrare e in che ordine",
                        showChevron = true,
                        onClick = onCustomize,
                    )
                }
            }

            item { SectionHeader("Notifica del mattino") }
            item { MorningSettings() }

            item { SectionHeader("Informazioni") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "Versione",
                        subtitle = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Code,
                        title = "Repository GitHub",
                        subtitle = BuildConfig.GITHUB_REPO,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))
                            )
                        },
                    )
                }
            }

            // In fondo: il sottomenu degli aggiornamenti.
            item { SectionHeader("Sistema") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "Aggiornamenti",
                        subtitle = "Changelog e aggiornamento da GitHub",
                        showChevron = true,
                        onClick = onOpenUpdates,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = if (showChevron) {
            { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }
        } else null,
    )
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

    SettingsGroup {
        ListItem(
            modifier = Modifier.clickable {
                if (!morning.enabled && !MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enable(!morning.enabled)
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text("Riepilogo ogni mattina") },
            supportingContent = { Text("Meteo, impegni, cose da fare e temi del giorno") },
            trailingContent = { Switch(checked = morning.enabled, onCheckedChange = null) },
        )
        if (morning.enabled) {
            SettingsRow(
                icon = Icons.Outlined.Schedule,
                title = "Orario",
                subtitle = morning.timeLabel,
                onClick = { pickTime = true },
            )
        }
        SettingsRow(
            icon = Icons.Outlined.Send,
            title = "Invia una prova",
            subtitle = "Ricevi subito la notifica di oggi",
            onClick = {
                if (!MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    MorningWorker.sendTest(context)
                }
            },
        )
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
