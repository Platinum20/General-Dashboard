package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.docs.DocumentsStore
import com.platinum20.dashboard.mail.MailStore
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioApi
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtRatings
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.net.Online
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.notes.NoteSmart
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.SearchKind
import com.platinum20.dashboard.search.EverywhereSearch
import com.platinum20.dashboard.search.PersonHit
import com.platinum20.dashboard.search.SearchMatch
import com.platinum20.dashboard.search.SearchPeople
import com.platinum20.dashboard.search.SearchRecents
import com.platinum20.dashboard.search.SearchSources
import com.platinum20.dashboard.search.SearchWhen
import com.platinum20.dashboard.shifts.ShiftStore
import com.platinum20.dashboard.tickets.TicketQrStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate

/** I filtri di Cerca ovunque: Tutto, oppure una sola parte dell'app. */
enum class SearchFilter(val label: String) {
    // Dalla 1.357 note ed eventi sono un filtro solo, "Calendario", come il calendario unico. AGENDA resta per le
    // ricerche salvate nelle rotte, ma non ha più il suo chip.
    ALL("Tutto"), NOTES("Calendario"), AGENDA("Calendario"), NEWS("News"),
    // Dalla 1.437 i podcast stanno con la Musica, come in Media; Radio è solo radio.
    MUSIC("Musica"), VIDEO("Video"), RADIO("Radio");

    /** L'icona del chip: scelto mostra anche il nome, gli altri solo questa (punto 10). */
    val icon: ImageVector get() = when (this) {
        ALL -> Icons.Outlined.Apps
        NOTES, AGENDA -> Icons.Outlined.CalendarMonth
        NEWS -> Icons.Outlined.Newspaper
        MUSIC -> Icons.Outlined.MusicNote
        VIDEO -> Icons.Outlined.SmartDisplay
        RADIO -> Icons.Outlined.Radio
    }

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

/** Quello che si trova in rete per la ricerca: Spotify, YouTube, radio e podcast. [failed] = chi non ha risposto. */
internal data class MediaHits(
    val query: String = "",
    val loading: Boolean = false,
    val spotify: List<SpItem> = emptyList(),
    val videos: List<YtVideo> = emptyList(),
    val radio: List<RadioStation> = emptyList(),
    val channels: List<Pair<YtChannel, Long>> = emptyList(),
    val failed: List<String> = emptyList(),
    val attempt: Int = 0,
)

/**
 * Quello che Cerca ha già trovato, finché l'app è aperta (2026-11-26, punto 16): aperto un risultato e tornati
 * indietro, la ricerca, i risultati in rete e il punto della lista sono ancora lì, senza ricaricare. Anche il + lo usa.
 */
internal object SearchCache {
    var media = MediaHits()
    var events: Map<LocalDate, List<CalendarEvent>> = emptyMap()
    var eventsDay: LocalDate? = null

