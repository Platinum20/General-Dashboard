package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.platinum20.dashboard.capture.SavedLinks
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioApi
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.player.YtResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.search.EverywhereSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.search.SearchRecents
import com.platinum20.dashboard.search.PersonHit
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.search.SearchPeople
import com.platinum20.dashboard.media.youtube.YtRatings
import com.platinum20.dashboard.media.youtube.YouTubeStore
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val eventHour = DateTimeFormatter.ofPattern("HH:mm")

/** I filtri di Cerca ovunque: Tutto, oppure una sola parte dell'app. */
enum class SearchFilter(val label: String) {
    // Dalla 1.357 note ed eventi sono un filtro solo, "Calendario", come il calendario unico. AGENDA resta per le
    // ricerche salvate nelle rotte, ma non ha più il suo chip.
    ALL("Tutto"), NOTES("Calendario"), AGENDA("Calendario"), NEWS("News"),
    // Dalla 1.437 i podcast stanno con la Musica, come in Media; Radio è solo radio.
    MUSIC("Musica"), VIDEO("Video"), RADIO("Radio");

    companion object {
        /** Il filtro già scelto quando si tira giù dentro una pagina di Media (es. "media/spotify" → Musica). */
        fun forRoute(route: String?): SearchFilter? = when {
            route == null -> null
            route.startsWith("media/spotify") || route.startsWith("media/podcast") -> MUSIC
            route.startsWith("media/youtube") || route.startsWith("media/video") -> VIDEO
            route.startsWith("media/radio") -> RADIO
            else -> null
        }
    }
}

/** Quello che si trova in rete per la ricerca: Spotify, YouTube, radio e podcast. */
private data class MediaHits(
    val query: String = "",
    val loading: Boolean = false,
    val spotify: List<SpItem> = emptyList(),
    val videos: List<YtVideo> = emptyList(),
    val radio: List<RadioStation> = emptyList(),
    val channels: List<Pair<com.platinum20.dashboard.media.youtube.YtChannel, Long>> = emptyList(),
)

