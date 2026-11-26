package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.docs.Document
import com.platinum20.dashboard.mail.MailMessage
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.MadeStore
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.notes.NoteInbox
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesSearch
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.SearchKind
import com.platinum20.dashboard.search.SearchMatch
import com.platinum20.dashboard.search.SearchRecents
import com.platinum20.dashboard.search.SearchSources
import com.platinum20.dashboard.search.SettingEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cosa sta a sinistra di un risultato (la riga Canvas, 2026-11-26 punto 6): icona, foto/copertina o ora. */
internal sealed interface Lead {
    data class Mark(val icon: ImageVector) : Lead
    data class Picture(val url: String, val round: Boolean = false) : Lead
    data class Clock(val text: String) : Lead
}

/**
 * Un risultato di Cerca, di qualunque fonte: una riga sola uguale per tutti (2026-11-26). [strong] = il titolo
 * può fare da "Risultato migliore"; [recent] = dove tornare da "Aperti di recente" (null = non si ricorda).
 */
internal data class Found(
    val key: String,
    val title: String,
    val line: String,
    val lead: Lead,
    val score: Int = 0,
    val strong: Boolean = false,
    val recent: SearchRecents.Opened? = null,
    /** La riga va colorata dove c'è la parola (no per date, orari, mittenti). */
    val lineMarks: Boolean = false,
    val onLong: (() -> Unit)? = null,
    val onOpen: () -> Unit,
) {
    fun open() {
        recent?.let(SearchRecents::open)
        onOpen()
    }
}

/** L'icona di una cosa aperta di recente, dal suo tipo salvato. */
internal fun recentIcon(kind: String): ImageVector = when (kind) {
    "note" -> Icons.AutoMirrored.Outlined.Notes
    "task" -> Icons.Outlined.CheckBoxOutlineBlank
    "date" -> Icons.Outlined.Cake
    "parcel" -> Icons.Outlined.LocalShipping
    "habit" -> Icons.Outlined.LocalFireDepartment
    "event" -> Icons.Outlined.Event
    "mail" -> Icons.Outlined.Mail
    "doc" -> Icons.Outlined.Badge
    "tag" -> Icons.Outlined.Tag
    "person" -> Icons.Outlined.Person
    "setting" -> Icons.Outlined.Settings
    "music" -> Icons.Outlined.MusicNote
    "video" -> Icons.Outlined.SmartDisplay
    "channel" -> Icons.Outlined.Subscriptions
    "news" -> Icons.Outlined.Newspaper
    else -> Icons.Outlined.Event
}

private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Chi costruisce i risultati: le fonti sul telefono (note, calendario, posta, documenti, impostazioni, la tua
 * musica e i tuoi video) e quelle in rete. Uguale in Cerca e nel + (idea 11), così un risultato fa le stesse
 * cose ovunque: tocco = si apre, tenuto premuto = il suo fumetto (punto 7).
 */
