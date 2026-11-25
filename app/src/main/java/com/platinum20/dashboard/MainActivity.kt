@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.platinum20.dashboard

import com.platinum20.dashboard.capture.VoiceSlide
import com.platinum20.dashboard.ui.screens.ComposerKey
import com.platinum20.dashboard.ui.screens.COMPOSER_KEY_DP
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.rounded.ChevronLeft
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.CaptureComposer
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.songid.SongId
import com.platinum20.dashboard.ui.screens.CaptureComposerVeil
import com.platinum20.dashboard.ui.screens.CaptureComposerInput
import com.platinum20.dashboard.ui.screens.CaptureDestinationChips
import com.platinum20.dashboard.ui.screens.rememberComposerSend
import com.platinum20.dashboard.ui.screens.ItemDetailScreen
import com.platinum20.dashboard.ui.screens.ItemKind
import com.platinum20.dashboard.capture.CaptureTargets
import com.platinum20.dashboard.capture.captureTarget
import com.platinum20.dashboard.ui.screens.CaptureFeedbackLayer
import com.platinum20.dashboard.ui.screens.SaveToNotesLayer
import com.platinum20.dashboard.ui.screens.ActionBubbleLayer
import com.platinum20.dashboard.ui.screens.WhatsNewLayer
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import com.platinum20.dashboard.capture.VoiceCapture
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableFloatStateOf
import android.Manifest
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
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
import androidx.compose.material.icons.automirrored.filled.Article as ArticleFilled
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.Article as ArticleOutlined
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.platinum20.dashboard.ui.screens.Motion
import com.platinum20.dashboard.ui.screens.bouncyPress
import com.platinum20.dashboard.ui.screens.card
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.draggable
import androidx.compose.animation.EnterTransition
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.platinum20.dashboard.adblock.AdBlocker
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.ui.screens.PullToSearchIndicator
import com.platinum20.dashboard.ui.screens.LocalOpenSearch
import com.platinum20.dashboard.ui.screens.LocalOpenSettings
import com.platinum20.dashboard.ui.screens.rememberPullToSearch
import com.platinum20.dashboard.ui.screens.SearchFilter
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
import com.platinum20.dashboard.media.MediaLauncher
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.youtubeWatchUrl
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.net.DiskCache
import com.platinum20.dashboard.news.NewsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.platinum20.dashboard.net.WebFetcher
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.LoginScreen
import com.platinum20.dashboard.ui.screens.MediaScreen
import com.platinum20.dashboard.ui.screens.FloatingPlayer
import com.platinum20.dashboard.ui.screens.ListenScreen
import com.platinum20.dashboard.ui.screens.PlayerScreen
import com.platinum20.dashboard.media.PlayerService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.platinum20.dashboard.ui.screens.VideoPlayerScreen
import com.platinum20.dashboard.ui.screens.ArticleScreen
import com.platinum20.dashboard.ui.screens.CustomizeScreen
import com.platinum20.dashboard.ui.screens.HomeScreen
import com.platinum20.dashboard.ui.screens.NoteEditorScreen
import com.platinum20.dashboard.ui.screens.NotesScreen
import com.platinum20.dashboard.ui.screens.NewsScreen
import com.platinum20.dashboard.ui.screens.PlacePickerScreen
import com.platinum20.dashboard.ui.screens.ReaderScreen
import com.platinum20.dashboard.ui.screens.SettingsPage
import com.platinum20.dashboard.ui.screens.SettingsPageScreen
import com.platinum20.dashboard.ui.screens.SettingsScreen
import com.platinum20.dashboard.ui.screens.UpdatesScreen
import com.platinum20.dashboard.ui.screens.AutoFillScreen
import com.platinum20.dashboard.ui.screens.MailScreen
import com.platinum20.dashboard.ui.screens.WeatherScreen
import com.platinum20.dashboard.ui.screens.SimScreen
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import com.platinum20.dashboard.ui.theme.isAppInDarkTheme
import com.platinum20.dashboard.widget.Widgets
import com.platinum20.dashboard.work.MorningWorker
import com.platinum20.dashboard.work.RainAlertWorker
import com.platinum20.dashboard.work.RefreshWorker
import com.platinum20.dashboard.work.UpdateCheckWorker
import androidx.compose.runtime.setValue
import com.platinum20.dashboard.ui.screens.Backdrop
import com.platinum20.dashboard.ui.screens.LocalBackdrop
import com.platinum20.dashboard.ui.screens.glassSource
import com.platinum20.dashboard.ui.screens.backdropSource

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
        /** Dai widget: scheda di un promemoria (url = id, source = ItemKind), di un evento (url = "id/inizio/fine"). */
        const val ITEM = "item"
        const val EVENT = "event"
        const val SIM = "sim"
        /** Dal widget Da fare: la pagina intera di Da fare (come toccando la card della Home). */
        const val LATER = "later"
        /** Dal widget Nota: la nota nell'editor (url = id). */
        const val NOTE = "note"
        /** Widget Scrivi e "+" del Calendario: la barra si apre già pronta per scrivere, o in ascolto. */
        const val WRITE = "write"
        const val VOICE = "voice"

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

    /** Video di YouTube a schermo intero: i tasti del volume mostrano la barra del player, non quella di sistema. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        com.platinum20.dashboard.ui.screens.FullscreenVolume.onKey(this, keyCode) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        (com.platinum20.dashboard.ui.screens.FullscreenVolume.active &&
            com.platinum20.dashboard.ui.screens.FullscreenVolume.isVolumeKey(keyCode)) || super.onKeyUp(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Un solo caricatore di immagini che sa anche fare i fotogrammi dei video del telefono (1.493).
        coil3.SingletonImageLoader.setSafe { ctx ->
            coil3.ImageLoader.Builder(ctx).components { add(coil3.video.VideoFrameDecoder.Factory()) }.build()
        }
        AppSettings.init(this)
        com.platinum20.dashboard.trips.TrainLive.init(this)
        // Le note di esempio (2026-11-24): una volta sola, per vedere Home e editor pieni.
        com.platinum20.dashboard.notes.ExampleNotes.seedOnce(this)
        // Gli avvisi dei posti delle note: il sistema li perde, si rimettono a ogni avvio (idea 11).
        runCatching { com.platinum20.dashboard.notes.NotePlaces.registerAll(this) }
        WebFetcher.init(this)
        DiskCache.init(this)
        // Le liste del blocco pubblicità si caricano in sottofondo, pronte per la prima pagina web.
        AdBlocker.init(this)
        // La coda di ascolto ritrovata com'era.
        com.platinum20.dashboard.media.CanvasPlayer.init(this)
        // Il riproduttore di Spotify si accende con l'app e resta pronto: il play parte subito.
        com.platinum20.dashboard.media.spotify.SpotifyAuth.init(this)
        com.platinum20.dashboard.media.youtube.YouTubeStore.init(this)
        if (com.platinum20.dashboard.media.spotify.SpotifyAuth.connected.value) com.platinum20.dashboard.media.spotify.SpotifyPlayer.start(this)
        if (savedInstanceState == null) openRequest.value = OpenRequest.from(intent)
        // YouTube uguale al PC: Guarda più tardi = Note, Mi piace, cronologia (solo con l'accesso fatto nell'app).
        com.platinum20.dashboard.media.youtube.YtSync.start(this)
        lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) com.platinum20.dashboard.media.youtube.YtSync.foreground(this)
            // Spotify pronto mentre l'app è aperta; chiusa e in silenzio, il suo riproduttore si spegne (batteria).
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) com.platinum20.dashboard.media.spotify.SpotifyPlayer.setBackground(this, false)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) com.platinum20.dashboard.media.spotify.SpotifyPlayer.setBackground(this, true)
        })
        // Lavori in background: widget (solo se presente), notifica del mattino e controllo aggiornamenti.
        // Programmarli avvia WorkManager e il suo database: fuori dal thread principale, il primo frame arriva prima.
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            runCatching {
                if (Widgets.hasAny(app)) RefreshWorker.schedule(app)
                MorningWorker.schedule(app)
                RainAlertWorker.schedule(app)
                com.platinum20.dashboard.work.TripLiveWorker.schedule(app)
                com.platinum20.dashboard.media.SavedDownloads.init(app)
                com.platinum20.dashboard.work.SavedDownloadWorker.schedule(app)
                com.platinum20.dashboard.work.SavedDownloadWorker.now(app)
                UpdateCheckWorker.schedule(app)
                com.platinum20.dashboard.mail.MailStore.init(app)
                if (com.platinum20.dashboard.mail.MailStore.connected()) com.platinum20.dashboard.work.MailWorker.schedule(app)
                // WindTre e Creators Pro: il giro che tiene vivo l'accesso (si accende solo se collegato).
                com.platinum20.dashboard.settings.Leftovers.clean(app)
                com.platinum20.dashboard.work.KeepAliveWorker.update(app)
                com.platinum20.dashboard.shifts.ShiftStore.init(app)
                if (com.platinum20.dashboard.shifts.ShiftStore.linked.value) com.platinum20.dashboard.work.ShiftWorker.schedule(app)
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

}

object Routes {
    const val HOME = "home"
    const val NEWS = "news"
    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/page/{page}"
    /** Le Impostazioni aperte alla parte di una sezione della barra ([HOME], [NEWS], [NOTES], [MEDIA]). */
    fun settingsPage(page: SettingsPage) = "settings/page/${page.name}"
    const val UPDATES = "settings/updates"
    const val AUTOFILL = "settings/autofill"
    const val MAIL = "home/mail/{id}"
    fun mail(id: String) = "home/mail/${Uri.encode(id)}"
    const val WEATHER = "home/weather"
    const val WEATHER_PLACE = "home/weather/place"
    const val CUSTOMIZE = "home/customize"
    const val SIM = "home/sim"
    /** L'archivio di Per dopo (1.493): le cose fatte, si apre toccando la card. */
    const val LATER_ARCHIVE = "home/later/archive"
    const val JOTTINGS_ARCHIVE = "home/jottings/archive"
    /** Un documento fotografato con la graffetta (dalla card Documenti della Home). */
    const val DOCUMENT = "home/document/{id}"
    fun document(id: Long) = "home/document/$id"
    /** L'accesso a Creators Pro per i turni di lavoro (una volta, poi la lettura è nascosta). */
    const val SHIFTS = "settings/shifts"
    /** La scheda della barra da accendere: una pagina aperta dalla Home tiene accesa la Home. */
    fun barTab(route: String?): String? = when {
        route == null -> null
        // Il calendario e le pagine aperte dalla Home (Da fare, Meteo, #etichette…) tengono accesa la Home.
        route.startsWith("home") || route.startsWith("notes") -> HOME
        route.startsWith("media") -> MEDIA
        route.startsWith("news") -> NEWS
        else -> route
    }

    /**
     * La regola della barra e del + (1.624, Antonio): ci sono dove si sfoglia un elenco, mancano dentro una cosa
     * sola (pagine con i loro tasti in fondo, player, lettore), nelle impostazioni e in Cerca.
     */
    fun hasBar(route: String?): Boolean = route in BAR_PAGES

    private val BAR_PAGES = setOf(
        HOME, NEWS, MEDIA, NOTES,
        LATER_ARCHIVE, JOTTINGS_ARCHIVE, WEATHER, SIM, TAG, PERSON,
        RADIO, PODCAST, SPOTIFY_ITEM, YOUTUBE_CHANNEL, YOUTUBE_PLAYLIST, YOUTUBE_FEED,
        MEDIA_MIX, MEDIA_NEWS, SAVED_MEDIA, MUSIC_ALL, MUSIC_DISCOVER, LIKED_TRACKS,
    )
    const val SEARCH = "home/search"
    const val SEARCH_PATTERN = "home/search?f={f}"
    fun search(filter: SearchFilter?) = if (filter == null) SEARCH else "$SEARCH?f=${filter.name}"
    const val NOTES = "notes"
    const val NOTE_EDIT = "notes/edit?id={id}&find={find}"
    const val MEDIA = "media"
    const val VIDEO = "media/video"
    const val VIDEO_PATTERN = "media/video?uri={uri}"

    const val YOUTUBE_FEED = "media/youtube"
    const val RADIO = "media/radio"
    const val PODCAST = "media/podcast"
    /** Il player grande unico (musica, radio, podcast). */
    const val PLAYER = "media/player"
    /** Sotto il player: qui il mini player si vede (si sistema la coda e si vede cosa suona). */
    const val QUEUE = "media/player/queue"
    const val YOUTUBE_WATCH = "media/watch?url={url}"
    /** Tutti i video di un canale YouTube (dal tocco sul canale nella pagina del video). */
    const val YOUTUBE_CHANNEL = "media/channel/{id}?title={title}&avatar={avatar}"
    fun youtubeChannel(c: com.platinum20.dashboard.media.youtube.YtChannel) =
        "media/channel/${c.id}?title=${Uri.encode(c.title)}&avatar=${Uri.encode(c.thumbnail)}"
    // Playlist e Mi piace di YouTube come pagine vere (1.357).
    const val YOUTUBE_PLAYLIST = "media/playlist/{id}?title={title}&count={count}"
    fun youtubePlaylist(p: com.platinum20.dashboard.media.youtube.YtPlaylist) =
        "media/playlist/${Uri.encode(p.id)}?title=${Uri.encode(p.title)}&count=${p.count}"
    /** I brani che ti piacciono (1.488): Mi piace diviso in brani e video. */
    const val LIKED_TRACKS = "media/liked/tracks"
    /** Un mix di Per te come pagina sua, con Riproduci (1.486). */
    const val MEDIA_MIX = "media/mix/{id}"
    fun mix(id: String) = "media/mix/${Uri.encode(id)}"
    /** Tutte le novità in verticale, dall'ultima tessera della fila (1.486). */
    const val MEDIA_NEWS = "media/news"
    /** Le cose salvate per dopo di una fila di Media (1.490): video o musica. */
    const val SAVED_MEDIA = "media/saved/{kind}"
    fun savedMedia(video: Boolean) = "media/saved/" + if (video) "video" else "musica"
    /** Scopri della musica (1.488): il tastino a destra del titolo Musica. */
    const val MUSIC_DISCOVER = "media/discover"
    /** Tutta la fila Musica in verticale, dal titolo (1.492). */
    const val MUSIC_ALL = "media/music"
    /** Un artista, un album o una playlist di Spotify come pagina sua (1.481: Musica non è più una pagina a parte). */
    const val SPOTIFY_ITEM = "media/spotify/item/{kind}/{id}?title={title}&subtitle={subtitle}&image={image}"
    fun spotifyItem(i: com.platinum20.dashboard.media.spotify.SpItem) =
        "media/spotify/item/${i.kind.name}/${Uri.encode(i.id)}?title=${Uri.encode(i.title)}&subtitle=${Uri.encode(i.subtitle)}&image=${Uri.encode(i.image)}"
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

    /** [find] = la parola cercata in Cerca ovunque: la nota si apre su quella riga, con la parola colorata. */
    fun noteEdit(id: Long?, find: String = "") =
        "notes/edit?id=${id ?: -1L}" + if (find.isEmpty()) "" else "&find=${android.net.Uri.encode(find)}"

    /** La pagina a schermo intero di un promemoria, una data, un pacco, o un'abitudine. */
    const val NOTE_ITEM = "notes/item/{kind}/{id}"

    fun noteItem(kind: ItemKind, id: Long) = "notes/item/${kind.name}/$id"

    /** La pagina di un'etichetta delle note (#casa) e di una persona dei turni (@Marco), 2026-11-23. */
    const val TAG = "notes/tag/{name}"
    fun tag(name: String) = "notes/tag/${Uri.encode(name)}"
    const val PERSON = "notes/person/{name}"
    fun person(name: String) = "notes/person/${Uri.encode(name)}"
    const val READER = "news/reader?url={url}&source={source}&title={title}"
    const val WEB_PAGE = "news/web?url={url}&source={source}"

    fun reader(url: String, source: String, title: String = "") =
        "news/reader?url=${Uri.encode(url)}&source=${Uri.encode(source)}&title=${Uri.encode(title)}"

    fun webPage(url: String, source: String) = "news/web?url=${Uri.encode(url)}&source=${Uri.encode(source)}"

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
    TopDestination(Routes.HOME, "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TopDestination(Routes.NEWS, "News", Icons.AutoMirrored.Filled.ArticleFilled, Icons.AutoMirrored.Outlined.ArticleOutlined),
    TopDestination(Routes.MEDIA, "Media", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
)
// Le Impostazioni non stanno nella barra: si aprono tenendo premuta una sezione, alla sua parte.

/**
 * In basso solo il + (1.634, Antonio: "lasciamo solo la Home"): niente più pillola delle sezioni. Toccandolo il + si
 * allunga verso sinistra e diventa il riquadro per scrivere, come nell'app Claude; tenendolo premuto diventa il vocale
 * a mani libere (le parole compaiono scritte, poi ✕ butta, ■ ferma e lascia correggere, Invia salva). Finito, il
 * riquadro rientra nel +.
 */
@Composable
private fun CanvasNavBar(
    onPlus: () -> Unit,
    listening: Boolean,
    level: Float,
    partial: String,
    /** Dito tenuto sul +: true se la dettatura è partita (resta accesa senza dito). */
    onHoldStart: () -> Boolean,
    /** Il vocale finito (dito sollevato o freccia toccata): le parole vanno nel riquadro, mai inviate da sole. */
    onVoiceStop: () -> Unit,
    onCancelDictation: () -> Unit,
    heardSound: Boolean,
    notice: String?,
    /** Sta ascoltando la musica per riconoscerla, non la voce. */
    music: Boolean,
    /** Il + toccato: il riquadro per scrivere è aperto. */
    composing: Boolean,
    onSend: () -> Unit,
    /** La freccia toccata col riquadro vuoto: detta a mani libere. */
    onMic: () -> Unit,
    /** Il riquadro non ha ancora testo (la freccia allora detta invece di inviare). */
    composeEmpty: Boolean,
) {
    val haptic = rememberHaptic()
    // Mentre si scrive il riquadro sale con la tastiera.
    val insets = if (composing) WindowInsets.navigationBars.union(WindowInsets.ime) else WindowInsets.navigationBars
    val barMode = when {
        listening || notice != null -> BAR_VOICE
        composing -> BAR_WRITE
        else -> BAR_PLUS
    }
    // Un tasto solo (1.636, Antonio): il + toccato apre il riquadro e diventa la freccia (aperto: invia), tenuto = vocale
    // (diventa il microfono), su = bloccato a mani libere, a sinistra = la graffetta diventa una ✕ rossiccia e butta l'audio.
    var voiceDrag by remember { mutableFloatStateOf(0f) }
    var voiceHeld by remember { mutableStateOf(false) }
    var voiceLift by remember { mutableFloatStateOf(0f) }
    val barOrigin = remember { FloatArray(2) }
    val plusMorph = remember { androidx.compose.animation.core.Animatable(0f) }
    val plusPop = remember { androidx.compose.animation.core.Animatable(1f) }
    val morphScope = androidx.compose.runtime.rememberCoroutineScope()
    // Chiudendo si fa il contrario (1.641, Antonio): la bolla rientra nella freccia e la freccia torna +. Dopo un invio
    // il + fa un piccolo salto con un colpetto: "salvato".
    val lastMode = remember { intArrayOf(barMode) }
    val justSent = remember { booleanArrayOf(false) }
    LaunchedEffect(barMode) {
        val was = lastMode[0]
        lastMode[0] = barMode
        if (barMode == BAR_PLUS && was == BAR_WRITE) {
            plusMorph.snapTo(1f)
            delay(140)
            plusMorph.animateTo(0f, tween(PLUS_MORPH_MS + 60))
            if (justSent[0]) {
                justSent[0] = false
                haptic(Haptic.TICK)
                plusPop.animateTo(1.18f, tween(110))
                plusPop.animateTo(1f, Motion.lively())
            }
        }
    }
    val density = LocalDensity.current
    val cancelAt = with(density) { VOICE_CANCEL_DP.dp.toPx() }
    val lockAt = with(density) { VOICE_LOCK_DP.dp.toPx() }
    val keyPx = with(density) { 64.dp.toPx() }
    val mode by rememberUpdatedState(barMode)
    val isListening by rememberUpdatedState(listening)
    val isMusic by rememberUpdatedState(music)
    val tap by rememberUpdatedState(onPlus)
    val holdStart by rememberUpdatedState(onHoldStart)
    val cancelVoice by rememberUpdatedState(onCancelDictation)
    val stopVoice by rememberUpdatedState(onVoiceStop)
    val send by rememberUpdatedState(onSend)
    val mic by rememberUpdatedState(onMic)
    val empty by rememberUpdatedState(composeEmpty)
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
            // Dove sta il riquadro sullo schermo: partito il vocale la tastiera si chiude e il riquadro scende sotto il
            // dito; i gesti si misurano sullo schermo, se no quel salto sembrava un "su" e lo bloccava subito (1.637).
            .onGloballyPositioned { val p = it.positionInWindow(); barOrigin[0] = p.x; barOrigin[1] = p.y }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x + barOrigin[0]
                    val startY = down.position.y + barOrigin[1]
                    // La freccia sta sempre nell'angolo in basso a destra, chiusa o dentro il riquadro.
                    val inCorner = down.position.x > size.width - keyPx && down.position.y > size.height - keyPx
                    val onArrow = (mode == BAR_PLUS || mode == BAR_WRITE) && inCorner
                    val onVoice = mode == BAR_VOICE && isListening && !isMusic
                    if (!onArrow && !onVoice) return@awaitEachGesture
                    var holding = false
                    if (onArrow) {
                        // Fino al tempo del tocco lungo: un tocco, oppure tenuta/trascinata = il vocale.
                        var outcome = PRESS_HOLD
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (outcome == PRESS_HOLD) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                outcome = when {
                                    change == null -> PRESS_CANCEL
                                    !change.pressed -> if (change.isConsumed) PRESS_CANCEL else PRESS_TAP
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop * 2.5f -> PRESS_DRAG
                                    else -> PRESS_HOLD
                                }
                            }
                        }
                        when {
                            isMusic || outcome == PRESS_CANCEL -> return@awaitEachGesture
                            outcome == PRESS_TAP -> {
                                haptic(Haptic.TICK)
                                when {
                                    // Prima il + ruota e diventa la freccia, poi la bolla le cresce attorno (1.639, Antonio).
                                    mode == BAR_PLUS -> morphScope.launch {
                                        plusMorph.animateTo(1f, tween(PLUS_MORPH_MS))
                                        tap()
                                        delay(400)
                                        plusMorph.snapTo(0f)
                                    }
                                    empty -> mic()
                                    else -> { justSent[0] = true; send() }
                                }
                                return@awaitEachGesture
                            }
                            !holdStart() -> return@awaitEachGesture
                            else -> { haptic(Haptic.CONFIRM); holding = true; voiceHeld = true }
                        }
                    }
                    // Il dito segue: su oltre la soglia si blocca, a sinistra oltre la soglia lasciando si butta.
                    var cancel = false
                    var locked = !holding
                    var moved = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x + barOrigin[0] - startX
                        val dy = change.position.y + barOrigin[1] - startY
                        if (!moved && kotlin.math.hypot(dx, dy) > viewConfiguration.touchSlop) moved = true
                        if (!moved) continue
                        if (!locked) {
                            voiceLift = (-dy / lockAt).coerceIn(0f, 1f)
                            if (VoiceSlide.locks(dy, lockAt)) {
                                locked = true
                                voiceHeld = false
                                voiceLift = 0f
                                haptic(Haptic.CONFIRM)
                            }
                        }
                        voiceDrag = dx.coerceAtMost(0f)
                        val now = VoiceSlide.cancels(dx, cancelAt)
                        if (now != cancel) {
                            cancel = now
                            haptic(if (now) Haptic.THRESHOLD else Haptic.TICK)
                        }
                        change.consume()
                    }
                    voiceDrag = 0f
                    voiceLift = 0f
                    voiceHeld = false
                    when {
                        cancel -> { haptic(Haptic.CONFIRM); cancelVoice() }
                        // Dito sollevato senza bloccare: il vocale finisce e le parole vanno nel riquadro.
                        holding && !locked -> { haptic(Haptic.TICK); stopVoice() }
                    }
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedContent(
            targetState = barMode,
            transitionSpec = {
                // Il riquadro nasce dal + e ci rientra: cresce verso sinistra e verso l'alto dall'angolo in basso a destra.
                // Dal + al riquadro la freccia resta dov'è: niente dissolvenza, la bolla le cresce attorno (il resto compare
                // da solo dentro il riquadro).
                val fromPlus = initialState == BAR_PLUS && targetState == BAR_WRITE
                val toPlus = initialState == BAR_WRITE && targetState == BAR_PLUS
                (if (fromPlus) EnterTransition.None togetherWith fadeOut(tween(60))
                else if (toPlus) EnterTransition.None togetherWith fadeOut(tween(140))
                else fadeIn(tween(160, delayMillis = 50)) togetherWith fadeOut(tween(90)))
                    .using(SizeTransform(clip = true) { _, _ -> Springs.snappy() })
            },
            contentAlignment = Alignment.BottomEnd,
            label = "plusMorph",
        ) { mode ->
            when (mode) {
                BAR_PLUS -> PlusButton(onPlus = onPlus, onHoldStart = onHoldStart, morph = plusMorph.value, pop = plusPop.value)
                else -> {
                    val shape = RoundedCornerShape(28.dp)
                    // Tirata giù la bolla si chiude e il testo resta come bozza (1.641, Antonio).
                    val pullDown = remember { androidx.compose.animation.core.Animatable(0f) }
                    val closeAt = with(density) { COMPOSER_PULL_DP.dp.toPx() }
                    val dragScope = androidx.compose.runtime.rememberCoroutineScope()
                    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { d ->
                        dragScope.launch { pullDown.snapTo((pullDown.value + d).coerceAtLeast(0f)) }
                    }
                    Surface(
                        shape = shape,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = pullDown.value
                                alpha = 1f - (pullDown.value / (closeAt * 2.5f)).coerceIn(0f, 0.5f)
                            }
                            .then(
                                if (mode == BAR_WRITE) Modifier.draggable(
                                    dragState,
                                    androidx.compose.foundation.gestures.Orientation.Vertical,
                                    onDragStopped = {
                                        if (pullDown.value >= closeAt) { haptic(Haptic.TICK); CaptureComposer.close() }
                                        pullDown.animateTo(0f, Motion.lively())
                                    },
                                ) else Modifier,
                            )
                            .glass(shape),
                    ) {
                        if (mode == BAR_WRITE) CaptureComposerInput(Modifier.fillMaxWidth(), onSend = onSend)
                        else VoiceBox(
                            listening = listening,
                            partial = partial,
                            level = level,
                            heardSound = heardSound || music,
                            notice = notice,
                            music = music,
                            onCancel = { haptic(Haptic.TICK); onCancelDictation() },
                            onStop = { haptic(Haptic.TICK); onVoiceStop() },
                            slide = (-voiceDrag / cancelAt).coerceIn(0f, 1f),
                            slidePx = voiceDrag,
                            held = voiceHeld,
                            lift = voiceLift,
                        )
                    }
                }
            }
        }
    }
}