/**
 * Cerca ovunque, trascinando giù dalla cima di una sezione: tutto quello che sta in Note
 * (promemoria, note, pacchi, date, abitudini), gli impegni del calendario di un mese
 * indietro e tre avanti, le notizie già scaricate e, in rete, brani e playlist di Spotify,
 * video di YouTube, radio e podcast. Toccando un brano, una radio o un podcast parte nel player
 * dell'app; i chip in alto restringono a una parte sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    /** Qualcosa è partito nel player condiviso: si apre il player. */
    onOpenPlayer: () -> Unit = {},
    onWatch: (String) -> Unit = {},
    /** Aperta da una pagina di Media: parte già ristretta a quella fonte. */
    initialFilter: SearchFilter = SearchFilter.ALL,
) {
    val context = LocalContext.current
    NotesStore.init(context)
    SpotifyAuth.init(context)
    RadioStore.init(context)
    PodcastStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val news by NewsStore.result.collectAsStateWithLifecycle()
    val podcasts by PodcastStore.search.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    SearchRecents.init(context)
    val recents by SearchRecents.list.collectAsStateWithLifecycle()
    var events by remember { mutableStateOf<Map<LocalDate, List<CalendarEvent>>>(emptyMap()) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var query by remember { mutableStateOf("") }
    var media by remember { mutableStateOf(MediaHits()) }
    // Le fonti aperte con "Mostra altri": le altre restano corte.
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        if (news == null) NewsStore.restore()
        events = CalendarRepository.eventsFrom(context, today.minusDays(30), 120)
    }
    // In rete si cerca quando si smette di scrivere, tutto insieme: ognuno risponde per conto suo.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            media = MediaHits()
            PodcastStore.clearSearch()
            return@LaunchedEffect
        }
        delay(450)
        media = media.copy(query = q, loading = true)
        PodcastStore.search(q)
        coroutineScope {
            val spotify = async { runCatching { SpotifyAuth.token()?.let { SpotifyApi.search(it, q) } }.getOrNull().orEmpty() }
            val videos = async { runCatching { YtResolver.search(q) }.getOrDefault(emptyList()) }
            val radio = async { runCatching { RadioApi.search(q) }.getOrDefault(emptyList()) }
            val channels = async { runCatching { YtResolver.searchChannels(q) }.getOrDefault(emptyList()) }
            media = MediaHits(q, false, spotify.await(), videos.await().take(MAX_ONLINE), radio.await().take(MAX_ONLINE), channels.await().take(MAX_ONLINE))
            expanded = emptySet()
        }
    }
    // Uscendo da Cerca l'ultima ricerca diventa una "recente".
    val lastQuery by rememberUpdatedState(query)
    DisposableEffect(Unit) { onDispose { SearchRecents.keep(lastQuery) } }
    fun shows(f: SearchFilter) = filter == SearchFilter.ALL || filter == f ||
        (f == SearchFilter.AGENDA && filter == SearchFilter.NOTES) || (f == SearchFilter.NOTES && filter == SearchFilter.AGENDA)

    Scaffold(topBar = { TopLevelBar(title = "Cerca", onBack = onBack) }) { inner ->
        NotesSearchPanel(
            data = data,
            padding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp + miniPlayerRoom(),
            ),
            onClose = onBack,
            onOpenNote = onOpenNote,
            onOpenParcels = { onOpenNotes() },
            placeholder = "Cerca…",
            showNotes = shows(SearchFilter.NOTES),
            perGroup = PER_SOURCE,
            onQuery = { query = it },
            header = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // I chip di Canvas, come nel Calendario e in YouTube (1.357).
                    SearchFilter.entries.filter { it != SearchFilter.AGENDA }.forEach { f ->
                        CanvasTabChip(
                            selected = filter == f || (f == SearchFilter.NOTES && filter == SearchFilter.AGENDA),
                            onClick = { haptic(Haptic.TICK); filter = f },
                            label = f.label,
                        )
                    }
                }
            },
            empty = { setQuery ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (recents.isNotEmpty()) Section(title = "Recenti") {
                        recents.forEachIndexed { i, r ->
                            HintRow(Icons.Outlined.History, r, onLongClick = {
                                ActionBubble.show(BubbleAction(Icons.Outlined.Delete, "Togli dalle recenti", danger = true) { SearchRecents.forget(r) })
                            }) { haptic(Haptic.TICK); setQuery(r) }
                        }
                    }
                    // Cosa si può cercare, con un tocco: insegna senza spiegare.
                    Section(title = "Prova") {
                        HintRow(Icons.Outlined.Work, "Turni") {
                            haptic(Haptic.TICK); filter = SearchFilter.NOTES; setQuery("Turno")
                        }
                    }
                }
            },
            extra = { q ->
                var count = 0
                val foundEvents = if (shows(SearchFilter.AGENDA)) EverywhereSearch.events(events, q, today) else emptyList()
                val foundNews = if (shows(SearchFilter.NEWS)) EverywhereSearch.news(news?.items.orEmpty(), q) else emptyList()
                if (foundEvents.isNotEmpty()) item(key = "events") {
                    SourceSection("Calendario", foundEvents, "events" in expanded, { expanded = SearchPeople.toggled(expanded, "events") }, Modifier.animateItem()) { (day, e) ->
                        val time = if (e.allDay) "" else " · " + Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(eventHour)
                        ResultRow(e.title, longDate(day) + time) { inApp?.openEvent?.invoke(e) }
                    }
                }
                if (foundNews.isNotEmpty()) item(key = "news") {
                    SourceSection("News", foundNews, "news" in expanded, { expanded = SearchPeople.toggled(expanded, "news") }, Modifier.animateItem()) { n ->
                        val whenText = n.publishedAt?.let { " · " + relativeTime(it) }.orEmpty()
                        ResultRow(n.title, n.source.name + whenText, onLongClick = { SavedLinks.offer(n.savedLink()) }) { onOpenArticle(n) }
                    }
                }
                count += foundEvents.size + foundNews.size
                // In rete: valgono solo se sono di questa ricerca (si scrive più in fretta di quanto rispondano).
                val fresh = media.query == q.trim()
                // Artisti e canali hanno il loro gruppo, in cima (1.518): qui restano brani, album e playlist.
                val people = if (fresh) SearchPeople.hits(media.spotify, media.channels, shows(SearchFilter.MUSIC), shows(SearchFilter.VIDEO)) else emptyList()
                val music = if (fresh && shows(SearchFilter.MUSIC)) media.spotify.filter { it.kind != SpKind.SHOW && it.kind != SpKind.EPISODE && it.kind != SpKind.ARTIST } else emptyList()
                val videos = if (fresh && shows(SearchFilter.VIDEO)) media.videos else emptyList()
                val radio = if (fresh && shows(SearchFilter.RADIO)) media.radio else emptyList()
                val pods = if (shows(SearchFilter.MUSIC) && q.trim().length >= 2) podcasts.data.orEmpty().take(MAX_ONLINE) else emptyList()
                if (people.isNotEmpty()) item(key = "people") {
                    SourceSection("Artisti e canali", people, "people" in expanded, { expanded = SearchPeople.toggled(expanded, "people") }, Modifier.animateItem()) { hit ->
                        when (hit) {
                            is PersonHit.Artist -> {
                                val open = { inApp?.openRoute?.invoke(com.platinum20.dashboard.Routes.spotifyItem(hit.item)); Unit }
                                ResultRow(hit.title, "Artista", onLongClick = SavedLinks.spotify(hit.item)?.let { l -> { SavedLinks.offer(l) } }, onClick = open)
                            }
                            is PersonHit.Channel -> {
                                val open = { inApp?.openRoute?.invoke(com.platinum20.dashboard.Routes.youtubeChannel(hit.channel)); Unit }
                                ResultRow(hit.title, SearchPeople.channelLine(hit.subscribers), onLongClick = {
                                    // Le stesse azioni della pagina del canale: aprirlo e l'iscrizione.
                                    val now = YtRatings.isSubscribed(hit.channel.id, YtRatings.subscribed.value, YouTubeStore.channels.value.data)
                                    ActionBubble.show(
                                        BubbleAction(Icons.Outlined.AccountCircle, "Apri il canale", onClick = open),
                                        BubbleAction(
                                            if (now) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                                            if (now) "Annulla l'iscrizione" else "Iscriviti",
                                            danger = now,
                                        ) {
                                            val ok = YtRatings.toggleSubscribe(context, hit.channel.id, now) {
                                                CaptureFeedback.tell(context, "YouTube non ha preso l'iscrizione, riprova")
                                            }
                                            if (!ok) CaptureFeedback.tell(context, "Accedi a YouTube in Impostazioni › Account")
                                        },
                                    )
                                }, onClick = open)
                            }
                        }
                    }
                }
                if (music.isNotEmpty()) item(key = "music") {
                    SourceSection("Spotify", music, "music" in expanded, { expanded = SearchPeople.toggled(expanded, "music") }, Modifier.animateItem()) { item ->
                        ResultRow(item.title, kindLabel(item.kind) + item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), onLongClick = SavedLinks.spotify(item)?.let { l -> { SavedLinks.offer(l) } }) {
                            // Artisti, album e playlist si aprono nella loro pagina (1.481); un brano parte.
                            if (item.kind == SpKind.ARTIST || item.kind == SpKind.ALBUM || item.kind == SpKind.PLAYLIST) {
                                inApp?.openRoute?.invoke(com.platinum20.dashboard.Routes.spotifyItem(item))
                            } else {
                                SpotifyPlayer.play(context, item)
                                onOpenPlayer()
                            }
                        }
                    }
                }
                if (videos.isNotEmpty()) item(key = "videos") {
                    SourceSection("YouTube", videos, "videos" in expanded, { expanded = SearchPeople.toggled(expanded, "videos") }, Modifier.animateItem()) { v ->
                        ResultRow(v.title, v.channel, onLongClick = { SavedLinks.offer(SavedLinks.youtube(v)) }) { onWatch(v.watchUrl) }
                    }
                }
                if (radio.isNotEmpty()) item(key = "radio") {
                    SourceSection("Radio", radio, "radio" in expanded, { expanded = SearchPeople.toggled(expanded, "radio") }, Modifier.animateItem()) { station ->
                        ResultRow(station.name, com.platinum20.dashboard.media.radio.RadioGenre.of(station.tags) ?: "Radio", onLongClick = { SavedLinks.offer(SavedLinks.radio(station)) }) {
                            RadioStore.play(station)
                            onOpenPlayer()
                        }
                    }
                }
                if (pods.isNotEmpty()) item(key = "podcasts") {
                    SourceSection("Podcast", pods, "podcasts" in expanded, { expanded = SearchPeople.toggled(expanded, "podcasts") }, Modifier.animateItem()) { podcast ->
                        ResultRow(podcast.title, "Ultimo episodio · ${podcast.author}") {
                            // Parte l'episodio più recente: gli altri si trovano in Media › Podcast.
                            scope.launch {
                                PodcastStore.loadEpisodes(podcast)
                                val episodes = withTimeoutOrNull(15_000) {
                                    PodcastStore.episodes.first { it[podcast.feedUrl]?.let { s -> !s.loading } == true }
                                }?.get(podcast.feedUrl)?.data
                                val latest = episodes?.maxByOrNull { it.publishedAt }
                                if (latest != null) {
                                    PodcastStore.play(latest)
                                    onOpenPlayer()
                                }
                            }
                        }
                    }
                }
                val online = people.size + music.size + videos.size + radio.size + pods.size
                if (media.loading && online == 0) item(key = "loading") {
                    Text(
                        "Cerco anche su Spotify, YouTube, radio e podcast…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                count + online + if (media.loading) 1 else 0
            },
        )
    }
}