    /** Tutto il calendario (punto 5): tre anni indietro e due avanti, letto una volta al giorno. */
    suspend fun events(context: android.content.Context, today: LocalDate): Map<LocalDate, List<CalendarEvent>> {
        if (eventsDay != today) {
            val from = today.minusYears(3)
            events = CalendarRepository.eventsFrom(context, from, java.time.temporal.ChronoUnit.DAYS.between(from, today.plusYears(2)).toInt())
            eventsDay = today
        }
        return events
    }
}

/** Quanti risultati si scaricano al massimo per fonte in rete. */
private const val MAX_ONLINE = 20

/**
 * Cerca ovunque, trascinando giù dalla cima di una pagina: il risultato migliore in cima, poi le tue cose (calendario,
 * note, posta, documenti, impostazioni, la tua musica e i tuoi video) e dopo quello che si trova in rete (Spotify,
 * YouTube, radio, podcast). I chip in alto restringono a una parte sola. Rifatta il 2026-11-26 (Antonio, "la ricerca
 * nella sua interezza").
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
    /** Aperta dal + con "Tutti i risultati" (idea 11): la parola è già scritta. */
    initialQuery: String = "",
) {
    val context = LocalContext.current
    NotesStore.init(context)
    SpotifyAuth.init(context)
    RadioStore.init(context)
    PodcastStore.init(context)
    SearchRecents.init(context)
    Online.init(context)
    MailStore.init(context)
    DocumentsStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val news by NewsStore.result.collectAsStateWithLifecycle()
    val podcasts by PodcastStore.search.collectAsStateWithLifecycle()
    val recents by SearchRecents.list.collectAsStateWithLifecycle()
    val opened by SearchRecents.opened.collectAsStateWithLifecycle()
    val online by Online.online.collectAsStateWithLifecycle()
    val mails by MailStore.important.collectAsStateWithLifecycle()
    val docs by DocumentsStore.docs.collectAsStateWithLifecycle()
    val shifts by ShiftStore.shifts.collectAsStateWithLifecycle()
    remember { com.platinum20.dashboard.notes.NotePhotoText.init(context) }
    val photoText by com.platinum20.dashboard.notes.NotePhotoText.texts.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val finder = rememberFinder(openNote = onOpenNote, openPlayer = onOpenPlayer)

    // Tutto si ricorda tornando indietro da un risultato (punto 16).
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var media by remember { mutableStateOf(SearchCache.media) }
    var events by remember { mutableStateOf(SearchCache.events) }
    var retry by remember { mutableIntStateOf(media.attempt) }
    val startedEmpty = remember { query.isEmpty() }
    val tickets = remember { TicketQrStore.eventIds(context) }
    val listState = rememberLazyListState()
    fun toggle(key: String) { expanded = if (key in expanded) expanded - key else expanded + key }

    LaunchedEffect(Unit) {
        if (news == null) NewsStore.restore()
        events = SearchCache.events(context, today)
    }
    // In rete si cerca quando si smette di scrivere, tutto insieme: ognuno risponde per conto suo.
    LaunchedEffect(query, retry, online) {
        val q = query.trim()
        if (q.length < 2) {
            media = MediaHits(attempt = retry)
            SearchCache.media = media
            PodcastStore.clearSearch()
            return@LaunchedEffect
        }
        // Già cercata (tornando da un risultato): resta quella.
        if (media.query == q && media.attempt == retry && !media.loading) return@LaunchedEffect
        // Senza rete non si prova nemmeno: lo dice la riga di stato (punto 8).
        if (!online) {
            media = MediaHits(q, attempt = retry)
            return@LaunchedEffect
        }
        delay(450)
        media = media.copy(query = q, loading = true, attempt = retry)
        PodcastStore.search(q)
        coroutineScope {
            val spotify = async { runCatching { SpotifyAuth.token()?.let { SpotifyApi.search(it, q) }.orEmpty() } }
            val videos = async { runCatching { YtResolver.search(q) } }
            val radio = async { runCatching { RadioApi.search(q) } }
            val channels = async { runCatching { YtResolver.searchChannels(q) } }
            val s = spotify.await()
            val v = videos.await()
            val r = radio.await()
            val c = channels.await()
            val failed = buildList {
                if (s.isFailure) add("Spotify")
                if (v.isFailure || c.isFailure) add("YouTube")
                if (r.isFailure) add("Radio")
            }
            media = MediaHits(
                q, false, s.getOrDefault(emptyList()), v.getOrDefault(emptyList()).take(MAX_ONLINE),
                r.getOrDefault(emptyList()).take(MAX_ONLINE), c.getOrDefault(emptyList()).take(MAX_ONLINE), failed, retry,
            )
            SearchCache.media = media
        }
    }
    // Uscendo da Cerca l'ultima ricerca diventa una "recente".
    val lastQuery by rememberUpdatedState(query)
    DisposableEffect(Unit) { onDispose { SearchRecents.keep(lastQuery) } }
    fun shows(f: SearchFilter) = filter == SearchFilter.ALL || filter == f ||
        (f == SearchFilter.AGENDA && filter == SearchFilter.NOTES) || (f == SearchFilter.NOTES && filter == SearchFilter.AGENDA)
    val calendar = shows(SearchFilter.NOTES)
    val all = filter == SearchFilter.ALL

    // I gruppi, nell'ordine in cui si vedono: prima le tue cose, poi la rete.
    val q = query.trim()
    val groups: List<Pair<String, List<Found>>> = if (q.isEmpty()) emptyList() else buildList {
        val span = SearchWhen.split(q, today)
        if (calendar && span != null) {
            val (whenSpan, rest) = span
            val inSpan = EverywhereSearch.eventsIn(events, whenSpan, rest).map { (d, e) -> finder.event(d, e, q, tickets, 0) }
            val dated = data.tasks.filter { t ->
                val due = t.dueAt ?: return@filter false
                whenSpan.contains(java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) &&
                    (rest.isBlank() || SearchMatch.score(rest, t.text) > 0)
            }.mapNotNull { t -> finder.notes(data.copy(tasks = listOf(t), notes = emptyList(), parcels = emptyList(), habits = emptyList()), t.text, emptyMap(), today).firstOrNull()?.second }
            add("when" to (inSpan + dated))
        }
        if (calendar) {
            finder.notes(data, q, photoText, today).groupBy({ it.first }, { it.second }).forEach { (kind, list) -> add(kind.name to list) }
            val ticketsAsked = SearchSources.asksTickets(q)
            val found = EverywhereSearch.events(events, q, today).map { (d, e) -> finder.event(d, e, q, tickets, SearchMatch.score(q, e.title, e.location)) }
            val withTickets = if (!ticketsAsked) emptyList() else events.flatMap { (d, list) -> list.map { d to it } }
                .filter { (d, e) -> e.eventId in tickets && !d.isBefore(today.minusDays(1)) }
                .distinctBy { it.second.eventId }.sortedBy { it.second.begin }
                .map { (d, e) -> finder.event(d, e, q, tickets, 0) }
            add("events" to (withTickets + found).distinctBy { it.key })
            val tags = SearchSources.names(NoteSmart.allTags(data), q).map { (t, s) ->
                val (n, tk) = NoteSmart.tagged(data, t)
                finder.tag(t, n.size + tk.size, s, q)
            }
            val people = SearchSources.names(NoteSmart.people(shifts.map { it.mates }), q).map { (p, s) -> finder.person(p, s, q) }
            add("names" to tags + people)
        }
        if (all) {
            add("mail" to finder.mailFound(mails, q))
            add("docs" to finder.docsFound(docs, q))
            add("settings" to finder.settingsFound(q))
        }
        if (shows(SearchFilter.NEWS)) add("news" to EverywhereSearch.news(news?.items.orEmpty(), q).map { finder.news(it, q, onOpenArticle) })
        val myMusic = if (shows(SearchFilter.MUSIC)) finder.myMusic(q) else emptyList()
        val myVideos = if (shows(SearchFilter.VIDEO)) finder.myVideos(q) else emptyList()
        add("mymusic" to myMusic)
        add("myvideos" to myVideos)
        // In rete: valgono solo se sono di questa ricerca (si scrive più in fretta di quanto rispondano).
        val fresh = media.query == q
        val mine = (myMusic + myVideos).map { it.key.substringAfter(':') }.toSet()
        val people = if (fresh) SearchPeople.hits(media.spotify, media.channels, shows(SearchFilter.MUSIC), shows(SearchFilter.VIDEO)) else emptyList()
        add("people" to people.mapNotNull { hit ->
            when (hit) {
                is PersonHit.Artist -> finder.spotify(hit.item, q)
                is PersonHit.Channel -> finder.channel(hit.channel, SearchPeople.channelLine(hit.subscribers), q).copy(onLong = {
                    // Le stesse azioni della pagina del canale: aprirlo e l'iscrizione.
                    val now = YtRatings.isSubscribed(hit.channel.id, YtRatings.subscribed.value, YouTubeStore.channels.value.data)
                    ActionBubble.show(
                        BubbleHead("Canale", hit.title),
                        Slot.open { inApp?.openRoute?.invoke(com.platinum20.dashboard.Routes.youtubeChannel(hit.channel)) },
                        Slot.subscribe(now) {
                            val ok = YtRatings.toggleSubscribe(context, hit.channel.id, now) {
                                CaptureFeedback.tell(context, "YouTube non ha preso l'iscrizione, riprova")
                            }
                            if (!ok) CaptureFeedback.tell(context, "Accedi a YouTube in Impostazioni › Account")
                        },
                    )
                })
            }.takeIf { hit.key.substringAfter(':') !in mine }
        })
        if (fresh && shows(SearchFilter.MUSIC)) add("spotify" to media.spotify
            .filter { it.kind != SpKind.SHOW && it.kind != SpKind.EPISODE && it.kind != SpKind.ARTIST && it.id !in mine }
            .map { finder.spotify(it, q) })
        if (fresh && shows(SearchFilter.VIDEO)) add("youtube" to media.videos.filter { it.id !in mine }.map { finder.video(it, q) })
        if (fresh && shows(SearchFilter.RADIO)) add("radio" to media.radio.map { finder.radio(it, q) })
        if (shows(SearchFilter.MUSIC) && q.length >= 2) add("podcasts" to podcasts.data.orEmpty().take(MAX_ONLINE).map { podcast ->
            Found(
                "pod:${podcast.feedUrl}", podcast.title, "Ultimo episodio · ${podcast.author}",
                if (podcast.artwork.isNotBlank()) Lead.Picture(podcast.artwork) else Lead.Mark(Icons.Outlined.MusicNote),
                SearchMatch.score(q, podcast.title, podcast.author), SearchMatch.strong(podcast.title, q),
            ) {
                // Parte l'episodio più recente: gli altri si trovano nella pagina del podcast.
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
        })
    }.filter { it.second.isNotEmpty() }
    // Il risultato migliore (punto 2): il più forte fra tutte le fonti, con un tocco si apre o parte.
    val best = groups.flatMap { it.second }.filter { it.strong }.maxByOrNull { it.score }
    val shown = groups.map { (k, list) -> k to list.filter { it.key != best?.key } }.filter { it.second.isNotEmpty() }
    val waiting = q.length >= 2 && online && (media.loading || media.query != q)

    Scaffold(topBar = { TopLevelBar(title = "Cerca", onBack = onBack) }) { inner ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp + miniPlayerRoom(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "field") {
                CanvasSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Cerca…",
                    // Tornando da un risultato la tastiera resta chiusa: si guarda la lista, non si riscrive.
                    modifier = Modifier.fillMaxWidth().autoFocus(startedEmpty),
                )
            }
            item(key = "filters") {
                SearchFilters(filter) { haptic(Haptic.TICK); filter = it }
            }
            if (q.isEmpty()) {
                item(key = "empty-hints") {
                    EmptyHints(
                        recents = recents,
                        opened = opened,
                        tags = NoteSmart.allTags(data).take(2),
                        hasTickets = tickets.isNotEmpty(),
                        setQuery = { query = it },
                        setFilter = { filter = it },
                        modifier = Modifier.animateItem(),
                    )
                }
            } else {
                // Senza rete, o se una fonte non risponde: lo dice e si riprova da qui (punto 8).
                if (q.length >= 2 && (!online || media.failed.isNotEmpty())) item(key = "status") {
                    StatusLine(
                        LinkStatus(
                            LinkStatus.Kind.STALE,
                            if (!online) "Senza rete: cerco solo sul telefono" else "Non risponde: " + media.failed.joinToString(", "),
                        ),
                        action = "Riprova",
                        onAction = { haptic(Haptic.TICK); retry += 1 },
                        modifier = Modifier.animateItem(),
                        start = 4.dp,
                    )
                }
                if (best != null) item(key = "best") {
                    Section(title = "Risultato migliore", modifier = Modifier.animateItem()) { FoundRow(best, q, big = true) }
                }
                shown.forEach { (key, list) ->
                    item(key = "g-$key") {
                        FoundGroup(groupTitle(key, q, today), list, q, key in expanded, { toggle(key) }, Modifier.animateItem())
                    }
                }
                if (waiting && shown.none { it.first in ONLINE_GROUPS }) item(key = "loading") {
                    Text(
                        "Cerco anche su Spotify, YouTube, radio e podcast…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp).animateItem(),
                    )
                }
                if (best == null && shown.isEmpty() && !waiting) item(key = "nothing") {
                    Text(
                        "Niente trovato per \"$q\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private val ONLINE_GROUPS = setOf("people", "spotify", "youtube", "radio", "podcasts")

/** Il nome di un gruppo. */
private fun groupTitle(key: String, query: String, today: LocalDate): String = when (key) {
    "when" -> SearchWhen.split(query, today)?.first?.label ?: "Quel giorno"
    "events" -> "Calendario"
    "names" -> "Etichette e persone"
    "mail" -> "Posta"
    "docs" -> "Documenti"
    "settings" -> "Impostazioni"
    "news" -> "News"
    "mymusic" -> "La tua musica"
    "myvideos" -> "I tuoi video"
    "people" -> "Artisti e canali"
    "spotify" -> "Spotify"
    "youtube" -> "YouTube"
    "radio" -> "Radio"
    "podcasts" -> "Podcast"
    else -> SearchKind.entries.firstOrNull { it.name == key }?.label ?: key
}

/**
 * I filtri in una fila sola che non scorre (punto 10, come le chip dei fumetti): quello scelto si allarga col nome,
 * gli altri solo icona.
 */
@Composable
private fun SearchFilters(selected: SearchFilter, onPick: (SearchFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SearchFilter.entries.filter { it != SearchFilter.AGENDA }.forEach { f ->
            val on = selected == f || (f == SearchFilter.NOTES && selected == SearchFilter.AGENDA)
            val weight by animateFloatAsState(if (on) 3.4f else 1f, Motion.settle(), label = "filtro")
            val container by animateColorAsState(if (on) colors.onSurface else colors.card, Motion.settle(), label = "filtroFondo")
            val content by animateColorAsState(if (on) colors.surface else colors.onSurfaceVariant, Motion.settle(), label = "filtroTesto")
            Surface(
                selected = on,
                onClick = { onPick(f) },
                shape = CircleShape,
                color = container,
                contentColor = content,
                border = if (on) null else BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.weight(weight).height(36.dp).bouncyPress(0.94f),
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(f.icon, contentDescription = f.label, modifier = Modifier.size(18.dp))
                    if (on) {
                        Spacer(Modifier.width(6.dp))
                        Text(f.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Sotto il campo vuoto (punto 9): le ultime ricerche, le cose aperte di recente da Cerca e qualche scorciatoia vera
 * (domani, la settimana, i turni, le tue #etichette, i biglietti).
 */
@Composable
private fun EmptyHints(
    recents: List<String>,
    opened: List<SearchRecents.Opened>,
    tags: List<String>,
    hasTickets: Boolean,
    setQuery: (String) -> Unit,
    setFilter: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val inApp = LocalInAppNav.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (opened.isNotEmpty()) Section(title = "Aperti di recente") {
            opened.forEach { o ->
                val found = Found(
                    "o" + o.route, o.title, o.line, Lead.Mark(recentIcon(o.icon)), recent = o,
                    onLong = {
                        ActionBubble.show(
                            BubbleHead("Aperto di recente", o.title),
                            Slot.open { SearchRecents.open(o); inApp?.openRoute?.invoke(o.route) },
                            Slot.remove { SearchRecents.forgetOpened(o) },
                        )
                    },
                ) { inApp?.openRoute?.invoke(o.route) }
                FoundRow(found, "")
            }
        }
        if (recents.isNotEmpty()) Section(title = "Ricerche recenti") {
            recents.forEach { r ->
                HintRow(Icons.Outlined.History, r, onLongClick = {
                    ActionBubble.show(BubbleHead("Ricerca recente", r), Slot.remove { SearchRecents.forget(r) })
                }) { haptic(Haptic.TICK); setQuery(r) }
            }
        }
        // Cosa si può cercare, con un tocco: insegna senza spiegare.
        Section(title = "Prova") {
            HintRow(Icons.Outlined.Today, "Domani") { haptic(Haptic.TICK); setQuery("domani") }
            HintRow(Icons.Outlined.DateRange, "Questa settimana") { haptic(Haptic.TICK); setQuery("questa settimana") }
            HintRow(Icons.Outlined.Work, "Turni") { haptic(Haptic.TICK); setFilter(SearchFilter.NOTES); setQuery("Turno") }
            tags.forEach { t -> HintRow(Icons.Outlined.Tag, "#$t") { haptic(Haptic.TICK); setQuery("#$t") } }
            if (hasTickets) HintRow(Icons.Outlined.ConfirmationNumber, "Biglietti") { haptic(Haptic.TICK); setQuery("biglietti") }
        }
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