/** Il + tondo, l'unico dell'app (1.636). I gesti li legge il riquadro sopra, che resta anche quando diventa il vocale. */
@Composable
private fun PlusButton(onPlus: () -> Unit, onHoldStart: () -> Boolean, morph: Float = 0f, pop: Float = 1f) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(COMPOSER_KEY_DP.dp)
            .graphicsLayer { scaleX = pop; scaleY = pop }
            // Il pallino delle cose salvate atterra sul + (le schede non ci sono più).
            .captureTarget(CaptureTargets.HOME_TAB)
            .captureTarget(CaptureTargets.MEDIA_TAB)
            .captureTarget(CaptureTargets.NEWS_TAB),
    ) {
        Surface(
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxSize().glass(CircleShape, MaterialTheme.colorScheme.primary)
                .semantics {
                    contentDescription = "Aggiungi"
                    onClick(label = "Aggiungi") { onPlus(); true }
                    onLongClick(label = "Detta a voce") { onHoldStart() }
                },
        ) {
            // Toccato: il + gira e sfuma nella freccia, che resta lì quando la bolla le cresce attorno.
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(24.dp).graphicsLayer {
                    rotationZ = 90f * morph
                    alpha = 1f - morph
                })
                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(24.dp).graphicsLayer {
                    rotationZ = -90f * (1f - morph)
                    alpha = morph
                })
            }
        }
    }
}

