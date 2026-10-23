package com.platinum20.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.delay
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import com.platinum20.dashboard.capture.VoiceCapture
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TextButton
import android.Manifest
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.foundation.layout.Column
import com.platinum20.dashboard.capture.Dictation
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.platinum20.dashboard.ui.screens.TabReselect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Add
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.CaptureDialog
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.ui.screens.Haptic
import com.platinum20.dashboard.ui.screens.PlusAction
import com.platinum20.dashboard.ui.screens.SearchScreen
import com.platinum20.dashboard.ui.screens.Springs
import com.platinum20.dashboard.ui.screens.bouncyPress
import com.platinum20.dashboard.ui.screens.card
import com.platinum20.dashboard.ui.screens.cardBorder
import com.platinum20.dashboard.ui.screens.LocalHazeState
import com.platinum20.dashboard.ui.screens.glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.platinum20.dashboard.ui.screens.rememberHaptic
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.adblock.AdBlocker
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.ui.screens.LocalOpenSettings
import com.platinum20.dashboard.ui.screens.LocalOpenSearch
import com.platinum20.dashboard.ui.screens.CalendarScreen
import com.platinum20.dashboard.ui.screens.EventDetailScreen
import com.platinum20.dashboard.ui.screens.EventEditScreen
import com.platinum20.dashboard.ui.screens.InAppLinks
import com.platinum20.dashboard.ui.screens.InAppNav
import com.platinum20.dashboard.ui.screens.LinkTarget
import com.platinum20.dashboard.ui.screens.LocalInAppNav
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.youtubeWatchUrl
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.net.DiskCache
import com.platinum20.dashboard.news.NewsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import com.platinum20.dashboard.net.WebFetcher
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.LoginScreen
import com.platinum20.dashboard.ui.screens.MediaScreen
import com.platinum20.dashboard.ui.screens.FloatingPlayer
import com.platinum20.dashboard.ui.screens.ListenScreen
import com.platinum20.dashboard.ui.screens.ListenTab
import com.platinum20.dashboard.ui.screens.PlayerScreen
import com.platinum20.dashboard.media.radio.RadioService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.platinum20.dashboard.ui.screens.VideoPlayerScreen
import com.platinum20.dashboard.ui.screens.YouTubeFeedScreen
import com.platinum20.dashboard.ui.screens.YouTubeWatchScreen
import com.platinum20.dashboard.ui.screens.MusicScreen
import com.platinum20.dashboard.ui.screens.ArticleScreen
import com.platinum20.dashboard.ui.screens.CustomizeScreen
import com.platinum20.dashboard.ui.screens.HomeScreen
import com.platinum20.dashboard.ui.screens.NoteEditorScreen
import com.platinum20.dashboard.ui.screens.NotesScreen
import com.platinum20.dashboard.ui.screens.NewsScreen
import com.platinum20.dashboard.ui.screens.PlacePickerScreen
import com.platinum20.dashboard.ui.screens.ReaderScreen
import com.platinum20.dashboard.ui.screens.RecapScreen
import com.platinum20.dashboard.ui.screens.SettingsScreen
import com.platinum20.dashboard.ui.screens.UpdatesScreen
import com.platinum20.dashboard.ui.screens.AutoFillScreen
import com.platinum20.dashboard.ui.screens.WeatherScreen
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import com.platinum20.dashboard.ui.theme.isAppInDarkTheme
import com.platinum20.dashboard.widget.DashboardWidget
import com.platinum20.dashboard.work.MorningWorker
import com.platinum20.dashboard.work.RainAlertWorker
import com.platinum20.dashboard.work.RefreshWorker
import com.platinum20.dashboard.work.UpdateCheckWorker
import androidx.compose.runtime.setValue
import com.platinum20.dashboard.ui.screens.Backdrop
import com.platinum20.dashboard.ui.screens.LocalBackdrop
import com.platinum20.dashboard.ui.screens.glassSource
import androidx.compose.foundation.BorderStroke