internal class Finder(
    private val context: Context,
    private val nav: InAppNav?,
    private val openNote: (Long) -> Unit,
    private val openPlayer: () -> Unit,
    private val openItem: (ItemKind, Long) -> Unit,
    private val rowActions: (DueRow) -> Unit,
) {
    private fun go(route: String) { nav?.openRoute?.invoke(route) }
    private fun bubbleOpen(kind: String, name: String, open: () -> Unit) =
        ActionBubble.show(BubbleHead(kind, name), Slot.open(open))

    /** Note, promemoria, date, pacchi e abitudini, divisi per tipo nell'ordine dei gruppi. */
    fun notes(data: NotesData, query: String, photoText: Map<String, String>, today: LocalDate): List<Pair<SearchKind, Found>> =
        NotesSearch.search(data, query, photoText).mapNotNull { hit ->
            val strong = SearchMatch.strong(hit.title, query)
            val found = when (hit.kind) {
                SearchKind.NOTE -> {
                    val n = data.notes.firstOrNull { it.id == hit.id } ?: return@mapNotNull null
                    val saved = SavedLinks.find(n.text) != null
                    val title = hit.title
                    val line = hit.line?.let { if (hit.inPhoto) "Nella foto: “$it”" else it }
                        ?: ("Modificata " + relativeTime(Instant.ofEpochMilli(n.updatedAt)))
                    Found(
                        "n${n.id}", title, line,
                        Lead.Mark(Icons.AutoMirrored.Outlined.Notes),
                        hit.score, strong,
                        recent = if (saved) null else SearchRecents.Opened(title, "Nota", Routes.noteEdit(n.id), "note"),
                        lineMarks = hit.line != null,
                        onLong = { showNoteBubble(context, n, openRoute = ::go) },
                    ) {
                        // Trovata in una riga del testo: la nota si apre lì, con la parola colorata.
                        if (hit.line != null && !hit.inPhoto && !saved && nav != null) go(Routes.noteEdit(n.id, query.trim())) else openNote(n.id)
                    }
                }
                SearchKind.TASK, SearchKind.DATE -> {
                    val t = data.tasks.firstOrNull { it.id == hit.id } ?: return@mapNotNull null
                    val date = hit.kind == SearchKind.DATE
                    val line = when {
                        date -> t.dateLine()
                        t.dueAt != null -> dayAndTime(t.dueAt)
                        t.done -> "Completato"
                        else -> "Da fare"
                    }
                    val icon = when {
                        date -> Icons.Outlined.Cake
                        t.dueAt != null -> Icons.Outlined.NotificationsNone
                        t.done -> Icons.Outlined.CheckBox
                        else -> Icons.Outlined.CheckBoxOutlineBlank
                    }
                    Found(
                        "t${t.id}", t.text, line, Lead.Mark(icon), hit.score, strong,
                        recent = SearchRecents.Opened(t.text, if (date) "Data" else "Promemoria", Routes.noteItem(ItemKind.TASK, t.id), if (date) "date" else "task"),
                        onLong = { rowActions(DueRow("t${t.id}", null, 0L, task = t)) },
                    ) { openItem(ItemKind.TASK, t.id) }
                }
                SearchKind.PARCEL -> {
                    val p = data.parcels.firstOrNull { it.id == hit.id } ?: return@mapNotNull null
                    Found(
                        "p${p.id}", p.title, if (p.delivered) "Arrivato" else p.code.ifEmpty { "In arrivo" },
                        Lead.Mark(Icons.Outlined.LocalShipping), hit.score, strong,
                        recent = SearchRecents.Opened(p.title, "Pacco", Routes.noteItem(ItemKind.PARCEL, p.id), "parcel"),
                        onLong = { rowActions(DueRow("p${p.id}", null, 0L, parcel = p)) },
                    ) { openItem(ItemKind.PARCEL, p.id) }
                }
                SearchKind.HABIT -> {
                    val h = data.habits.firstOrNull { it.id == hit.id } ?: return@mapNotNull null
                    Found(
                        "h${h.id}", h.title, if (h.isDone(today)) "Fatta oggi" else "Abitudine",
                        Lead.Mark(Icons.Outlined.LocalFireDepartment), hit.score, strong,
                        recent = SearchRecents.Opened(h.title, "Abitudine", Routes.noteItem(ItemKind.HABIT, h.id), "habit"),
                        onLong = { showHabit(h, today) { openItem(ItemKind.HABIT, it.id) } },
                    ) { openItem(ItemKind.HABIT, h.id) }
                }
            }
            hit.kind to found
        }

    /**
     * Un evento trovato: tocco = l'evento stesso (Antonio, 2026-11-26, punto 17), non il giorno del calendario.
     * [score] 0 per gli eventi di un giorno chiesto a parole: lì conta il giorno, non il nome.
     */
    fun event(day: LocalDate, e: CalendarEvent, query: String, tickets: Set<Long>, score: Int): Found {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(e.begin).atZone(zone).format(hourFormat)
        val line = listOfNotNull(
            if (e.allDay) longDate(day) else longDate(day) + " · " + time,
            e.location.takeIf { it.isNotBlank() },
            "Biglietto".takeIf { e.eventId in tickets },
        ).joinToString(" · ")
        return Found(
            "e${e.eventId}@${e.begin}", e.title, line,
            if (e.allDay) Lead.Mark(Icons.Outlined.Event) else Lead.Clock(time),
            score, score > 0 && SearchMatch.strong(e.title, query),
            recent = SearchRecents.Opened(e.title, longDate(day), Routes.calendarEvent(e.eventId, e.begin, e.end), "event"),
            onLong = { rowActions(DueRow("e${e.eventId}@${e.begin}", day, e.begin, event = e)) },
        ) { nav?.openEvent?.invoke(e) }
    }

    fun mail(m: MailMessage, score: Int, query: String): Found {
        val title = m.subject.ifBlank { "(senza oggetto)" }
        return Found(
            "m${m.id}", title, m.from + " · " + relativeTime(Instant.ofEpochMilli(m.at)),
            Lead.Mark(Icons.Outlined.Mail), score, SearchMatch.strong(title, query),
            recent = SearchRecents.Opened(title, "Mail da " + m.from, Routes.mail(m.id), "mail"),
            onLong = { bubbleOpen("Mail", title) { go(Routes.mail(m.id)) } },
        ) { go(Routes.mail(m.id)) }
    }

    fun doc(d: Document, score: Int, query: String): Found = Found(
        "d${d.id}", d.title, listOfNotNull(d.kind.label, d.number).joinToString(" · "),
        Lead.Mark(Icons.Outlined.Badge), score, SearchMatch.strong(d.title, query),
        recent = SearchRecents.Opened(d.title, d.kind.label, Routes.document(d.id), "doc"),
        lineMarks = true,
        onLong = { bubbleOpen("Documento", d.title) { go(Routes.document(d.id)) } },
    ) { go(Routes.document(d.id)) }

    fun tag(name: String, count: Int, score: Int, query: String): Found = Found(
        "#$name", "#$name", if (count == 1) "Etichetta · 1 cosa" else "Etichetta · $count cose",
        Lead.Mark(Icons.Outlined.Tag), score, SearchMatch.strong(name, query.removePrefix("#")),
        recent = SearchRecents.Opened("#$name", "Etichetta", Routes.tag(name), "tag"),
        onLong = { bubbleOpen("Etichetta", "#$name") { go(Routes.tag(name)) } },
    ) { go(Routes.tag(name)) }

    fun person(name: String, score: Int, query: String): Found = Found(
        "@$name", name, "In turno con te",
        Lead.Mark(Icons.Outlined.Person), score, SearchMatch.strong(name, query),
        recent = SearchRecents.Opened(name, "Persona dei turni", Routes.person(name), "person"),
        onLong = { bubbleOpen("Persona", name) { go(Routes.person(name)) } },
    ) { go(Routes.person(name)) }

    fun setting(s: SettingEntry, score: Int, query: String): Found {
        val route = s.page?.let { p -> SettingsPage.entries.firstOrNull { it.name == p } }?.let(Routes::settingsPage) ?: Routes.SETTINGS
        return Found(
            "s${s.title}", s.title, s.line, Lead.Mark(Icons.Outlined.Settings), score, SearchMatch.strong(s.title, query),
            recent = SearchRecents.Opened(s.title, "Impostazioni", route, "setting"),
            onLong = { bubbleOpen("Impostazione", s.title) { go(route) } },
        ) { go(route) }
    }

    fun news(n: NewsItem, query: String, openArticle: (NewsItem) -> Unit): Found = Found(
        "news" + n.link, n.title, n.source.name + (n.publishedAt?.let { " · " + relativeTime(it) }.orEmpty()),
        n.imageUrl?.takeIf { it.isNotBlank() }?.let { Lead.Picture(it) } ?: Lead.Mark(Icons.Outlined.Newspaper),
        SearchMatch.score(query, n.title, n.summary), false,
        recent = SearchRecents.Opened(n.title, n.source.name, Routes.reader(n.link, n.source.name, n.title), "news"),
        onLong = { SavedLinks.offer(n.savedLink()) },
    ) { openArticle(n) }

    /** Un elemento di Spotify: brani partono, il resto apre la sua pagina (come in Media). */
    fun spotify(item: SpItem, query: String, prefix: String = "sp"): Found {
        val page = item.kind == SpKind.ARTIST || item.kind == SpKind.ALBUM || item.kind == SpKind.PLAYLIST
        val line = spKindLabel(item.kind) + item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        return Found(
            "$prefix:${item.id}", item.title, line,
            if (item.image.isNotBlank()) Lead.Picture(item.image, round = item.kind == SpKind.ARTIST) else Lead.Mark(Icons.Outlined.MusicNote),
            SearchMatch.score(query, item.title, item.subtitle), SearchMatch.strong(item.title, query),
            recent = if (page) SearchRecents.Opened(item.title, spKindLabel(item.kind), Routes.spotifyItem(item), "music") else null,
            onLong = SavedLinks.spotify(item)?.let { l -> { SavedLinks.offer(l) } },
        ) {
            if (page) go(Routes.spotifyItem(item)) else { SpotifyPlayer.play(context, item); openPlayer() }
        }
    }

    fun video(v: YtVideo, query: String, prefix: String = "yt"): Found = Found(
        "$prefix:${v.id}", v.title, v.channel, Lead.Picture(v.thumbnail),
        SearchMatch.score(query, v.title, v.channel), SearchMatch.strong(v.title, query),
        recent = SearchRecents.Opened(v.title, v.channel, Routes.youtubeWatch(v.watchUrl), "video"),
        onLong = { SavedLinks.offer(SavedLinks.youtube(v)) },
    ) { go(Routes.youtubeWatch(v.watchUrl)) }

    fun channel(c: com.platinum20.dashboard.media.youtube.YtChannel, line: String, query: String): Found = Found(
        "ch:${c.id}", c.title, line,
        if (c.thumbnail.isNotBlank()) Lead.Picture(c.thumbnail, round = true) else Lead.Mark(Icons.Outlined.Subscriptions),
        SearchMatch.score(query, c.title), SearchMatch.strong(c.title, query),
        recent = SearchRecents.Opened(c.title, "Canale", Routes.youtubeChannel(c), "channel"),
        onLong = { bubbleOpen("Canale", c.title) { go(Routes.youtubeChannel(c)) } },
    ) { go(Routes.youtubeChannel(c)) }

    fun radio(station: RadioStation, query: String): Found = Found(
        "radio:${station.id}", station.name, com.platinum20.dashboard.media.radio.RadioGenre.of(station.tags) ?: "Radio",
        if (station.logo.isNotBlank()) Lead.Picture(station.logo) else Lead.Mark(Icons.Outlined.Radio),
        SearchMatch.score(query, station.name, station.tags), SearchMatch.strong(station.name, query),
        onLong = { SavedLinks.offer(SavedLinks.radio(station)) },
    ) { RadioStore.play(station); openPlayer() }

    /**
     * La tua musica (2026-11-26, punto 3): playlist, mix fatti da Canvas, album e brani salvati, ascoltati da poco,
     * artisti che ascolti. Sta prima di Spotify in rete, e in rete non si ripete.
     */
    fun myMusic(query: String): List<Found> {
        val lib = SpotifyStore.library.value.data
        val home = SpotifyStore.home.value.data
        val items = buildList {
            addAll(SpotifyStore.playlists.value.data.orEmpty())
            addAll(lib?.albums.orEmpty())
            addAll(home?.topArtists.orEmpty())
            addAll(lib?.liked.orEmpty())
            addAll(SpotifyStore.played.value.map { it.first })
            addAll(home?.recent.orEmpty())
        }.filter { it.kind != SpKind.SHOW && it.kind != SpKind.EPISODE }.distinctBy { it.id }
        val mixes = MadeStore.mixes.value.map { m ->
            Found(
                "mix:${m.id}", m.title, "Mix · " + m.subtitle,
                if (m.image.isNotBlank()) Lead.Picture(m.image) else Lead.Mark(Icons.Outlined.LibraryMusic),
                SearchMatch.score(query, m.title, m.subtitle), SearchMatch.strong(m.title, query),
                recent = SearchRecents.Opened(m.title, "Mix", Routes.mix(m.id), "music"),
                onLong = { bubbleOpen("Mix", m.title) { go(Routes.mix(m.id)) } },
            ) { go(Routes.mix(m.id)) }
        }
        return (mixes + items.map { spotify(it, query, "my") }).filter { it.score > 0 }.sortedByDescending { it.score }
    }

    /** I tuoi video: canali a cui sei iscritto, guardati da poco, Guarda più tardi e le tue playlist. */
    fun myVideos(query: String): List<Found> {
        val channels = YouTubeStore.channels.value.data.orEmpty().map { channel(it, "Canale a cui sei iscritto", query) }
        val playlists = YouTubeStore.playlists.value.data.orEmpty().map { p ->
            Found(
                "ypl:${p.id}", p.title, "Playlist · ${p.count} video",
                if (p.thumbnail.isNotBlank()) Lead.Picture(p.thumbnail) else Lead.Mark(Icons.Outlined.SmartDisplay),
                SearchMatch.score(query, p.title), SearchMatch.strong(p.title, query),
                recent = SearchRecents.Opened(p.title, "Playlist", Routes.youtubePlaylist(p), "video"),
                onLong = { bubbleOpen("Playlist", p.title) { go(Routes.youtubePlaylist(p)) } },
            ) { go(Routes.youtubePlaylist(p)) }
        }
        YtHistory.init(context)
        val watched = YtHistory.recent().map { w ->
            val url = "https://m.youtube.com/watch?v=${w.id}"
            Found(
                "my:${w.id}", w.title, "Guardato · " + w.channel, Lead.Picture("https://i.ytimg.com/vi/${w.id}/mqdefault.jpg"),
                SearchMatch.score(query, w.title, w.channel), SearchMatch.strong(w.title, query),
                recent = SearchRecents.Opened(w.title, w.channel, Routes.youtubeWatch(url), "video"),
            ) { go(Routes.youtubeWatch(url)) }
        }
        val later = YouTubeStore.watchLater.value.data.orEmpty().map { video(it, query, "my") }
        return (channels + playlists + watched + later).filter { it.score > 0 }.distinctBy { it.key }.sortedByDescending { it.score }
    }

    /** Le fonti sul telefono che non sono il calendario: posta, documenti, #etichette, persone, impostazioni. */
    fun mailFound(list: List<MailMessage>, query: String) = SearchSources.mail(list, query).map { (m, s) -> mail(m, s, query) }
    fun docsFound(list: List<Document>, query: String) = SearchSources.docs(list, query).map { (d, s) -> doc(d, s, query) }
    fun settingsFound(query: String) = SearchSources.settings(query).map { (e, s) -> setting(e, s, query) }
}

