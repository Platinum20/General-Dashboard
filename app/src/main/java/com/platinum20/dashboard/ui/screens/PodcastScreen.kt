package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.lazy.LazyListScope
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

/**
 * La parte podcast della pagina unica "Radio e podcast": quelli seguiti qui e quelli salvati su
 * Spotify insieme, poi gli ultimi episodi. Senza podcast seguiti, qualche idea da cui partire.
 */
internal fun LazyListScope.podcastSections(
    followed: List<Podcast>,
    latest: Loadable<List<Episode>>,
    now: PodcastNow?,
    positions: Map<String, Long>,
    ideas: Loadable<List<Podcast>>?,
    onIdea: (String) -> Unit,
    onOpen: (Podcast) -> Unit,
) {
    if (followed.isEmpty()) {
        // Primo uso: niente pagina vuota, ma da dove cominciare.
        item(key = "podcast-start") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Podcast per iniziare", Modifier.padding(top = 16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    podcastIdeas.forEach { idea -> CanvasChip(selected = false, onClick = { onIdea(idea) }, label = idea) }
                }
            }
        }
        val found = ideas?.data.orEmpty()
        itemsIndexed(found, key = { _, r -> "idea-${r.feedUrl}" }) { i, result ->
            PodcastRow(result, i == 0, i == found.lastIndex, onOpen = { onOpen(result) })
        }
        return
    }
    item(key = "followed") {
        Column {
            SectionHeader("I tuoi podcast", Modifier.padding(top = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                followed.forEach { podcast -> FollowedTile(podcast, onClick = { onOpen(podcast) }) }
            }
        }
    }
    val episodes = latest.data.orEmpty()
    if (episodes.isNotEmpty()) {
        item(key = "latest-header") { SectionHeader("Ultimi episodi", Modifier.padding(top = 16.dp, bottom = 4.dp)) }
        val shown = episodes.take(LATEST_SHOWN)
        itemsIndexed(shown, key = { _, e -> "latest-${e.id}" }) { i, episode ->
            EpisodeRow(episode, now, positions[episode.id] ?: 0, showPodcast = true, group = (i == 0) to (i == shown.lastIndex))
        }
    }
}

/** Gli ultimi episodi nella pagina unica: abbastanza per scegliere, senza seppellire le radio. */
private const val LATEST_SHOWN = 8

@Composable
internal fun PodcastPage(podcast: Podcast, now: PodcastNow?) {
    val all by PodcastStore.episodes.collectAsState()
    val followed by PodcastStore.followed.collectAsState()
    val positions by PodcastStore.positions.collectAsState()
    val haptic = rememberHaptic()
    val isFollowed = followed.any { it.feedUrl == podcast.feedUrl }
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val onSpotify = podcast.fromSpotify || spotifyShows.any { it.feedUrl == podcast.feedUrl }
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
                    if (onSpotify && !isFollowed) {
                        // Salvato su Spotify: si segue (e si toglie) da lì, qui arriva da solo.
                        Text("Nella tua libreria di Spotify", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else if (isFollowed) {
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
private fun PodcastRow(podcast: Podcast, first: Boolean, last: Boolean, onOpen: () -> Unit) {
    val followed by PodcastStore.followed.collectAsState()
    val spotify by PodcastStore.spotify.collectAsState()
    val isFollowed = remember(followed, spotify, podcast) { PodcastStore.isInLibrary(podcast) }
    Box(Modifier.fillMaxWidth().cardGroupItem(first, last).clickable(onClick = onOpen)) {
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
        TileCaption(podcast.title, null)
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    now: PodcastNow?,
    savedMs: Long,
    showPodcast: Boolean,
    /** Prima e ultima di un gruppo: righe di un'unica card (1.423). null = card a sé, come nella pagina del podcast. */
    group: Pair<Boolean, Boolean>? = null,
) {
    val playing = now?.episode?.id == episode.id
    val today = remember { ZonedDateTime.now() }
    val durationMs = episode.durationSec * 1000
    val started = PodcastProgress.resumeAt(savedMs, durationMs) > 0
    val finished = durationMs > 0 && savedMs >= durationMs - 30_000
    val container = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card
    val tap = Modifier.tapOrSave({ episodeLink(episode, PodcastProgress.resumeAt(savedMs, durationMs)) }) {
        if (playing) PodcastStore.togglePause() else PodcastStore.play(episode)
    }
    Surface(
        shape = if (group != null) androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.large,
        color = if (group != null) androidx.compose.ui.graphics.Color.Transparent else container,
        border = if (playing || group != null) null else cardBorder(),
        modifier = if (group != null) Modifier.fillMaxWidth().cardGroupItem(group.first, group.second, container).then(tap)
        else Modifier.fillMaxWidth().bouncyPress(0.97f).clip(MaterialTheme.shapes.large).then(tap),
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

/** "12:05" o "1:02:03". */
internal fun clockLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
