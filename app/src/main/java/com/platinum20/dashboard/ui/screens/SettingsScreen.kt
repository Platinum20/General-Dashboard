package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import com.platinum20.dashboard.media.songid.SongId
import android.Manifest
import androidx.compose.material.icons.outlined.Wifi
import com.platinum20.dashboard.net.HeavyDownloads
import com.platinum20.dashboard.news.RecapSpeaker
import com.platinum20.dashboard.news.voice.NeuralVoice
import com.platinum20.dashboard.news.voice.ReadingVoice
import com.platinum20.dashboard.news.voice.VoiceStatus
import com.platinum20.dashboard.news.voice.VoiceStore
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
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
 * Impostazioni, ordinate per quello che si vuole fare e non per sezione dell'app:
 * com'è fatta (aspetto), cosa ti avvisa (notifiche), cosa fa da sola, gli account, i dati.
 * Una pagina sola e corta, senza chip: ogni gruppo ha un titolo che dice a cosa serve.
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
    /** Da dove si arriva (Routes.HOME, NEWS, NOTES, MEDIA): la pagina parte dal gruppo che le riguarda. */
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
    LaunchedEffect(Unit) {
        val start = SettingsGroupKey.forSection(initialSection)
        if (start > 0) listState.scrollToItem(start)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = "Impostazioni",
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = SettingsGroupKey.LOOK) {
                SettingsBlock("Aspetto") {
                    ThemePicker(theme) { option ->
                        if (theme != option) haptic(Haptic.TICK)
                        AppSettings.setTheme(option)
                    }
                }
            }

            item(key = SettingsGroupKey.ALERTS) {
                SettingsBlock("Cosa ti avvisa") {
                    MorningSettings()
                    RowDivider()
                    RainAlertSetting()
                    RowDivider()
                    UpdateNotifySetting()
                }
            }

            item(key = SettingsGroupKey.HOME) {
                SettingsBlock("Home") {
                    SettingsItem(
                        icon = Icons.Outlined.Dashboard,
                        accent = TileAccent.BLUE,
                        title = "Card della Home",
                        subtitle = "Quali mostrare e in che ordine",
                        value = "${layout.visible.size} su ${HomeCard.entries.size}",
                        showChevron = true,
                        onClick = onCustomize,
                    )
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
                }
            }

            item(key = SettingsGroupKey.VOICE) {
                SettingsBlock("Voce di lettura") { VoicePicker() }
            }

            item(key = SettingsGroupKey.AUTO) {
                SettingsBlock("Fa da solo") {
                    SettingsItem(
                        icon = Icons.Outlined.AutoAwesome,
                        accent = TileAccent.BLUE,
                        title = "Compila le note",
                        subtitle = "Pacchi, spese e scadenze presi da notifiche, mail e SMS",
                        value = if (autoFillOn) "Acceso" else "Spento",
                        showChevron = true,
                        onClick = onOpenAutoFill,
                    )
                    RowDivider()
                    SettingsItem(
                        icon = Icons.Outlined.MusicNote,
                        accent = TileAccent.ORANGE,
                        title = "Riconosci canzoni",
                        subtitle = if (songToken.isBlank()) "Serve il token di AudD: tocca per incollarlo"
                        else "Tieni premuto + vicino alla musica: finisce in «${SongId.PLAYLIST_NAME}»",
                        value = if (songToken.isBlank()) "Da attivare" else "Attivo",
                        showChevron = true,
                        onClick = { editSongToken = true },
                    )
                    RowDivider()
                    AdBlockSettings()
                }
            }

            item(key = SettingsGroupKey.ACCOUNTS) {
                SettingsBlock("Account") {
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
                                        haptic(Haptic.TICK)
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
                }
            }

            item(key = SettingsGroupKey.DATA) {
                SettingsBlock("I tuoi dati") {
                    WifiOnlySetting()
                    RowDivider()
                    BackupSettings()
                }
            }

            item(key = SettingsGroupKey.INFO) {
                SettingsBlock("Informazioni") {
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
                        subtitle = "Carattere Inter, liste del blocco pubblicità, Open-Meteo",
                        showChevron = true,
                        onClick = { showLicenses = true },
                    )
                }
            }

            // In fondo l'app stessa: la versione e, toccando, le novità e gli aggiornamenti.
            item(key = SettingsGroupKey.ABOUT) { AppHero(onClick = onOpenUpdates) }
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
                        "Meteo e località: Open-Meteo (CC BY 4.0).\n\n" +
                        "Voci di lettura: Piper (voci paola e riccardo) con sherpa-onnx (Apache 2.0).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("OK") } },
        )
    }
}