internal fun spKindLabel(kind: SpKind) = when (kind) {
    SpKind.TRACK -> "Brano"
    SpKind.ALBUM -> "Album"
    SpKind.PLAYLIST -> "Playlist"
    SpKind.ARTIST -> "Artista"
    SpKind.SHOW -> "Podcast"
    SpKind.EPISODE -> "Episodio"
}

@Composable
internal fun rememberFinder(openNote: (Long) -> Unit = {}, openPlayer: () -> Unit = {}): Finder {
    val context = LocalContext.current
    val nav = LocalInAppNav.current
    val openItem = rememberOpenItem()
    val rowActions = rememberRowActions()
    val note by rememberUpdatedState(openNote)
    val player by rememberUpdatedState(openPlayer)
    return remember(nav, openItem, rowActions) {
        Finder(context, nav, { note(it) }, { player() }, openItem, rowActions)
    }
}

/** Il testo con le parole cercate in blu e in grassetto (anche quelle con un errore). */
internal fun marked(text: String, query: String, accent: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isBlank()) return@buildAnnotatedString
    SearchMatch.ranges(text, query).forEach { r ->
        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), r.first, r.last + 1)
    }
}

/** La riga di un risultato: a sinistra icona/copertina/ora, in mezzo il nome e una riga, la parola in blu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FoundRow(f: Found, query: String, modifier: Modifier = Modifier, big: Boolean = false, onOpened: () -> Unit = {}) {
    val haptic = rememberHaptic()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(
                onClick = { haptic(Haptic.TICK); f.open(); onOpened() },
                onLongClick = f.onLong?.let { long -> { haptic(Haptic.LONG_PRESS); long() } },
            )
            .padding(horizontal = 14.dp, vertical = if (big) 14.dp else 10.dp),
    ) {
        LeadBox(f.lead, if (big) 52 else 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                marked(f.title, query, accent),
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (f.line.isNotBlank()) Text(
                if (f.lineMarks) marked(f.line, query, accent) else AnnotatedString(f.line),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LeadBox(lead: Lead, sizeDp: Int) {
    val shape = RoundedCornerShape(10.dp)
    when (lead) {
        is Lead.Mark -> Box(
            Modifier.size(sizeDp.dp).clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(lead.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((sizeDp * 0.5f).dp))
        }
        is Lead.Picture -> AsyncImage(
            model = lead.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(if (lead.round) CircleShape else shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        )
        is Lead.Clock -> Box(Modifier.width(sizeDp.dp), contentAlignment = Alignment.Center) {
            Text(lead.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Quanti se ne vedono per gruppo prima di "Mostra altri": la pagina resta corta e si vede ogni fonte. */