/**
 * Il riquadro del vocale, come nell'app Claude: sopra le parole capite finora (a capo, fino a cinque righe), sotto
 * a sinistra la graffetta che trascinando diventa una ✕ rossiccia (butta), l'onda, a destra la freccia: toccata ferma il vocale
 * e mette le parole nel riquadro, da inviare a mano (Antonio, 1.636: il vocale non parte mai da solo).
 */
@Composable
private fun VoiceBox(
    listening: Boolean,
    partial: String,
    level: Float,
    heardSound: Boolean,
    notice: String?,
    music: Boolean,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    /** Da 0 a 1: quanto il dito è andato a sinistra (1 = lasciando si butta). */
    slide: Float = 0f,
    slidePx: Float = 0f,
    /** Il dito è ancora sulla freccia (non bloccato). */
    held: Boolean = false,
    /** Da 0 a 1: quanto il dito è salito verso il blocco. */
    lift: Float = 0f,
) {
    // Dopo due secondi di solo silenzio: "Nessun suono rilevato" invece di "Ti ascolto…".
    var silent by remember { mutableStateOf(false) }
    // Da quanto stai registrando, accanto all'onda (1.641, Antonio).
    var seconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(listening) {
        seconds = 0
        while (listening) {
            delay(1_000)
            seconds += 1
        }
    }
    // Bloccato e zitto da qualche secondo (1.641, Antonio): si ferma da solo e il testo va nel riquadro.
    val stopNow by rememberUpdatedState(onStop)
    var quietSince by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(partial, held, listening, music) {
        quietSince = System.currentTimeMillis()
        if (!held && listening && !music && partial.isNotBlank()) {
            delay(com.platinum20.dashboard.capture.CaptureMarks.SILENCE_MS)
            if (com.platinum20.dashboard.capture.CaptureMarks.autoStop(true, partial, System.currentTimeMillis() - quietSince)) stopNow()
        }
    }
    LaunchedEffect(listening, heardSound) {
        silent = false
        if (listening && !heardSound) {
            delay(2_000)
            silent = true
        }
    }
    // Le misure del volume, la più nuova a destra: l'onda entra da destra e cresce verso sinistra (1.637).
    val bars = remember { mutableStateListOf<Float>() }
    val currentLevel by rememberUpdatedState(level)
    LaunchedEffect(listening) {
        bars.clear()
        while (listening) {
            bars.add(currentLevel)
            if (bars.size > BAR_MAX) bars.removeAt(0)
            delay(70)
        }
        bars.clear()
    }
    val shown = when {
        !listening && notice != null -> notice
        partial.isNotBlank() -> partial
        silent -> Dictation.NO_SOUND
        else -> "Ti ascolto…"
    }
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 10.dp)) {
        val quiet = shown == Dictation.NO_SOUND || shown == "Ti ascolto…" 
        // Gli aiuti dei gesti si muovono invece di scrivere (Antonio, 1.637, B): un'onda lenta che ripete la strada.
        val hintMove = androidx.compose.animation.core.rememberInfiniteTransition(label = "aiuti")
        val hintPhase by hintMove.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween<Float>(1_100), androidx.compose.animation.core.RepeatMode.Restart),
            label = "fase",
        )
        val showHints = held && listening && !music
        // Bloccato (1.639, Antonio: "mostrami che l'ho fatto"): il lucchetto si chiude, diventa blu pieno e resta lì.
        val locked = !held && listening && !music
        val lockPop by androidx.compose.animation.core.animateFloatAsState(
            if (locked) 1f else 0f,
            androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
            label = "lucchetto",
        )
        val showLock = showHints || locked
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
                color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = if (showLock) 52.dp else 12.dp, bottom = 12.dp)
                    .heightIn(min = if (showLock) 52.dp else 0.dp).animateContentSize(Springs.snappy()),
            )
            if (locked) Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 6.dp)
                    .width(COMPOSER_KEY_DP.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer { scaleX = 0.6f + 0.4f * lockPop; scaleY = 0.6f + 0.4f * lockPop; alpha = lockPop.coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Bloccato: puoi togliere il dito", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
                }
            }
            // Il lucchetto dentro il riquadro, subito sopra il microfono: la freccia sale piano e dice "tira su".
            if (showHints) Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(COMPOSER_KEY_DP.dp)
                    .graphicsLayer { translationY = -lift * 10.dp.toPx() },
            ) {
                val tint = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary, lift)
                Icon(Icons.Rounded.LockOpen, contentDescription = "Tira su per bloccare", tint = tint, modifier = Modifier.size(18.dp))
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp).graphicsLayer {
                        translationY = -4.dp.toPx() * hintPhase
                        alpha = 1f - 0.6f * hintPhase
                    },
                )
            }
        }
        if (listening) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Mentre registri la graffetta è una ✕ (Antonio, 1.637): trascinando a sinistra cresce e si fa rossiccia, lasciando
            // butta l'audio; un tocco butta.
            Box(Modifier.graphicsLayer { val sc = 1f + 0.2f * slide; scaleX = sc; scaleY = sc }) {
                ComposerKey(Icons.Rounded.Close, "Butta il vocale", danger = slide * 0.65f, onClick = onCancel)
            }
            if (!music) Text(
                VoiceSlide.clock(seconds),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
            val barColor = MaterialTheme.colorScheme.primary
            // Accanto alla ✕ tre ‹ che si accendono una dopo l'altra verso sinistra: "scorri per annullare". L'onda, che
            // entra da destra, quando si è parlato abbastanza ci arriva sopra e le copre.
            Box(Modifier.weight(1f).height(26.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (showHints) Row(
                    modifier = Modifier.fillMaxWidth().drawWithContent {
                        val cover = VoiceSlide.waveStart(size.width, bars.size, BAR_W_DP.dp.toPx(), BAR_GAP_DP.dp.toPx()) - BAR_GAP_DP.dp.toPx() * 2
                        if (cover > 0f) clipRect(right = cover) { this@drawWithContent.drawContent() }
                    },
                ) {
                    repeat(3) { i ->
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = if (i == 0) "Scorri a sinistra per annullare" else null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).graphicsLayer { alpha = VoiceSlide.chevronAlpha(i, hintPhase) },
                        )
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val w = BAR_W_DP.dp.toPx()
                    val gap = BAR_GAP_DP.dp.toPx()
                    val n = bars.size
                    bars.forEachIndexed { i, v ->
                        val x = size.width - (n - i) * (w + gap) + gap
                        if (x < 0f) return@forEachIndexed
                        val h = (size.height * (0.14f + 0.86f * v)).coerceAtMost(size.height)
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.35f + 0.65f * v),
                            topLeft = Offset(x, (size.height - h) / 2),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(w / 2, w / 2),
                        )
                    }
                }
            }
            // La canzone si riconosce da sola: niente freccia.
            if (!music) Box(
                Modifier.graphicsLayer {
                    // Sotto il dito: segue un poco a sinistra e sale verso il blocco.
                    translationX = slidePx * 0.25f
                    translationY = -lift * 14.dp.toPx()
                },
            ) {
                // Col dito sopra è il microfono (il + diventato vocale); bloccato è la freccia: toccata mette le parole nel testo.
                ComposerKey(if (held) Icons.Rounded.Mic else Icons.Rounded.ArrowUpward, "Metti nel testo", main = true, onClick = onStop)
            }
        }
    }
}

