package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.EpisodeDate
import com.platinum20.dashboard.media.podcast.Podcast
import com.platinum20.dashboard.media.podcast.PodcastNow
import com.platinum20.dashboard.media.podcast.PodcastProgress
import com.platinum20.dashboard.media.podcast.PodcastStore
import java.time.ZonedDateTime

/** Ricerche già pronte per chi non segue ancora nessun podcast. */
internal val podcastIdeas = listOf("Il Post", "Muschio Selvaggio", "Geopop", "Tintoria", "Storia")

/** Podcast: quelli seguiti con gli ultimi episodi, la ricerca e la pagina di ogni podcast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    PodcastStore.init(context)
    val search by PodcastStore.search.collectAsState()
    val followed by PodcastStore.followed.collectAsState()
    val latest by PodcastStore.latest.collectAsState()
    val now by PodcastStore.now.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<Podcast?>(null) }

    LaunchedEffect(Unit) { PodcastStore.loadLatest() }
    BackHandler(enabled = open != null) { open = null }

    val runSearch: (String) -> Unit = { text ->
        query = text
        searching = true
        PodcastStore.search(text)
    }

    Scaffold(
        topBar = {
            TopLevelBar(
                title = open?.title ?: "Podcast",
                subtitle = when {
                    open != null -> open?.author
                    followed.isNotEmpty() -> "Ne segui ${followed.size}"
                    else -> "Ascolta e riprendi da dove eri"
                },
                onBack = { if (open != null) open = null else onBack() },
            )
        },
        bottomBar = { now?.let { PodcastNowBar(it) } },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            val podcast = open
            if (podcast != null) {
                PodcastPage(podcast, now)
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) {
                        searching = false
                        PodcastStore.clearSearch()
                    }
                },
                placeholder = { Text("Cerca un podcast") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            query = ""
                            searching = false
                            PodcastStore.clearSearch()
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Cancella") }
                    }
                } else null,
                singleLine = true,
                colors = canvasFieldColors(),
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (searching) {
                FeedList(search, empty = "Nessun podcast trovato") {
                    items(search.data.orEmpty(), key = { "search-${it.feedUrl}" }) { result ->
                        PodcastRow(result, onOpen = { open = result })
                    }
                }
            } else {
                HomeList(
                    followed = followed,
                    latest = latest,
                    now = now,
                    onOpen = { open = it },
                    onIdea = runSearch,
                )
            }
        }
    }
}

@Composable
private fun HomeList(
    followed: List<Podcast>,
    latest: Loadable<List<Episode>>,
    now: PodcastNow?,
    onOpen: (Podcast) -> Unit,
    onIdea: (String) -> Unit,
) {
    val positions by PodcastStore.positions.collectAsState()
    if (followed.isEmpty()) {
        // Primo uso: niente pagina vuota, ma da dove cominciare.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CanvasCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Mic, TileAccent.PURPLE)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Segui i tuoi podcast", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Qui arrivano i nuovi episodi, e ognuno riparte dal punto in cui eri.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SectionHeader("Per iniziare")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                podcastIdeas.forEach { idea -> CanvasChip(selected = false, onClick = { onIdea(idea) }, label = idea) }
            }
        }
        return
    }
    FeedList(latest, empty = "Nessun episodio nei podcast che segui") {
        item(key = "followed") {
            Column {
                SectionHeader("I tuoi podcast")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    followed.forEach { podcast -> FollowedTile(podcast, onClick = { onOpen(podcast) }) }
                }
            }
        }
        item(key = "latest-header") { SectionHeader("Ultimi episodi") }
        items(latest.data.orEmpty(), key = { "latest-${it.id}" }) { episode ->
            EpisodeRow(episode, now, positions[episode.id] ?: 0, showPodcast = true)
        }
    }
}

@Composable
private fun PodcastPage(podcast: Podcast, now: PodcastNow?) {
    val all by PodcastStore.episodes.collectAsState()
    val followed by PodcastStore.followed.collectAsState()
    val positions by PodcastStore.positions.collectAsState()
    val haptic = rememberHaptic()
    val isFollowed = followed.any { it.feedUrl == podcast.feedUrl }
    LaunchedEffect(podcast.feedUrl) { PodcastStore.loadEpisodes(podcast) }
    val state = all[podcast.feedUrl] ?: Loadable(loading = true)
    FeedList(state, empty = "Questo podcast non ha episodi da ascoltare") {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(podcast.artwork, 88)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(podcast.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (podcast.author.isNotBlank()) {
                        Text(podcast.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    val toggle = {
                        haptic(if (isFollowed) Haptic.TICK else Haptic.CONFIRM)
                        PodcastStore.toggleFollow(podcast)
                    }
                    if (isFollowed) {
                        OutlinedButton(onClick = toggle) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Seguito")
                        }
                    } else {
                        FilledTonalButton(onClick = toggle) { Text("Segui") }
                    }
                }
            }
        }
        items(state.data.orEmpty(), key = { "ep-${it.id}" }) { episode ->
            EpisodeRow(episode, now, positions[episode.id] ?: 0, showPodcast = false)
        }
    }
}

@Composable
private fun PodcastRow(podcast: Podcast, onOpen: () -> Unit) {
    val followed by PodcastStore.followed.collectAsState()
    val isFollowed = followed.any { it.feedUrl == podcast.feedUrl }
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(podcast.artwork, 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(podcast.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isFollowed) "Seguito · ${podcast.author}" else podcast.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FollowedTile(podcast: Podcast, onClick: () -> Unit) {
    Column(
        Modifier
            .width(96.dp)
            .clip(MaterialTheme.shapes.medium)
            .bouncyPress()
            .clickable(onClick = onClick)
    ) {
        Artwork(podcast.artwork, 96)
        Spacer(Modifier.height(6.dp))
        Text(podcast.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EpisodeRow(episode: Episode, now: PodcastNow?, savedMs: Long, showPodcast: Boolean) {
    val playing = now?.episode?.id == episode.id
    val today = remember { ZonedDateTime.now() }
    val durationMs = episode.durationSec * 1000
    val started = PodcastProgress.resumeAt(savedMs, durationMs) > 0
    val finished = durationMs > 0 && savedMs >= durationMs - 30_000
    Surface(
        onClick = { if (playing) PodcastStore.togglePause() else PodcastStore.play(episode) },
        shape = MaterialTheme.shapes.large,
        color = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card,
        border = if (playing) null else cardBorder(),
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPodcast) {
                    Artwork(episode.image, 48)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    val detail = listOf(
                        if (showPodcast) episode.podcastTitle else "",
                        EpisodeDate.label(episode.publishedAt, today),
                        when {
                            finished -> "ascoltato"
                            else -> PodcastProgress.label(episode.durationSec, if (started) savedMs else 0)
                        },
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (playing && now?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing && now?.playing == true) "Pausa" else "Ascolta",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (!showPodcast && episode.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(episode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (started && !finished && durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (savedMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}

@Composable
private fun Artwork(url: String, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        }
    }
}

/** Mini player dell'episodio: tempo che scorre, 10 secondi indietro, 30 avanti. */
@Composable
private fun PodcastNowBar(now: PodcastNow) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Artwork(now.episode.image, 44)
                Column(Modifier.weight(1f)) {
                    Text(now.episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        now.error ?: if (now.buffering) "Caricamento…" else listOf(
                            clockLabel(now.positionMs),
                            if (now.durationMs > 0) clockLabel(now.durationMs) else "",
                        ).filter { it.isNotBlank() }.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (now.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (now.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(onClick = { PodcastStore.seekBy(-10_000) }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Indietro di 10 secondi")
                }
                FilledIconButton(onClick = { PodcastStore.togglePause() }) {
                    Icon(if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (now.playing) "Pausa" else "Riproduci")
                }
                IconButton(onClick = { PodcastStore.seekBy(30_000) }) {
                    Icon(Icons.Filled.Forward30, contentDescription = "Avanti di 30 secondi")
                }
                IconButton(onClick = { PodcastStore.stop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Ferma")
                }
            }
            if (now.durationMs > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (now.positionMs.toFloat() / now.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}

/** "12:05" o "1:02:03". */
internal fun clockLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