internal const val PER_GROUP = 4

/** Un gruppo di risultati: i primi [PER_GROUP] e, se ce ne sono altri, "Mostra altri" che si richiude. */
@Composable
internal fun FoundGroup(
    title: String,
    items: List<Found>,
    query: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Section(title = "$title · ${items.size}", modifier = modifier) {
        val shown = if (expanded) items else items.take(PER_GROUP)
        shown.forEach { FoundRow(it, query) }
        if (items.size > PER_GROUP) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(if (expanded) "Mostra meno" else "Mostra altri ${items.size - PER_GROUP}")
            }
        }
    }
}

/** "Aggiungi qui" dal + (idea 11): quello che hai scritto va in fondo a una nota o agli Appunti di un evento. */
internal object AddFromPlus {
    fun toNote(id: Long, text: String): Boolean = NoteInbox.appendTo(id, listOf(text.trim()))

    fun toEvent(e: CalendarEvent, text: String) {
        val notes = NotesStore.data.value.notes
        val existing = com.platinum20.dashboard.notes.AttachedNotes.forEvent(notes, e.eventId, e.begin)
        if (existing != null) {
            NotesStore.editAttachedBody(existing.id) { body -> NoteInbox.appended(body, listOf(text.trim())) }
        } else {
            NotesStore.saveAttached(com.platinum20.dashboard.notes.AttachedNotes.event(e.eventId), e.title, text.trim())
        }
    }
}
