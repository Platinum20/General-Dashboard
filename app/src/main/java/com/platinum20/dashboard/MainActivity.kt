package com.platinum20.dashboard

import android.content.Intent
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.ui.screens.LocalOpenSettings
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
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.LoginScreen
import com.platinum20.dashboard.ui.screens.MediaScreen
import com.platinum20.dashboard.ui.screens.VideoPlayerScreen
import com.platinum20.dashboard.ui.screens.WebPlayerScreen
import com.platinum20.dashboard.ui.screens.YouTubeFeedScreen
import com.platinum20.dashboard.ui.screens.SpotifyFeedScreen
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
import com.platinum20.dashboard.ui.screens.WeatherScreen
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import com.platinum20.dashboard.ui.theme.isAppInDarkTheme
import com.platinum20.dashboard.widget.DashboardWidget
import com.platinum20.dashboard.work.MorningWorker
import com.platinum20.dashboard.work.RefreshWorker
import com.platinum20.dashboard.work.UpdateCheckWorker

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
        if (savedInstanceState == null) openRequest.value = OpenRequest.from(intent)
        // Lavori in background: widget (solo se presente), notifica del mattino e controllo aggiornamenti.
        if (DashboardWidget.hasWidgets(this)) RefreshWorker.schedule(this)
        MorningWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
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
    const val WEATHER = "home/weather"
    const val WEATHER_PLACE = "home/weather/place"
    const val RECAP = "home/recap"
    const val CUSTOMIZE = "home/customize"
    const val NOTES = "notes"
    const val NOTE_EDIT = "notes/edit?id={id}"
    const val MEDIA = "media"
    const val VIDEO = "media/video"
    const val YOUTUBE_FEED = "media/youtube"
    const val SPOTIFY_FEED = "media/spotify"
    const val WEB_PLAYER = "media/web/{service}?url={url}"
    const val LOGIN = "settings/login/{service}"

    fun webPlayer(service: MediaService, url: String? = null) =
        "media/web/${service.name}" + (url?.let { "?url=${Uri.encode(it)}" } ?: "")

    fun login(service: MediaService) = "settings/login/${service.name}"

    fun noteEdit(id: Long?) = "notes/edit?id=${id ?: -1L}"
    const val READER = "news/reader?url={url}&source={source}&title={title}"
    const val WEB_PAGE = "news/web?url={url}&source={source}"

    fun reader(url: String, source: String, title: String = "") =
        "news/reader?url=${Uri.encode(url)}&source=${Uri.encode(source)}&title=${Uri.encode(title)}"

    fun webPage(url: String, source: String) = "news/web?url=${Uri.encode(url)}&source=${Uri.encode(source)}"
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
    CompositionLocalProvider(LocalOpenSettings provides { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }) {
        AppContent(navController, openRequest, onOpenHandled)
    }
}

@Composable
private fun AppContent(navController: NavHostController, openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        runCatching {
            navController.switchTab(
                when (request.target) {
                    OpenRequest.NOTES -> Routes.NOTES
                    OpenRequest.MEDIA -> Routes.MEDIA
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
                OpenRequest.MEDIA -> MediaService.entries.firstOrNull { it.name == request.source }
                    ?.let { navController.navigate(Routes.webPlayer(it)) }
            }
        }
        onOpenHandled()
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topDestinations.any { it.route == currentRoute }
    // Pallino sulla scheda Note: promemoria scaduti o in scadenza oggi.
    val context = LocalContext.current
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsState()
    val dueToday = remember(notes) {
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        notes.openTasks.count { (it.dueAt ?: Long.MAX_VALUE) < endOfDay }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(dest.route) },
                            icon = {
                                BadgedBox(badge = {
                                    if (dest.route == Routes.NOTES && dueToday > 0) Badge { Text("$dueToday") }
                                }) {
                                    Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null)
                                }
                            },
                            label = { Text(dest.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    bottomPadding = padding,
                    onOpenNews = { navController.switchTab(Routes.NEWS) },
                    onOpenWeather = { navController.navigate(Routes.WEATHER) },
                    onOpenRecap = { navController.navigate(Routes.RECAP) },
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                    onOpenNotes = { navController.switchTab(Routes.NOTES) },
                    onOpenService = { navController.navigate(Routes.webPlayer(it)) },
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                    onCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                )
            }
            composable(Routes.MEDIA) {
                MediaScreen(
                    bottomPadding = padding,
                    onOpenVideo = { navController.navigate(Routes.VIDEO) },
                    onOpenService = {
                        navController.navigate(if (it == MediaService.YOUTUBE) Routes.YOUTUBE_FEED else Routes.SPOTIFY_FEED)
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.YOUTUBE_FEED) {
                YouTubeFeedScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.webPlayer(MediaService.YOUTUBE, it)) },
                    onOpenWeb = { navController.navigate(Routes.webPlayer(MediaService.YOUTUBE)) },
                )
            }
            composable(Routes.SPOTIFY_FEED) {
                SpotifyFeedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWeb = { navController.navigate(Routes.webPlayer(MediaService.SPOTIFY)) },
                )
            }
            composable(Routes.VIDEO) {
                VideoPlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenYouTube = { navController.navigate(Routes.webPlayer(MediaService.YOUTUBE, it)) },
                )
            }
            composable(
                Routes.WEB_PLAYER,
                arguments = listOf(
                    navArgument("service") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val service = MediaService.entries.firstOrNull { it.name == entry.arguments?.getString("service") }
                    ?: MediaService.YOUTUBE
                WebPlayerScreen(
                    service = service,
                    startUrl = entry.arguments?.getString("url"),
                    onBack = { navController.popBackStack() },
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
                )
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
            composable(Routes.UPDATES) {
                UpdatesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