/** Cosa aprire quando l'app parte da widget o notifica. */
data class OpenRequest(
    val target: String,
    val url: String = "",
    val source: String = "",
    val title: String = "",
) {
    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_TARGET, target)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_SOURCE, source)
            .putExtra(EXTRA_TITLE, title)
    }

    companion object {
        const val HOME = "home"
        const val WEATHER = "weather"
        const val RECAP = "recap"
        const val ARTICLE = "article"
        const val NOTES = "notes"
        const val MEDIA = "media"
        /** Video, audio o link condiviso da un'altra app (url = indirizzo o content://). */
        const val PLAY = "play"
        const val UPDATES = "updates"

        private const val EXTRA_TARGET = "open_target"
        private const val EXTRA_URL = "open_url"
        private const val EXTRA_SOURCE = "open_source"
        private const val EXTRA_TITLE = "open_title"

        fun from(intent: Intent?): OpenRequest? {
            val target = intent?.getStringExtra(EXTRA_TARGET) ?: return null
            return OpenRequest(
                target,
                intent.getStringExtra(EXTRA_URL).orEmpty(),
                intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        WebFetcher.init(this)
        DiskCache.init(this)
        // Le liste del blocco pubblicità si caricano in sottofondo, pronte per la prima pagina web.
        AdBlocker.init(this)
        if (savedInstanceState == null) openRequest.value = OpenRequest.from(intent)
        // Lavori in background: widget (solo se presente), notifica del mattino e controllo aggiornamenti.
        // Programmarli avvia WorkManager e il suo database: fuori dal thread principale, il primo frame arriva prima.
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                if (DashboardWidget.hasWidgets(app)) RefreshWorker.schedule(app)
                MorningWorker.schedule(app)
                RainAlertWorker.schedule(app)
                UpdateCheckWorker.schedule(app)
            }
        }
        // Le notizie salvate tornano in memoria subito, così News e Oggi non partono vuote.
        lifecycleScope.launch { NewsStore.restore() }
        enableEdgeToEdge()
        setContent {
            val theme by AppSettings.theme.collectAsState()
            val dark = isAppInDarkTheme(theme)

            // Icone della barra di stato chiare o scure in base al tema scelto, non solo al sistema.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            GeneralDashboardTheme(theme = theme) {
                AppRoot(
                    openRequest = openRequest.value,
                    onOpenHandled = { openRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OpenRequest.from(intent)?.let { openRequest.value = it }
    }

    override fun onDestroy() {
        // Chiudendo l'app si fermano anche YouTube e Spotify lasciati in sottofondo.
        if (isFinishing) MediaWebViews.releaseAll()
        super.onDestroy()
    }
}

object Routes {
    const val HOME = "home"
    const val NEWS = "news"
    const val SETTINGS = "settings"
    const val UPDATES = "settings/updates"
    const val AUTOFILL = "settings/autofill"
    const val WEATHER = "home/weather"
    const val WEATHER_PLACE = "home/weather/place"
    const val RECAP = "home/recap"
    const val CUSTOMIZE = "home/customize"
    const val SEARCH = "home/search"
    const val NOTES = "notes"
    const val NOTE_EDIT = "notes/edit?id={id}"
    const val MEDIA = "media"
    const val VIDEO = "media/video"
    const val VIDEO_PATTERN = "media/video?uri={uri}"

    fun video(uri: String) = "media/video?uri=${Uri.encode(uri)}"
    const val YOUTUBE_FEED = "media/youtube"
    const val SPOTIFY_FEED = "media/spotify"
    const val RADIO = "media/radio"
    const val PODCAST = "media/podcast"
    /** Il player grande unico (musica, radio, podcast). */
    const val PLAYER = "media/player"
    const val YOUTUBE_WATCH = "media/watch?url={url}"
    const val LOGIN = "settings/login/{service}"

    /** Il player YouTube dell'app (senza la pagina del sito); senza [url] mostra il video in corso. */
    fun youtubeWatch(url: String? = null) = "media/watch" + (url?.let { "?url=${Uri.encode(it)}" } ?: "")

    /** I video di YouTube nel loro player (con il video), tutto il resto nel player unico. */
    fun mediaPlayer(service: MediaService, url: String? = null) =
        when {
            service == MediaService.YOUTUBE && (url != null || com.platinum20.dashboard.media.youtube.player.YtPlayer.now.value?.audioOnly != true) -> youtubeWatch(url)
            else -> PLAYER
        }

    fun login(service: MediaService) = "settings/login/${service.name}"

    fun noteEdit(id: Long?) = "notes/edit?id=${id ?: -1L}"
    const val READER = "news/reader?url={url}&source={source}&title={title}"
    const val WEB_PAGE = "news/web?url={url}&source={source}"

    fun reader(url: String, source: String, title: String = "") =
        "news/reader?url=${Uri.encode(url)}&source=${Uri.encode(source)}&title=${Uri.encode(title)}"

    fun webPage(url: String, source: String) = "news/web?url=${Uri.encode(url)}&source=${Uri.encode(source)}"

    const val CALENDAR = "home/calendar"
    const val CALENDAR_EVENT = "home/calendar/event?id={id}&begin={begin}&end={end}"
    const val CALENDAR_EDIT = "home/calendar/edit?id={id}&day={day}"

    fun calendarEvent(id: Long, begin: Long = 0, end: Long = 0) = "home/calendar/event?id=$id&begin=$begin&end=$end"

    fun calendarEdit(id: Long?, day: LocalDate = LocalDate.now()) = "home/calendar/edit?id=${id ?: -1L}&day=${day.toEpochDay()}"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val topDestinations = listOf(
    TopDestination(Routes.HOME, "Oggi", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TopDestination(Routes.NEWS, "News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    TopDestination(Routes.NOTES, "Note", Icons.Filled.EditNote, Icons.Outlined.EditNote),
    TopDestination(Routes.MEDIA, "Media", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
)
// Le Impostazioni non stanno nella barra: si aprono dall'ingranaggio in alto in ogni sezione.

/**
 * Barra in basso flottante: una pillola con le quattro sezioni (il nome compare solo su quella aperta)
 * e accanto il + tondo per aggiungere al volo. La pillola è di vetro sfocato sopra le liste.
 */
@Composable
private fun CanvasNavBar(
    currentRoute: String?,
    dueToday: Int,
    onSelect: (TopDestination) -> Unit,
    onPlus: () -> Unit,
    listening: Boolean,
    level: Float,
    partial: String,
    /** Dito tenuto sul +: true se la dettatura è partita. */
    onHoldStart: () -> Boolean,
    onHoldEnd: () -> Unit,
    /** Trascinato verso l'alto: la dettatura resta accesa senza dito. */
    locked: Boolean,
    onLock: () -> Unit,
    onCancelDictation: () -> Unit,
    heardSound: Boolean,
    notice: String?,
) {
    val haptic = rememberHaptic()
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
    // Il fumetto con quello che si sta dicendo: alto zero, disegnato sopra la barra così non la sposta.
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0))
                layout(p.width, 0) { p.place(0, -p.height) }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        DictationBubble(listening, partial, locked, level, heardSound, notice, onCancelDictation)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Pillola di vetro: le liste scorrono sotto e si intravedono sfocate.
        Surface(
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = cardBorder(),
            // Niente ombra: sotto un fondo trasparente scurirebbe il vetro rendendolo opaco.
            modifier = Modifier.weight(1f).height(60.dp).glass(CircleShape),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                topDestinations.forEach { dest ->
                    NavPill(
                        dest = dest,
                        selected = currentRoute == dest.route,
                        badge = if (dest.route == Routes.NOTES) dueToday else 0,
                        onClick = { haptic(Haptic.TICK); onSelect(dest) },
                    )
                }
            }
        }
        // Il +: tocco = scrivi; tenuto premuto = diventa un microfono e detta, come i vocali di WhatsApp.
        val grow by animateFloatAsState(if (listening) 1.18f else 1f, Springs.snappy(), label = "micGrow")
        val pulse by animateFloatAsState(if (listening) level else 0f, tween(90), label = "micLevel")
        val ring = MaterialTheme.colorScheme.primary
        // Il gesto vive più a lungo di una ricomposizione: sempre le azioni aggiornate.
        val tap by rememberUpdatedState(onPlus)
        val holdStart by rememberUpdatedState(onHoldStart)
        val holdEnd by rememberUpdatedState(onHoldEnd)
        val isLocked by rememberUpdatedState(locked)
        val lock by rememberUpdatedState(onLock)
        // Quanto si è trascinato il dito verso l'alto: il lucchetto sale con lui.
        var drag by remember { mutableFloatStateOf(0f) }
        val lockDistance = with(LocalDensity.current) { 64.dp.toPx() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .drawBehind {
                    // L'alone cresce con la voce.
                    if (grow > 1.01f) drawCircle(ring.copy(alpha = 0.22f), radius = size.minDimension / 2 * (grow + 0.15f + pulse * 0.45f))
                }
                .graphicsLayer { scaleX = grow; scaleY = grow },
        ) {
            Surface(
                shape = CircleShape,
                // Vetro colorato come la pillola accanto: stesso materiale, tinta del tema.
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxSize().bouncyPress(0.88f).glass(CircleShape, MaterialTheme.colorScheme.primary)
                    .semantics {
                        contentDescription = when {
                            listening && locked -> "Inserisci"
                            listening -> "In ascolto"
                            else -> "Aggiungi"
                        }
                        onClick(label = "Aggiungi") { onPlus(); true }
                        onLongClick(label = "Detta a voce") { onHoldStart() }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var lifted = false
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation().also { lifted = true }
                            }
                            when {
                                // Microfono bloccato: il tocco inserisce quello che si è detto.
                                up != null && isLocked -> { haptic(Haptic.CONFIRM); holdEnd() }
                                up != null -> { haptic(Haptic.TICK); tap() }
                                // Annullato (es. il dito è scivolato via): niente.
                                lifted -> Unit
                                isLocked -> Unit
                                else -> {
                                    if (holdStart()) {
                                        haptic(Haptic.CONFIRM)
                                        // Come WhatsApp: trascinando in su il microfono si blocca.
                                        var lockedNow = false
                                        while (true) {
                                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            drag = (down.position.y - change.position.y).coerceAtLeast(0f)
                                            if (!lockedNow && drag > lockDistance) {
                                                lockedNow = true
                                                haptic(Haptic.CONFIRM)
                                                lock()
                                            }
                                            change.consume()
                                        }
                                        drag = 0f
                                        if (!lockedNow) {
                                            haptic(Haptic.TICK)
                                            holdEnd()
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = when {
                        listening && locked -> 2
                        listening -> 1
                        else -> 0
                    }
                    Crossfade(icon, label = "plusMic") { i ->
                        Icon(
                            when (i) {
                                2 -> Icons.AutoMirrored.Rounded.Send
                                1 -> Icons.Rounded.Mic
                                else -> Icons.Rounded.Add
                            },
                            contentDescription = null,
                            modifier = Modifier.size(if (i == 2) 24.dp else 28.dp),
                        )
                    }
                }
            }
            // Il lucchetto sopra il microfono: sale con il dito, arrivato in cima blocca.
            if (listening && !locked) {
                val progress = (drag / lockDistance).coerceIn(0f, 1f)
                Surface(
                    shape = CircleShape,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    border = cardBorder(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, (-62.dp.toPx() - drag * 0.6f).roundToInt()) }
                        .glass(CircleShape),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            if (progress >= 1f) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = "Trascina in su per bloccare",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f - progress * 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * Il fumetto sopra la barra mentre si detta tenendo premuto il +: le barre che seguono la voce
 * e, sotto, quello che Canvas sta capendo. Se dopo un attimo non si sente niente lo dice.
 */
@Composable
private fun DictationBubble(
    listening: Boolean,
    partial: String,
    locked: Boolean,
    level: Float,
    heardSound: Boolean,
    notice: String?,
    onCancel: () -> Unit,
) {
    // Dopo due secondi di solo silenzio: "Nessun suono rilevato" invece di "Ti ascolto…".
    var silent by remember { mutableStateOf(false) }
    LaunchedEffect(listening, heardSound) {
        silent = false
        if (listening && !heardSound) {
            delay(2_000)
            silent = true
        }
    }
    // Le ultime misure del volume, la più nuova a destra: un piccolo visualizzatore.
    val bars = remember { mutableStateListOf<Float>().apply { repeat(BAR_COUNT) { add(0f) } } }
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(listening) {
        while (listening) {
            bars.removeAt(0)
            bars.add(currentLevel)
            delay(70)
        }
        for (i in bars.indices) bars[i] = 0f
    }
    AnimatedVisibility(
        visible = listening || notice != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(tween(120)),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = cardBorder(),
            modifier = Modifier.padding(horizontal = 16.dp).glass(RoundedCornerShape(22.dp)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (listening) {
                    val barColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                        val gap = 3.dp.toPx()
                        val w = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
                        bars.forEachIndexed { i, v ->
                            val h = (size.height * (0.12f + 0.88f * v)).coerceAtMost(size.height)
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.35f + 0.65f * v),
                                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(w / 2, w / 2),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                val shown = when {
                    !listening && notice != null -> notice
                    partial.isNotBlank() -> partial
                    silent -> Dictation.NO_SOUND
                    else -> "Ti ascolto…"
                }
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (shown == Dictation.NO_SOUND) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (listening) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (locked) "Bloccato · tocca ➤ per inserire" else "Rilascia per finire · trascina su per bloccare",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (locked) {
                        TextButton(onClick = onCancel) { Text("Annulla") }
                    }
                }
            }
        }
    }
}

private const val BAR_COUNT = 28

@Composable
private fun NavPill(dest: TopDestination, selected: Boolean, badge: Int, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, label = "pill",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant, label = "pillText",
    )
    Row(
        modifier = Modifier
            .bouncyPress(0.9f)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .semantics { contentDescription = dest.label }
            .animateContentSize(Springs.snappy())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
            Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(dest.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

// Molle delle transizioni: le pagine arrivano con un leggero rimbalzo invece di una curva fissa.
private val pageSpring = spring(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
private val scaleSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    topDestinations.any { it.route == initialState.destination.route } &&
        topDestinations.any { it.route == targetState.destination.route }

/** Passa a una sezione della barra in basso mantenendo lo stato delle altre. */
private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppRoot(openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Link, pagine e calendario si aprono sempre dentro l'app.
    val inApp = remember(navController) {
        InAppNav(
            openWeb = { url, title ->
                when (val target = InAppLinks.target(url)) {
                    is LinkTarget.YouTube -> navController.navigate(Routes.youtubeWatch(target.watchUrl))
                    is LinkTarget.Spotify -> {
                        SpotifyPlayer.playLink(context, target.url)
                        navController.navigate(Routes.PLAYER)
                    }
                    is LinkTarget.Web -> navController.navigate(Routes.webPage(target.url, title))
                    LinkTarget.None -> Unit
                }
            },
            openCalendar = { navController.navigate(Routes.CALENDAR) { launchSingleTop = true } },
            openEvent = { navController.navigate(Routes.calendarEvent(it.eventId, it.begin, it.end)) },
            newEvent = { navController.navigate(Routes.calendarEdit(null, it)) },
            openRoute = { navController.navigate(it) },
        )
    }
    CompositionLocalProvider(
        LocalOpenSettings provides { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
        LocalOpenSearch provides { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
        LocalInAppNav provides inApp,
    ) {
        AppContent(navController, openRequest, onOpenHandled)
    }
}

@Composable
private fun AppContent(navController: NavHostController, openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        runCatching {
            navController.switchTab(
                when (request.target) {
                    OpenRequest.NOTES -> Routes.NOTES
                    OpenRequest.MEDIA, OpenRequest.PLAY -> Routes.MEDIA
                    else -> Routes.HOME
                }
            )
            when (request.target) {
                OpenRequest.UPDATES -> {
                    // Le Impostazioni non sono più una scheda: Oggi → Impostazioni → Aggiornamenti.
                    navController.navigate(Routes.SETTINGS)
                    navController.navigate(Routes.UPDATES)
                }
                OpenRequest.WEATHER -> navController.navigate(Routes.WEATHER)
                OpenRequest.RECAP -> navController.navigate(Routes.RECAP)
                OpenRequest.ARTICLE -> if (request.url.isNotBlank()) {
                    navController.navigate(Routes.reader(request.url, request.source, request.title))
                }
                OpenRequest.MEDIA -> if (request.source == RadioService.SOURCE_RADIO) {
                    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                } else {
                    MediaService.entries.firstOrNull { it.name == request.source }
                        ?.let { navController.navigate(Routes.mediaPlayer(it)) }
                }
                OpenRequest.PLAY -> if (request.url.isNotBlank()) {
                    val youtube = youtubeWatchUrl(request.url)
                    when {
                        youtube != null -> navController.navigate(Routes.youtubeWatch(youtube))
                        SpotifyPlayer.spotifyUri(request.url) != null -> {
                            SpotifyPlayer.playLink(context, request.url)
                            navController.navigate(Routes.PLAYER)
                        }
                        else -> navController.navigate(Routes.video(request.url))
                    }
                }
            }
        }
        onOpenHandled()
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topDestinations.any { it.route == currentRoute }
    // Pallino sulla scheda Note: promemoria scaduti o in scadenza oggi.
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsState()
    // L'app può restare in memoria per giorni: le spese fisse del giorno vanno aggiunte a ogni ritorno.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { NotesStore.applyFixedExpenses() }
    val notesProblem by NotesStore.readProblem.collectAsState()
    LaunchedEffect(notesProblem) {
        if (notesProblem != null) {
            Toast.makeText(
                context,
                "Non sono riuscito a leggere le note: ne ho salvato una copia, puoi ripristinare un backup",
                Toast.LENGTH_LONG,
            ).show()
            NotesStore.readProblemShown()
        }
    }
    val dueToday = remember(notes) {
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        notes.openTasks.count { (it.dueAt ?: Long.MAX_VALUE) < endOfDay }
    }

    // Il + della barra: cattura veloce, oppure l'azione della sezione aperta (in Note, la scheda scelta).
    var capturing by remember { mutableStateOf(false) }
    // Il testo dettato tenendo premuto il +: il dialogo lo riceve e ne riconosce il tipo.
    var dictated by remember { mutableStateOf("") }
    val plusOverride by PlusAction.override
    if (capturing) CaptureDialog(initial = dictated, initialMode = CaptureMode.TASK, onClose = { capturing = false; dictated = "" })
    // Tenendo premuto il + si detta, senza finestre: il microfono e il testo li disegna la barra.
    val dictation = remember { Dictation(context.applicationContext) }
    val voiceScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(Unit) { onDispose { dictation.cancel() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Toast.makeText(context, "Ora tieni premuto il + e parla", Toast.LENGTH_SHORT).show()
    }
    val startDictation: () -> Boolean = start@{
        when {
            !dictation.hasPermission() -> {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return@start false
            }
            !dictation.available -> {
                Toast.makeText(context, "Dettatura non disponibile su questo telefono", Toast.LENGTH_SHORT).show()
                return@start false
            }
        }
        dictation.start { text ->
            // Niente di detto: "Nessun suono rilevato" resta un attimo nel fumetto.
            if (text.isNullOrBlank()) voiceScope.launch {
                delay(1_600)
                dictation.clearNotice()
            } else voiceScope.launch {
                // Si salva da solo, nel tipo giusto; il dialogo solo se manca qualcosa.
                when (val outcome = VoiceCapture.save(context, text)) {
                    VoiceCapture.Outcome.NeedsDialog -> {
                        dictated = text
                        capturing = true
                    }
                    is VoiceCapture.Outcome.Saved -> {
                        val undo = outcome.undo
                        val answer = snackbar.showSnackbar(
                            outcome.message,
                            actionLabel = if (undo != null) "Annulla" else null,
                            withDismissAction = undo == null,
                            duration = SnackbarDuration.Short,
                        )
                        if (answer == SnackbarResult.ActionPerformed) undo?.invoke()
                    }
                }
            }
        }
        true
    }

    // Le pagine sono la sorgente del vetro: barra in basso e mini player le sfocano sotto di sé.
    val hazeState = remember { HazeState() }
    val backdrop = remember { Backdrop() }
    CompositionLocalProvider(LocalHazeState provides hazeState, LocalBackdrop provides backdrop) {
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(pageSpring) { it } + fadeIn(),
                exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
            ) {
                CanvasNavBar(
                    currentRoute = currentRoute,
                    dueToday = dueToday,
                    onSelect = { dest ->
                        // Toccare la scheda già aperta riporta la lista in cima.
                        if (currentRoute == dest.route) TabReselect.events.tryEmit(dest.route) else navController.switchTab(dest.route)
                    },
                    onPlus = { plusOverride?.invoke() ?: run { capturing = true } },
                    listening = dictation.listening,
                    level = dictation.level,
                    partial = dictation.partial,
                    onHoldStart = startDictation,
                    onHoldEnd = dictation::release,
                    locked = dictation.locked,
                    onLock = dictation::lock,
                    onCancelDictation = dictation::discard,
                    heardSound = dictation.heardSound,
                    notice = dictation.notice,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        NavHost(
            modifier = Modifier.glassSource(hazeState, backdrop),
            navController = navController,
            startDestination = Routes.HOME,
            // Tra le schede una dissolvenza; le pagine di dettaglio entrano da destra ed escono all'indietro.
            enterTransition = {
                if (isTabSwitch()) fadeIn(tween(180)) + scaleIn(scaleSpring, initialScale = 0.98f)
                else slideInHorizontally(pageSpring) { it / 4 } + fadeIn(tween(200)) + scaleIn(scaleSpring, initialScale = 0.97f)
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                if (isTabSwitch()) fadeOut(tween(160))
                else slideOutHorizontally(pageSpring) { it / 4 } + fadeOut(tween(200))
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    bottomPadding = padding,
                    onOpenNews = { navController.switchTab(Routes.NEWS) },
                    onOpenWeather = { navController.navigate(Routes.WEATHER) },
                    onOpenRecap = { navController.navigate(Routes.RECAP) },
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                    onOpenNotes = { navController.switchTab(Routes.NOTES) },
                    onCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenAutoFill = { navController.navigate(Routes.AUTOFILL) },
                )
            }
            composable(Routes.MEDIA) {
                MediaScreen(
                    bottomPadding = padding,
                    onOpenMusic = { navController.navigate(Routes.SPOTIFY_FEED) },
                    onOpenVideo = { navController.navigate(Routes.YOUTUBE_FEED) },
                    onOpenListen = { navController.navigate(if (it == ListenTab.RADIO) Routes.RADIO else Routes.PODCAST) },
                    onOpenPlayer = { navController.navigate(if (it.isVideo) Routes.youtubeWatch() else Routes.PLAYER) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            // Radio e podcast sono una sezione sola: le due strade aprono la stessa pagina sulla scheda giusta.
            composable(Routes.RADIO) {
                ListenScreen(start = ListenTab.RADIO, onBack = { navController.popBackStack() })
            }
            composable(Routes.PODCAST) {
                ListenScreen(start = ListenTab.PODCAST, onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onWatchVideo = { link -> navController.navigate(Routes.youtubeWatch(link)) { popUpTo(Routes.PLAYER) { inclusive = true } } },
                )
            }
            composable(
                Routes.YOUTUBE_WATCH,
                arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }),
                // Il video sale dal basso e, chiuso (anche trascinandolo giù), ridiscende verso il mini player.
                enterTransition = { slideInVertically(pageSpring) { it / 3 } + fadeIn(tween(200)) },
                popExitTransition = { slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(200)) },
            ) { entry ->
                YouTubeWatchScreen(
                    url = entry.arguments?.getString("url"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.YOUTUBE_FEED) {
                YouTubeFeedScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.youtubeWatch(it)) },
                    onLogin = { navController.navigate(Routes.login(MediaService.YOUTUBE)) },
                    onOpenPhone = { navController.navigate(Routes.VIDEO) },
                )
            }
            composable(Routes.SPOTIFY_FEED) {
                MusicScreen(
                    onBack = { navController.popBackStack() },
                    onLogin = { navController.navigate(Routes.login(MediaService.SPOTIFY)) },
                )
            }
            composable(
                Routes.VIDEO_PATTERN,
                arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                VideoPlayerScreen(
                    startUri = entry.arguments?.getString("uri"),
                    onBack = { navController.popBackStack() },
                    onOpenYouTube = { navController.navigate(Routes.youtubeWatch(it)) },
                )
            }
            composable(
                Routes.LOGIN,
                arguments = listOf(navArgument("service") { type = NavType.StringType }),
            ) { entry ->
                val service = MediaService.entries.firstOrNull { it.name == entry.arguments?.getString("service") }
                    ?: MediaService.YOUTUBE
                LoginScreen(service = service, onDone = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteEdit(it)) },
                    onOpenNotes = { navController.switchTab(Routes.NOTES) },
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                )
            }
            composable(Routes.CUSTOMIZE) {
                CustomizeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTES) {
                NotesScreen(
                    bottomPadding = padding,
                    onOpenNote = { navController.navigate(Routes.noteEdit(it)) },
                )
            }
            composable(
                Routes.NOTE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                NoteEditorScreen(
                    noteId = entry.arguments?.getLong("id")?.takeIf { it >= 0 },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.WEATHER) {
                WeatherScreen(
                    onBack = { navController.popBackStack() },
                    onPickPlace = { navController.navigate(Routes.WEATHER_PLACE) },
                )
            }
            composable(Routes.WEATHER_PLACE) {
                PlacePickerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECAP) {
                RecapScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                )
            }
            composable(Routes.NEWS) {
                NewsScreen(
                    bottomPadding = padding,
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUpdates = { navController.navigate(Routes.UPDATES) },
                    onCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                    onLogin = { navController.navigate(Routes.login(it)) },
                    onOpenAutoFill = { navController.navigate(Routes.AUTOFILL) },
                    onPickPlace = { navController.navigate(Routes.WEATHER_PLACE) },
                )
            }
            composable(Routes.AUTOFILL) {
                AutoFillScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("source") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val source = entry.arguments?.getString("source").orEmpty()
                ReaderScreen(
                    sourceName = source,
                    fallbackTitle = entry.arguments?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenLink = { navController.navigate(Routes.reader(it, source)) },
                    onOpenOriginal = { navController.navigate(Routes.webPage(it, source)) },
                )
            }
            composable(
                Routes.WEB_PAGE,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("source") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ArticleScreen(
                    url = entry.arguments?.getString("url").orEmpty(),
                    sourceName = entry.arguments?.getString("source").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEvent = { navController.navigate(Routes.calendarEvent(it.eventId, it.begin, it.end)) },
                    onNewEvent = { navController.navigate(Routes.calendarEdit(null, it)) },
                )
            }
            composable(
                Routes.CALENDAR_EVENT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("begin") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("end") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                EventDetailScreen(
                    eventId = entry.arguments?.getLong("id") ?: -1L,
                    begin = entry.arguments?.getLong("begin") ?: 0L,
                    end = entry.arguments?.getLong("end") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.calendarEdit(it)) },
                )
            }
            composable(
                Routes.CALENDAR_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("day") { type = NavType.LongType; defaultValue = LocalDate.now().toEpochDay() },
                ),
            ) { entry ->
                EventEditScreen(
                    eventId = entry.arguments?.getLong("id")?.takeIf { it >= 0 },
                    day = LocalDate.ofEpochDay(entry.arguments?.getLong("day") ?: LocalDate.now().toEpochDay()),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.UPDATES) {
                UpdatesScreen(onBack = { navController.popBackStack() })
            }
        }
        // Mini player a bolla, sopra tutte le schermate.
        FloatingPlayer(
            currentRoute = currentRoute,
            onExpand = { playing ->
                navController.navigate(if (playing.isVideo) Routes.youtubeWatch() else Routes.PLAYER) { launchSingleTop = true }
            },
            modifier = Modifier.padding(padding),
        )
        }
    }
    }
}