private fun kindLabel(kind: SpKind) = when (kind) {
    SpKind.TRACK -> "Brano"
    SpKind.ALBUM -> "Album"
    SpKind.PLAYLIST -> "Playlist"
    SpKind.ARTIST -> "Artista"
    SpKind.SHOW -> "Podcast"
    SpKind.EPISODE -> "Episodio"
}

/** Quanti risultati si scaricano al massimo per fonte in rete. */
private const val MAX_ONLINE = 20

/** Quanti se ne vedono per fonte prima di "Mostra altri": la pagina resta corta e si vede ogni fonte. */
private const val PER_SOURCE = 4

/** Una fonte di risultati: i primi [PER_SOURCE] e, se ce ne sono altri, "Mostra altri" per vederli tutti. */
@Composable
private fun <T> SourceSection(
    title: String,
    items: List<T>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    // Senza divisori (1.452): le righe hanno già il loro respiro, una linea in più spezzava la lista.
    Section(title = "$title · ${items.size}", modifier = modifier) {
        val shown = if (expanded) items else items.take(PER_SOURCE)
        shown.forEach { item -> row(item) }
        // Aperta si richiude con lo stesso pulsante (1.518): prima restava aperta per sempre.
        if (items.size > PER_SOURCE) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Mostra meno" else "Mostra altri ${items.size - PER_SOURCE}")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(title: String, detail: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Una riga corta sotto il campo vuoto: una ricerca recente o una scorciatoia. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HintRow(icon: ImageVector, title: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
