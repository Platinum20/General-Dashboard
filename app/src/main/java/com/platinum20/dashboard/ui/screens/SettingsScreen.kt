package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.platinum20.dashboard.media.songid.SongId
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.adblock.AdBlocker
import com.platinum20.dashboard.autofill.AutoFill
import androidx.compose.material.icons.outlined.AutoAwesome
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.spotify.SpotifyWebSession
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.work.UpdateCheckWorker
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.work.MorningWorker
import com.platinum20.dashboard.work.RainAlertWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.widthIn

/**
 * Impostazioni: tutto ciò che si regola una volta sola, raccolto qui anche se prima stava
 * sparso nell'app (blocco pubblicità, avviso delle nuove versioni, località del meteo).
 * Ogni riga mostra a destra lo stato attuale, così non serve aprirla per sapere com'è.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUpdates: () -> Unit,
    onCustomize: () -> Unit,
    onLogin: (MediaService) -> Unit,
    onPickPlace: () -> Unit,
    onOpenAutoFill: () -> Unit = {},
    /** La parte da cui partire: la sezione della barra tenuta premuta (Routes.HOME, NEWS, NOTES, MEDIA). */
    initialSection: String? = null,
) {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val theme by AppSettings.theme.collectAsState()
    val layout by AppSettings.homeLayout.collectAsState()
    val haptic = rememberHaptic()
    val loggedIn by Accounts.loggedIn.collectAsState()
    AutoFill.init(context)
    val autoFillOn by AutoFill.enabled.collectAsState()
    // La località si legge dalle preferenze: si rilegge tornando dalla scelta della città.
    var place by remember { mutableStateOf(WeatherStore.manualPlace(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        Accounts.refresh()
        place = WeatherStore.manualPlace(context)
    }
    var showLicenses by remember { mutableStateOf(false) }
    var editSongToken by remember { mutableStateOf(false) }
    SongId.init(context)
    val songToken by SongId.token.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Tenendo premuta una sezione della barra si arriva qui, già alla sua parte.
    LaunchedEffect(Unit) {
        val start = SettingsPart.entries.indexOfFirst { it.key == initialSection }
        if (start > 0) listState.scrollToItem(start * 2)
    }
    // Il chip acceso segue lo scorrimento: è la parte che si sta guardando.
    val current by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val atEnd = info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1 && listState.canScrollForward.not()
            if (atEnd && listState.firstVisibleItemIndex > 0) SettingsPart.entries.last()
            else SettingsPart.entries[(listState.firstVisibleItemIndex / 2).coerceAtMost(SettingsPart.entries.size - 1)]
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopLevelBar(
                    title = "Impostazioni",
                    subtitle = "Canvas ${BuildConfig.VERSION_NAME}",
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                )
                // Una parte per ogni sezione della barra in basso, più quelle di tutta l'app.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsPart.entries.forEachIndexed { index, part ->
                        FilterChip(
                            selected = current == part,
                            onClick = {
                                haptic(Haptic.TICK)
                                scope.launch { listState.animateScrollToItem(index * 2) }
                            },
                            label = { Text(part.label) },
                            leadingIcon = { Icon(part.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
        ) {
            // Ogni parte sono due righe, intestazione e gruppo: i chip saltano a parte × 2.
            item { SettingsHeader(SettingsPart.HOME.title) }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.Dashboard,
                        accent = TileAccent.BLUE,
                        title = "Card di Oggi",
                        subtitle = "Quali mostrare e in che ordine",
                        value = "${layout.visible.size} su ${HomeCard.entries.size}",
                        showChevron = true,
                        onClick = onCustomize,
                    )
                    RowDivider()
                    MorningSettings()
                    RowDivider()
                    SettingsItem(
                        icon = Icons.Outlined.LocationOn,
                        accent = TileAccent.GREEN,
                        title = "Località del meteo",
                        subtitle = if (place != null) "Scelta a mano" else "Dalla posizione del telefono",
                        value = place?.name ?: "Automatica",
                        showChevron = true,
                        onClick = onPickPlace,
                    )
                    RowDivider()
                    RainAlertSetting()
                }
            }

            item { SettingsHeader(SettingsPart.NOTES.title) }
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Outlined.AutoAwesome,
                        accent = TileAccent.BLUE,
                        title = "Compila da solo",
                        subtitle = if (autoFillOn) "Pacchi, spese e scadenze dalle notifiche" else "Pacchi, spese e scadenze presi da notifiche, mail e SMS",
                        value = if (autoFillOn) "Acceso" else "Spento",
                        showChevron = true,
                        onClick = onOpenAutoFill,
                    )
                }
            }

            item { SettingsHeader(SettingsPart.NEWS.title) }
            item { SettingsGroup { AdBlockSettings() } }

            item { SettingsHeader(SettingsPart.MEDIA.title) }
            item {
                SettingsGroup {
                    // Un solo accesso per servizio: serve sia al player sia al feed nell'app.
                    MediaService.entries.forEachIndexed { index, service ->
                        val connected = service in loggedIn
                        if (index > 0) RowDivider()
                        SettingsItem(
                            icon = when (service) {
                                MediaService.YOUTUBE -> Icons.Outlined.OndemandVideo
                                MediaService.SPOTIFY -> Icons.Outlined.GraphicEq
                            },
                            accent = when (service) {
                                MediaService.YOUTUBE -> TileAccent.ORANGE
                                MediaService.SPOTIFY -> TileAccent.GREEN
                            },
                            title = service.label,
                            subtitle = if (connected) "Collegato · feed e player in Media" else "Non collegato",
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
                                    OutlinedButton(onClick = { onLogin(service) }, border = cardBorder()) { Text("Accedi") }
                                }
                            },
                        )
                    }
                    RowDivider()
                    SettingsItem(
                        icon = Icons.Outlined.MusicNote,
                        accent = TileAccent.ORANGE,
                        title = "Riconosci canzoni",
                        subtitle = if (songToken.isBlank()) "Incolla il token di AudD" else "Dettando con il +: «♪ Canzone», poi in ${SongId.PLAYLIST_NAME}",
                        value = if (songToken.isBlank()) "Da attivare" else "Attivo",
                        showChevron = true,
                        onClick = { editSongToken = true },
                    )
                }
            }

            item { SettingsHeader(SettingsPart.APP.title) }
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
            item { Spacer(Modifier.height(12.dp)) }
            item { SettingsGroup { BackupSettings() } }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                SettingsGroup {
                    UpdateNotifySetting()
                    RowDivider()
                    SettingsItem(
                        icon = Icons.Outlined.SystemUpdate,
                        accent = TileAccent.BLUE,
                        title = "Aggiornamenti",
                        subtitle = "Novità di ogni versione e aggiornamento",
                        showChevron = true,
                        onClick = onOpenUpdates,
                    )
                    RowDivider()
                    SettingsItem(
                        icon = Icons.Outlined.Code,
                        accent = TileAccent.NEUTRAL,
                        title = "Codice sorgente",
                        subtitle = BuildConfig.GITHUB_REPO,
                        showChevron = true,
                        onClick = { inApp?.openWeb("https://github.com/${BuildConfig.GITHUB_REPO}", "Codice sorgente") },
                    )
                    RowDivider()
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        accent = TileAccent.NEUTRAL,
                        title = "Licenze",
                        subtitle = "Caratteri Source Serif e Inter, liste del blocco pubblicità",
                        showChevron = true,
                        onClick = { showLicenses = true },
                    )
                }
            }
            item {
                Text(
                    "Canvas ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
            // Spazio in fondo: anche l'ultima parte può arrivare in cima quando si tocca il suo chip.
            item { Spacer(Modifier.height(320.dp)) }
        }
    }

    if (editSongToken) {
        var value by remember { mutableStateOf(songToken) }
        CanvasDialog(
            onDismissRequest = { editSongToken = false },
            title = { Text("Riconosci canzoni") },
            text = {
                Column {
                    Text(
                        "Il token di AudD (dashboard.audd.io) resta solo su questo telefono. " +
                            "Le canzoni riconosciute partono su Spotify e finiscono nella playlist «${SongId.PLAYLIST_NAME}».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        label = { Text("API token") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { SongId.setToken(value); editSongToken = false }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { editSongToken = false }) { Text("Annulla") } },
        )
    }

    if (showLicenses) {
        CanvasDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Licenze") },
            text = {
                Text(
                    "Caratteri Inter e Inter Display di Rasmus Andersson, SIL Open Font License 1.1.\n\n" +
                        "Liste del blocco pubblicità: EasyList ed EasyPrivacy (CC BY-SA 3.0, GPLv3), " +
                        "filtri di uBlock Origin (GPLv3), scaricate dai loro siti.\n\n" +
                        "Meteo e località: Open-Meteo (CC BY 4.0).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("OK") } },
        )
    }
}

/** Le parti delle Impostazioni: una per sezione della barra, più quella di tutta l'app. */
private enum class SettingsPart(val key: String, val label: String, val title: String, val icon: ImageVector) {
    HOME("home", "Home", "Home e notifiche", Icons.Outlined.Dashboard),
    NOTES("notes", "Note", "Note", Icons.Outlined.EditNote),
    NEWS("news", "News", "News e lettura", Icons.Outlined.Newspaper),
    MEDIA("media", "Media", "Media", Icons.Outlined.PlayCircle),
    APP("app", "App", "Tutta l'app", Icons.Outlined.Tune),
}

@Composable
internal fun SettingsHeader(text: String) {
    SectionHeader(text, Modifier.padding(top = 14.dp, bottom = 2.dp))
}

/** Riga che apre un'altra pagina: titolo, descrizione e freccia. */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    accent: TileAccent = TileAccent.BLUE,
) {
    SettingsItem(icon = icon, title = title, subtitle = subtitle, showChevron = showChevron, onClick = onClick, accent = accent)
}

/** Un gruppo di righe in una card, separate da linee sottili. */
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

/**
 * Riga delle impostazioni: icona nel quadratino colorato, titolo, una riga di descrizione,
 * il valore attuale a destra (o un comando) e la freccia se apre un'altra pagina.
 */
@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    accent: TileAccent = TileAccent.BLUE,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyPress(0.98f).clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, accent, size = 34.dp, iconSize = 19.dp)
        Spacer(Modifier.width(14.dp))
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
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
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
            accent = TileAccent.ORANGE,
            title = "Notifica del mattino",
            subtitle = if (morning.enabled) "Ogni giorno alle ${morning.timeLabel}" else "Meteo, impegni, cose da fare e notizie",
            onClick = toggle,
            trailing = { Switch(checked = morning.enabled, onCheckedChange = hapticToggle { toggle() }) },
        )
        if (morning.enabled) {
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.Schedule,
                accent = TileAccent.NEUTRAL,
                title = "Orario",
                value = morning.timeLabel,
                showChevron = true,
                onClick = { pickTime = true },
            )
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.Send,
                accent = TileAccent.NEUTRAL,
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
        CanvasDialog(
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

/** Avviso pioggia: una notifica quando sta per piovere dove sei. */
@Composable
private fun RainAlertSetting() {
    val context = LocalContext.current
    val enabled by AppSettings.rainAlert.collectAsState()

    fun set(on: Boolean) {
        AppSettings.setRainAlert(on)
        RainAlertWorker.schedule(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) set(true)
    }
    val blocked = enabled && !MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33
    val toggle = {
        if (!enabled && !MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            set(!enabled)
        }
    }
    SettingsItem(
        icon = Icons.Outlined.Umbrella,
        accent = TileAccent.BLUE,
        title = "Avvisami se sta per piovere",
        subtitle = when {
            blocked -> "Notifiche bloccate: tocca per consentirle"
            enabled -> "Una notifica circa mezz'ora prima, dalle 7 alle 23"
            else -> "Una notifica quando la pioggia è in arrivo"
        },
        onClick = toggle,
        trailing = { Switch(checked = enabled && !blocked, onCheckedChange = hapticToggle { toggle() }) },
    )
}

/** Avviso delle nuove versioni: prima stava solo dentro Aggiornamenti. */
@Composable
private fun UpdateNotifySetting() {
    val context = LocalContext.current
    val enabled by AppSettings.updateNotify.collectAsState()

    fun set(on: Boolean) {
        AppSettings.setUpdateNotify(on)
        UpdateCheckWorker.schedule(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) set(true)
    }
    val blocked = enabled && !MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33
    val toggle = {
        if (!MorningWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            set(!enabled)
        }
    }
    SettingsItem(
        icon = Icons.Outlined.SystemUpdate,
        accent = TileAccent.NEUTRAL,
        title = "Avviso nuove versioni",
        subtitle = if (blocked) "Notifiche bloccate: tocca per consentirle" else "Una notifica quando esce un aggiornamento",
        onClick = toggle,
        trailing = { Switch(checked = enabled && !blocked, onCheckedChange = hapticToggle { toggle() }) },
    )
}

/** Blocco pubblicità nei player di YouTube e Spotify (spostato qui da Media). */
@Composable
private fun AdBlockSettings() {
    val enabled by AppSettings.adBlock.collectAsState()
    val blocked by AdBlocker.blocked.collectAsState()
    val status by AdBlocker.status.collectAsState()
    val toggle = { on: Boolean ->
        AppSettings.setAdBlock(on)
        // Le pagine aperte ripartono con la nuova impostazione.
        MediaWebViews.releaseAll()
    }
    SettingsItem(
        icon = Icons.Outlined.Shield,
        accent = TileAccent.PURPLE,
        title = "Blocco pubblicità",
        subtitle = if (enabled) "Attivo nei player · $blocked richieste bloccate" else "Spento",
        onClick = { toggle(!enabled) },
        trailing = { Switch(checked = enabled, onCheckedChange = hapticToggle(toggle)) },
    )
    if (enabled) {
        RowDivider()
        SettingsItem(
            icon = Icons.Outlined.FilterList,
            accent = TileAccent.NEUTRAL,
            title = "Liste dei filtri",
            subtitle = adBlockStatusText(status),
            trailing = {
                TextButton(onClick = { AdBlocker.updateNow() }, enabled = !status.updating) { Text("Aggiorna") }
            },
        )
    }
}

/** "EasyList, EasyPrivacy e uBlock Origin · 163.784 regole · aggiornate il 22 ott". */
private fun adBlockStatusText(status: AdBlocker.Status): String {
    val parts = mutableListOf<String>()
    when {
        status.updating -> parts += "Aggiornamento in corso…"
        !status.ready -> parts += "Caricamento delle liste…"
        else -> parts += "%,d regole".format(Locale.ITALY, status.rules)
    }
    parts += status.updatedAt?.let {
        "aggiornate il " + SimpleDateFormat("d MMM", Locale.ITALIAN).format(Date(it))
    } ?: "liste incluse nell'app"
    status.lastError?.let { parts += it }
    return "EasyList, EasyPrivacy e uBlock Origin · " + parts.joinToString(" · ")
}