/** I gruppi della pagina, nell'ordine in cui compaiono (sono anche le chiavi della lista). */
internal object SettingsGroupKey {
    const val ABOUT = "about"
    const val LOOK = "look"
    const val ALERTS = "alerts"
    const val HOME = "home"
    const val VOICE = "voice"
    const val AUTO = "auto"
    const val ACCOUNTS = "accounts"
    const val DATA = "data"
    const val INFO = "info"
    val order = listOf(LOOK, ALERTS, HOME, VOICE, AUTO, ACCOUNTS, DATA, INFO, ABOUT)

    /** Da quale gruppo partire arrivando da una sezione: Home e meteo, quello che fa da solo, gli account. */
    fun forSection(section: String?): Int = order.indexOf(
        when (section) {
            "home" -> HOME
            "notes" -> AUTO
            "news" -> VOICE
            "media" -> ACCOUNTS
            else -> LOOK
        }
    )
}

/** Titolo del gruppo fuori, righe dentro una card. */
@Composable
private fun SettingsBlock(title: String, content: @Composable () -> Unit) {
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(4.dp))
        SettingsGroup(content)
    }
}

/** La card in fondo: l'app, la sua versione e le novità. */
@Composable
private fun AppHero(onClick: () -> Unit) {
    CanvasCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Brush,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Canvas", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Versione ${BuildConfig.VERSION_NAME} · novità e aggiornamenti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * Il tema si sceglie guardandolo: tre anteprime in miniatura (fondo, una card, il colore d'accento),
 * quella scelta ha l'anello e si ingrandisce appena.
 */
@Composable
private fun ThemePicker(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTheme.entries.forEach { option ->
                val on = option == selected
                val ring by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                    tween(Motion.ENTER_MS, easing = Motion.Decelerate),
                    label = "themeRing",
                )
                val (bg, card, accent) = themeSwatch(option)
                // "Puro" mostra metà bianco e metà nero: di giorno e di sera.
                val fill = if (option == AppTheme.AMOLED) {
                    Brush.horizontalGradient(0f to Color.White, 0.5f to Color.White, 0.5f to Color.Black, 1f to Color.Black)
                } else SolidColor(bg)
                Column(
                    Modifier
                        .weight(1f)
                        .bouncyPress(0.94f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onSelect(option) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            // Anello sempre della stessa misura: cambia solo il colore, l'anteprima non si sposta.
                            .border(2.dp, ring, MaterialTheme.shapes.large)
                            .padding(4.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clip(MaterialTheme.shapes.medium)
                            .background(fill)
                            .padding(10.dp),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(card)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(CircleShape).background(accent))
                            Box(Modifier.fillMaxWidth(0.45f).height(5.dp).clip(CircleShape).background(accent.copy(alpha = 0.35f)))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        AnimatedContent(
            selected.description,
            transitionSpec = { fadeIn(tween(Motion.ENTER_MS)).togetherWith(fadeOut(tween(Motion.EXIT_MS))) },
            label = "themeText",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            )
        }
    }
}

/** Fondo, card e accento di ogni tema, per le anteprime. Material You usa i colori del telefono se ci sono. */
@Composable
private fun themeSwatch(theme: AppTheme): Triple<Color, Color, Color> {
    val context = LocalContext.current
    return when (theme) {
        AppTheme.CANVAS -> Triple(Color(0xFFF5F3EE), Color.White, Color(0xFF3550E8))
        AppTheme.AMOLED -> Triple(Color.White, Color(0xFF1E1D1A), Color(0xFF9AABFF))
        AppTheme.DYNAMIC -> if (Build.VERSION.SDK_INT >= 31) {
            val scheme = androidx.compose.material3.dynamicLightColorScheme(context)
            Triple(scheme.surfaceContainer, scheme.surfaceContainerLowest, scheme.primary)
        } else {
            Triple(Color(0xFFE8EEF8), Color.White, Color(0xFF4A6FA5))
        }
    }
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
        // Orario e prova compaiono scivolando solo quando la notifica è accesa.
        AnimatedVisibility(
            visible = morning.enabled,
            enter = expandVertically(tween(Motion.ENTER_MS, easing = Motion.Decelerate)) + fadeIn(tween(Motion.ENTER_MS)),
            exit = shrinkVertically(tween(Motion.ENTER_MS - 80, easing = Motion.Accelerate)) + fadeOut(tween(Motion.EXIT_MS)),
        ) { Column {
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
        } }
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

/**
 * La voce si sceglie come il tema: tre riquadri con l'onda della voce, quella scelta ha l'anello,
 * e sotto una riga sola che dice com'è (o a che punto è il download).
 */
@Composable
private fun VoicePicker() {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val selected by AppSettings.readingVoice.collectAsState()
    remember { VoiceStore.init(context) }
    val status by VoiceStore.status.collectAsState()
    Column(Modifier.padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadingVoice.entries.forEach { option ->
                val on = option == selected
                val ring by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                    tween(Motion.ENTER_MS, easing = Motion.Decelerate),
                    label = "voiceRing",
                )
                val state = status[option] ?: VoiceStatus.Missing
                Column(
                    Modifier
                        .weight(1f)
                        .bouncyPress(0.94f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            haptic(Haptic.TICK)
                            RecapSpeaker.retryNeural()
                            // Un tocco sceglie la voce e la fa sentire subito; se manca, la scarica.
                            AppSettings.setReadingVoice(option)
                            if (state == VoiceStatus.Ready) {
                                RecapSpeaker.play(context, listOf(voiceSample(option)), title = "Voce: ${option.label}")
                            } else {
                                RecapSpeaker.stop()
                                VoiceStore.download(option, context)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            // Anello sempre della stessa misura: cambia solo il colore, il riquadro non si sposta.
                            .border(2.dp, ring, MaterialTheme.shapes.large)
                            .padding(4.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.card)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VoiceWave(on, state)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        val failure by NeuralVoice.failure.collectAsState()
        val selectedState = status[selected] ?: VoiceStatus.Missing
        val line = failure?.takeIf { selectedState == VoiceStatus.Ready }
            ?.let { "${selected.label} non parte, legge la voce del telefono: $it" }
            ?: voiceLine(selected, selectedState)
        AnimatedContent(
            line,
            transitionSpec = { fadeIn(tween(Motion.ENTER_MS)).togetherWith(fadeOut(tween(Motion.EXIT_MS))) },
            label = "voiceText",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            )
        }
    }
}

/** L'onda di una voce neurale: si riempie col download e respira piano quando è la voce scelta. */
@Composable
private fun VoiceWave(on: Boolean, state: VoiceStatus) {
    val heights = listOf(0.35f, 0.7f, 1f, 0.55f, 0.85f, 0.4f)
    val filled = when (state) {
        is VoiceStatus.Downloading -> state.progress
        VoiceStatus.Ready -> 1f
        else -> 0f
    }
    val grown by animateFloatAsState(filled, tween(Motion.ENTER_MS, easing = Motion.Decelerate), label = "voiceFill")
    val accent = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, height ->
            val done = (index + 1f) / heights.size <= grown + 0.001f
            Box(
                Modifier
                    .weight(1f)
                    .height((34 * height).dp)
                    .clip(CircleShape)
                    .background(if (done) accent else accent.copy(alpha = 0.22f)),
            )
        }
    }
}

private fun voiceSample(voice: ReadingVoice): String =
    "Ciao, sono ${voice.label}. Ti leggo le notizie e il punto del giorno, anche senza internet."

/** La riga sotto i riquadri: com'è la voce, a che punto è il download, o perché non è partito. */
private fun voiceLine(voice: ReadingVoice, state: VoiceStatus): String = when (state) {
    is VoiceStatus.Downloading -> "Scarico ${voice.label}… ${(state.progress * 100).toInt()}%"
    is VoiceStatus.Failed -> state.message
    VoiceStatus.Waiting -> HeavyDownloads.BLOCKED
    VoiceStatus.Missing -> "${voice.description} · si scarica una volta (${voice.sizeMb} MB)"
    VoiceStatus.Ready -> "${voice.description} · tocca per sentirla"
}

/** Download pesanti solo col Wi-Fi: vale per le voci e per gli aggiornamenti dell'app. */
@Composable
private fun WifiOnlySetting() {
    val enabled by AppSettings.wifiOnlyDownloads.collectAsState()
    val toggle = { AppSettings.setWifiOnlyDownloads(!enabled) }
    SettingsItem(
        icon = Icons.Outlined.Wifi,
        accent = TileAccent.BLUE,
        title = "Scarica solo col Wi-Fi",
        subtitle = if (enabled) "Voci e aggiornamenti aspettano il Wi-Fi" else "Voci e aggiornamenti scaricano con qualunque rete",
        onClick = toggle,
        trailing = { Switch(checked = enabled, onCheckedChange = hapticToggle { toggle() }) },
    )
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
    val toggle = { on: Boolean -> AppSettings.setAdBlock(on) }
    SettingsItem(
        icon = Icons.Outlined.Shield,
        accent = TileAccent.PURPLE,
        title = "Blocco pubblicità",
        subtitle = if (enabled) "Attivo nei player · $blocked richieste bloccate" else "Spento",
        onClick = { toggle(!enabled) },
        trailing = { Switch(checked = enabled, onCheckedChange = hapticToggle(toggle)) },
    )
    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically(tween(Motion.ENTER_MS, easing = Motion.Decelerate)) + fadeIn(tween(Motion.ENTER_MS)),
        exit = shrinkVertically(tween(Motion.ENTER_MS - 80, easing = Motion.Accelerate)) + fadeOut(tween(Motion.EXIT_MS)),
    ) { Column {
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
    } }
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