/** L'onda: barrette sottili, fino a riempire la riga. */
private const val BAR_MAX = 90

/** Quanto ci mette il + a diventare la freccia prima che si apra il riquadro. */
private const val PLUS_MORPH_MS = 170

/** Quanto tirare giù la bolla per chiuderla. */
private const val COMPOSER_PULL_DP = 70f
private const val BAR_W_DP = 3f
private const val BAR_GAP_DP = 3f

/** Quanto trascinare il dito sul vocale: a sinistra per buttarlo, in su per bloccarlo a mani libere. */
private const val VOICE_CANCEL_DP = 110f
private const val VOICE_LOCK_DP = 70f


// Cosa mostra il fondo dello schermo.
private const val BAR_PLUS = 0
private const val BAR_VOICE = 1
private const val BAR_WRITE = 2

// Come finisce la prima parte del gesto sul +.
private const val PRESS_HOLD = 0
private const val PRESS_TAP = 1
private const val PRESS_CANCEL = 2
private const val PRESS_DRAG = 3


// Molle delle transizioni: le pagine arrivano con un leggero rimbalzo invece di una curva fissa.
private val pageSpring = spring(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

// Dalla 1.634 non ci sono più schede: News e Media si aprono sopra la Home, come le altre pagine.
@Suppress("UnusedReceiverParameter")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean = false

/** Verso destra nella barra (Home → Note): la pagina nuova arriva da destra. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabForward(): Boolean =
    topDestinations.indexOfFirst { it.route == targetState.destination.route } >
        topDestinations.indexOfFirst { it.route == initialState.destination.route }

/** Passa a una sezione della barra in basso mantenendo lo stato delle altre. */
/** Il calendario non è più una scheda della barra: si apre sopra la Home. */
private fun NavController.openCalendar() {
    switchTab(Routes.HOME)
    navigate(Routes.NOTES) { launchSingleTop = true }
}

private fun NavController.switchTab(route: String) {
    // La Home è il fondo di tutto: ci si torna togliendo le pagine sopra.
    if (route == Routes.HOME) {
        popBackStack(graph.findStartDestination().id, inclusive = false)
        return
    }
    // News e Media (1.634): pagine sopra la Home, Indietro torna lì.
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

/** Solo per il giro fotografico (ScreenshotTour): apre una pagina profonda per rotta, senza toccare la UI. */
@androidx.annotation.VisibleForTesting
internal var tourNavigate: ((String) -> Unit)? = null

@Composable
private fun AppRoot(openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        tourNavigate = { navController.navigate(it) }
        onDispose { tourNavigate = null }
    }
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
            // Il calendario unico (con le note) si apre dalla Home: sopra la Home, "indietro" ci torna.
            openCalendar = { navController.openCalendar() },
            openEvent = { navController.navigate(Routes.calendarEvent(it.eventId, it.begin, it.end)) },
            newEvent = { navController.navigate(Routes.calendarEdit(null, it)) },
            // Un indirizzo che non c'è più (un link @ vecchio in una nota) non deve chiudere l'app.
            openRoute = { runCatching { navController.navigate(it) } },
            openTab = { navController.switchTab(it) },
        )
    }
    CompositionLocalProvider(
        LocalInAppNav provides inApp,
    ) {
        AppContent(navController, openRequest, onOpenHandled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppContent(navController: NavHostController, openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val context = LocalContext.current
    // Una nota fatta con "Salva nelle note" riapre la sua cosa (ferma, in attesa del play); le altre si scrivono.
    val openNote: (Long?) -> Unit = { id ->
        val saved = id?.let { NotesStore.data.value.notes.firstOrNull { n -> n.id == it } }?.let { SavedLinks.find(it.text) }
        if (saved != null) SavedLinks.open(context, saved) { navController.navigate(it) }
        else navController.navigate(Routes.noteEdit(id))
    }
    // Il microfono del widget Scrivi: si detta appena la barra c'è (startDictation è definito più sotto).
    var voiceRequested by remember { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        runCatching {
            navController.switchTab(
                when (request.target) {
                    // Il titolo delle notizie nel widget apre la scheda News (la pagina del punto del giorno non c'è più).
                    OpenRequest.RECAP -> Routes.NEWS
                    else -> Routes.HOME
                }
            )
            when (request.target) {
                OpenRequest.NOTES -> navController.navigate(Routes.NOTES) { launchSingleTop = true }
                OpenRequest.UPDATES -> {
                    // Le Impostazioni non sono più una scheda: Oggi → Impostazioni → Aggiornamenti.
                    navController.navigate(Routes.SETTINGS)
                    navController.navigate(Routes.UPDATES)
                }
                OpenRequest.WEATHER -> navController.navigate(Routes.WEATHER)
                OpenRequest.ITEM -> request.url.toLongOrNull()?.let { id ->
                    val kind = runCatching { ItemKind.valueOf(request.source) }.getOrDefault(ItemKind.TASK)
                    navController.navigate(Routes.noteItem(kind, id))
                }
                OpenRequest.EVENT -> request.url.split("/").mapNotNull { it.toLongOrNull() }.takeIf { it.size == 3 }?.let { (id, begin, end) ->
                    navController.navigate(Routes.calendarEvent(id, begin, end))
                }
                OpenRequest.SIM -> navController.navigate(Routes.SIM)
                OpenRequest.LATER -> navController.navigate(Routes.LATER_ARCHIVE)
                OpenRequest.NOTE -> request.url.toLongOrNull()?.let { openNote(it) }
                OpenRequest.WRITE -> CaptureComposer.open()
                OpenRequest.VOICE -> voiceRequested = true
                OpenRequest.RECAP -> Unit
                OpenRequest.ARTICLE -> if (request.url.isNotBlank()) {
                    navController.navigate(Routes.reader(request.url, request.source, request.title))
                }
                OpenRequest.MEDIA -> if (request.source == PlayerService.SOURCE_PLAYER || request.source == "RADIO") {
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
                        else -> navController.navigate(MediaLauncher.playVideo(context, Uri.parse(request.url)))
                    }
                }
            }
        }
        onOpenHandled()
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // Barra e + dove si sfoglia un elenco, non dentro una cosa sola: vedi Routes.hasBar.
    val showBottomBar = Routes.hasBar(currentRoute) && com.platinum20.dashboard.ui.screens.BarHide.count == 0
    val composing = CaptureComposer.isOpen
    val imeOpen = WindowInsets.isImeVisible
    // Uscendo dalle sezioni della barra (es. da una notifica) quello che si scriveva si chiude.
    LaunchedEffect(showBottomBar) { if (!showBottomBar) CaptureComposer.close() }
    // Pallino sulla scheda Note: promemoria scaduti o in scadenza oggi.
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsState()
    val notesProblem by NotesStore.readProblem.collectAsState()
    LaunchedEffect(notesProblem) {
        if (notesProblem != null) {
            CaptureFeedback.tell(context, "Non sono riuscito a leggere le note: ne ho salvato una copia, puoi ripristinare un backup")
            NotesStore.readProblemShown()
        }
    }
    // Il testo nelle foto delle note (2026-11-23, idea 30): letto con calma, qualche secondo dopo l'ultima modifica.
    LaunchedEffect(notes.notes) {
        kotlinx.coroutines.delay(4_000)
        com.platinum20.dashboard.notes.NotePhotoText.scan(context, notes.notes)
    }

    // Il + della barra: cattura veloce, oppure l'azione della sezione aperta (in Note, la scheda scelta).
    var capturing by remember { mutableStateOf(false) }
    // Il testo dettato tenendo premuto il +: il dialogo lo riceve e ne riconosce il tipo.
    var dictated by remember { mutableStateOf("") }
    val sendComposer = rememberComposerSend { dictated = it; capturing = true }
    val plusOverride by PlusAction.override
    if (capturing) CaptureDialog(initial = dictated, initialMode = CaptureMode.TASK, onClose = { capturing = false; dictated = "" })
    com.platinum20.dashboard.ui.screens.CaptureWhenPickers()
    // Tenendo premuto il + si detta, senza finestre: il microfono e il testo li disegna la barra.
    val dictation = remember { Dictation(context.applicationContext) }
    // ■ nel vocale: il testo resta da correggere invece di salvarsi.
    var voiceToEdit by remember { mutableStateOf(false) }
    val voiceScope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { dictation.cancel() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) CaptureFeedback.tell(context, "Ora tieni premuto il + e parla", Icons.Rounded.Mic)
    }
    SongId.init(context)
    // Un vocale senza parole ma con suono è musica: Canvas la riconosce, la fa partire su Spotify e la mette in "Sentite da Canvas".
    val identifySong: () -> Unit = {
        dictation.discard()
        dictation.clearNotice()
        voiceScope.launch {
            when (val outcome = SongId.identify(context)) {
                is SongId.Outcome.Failed -> dictation.show(outcome.message)
                is SongId.Outcome.Found -> {
                    // Come una nota: il fumetto con la canzone, poi il pallino vola alla scheda Media.
                    val song = outcome.song
                    val message = "${song.title} · ${song.artist}" + when {
                        outcome.inPlaylist -> " · in ${SongId.PLAYLIST_NAME}"
                        song.spotifyUri == null -> " · non è su Spotify"
                        else -> ""
                    }
                    CaptureFeedback.show(
                        CaptureNotice(CaptureMode.NOTE, message, undo = null, icon = Icons.Rounded.MusicNote, target = CaptureTargets.MEDIA_TAB),
                    )
                    return@launch
                }
            }
            delay(4_500)
            dictation.clearNotice()
        }
    }
    val startDictation: () -> Boolean = start@{
        when {
            !dictation.hasPermission() -> {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return@start false
            }
            !dictation.available -> {
                CaptureFeedback.tell(context, "Dettatura non disponibile su questo telefono", Icons.Rounded.MicOff)
                return@start false
            }
        }
        // Si stava già scrivendo nella barra: la voce completa quel testo invece di salvarsi da sola.
        val intoComposer = CaptureComposer.isOpen
        CaptureComposer.dictating = intoComposer
        voiceToEdit = false
        dictation.start { text ->
            // ■ toccato: come se si stesse scrivendo, le parole vanno nel riquadro da correggere.
            if (intoComposer || voiceToEdit) {
                voiceToEdit = false
                CaptureComposer.dictating = false
                if (text.isNullOrBlank()) voiceScope.launch {
                    delay(1_600)
                    dictation.clearNotice()
                } else CaptureComposer.append(text)
            }
            // Nessuna parola ma qualcosa si sentiva: forse è musica, Canvas prova a riconoscerla.
            else if (text.isNullOrBlank() && dictation.soundedLikeMusic() && SongId.ready) identifySong()
            // Niente di detto: "Nessun suono rilevato" resta un attimo nel fumetto.
            else if (text.isNullOrBlank()) voiceScope.launch {
                delay(1_600)
                dictation.clearNotice()
            } else voiceScope.launch {
                // Si salva da solo, nel tipo giusto; il dialogo solo se manca qualcosa.
                when (val outcome = VoiceCapture.save(context, text)) {
                    VoiceCapture.Outcome.NeedsDialog -> {
                        dictated = text
                        capturing = true
                    }
                    is VoiceCapture.Outcome.NeedsWhen -> com.platinum20.dashboard.ui.screens.CaptureWhenAsk.askDay(context, voiceScope, outcome.text)
                    is VoiceCapture.Outcome.Saved ->
                        CaptureFeedback.show(CaptureNotice(outcome.mode, outcome.message, outcome.undo))
                    is VoiceCapture.Outcome.Answered -> com.platinum20.dashboard.capture.CaptureAsk.show(context, outcome.answer)
                }
            }
        }
        true
    }
    // Dal widget Scrivi: si ascolta a mani libere (bloccato), si conferma con "Inserisci" come tenendo premuto.
    LaunchedEffect(voiceRequested) {
        if (!voiceRequested) return@LaunchedEffect
        voiceRequested = false
        delay(350)
        if (startDictation()) {
            delay(250)
            dictation.lock()
        }
    }

    // Le pagine sono la sorgente del vetro: barra in basso e mini player le sfocano sotto di sé.
    val hazeState = remember { HazeState() }
    val backdrop = remember { Backdrop() }
    // Tutto lo schermo sotto i fumetti (pagine + barra + mini player): i fumetti, la coda e le impostazioni del video
    // sfocano questo, non solo le pagine. Prima sopra il mini player o la barra il vetro non sfocava niente (1.608).
    val screenBackdrop = remember { Backdrop() }
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBackdrop provides backdrop,
        LocalOpenSearch provides { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
        LocalOpenSettings provides { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
    ) {
    // La conferma dei salvataggi vive sopra tutto, barra compresa: il pallino vola fino alle schede.
    LifecycleResumeEffect(Unit) {
        CaptureFeedback.active = true
        onPauseOrDispose { CaptureFeedback.active = false }
    }
    // Dove finisce la pagina (sopra la barra): lo legge il mini player, disegnato sopra lo Scaffold.
    var scaffoldPadding by remember { mutableStateOf(androidx.compose.foundation.layout.PaddingValues()) }
    Box(Modifier.fillMaxSize()) {
    // Solo pagine, barra e mini player: i livelli col vetro stanno fuori, se no il vetro disegnerebbe sé stesso (1.609 si chiudeva).
    Box(Modifier.fillMaxSize().then(if (android.os.Build.VERSION.SDK_INT >= 33) Modifier.backdropSource(screenBackdrop) else Modifier)) {
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                // La tastiera aperta per altro (cerca, modifica) nasconde la barra; se si scrive nella barra sale con lei.
                visible = showBottomBar && (composing || !imeOpen),
                enter = slideInVertically(pageSpring) { it } + fadeIn(),
                exit = slideOutVertically(tween(Motion.EXIT_MS, easing = Motion.Accelerate)) { it } + fadeOut(tween(Motion.EXIT_MS)),
            ) {
                CanvasNavBar(
                    onPlus = { plusOverride?.invoke() ?: CaptureComposer.open() },
                    listening = dictation.listening || SongId.listening,
                    level = if (SongId.listening) SongId.level else dictation.level,
                    partial = if (SongId.listening) "Ascolto la musica…" else dictation.partial,
                    music = SongId.listening,
                    onHoldStart = { startDictation().also { if (it) dictation.lock() } },
                    onVoiceStop = {
                        // Le parole vanno nel riquadro per scrivere invece di salvarsi da sole.
                        voiceToEdit = true
                        CaptureComposer.dictating = true
                        dictation.release()
                    },
                    // Buttato solo l'audio: il testo già scritto nel riquadro resta (Antonio, 1.636).
                    onCancelDictation = { voiceToEdit = false; CaptureComposer.dictating = false; dictation.discard() },
                    heardSound = dictation.heardSound,
                    notice = dictation.notice,
                    composing = composing,
                    onSend = sendComposer,
                    onMic = { if (startDictation()) dictation.lock() },
                    composeEmpty = CaptureComposer.text.text.isBlank(),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // Nelle sezioni della barra, trascinare giù dalla cima apre Cerca ovunque; dentro Musica,
        // Video e Radio e podcast parte già ristretta a quella fonte (al posto delle loro ricerche).
        val pageFilter = SearchFilter.forRoute(currentRoute)
        val pullSearch = rememberPullToSearch(enabled = topDestinations.any { it.route == currentRoute } || currentRoute == Routes.NOTES || pageFilter != null) {
            navController.navigate(Routes.search(pageFilter)) { launchSingleTop = true }
        }
        // Trascinare qualunque pagina chiude la tastiera (KeyboardDismiss.kt): sta fuori, vede ogni scorrimento.
        val hideKeyboard = com.platinum20.dashboard.ui.screens.rememberHideKeyboardOnDrag()
        Box(Modifier.fillMaxSize().then(hideKeyboard.modifier()).nestedScroll(pullSearch.connection)) {
        // Gli elementi condivisi fra le pagine (riga → scheda, card Calendario → pagina): vedi SharedItems.kt.
        androidx.compose.animation.SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            com.platinum20.dashboard.ui.screens.LocalSharedScope provides this,
            com.platinum20.dashboard.ui.screens.LocalBarSpace provides if (showBottomBar) padding.calculateBottomPadding() else 0.dp,
        ) {
        NavHost(
            modifier = Modifier.glassSource(hazeState, backdrop),
            navController = navController,
            startDestination = Routes.HOME,
            // Tra le schede la pagina scivola dal lato della sua scheda; i dettagli entrano da destra
            // e tornano indietro con lo stesso movimento al contrario (anche seguendo il gesto Indietro).
            enterTransition = { if (isTabSwitch()) Motion.tabEnter(tabForward()) else Motion.pushEnter },
            exitTransition = { if (isTabSwitch()) Motion.tabExit(tabForward()) else Motion.pushExit },
            popEnterTransition = { if (isTabSwitch()) Motion.tabEnter(tabForward()) else Motion.popEnter },
            popExitTransition = { if (isTabSwitch()) Motion.tabExit(tabForward()) else Motion.popExit },
        ) {
            composable(Routes.HOME) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                HomeScreen(
                    bottomPadding = padding,
                    onOpenNews = { navController.switchTab(Routes.NEWS) },
                    onOpenWeather = { navController.navigate(Routes.WEATHER) },
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                    onOpenNotes = { navController.openCalendar() },
                    onCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenAutoFill = { navController.navigate(Routes.AUTOFILL) },
                    onOpenNote = { openNote(it) },
                )
                }
            }
            // La pagina Media non c'è più (1.634): un vecchio collegamento (note, widget) torna in Home.
            composable(Routes.MEDIA) {
                LaunchedEffect(Unit) { navController.popBackStack(Routes.HOME, inclusive = false) }
            }
            // Dalla 1.437 la radio è una sezione da sola e i podcast stanno in Musica: la vecchia strada dei podcast apre Musica.
            composable(Routes.RADIO) {
                ListenScreen(onBack = { navController.popBackStack() })
            }
            // "I tuoi podcast" (1.481): una riga in fondo a Media.
            composable(Routes.PODCAST) {
                com.platinum20.dashboard.ui.screens.PodcastsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.SPOTIFY_ITEM,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("subtitle") { type = NavType.StringType; defaultValue = "" },
                    navArgument("image") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val args = entry.arguments
                val kind = runCatching { com.platinum20.dashboard.media.spotify.SpKind.valueOf(args?.getString("kind").orEmpty()) }
                    .getOrDefault(com.platinum20.dashboard.media.spotify.SpKind.ARTIST)
                val id = args?.getString("id").orEmpty()
                com.platinum20.dashboard.ui.screens.SpotifyItemScreen(
                    item = com.platinum20.dashboard.media.spotify.SpItem(
                        id, "spotify:${kind.name.lowercase()}:$id", kind,
                        args?.getString("title").orEmpty(), args?.getString("subtitle").orEmpty(), args?.getString("image").orEmpty(),
                    ),
                    onBack = { navController.popBackStack() },
                    onOpenItem = { navController.navigate(Routes.spotifyItem(it)) },
                )
            }
            composable(
                Routes.PLAYER,
                // Come il video: il player sale dal basso (dal mini player) e chiudendolo ci ridiscende.
                enterTransition = { slideInVertically(Motion.settle()) { it / 3 } + fadeIn(tween(Motion.ENTER_MS - 140)) },
                popExitTransition = {
                    if (com.platinum20.dashboard.ui.screens.MiniDock.docking) com.platinum20.dashboard.ui.screens.MiniDock.holdExit
                    else slideOutVertically(tween(Motion.ENTER_MS - 120, easing = Motion.Accelerate)) { it / 2 } + fadeOut(tween(Motion.ENTER_MS - 140))
                },
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChannel = { navController.navigate(Routes.youtubeChannel(it)) },
                )
            }
            composable(
                Routes.YOUTUBE_CHANNEL,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("avatar") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val args = entry.arguments
                com.platinum20.dashboard.ui.screens.YouTubeChannelScreen(
                    channel = com.platinum20.dashboard.media.youtube.YtChannel(
                        args?.getString("id").orEmpty(),
                        args?.getString("title").orEmpty(),
                        args?.getString("avatar").orEmpty(),
                    ),
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.youtubeWatch(it)) },
                    onOpenPlaylist = { navController.navigate(Routes.youtubePlaylist(it)) },
                )
            }
            composable(
                Routes.YOUTUBE_WATCH,
                arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }),
                // Il video sale dal basso; chiuso (trascinandolo giù o col tasto) vola nel mini player, col gesto indietro scende.
                enterTransition = { slideInVertically(Motion.settle()) { it / 3 } + fadeIn(tween(Motion.ENTER_MS - 140)) },
                popExitTransition = {
                    if (com.platinum20.dashboard.ui.screens.MiniDock.docking) com.platinum20.dashboard.ui.screens.MiniDock.holdExit
                    else slideOutVertically(tween(Motion.ENTER_MS - 120, easing = Motion.Accelerate)) { it / 2 } + fadeOut(tween(Motion.ENTER_MS - 140))
                },
            ) { entry ->
                // Lo stesso player unico: con un video mostra la pagina del video.
                PlayerScreen(
                    startUrl = entry.arguments?.getString("url"),
                    onBack = { navController.popBackStack() },
                    onOpenChannel = { navController.navigate(Routes.youtubeChannel(it)) },
                )
            }
            // Cronologia (1.481): una riga in fondo a Media, non più una scheda di Video.
            composable(Routes.YOUTUBE_FEED) {
                com.platinum20.dashboard.ui.screens.YouTubeHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.youtubeWatch(it)) },
                )
            }
            composable(
                Routes.YOUTUBE_PLAYLIST,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("count") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                val args = entry.arguments
                com.platinum20.dashboard.ui.screens.YouTubePlaylistScreen(
                    playlist = com.platinum20.dashboard.media.youtube.YtPlaylist(
                        args?.getString("id").orEmpty(),
                        args?.getString("title").orEmpty(),
                        "",
                        args?.getInt("count") ?: 0,
                    ),
                    onBack = { navController.popBackStack() },
                    onPlay = { navController.navigate(Routes.youtubeWatch(it)) },
                )
            }
            composable(Routes.MEDIA_MIX, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                com.platinum20.dashboard.ui.screens.MixScreen(
                    mixId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            // Le pagine di Video e Musica crescono dalla loro card in Home (2026-11-25).
            composable(Routes.MEDIA_NEWS) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                com.platinum20.dashboard.ui.screens.MediaNewsScreen(
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { navController.navigate(Routes.youtubeWatch(it)) },
                )
                }
            }
            composable(Routes.SAVED_MEDIA) { entry ->
                com.platinum20.dashboard.ui.screens.SavedMediaScreen(
                    video = entry.arguments?.getString("kind") == "video",
                    onBack = { navController.popBackStack() },
                    onOpenRoute = { navController.navigate(it) },
                )
            }
            composable(Routes.MUSIC_ALL) {
                com.platinum20.dashboard.ui.screens.MusicAllScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRoute = { navController.navigate(it) },
                )
            }
            composable(Routes.MUSIC_DISCOVER) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                com.platinum20.dashboard.ui.screens.MusicDiscoverScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Routes.spotifyItem(it)) },
                )
                }
            }
            composable(Routes.LIKED_TRACKS) {
                com.platinum20.dashboard.ui.screens.MediaLikedScreen(
                    videosOnly = false,
                    onBack = { navController.popBackStack() },
                    onPlayVideo = { navController.navigate(Routes.youtubeWatch(it)) },
                )
            }
            composable(
                Routes.VIDEO_PATTERN,
                arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                VideoPlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenYouTube = { navController.navigate(Routes.youtubeWatch(it)) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
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
            composable(
                Routes.SEARCH_PATTERN,
                arguments = listOf(navArgument("f") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                SearchScreen(
                    initialFilter = entry.arguments?.getString("f")
                        ?.let { f -> SearchFilter.entries.firstOrNull { it.name == f } } ?: SearchFilter.ALL,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { openNote(it) },
                    onOpenNotes = { navController.openCalendar() },
                    onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                    onWatch = { navController.navigate(Routes.youtubeWatch(it)) },
                )
            }
            composable(Routes.CUSTOMIZE) {
                CustomizeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTES) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                NotesScreen(
                    bottomPadding = padding,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { openNote(it) },
                    onOpenEvent = { navController.navigate(Routes.calendarEvent(it.eventId, it.begin, it.end)) },
                )
                }
            }
            composable(
                Routes.NOTE_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("find") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                // Il titolo della nota vola dalla sua riga (2026-11-24, proposta 5).
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                NoteEditorScreen(
                    noteId = entry.arguments?.getLong("id")?.takeIf { it >= 0 },
                    find = entry.arguments?.getString("find").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
                }
            }
            composable(
                Routes.NOTE_ITEM,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                ItemDetailScreen(
                    kind = ItemKind.entries.firstOrNull { it.name == entry.arguments?.getString("kind") } ?: ItemKind.TASK,
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onBack = { navController.popBackStack() },
                )
                }
            }
            // Le pagine delle card della Home (1.357): la card si allarga fino a diventare la pagina, come Calendario.
            composable(Routes.WEATHER) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                WeatherScreen(
                    onBack = { navController.popBackStack() },
                    onPickPlace = { navController.navigate(Routes.WEATHER_PLACE) },
                )
                }
            }
            composable(Routes.TAG, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                com.platinum20.dashboard.ui.screens.TagScreen(name = entry.arguments?.getString("name").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable(Routes.PERSON, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                com.platinum20.dashboard.ui.screens.PersonScreen(name = entry.arguments?.getString("name").orEmpty(), onBack = { navController.popBackStack() })
            }
            composable(Routes.LATER_ARCHIVE) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                com.platinum20.dashboard.ui.screens.LaterArchiveScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.JOTTINGS_ARCHIVE) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                com.platinum20.dashboard.ui.screens.LaterArchiveScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.SIM) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                SimScreen(onBack = { navController.popBackStack() }, bottomPadding = padding)
                }
            }
            composable(Routes.DOCUMENT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                com.platinum20.dashboard.ui.screens.DocumentScreen(id = entry.arguments?.getLong("id") ?: -1L, onBack = { navController.popBackStack() })
                }
            }
            composable(Routes.SHIFTS) {
                com.platinum20.dashboard.ui.screens.ShiftsLoginScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.WEATHER_PLACE) {
                PlacePickerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.NEWS) {
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                    NewsScreen(
                        bottomPadding = padding,
                        onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPage = { navController.navigate(Routes.settingsPage(it)) },
                    onOpenAutoFill = { navController.navigate(Routes.AUTOFILL) },
                    onOpenCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                    onPickPlace = { navController.navigate(Routes.WEATHER_PLACE) },
                )
            }
            composable(Routes.SETTINGS_PAGE, arguments = listOf(navArgument("page") { type = NavType.StringType })) { entry ->
                val page = SettingsPage.entries.firstOrNull { it.name == entry.arguments?.getString("page") } ?: SettingsPage.HOME
                SettingsPageScreen(
                    page = page,
                    onBack = { navController.popBackStack() },
                    onOpenUpdates = { navController.navigate(Routes.UPDATES) },
                    onCustomize = { navController.navigate(Routes.CUSTOMIZE) },
                    onLogin = { navController.navigate(Routes.login(it)) },
                    onPickPlace = { navController.navigate(Routes.WEATHER_PLACE) },
                    onOpenPage = { navController.navigate(Routes.settingsPage(it)) },
                )
            }
            composable(Routes.AUTOFILL) {
                AutoFillScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.MAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                MailScreen(id = entry.arguments?.getString("id").orEmpty(), onBack = { navController.popBackStack() })
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
                CompositionLocalProvider(com.platinum20.dashboard.ui.screens.LocalRouteScope provides this) {
                    ReaderScreen(
                        sourceName = source,
                        fallbackTitle = entry.arguments?.getString("title").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenLink = { navController.navigate(Routes.reader(it, source)) },
                        onOpenArticle = { navController.navigate(Routes.reader(it.link, it.source.name, it.title)) },
                        // "Prossima nuova" prende il posto dell'articolo letto: indietro torna alla lista.
                        onOpenNext = {
                            navController.navigate(Routes.reader(it.link, it.source.name, it.title)) {
                                popUpTo(Routes.READER) { inclusive = true }
                            }
                        },
                    )
                }
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
        }
        }
        // Il mini player si disegna fuori dalla pagina (sotto): qui serve solo sapere dove sta la barra.
        androidx.compose.runtime.SideEffect { scaffoldPadding = padding }
        PullToSearchIndicator(pullSearch)
        // Sotto la barra, che intanto è diventata il campo per scrivere.
        CaptureComposerVeil()
        }
    }
    // Mini player sopra tutte le schermate e sopra la barra: portato giù per chiuderlo le passa davanti (1.517: fuori dallo Scaffold, che disegna la barra sopra il suo contenuto).
    // Fuori dallo Scaffold manca il suo colore del testo: senza, il titolo veniva scuro su scuro (1.521).
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    // Sulle pagine con i tasti in fondo (note, dettagli) il mini player si aggancia sopra di loro: i tasti restano fermi (1.549).
    val miniDensity = androidx.compose.ui.platform.LocalDensity.current
    val miniLift by androidx.compose.animation.core.animateDpAsState(
        with(miniDensity) {
            com.platinum20.dashboard.ui.screens.MiniDock.lift(
                com.platinum20.dashboard.ui.screens.MiniDock.floor(com.platinum20.dashboard.ui.screens.MiniDock.floors.values),
                scaffoldPadding.calculateBottomPadding().toPx(),
            ).toDp()
        },
        com.platinum20.dashboard.ui.screens.Motion.settle(),
        label = "miniLift",
    )
    FloatingPlayer(
        currentRoute = currentRoute,
        onExpand = { playing ->
            // Quello che suona al momento del tocco, non quello di quando la barra è comparsa.
            val current = com.platinum20.dashboard.media.Playback.now.value ?: playing
            navController.navigate(if (current.isVideo) Routes.youtubeWatch() else Routes.PLAYER) { launchSingleTop = true }
        },
        modifier = Modifier.padding(scaffoldPadding).padding(bottom = miniLift),
    )
    }
    }
    CompositionLocalProvider(LocalBackdrop provides screenBackdrop) {
    // La coda per prima: sul player della musica spunta sempre dal bordo, e i fumetti devono starle sopra.
    com.platinum20.dashboard.ui.screens.QueueLayer(
        drawer = currentRoute == Routes.PLAYER && com.platinum20.dashboard.ui.screens.QueueOpen.peek,
    )
    SaveToNotesLayer()
    com.platinum20.dashboard.ui.screens.VideoSettingsLayer()
    ActionBubbleLayer()
    com.platinum20.dashboard.ui.screens.PlaylistNameLayer()
    WhatsNewLayer()
    CaptureFeedbackLayer(onNotes = currentRoute == Routes.NOTES)
    }
    }
    }
}
