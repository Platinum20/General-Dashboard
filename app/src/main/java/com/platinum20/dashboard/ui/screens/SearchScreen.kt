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

private val eventHour = DateTimeFormatter.ofPattern("HH:mm")

/** I filtri di Cerca ovunque: Tutto, oppure una sola parte dell'app. */
private enum class SearchFilter(val label: String) {
    ALL("Tutto"), NOTES("Note"), AGENDA("Agenda"), NEWS("Notizie"),
    MUSIC("Musica"), VIDEO("Video"), RADIO("Radio"), PODCAST("Podcast"),
}

/** Quello che si trova in rete per la ricerca: Spotify, YouTube, radio e podcast. */
private data class MediaHits(
    val query: String = "",
    val loading: Boolean = false,
    val spotify: List<SpItem> = emptyList(),
    val videos: List<YtVideo> = emptyList(),
    val radio: List<RadioStation> = emptyList(),
)

/**
 * Cerca ovunque, trascinando giù dalla cima di una sezione: tutto quello che sta in Note
 * (promemoria, note, spese, pacchi, date, abitudini), gli impegni del calendario di un mese
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
    var events by remember { mutableStateOf<Map<LocalDate, List<CalendarEvent>>>(emptyMap()) }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
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
            media = MediaHits(q, false, spotify.await(), videos.await().take(MAX_ONLINE), radio.await().take(MAX_ONLINE))
            expanded = emptySet()
        }
    }
    fun shows(f: SearchFilter) = filter == SearchFilter.ALL || filter == f

    Scaffold(topBar = { TopLevelBar(title = "Cerca", onBack = onBack) }) { inner ->
        NotesSearchPanel(
            data = data,
            padding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            onClose = onBack,
            onOpenNote = onOpenNote,
            onOpenExpenses = { inApp?.openRoute?.invoke(com.platinum20.dashboard.Routes.EXPENSES) },
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
                    SearchFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { haptic(Haptic.TICK); filter = f },
                            label = { Text(f.label) },
                        )
                    }
                }
            },
            extra = { q ->
                var count = 0
                val foundEvents = if (shows(SearchFilter.AGENDA)) EverywhereSearch.events(events, q, today) else emptyList()
                val foundNews = if (shows(SearchFilter.NEWS)) EverywhereSearch.news(news?.items.orEmpty(), q) else emptyList()
                if (foundEvents.isNotEmpty()) item(key = "events") {
                    SourceSection("Agenda", foundEvents, "events" in expanded, { expanded = expanded + "events" }) { (day, e) ->
                        val time = if (e.allDay) "" else " · " + Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(eventHour)
                        ResultRow(e.title, longDate(day) + time) { inApp?.openEvent?.invoke(e) }
                    }
                }
                if (foundNews.isNotEmpty()) item(key = "news") {
                    SourceSection("Notizie", foundNews, "news" in expanded, { expanded = expanded + "news" }) { n ->
                        val whenText = n.publishedAt?.let { " · " + relativeTime(it) }.orEmpty()
                        ResultRow(n.title, n.source.name + whenText, onLongClick = { SavedLinks.offer(n.savedLink()) }) { onOpenArticle(n) }
                    }
                }
                count += foundEvents.size + foundNews.size
                // In rete: valgono solo se sono di questa ricerca (si scrive più in fretta di quanto rispondano).
                val fresh = media.query == q.trim()
                val music = if (fresh && shows(SearchFilter.MUSIC)) media.spotify.filter { it.kind != SpKind.SHOW && it.kind != SpKind.EPISODE } else emptyList()
                val videos = if (fresh && shows(SearchFilter.VIDEO)) media.videos else emptyList()
                val radio = if (fresh && shows(SearchFilter.RADIO)) media.radio else emptyList()
                val pods = if (shows(SearchFilter.PODCAST) && q.trim().length >= 2) podcasts.data.orEmpty().take(MAX_ONLINE) else emptyList()
                if (music.isNotEmpty()) item(key = "music") {
                    SourceSection("Spotify", music, "music" in expanded, { expanded = expanded + "music" }) { item ->
                        ResultRow(item.title, kindLabel(item.kind) + item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), onLongClick = SavedLinks.spotify(item)?.let { l -> { SavedLinks.offer(l) } }) {
                            SpotifyPlayer.play(context, item)
                            onOpenPlayer()
                        }
                    }
                }
                if (videos.isNotEmpty()) item(key = "videos") {
                    SourceSection("YouTube", videos, "videos" in expanded, { expanded = expanded + "videos" }) { v ->
                        ResultRow(v.title, v.channel, onLongClick = { SavedLinks.offer(SavedLinks.youtube(v)) }) { onWatch(v.watchUrl) }
                    }
                }
                if (radio.isNotEmpty()) item(key = "radio") {
                    SourceSection("Radio", radio, "radio" in expanded, { expanded = expanded + "radio" }) { station ->
                        ResultRow(station.name, listOf(station.country, station.tags.split(',').firstOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), onLongClick = { SavedLinks.offer(SavedLinks.radio(station)) }) {
                            RadioStore.play(station)
                            onOpenPlayer()
                        }
                    }
                }
                if (pods.isNotEmpty()) item(key = "podcasts") {
                    SourceSection("Podcast", pods, "podcasts" in expanded, { expanded = expanded + "podcasts" }) { podcast ->
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
                val online = music.size + videos.size + radio.size + pods.size
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
private fun <T> SourceSection(title: String, items: List<T>, expanded: Boolean, onExpand: () -> Unit, row: @Composable (T) -> Unit) {
    Section(title = "$title · ${items.size}") {
        val shown = if (expanded) items else items.take(PER_SOURCE)
        shown.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            row(item)
        }
        if (!expanded && items.size > PER_SOURCE) {
            RowDivider()
            TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
                Text("Mostra altri ${items.size - PER_SOURCE}")
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
