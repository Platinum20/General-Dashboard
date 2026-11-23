package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.EpisodeDate
import com.platinum20.dashboard.media.podcast.Podcast
import com.platinum20.dashboard.media.podcast.PodcastHero
import com.platinum20.dashboard.media.podcast.PodcastNow
import com.platinum20.dashboard.media.podcast.PodcastProgress
import com.platinum20.dashboard.media.podcast.PodcastStore
import java.time.ZonedDateTime

/** Ricerche già pronte per chi non segue ancora nessun podcast. */
internal val podcastIdeas = listOf("Il Post", "Muschio Selvaggio", "Geopop", "Tintoria", "Storia")

/**
 * La pagina di un podcast nello stile delle pagine di Spotify e YouTube (1.572, Antonio): in cima il blocco grande
 * con la copertina, il nome una volta sola, l'autore, quale puntata parte col ▶ grande (quella da riprendere, se no
 * l'ultima uscita) e il tasto Segui; sotto le puntate sullo sfondo, senza card.
 */
@Composable
internal fun PodcastPage(podcast: Podcast, now: PodcastNow?, onBack: () -> Unit) {
    val all by PodcastStore.episodes.collectAsState()
    val followed by PodcastStore.followed.collectAsState()
    val positions by PodcastStore.positions.collectAsState()
    val haptic = rememberHaptic()
    val isFollowed = followed.any { it.feedUrl == podcast.feedUrl }
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val onSpotify = podcast.fromSpotify || spotifyShows.any { it.feedUrl == podcast.feedUrl }
    LaunchedEffect(podcast.feedUrl) { PodcastStore.loadEpisodes(podcast) }
    val state = all[podcast.feedUrl] ?: Loadable(loading = true)
    val episodes = state.data.orEmpty()
    val hero = PodcastHero.pick(episodes, positions)
    val today = remember { ZonedDateTime.now() }
    val heroPlaying = hero != null && now?.episode?.id == hero.id
    val header: @Composable () -> Unit = {
        MediaHero(
            title = podcast.title,
            image = podcast.artwork,
            onBack = onBack,
            mark = MediaMark(MediaType.PODCAST),
            line = podcast.author.ifBlank { if (onSpotify) "Da Spotify" else "Podcast" },
            detail = hero?.let { PodcastHero.line(it, positions[it.id] ?: 0, today) }
                ?: if (episodes.isNotEmpty()) "${episodes.size} puntate" else "",
            playing = heroPlaying && now?.playing == true,
            onPlay = if (hero == null) null else ({ if (heroPlaying) PodcastStore.togglePause() else PodcastStore.play(hero) }),
            onMore = if (hero == null) null else ({ com.platinum20.dashboard.capture.SavedLinks.offer(episodeLink(hero, PodcastProgress.resumeAt(positions[hero.id] ?: 0, hero.durationSec * 1000))) }),
            buttons = {
                // Salvato su Spotify: si segue (e si toglie) da lì, qui arriva da solo; altrimenti il tasto Segui.
                if (!(onSpotify && !isFollowed)) IconButton(onClick = {
                    haptic(if (isFollowed) Haptic.TICK else Haptic.CONFIRM)
                    PodcastStore.toggleFollow(podcast)
                }) {
                    Icon(
                        if (isFollowed) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                        contentDescription = if (isFollowed) "Non seguire più" else "Segui",
                        tint = if (isFollowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
    FeedList(state, empty = "Questo podcast non ha episodi da ascoltare", spacing = 0.dp, edge = 0.dp, top = 0.dp, header = header) {
        if (episodes.isNotEmpty()) mediaGroup(
            key = "eps",
            title = "Puntate",
            items = episodes,
            itemKey = { it.id },
            trailing = { Text("${episodes.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        ) { _, episode -> EpisodeLine(episode, now, positions[episode.id] ?: 0, today) }
    }
}

/**
 * La riga Canvas di una puntata, sullo sfondo: in mezzo il titolo e una riga (quando è uscita · quanto dura o quanto
 * manca), a destra ▶/❚❚; quella che suona ha il colore di "adesso", una iniziata la barra di quanto hai ascoltato.
 */
@Composable
private fun EpisodeLine(episode: Episode, now: PodcastNow?, savedMs: Long, today: ZonedDateTime) {
    val playing = now?.episode?.id == episode.id
    val durationMs = episode.durationSec * 1000
    val started = PodcastProgress.resumeAt(savedMs, durationMs) > 0
    val finished = durationMs > 0 && savedMs >= durationMs - 30_000
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).bouncyPress(0.98f).clip(MaterialTheme.shapes.medium)
            .tapOrSave({ episodeLink(episode, PodcastProgress.resumeAt(savedMs, durationMs)) }) {
                if (playing) PodcastStore.togglePause() else PodcastStore.play(episode)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playing) accent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (playing) FontWeight.SemiBold else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        EpisodeDate.label(episode.publishedAt, today),
                        if (finished) "ascoltata" else PodcastProgress.label(episode.durationSec, if (started) savedMs else 0),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                if (playing && now?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing && now?.playing == true) "Pausa" else "Ascolta",
                tint = accent,
            )
        }
        if (started && !finished && durationMs > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (savedMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
}

/**
 * Tenendo premuto un podcast (nella fila Musica, in "I tuoi podcast"): Apri e Segui / Non seguire più, come le
 * altre cose con una pagina (regola del tocco lungo, 1.513). Salvato su Spotify si segue da lì: c'è solo Apri.
 */
internal fun podcastActions(podcast: Podcast, followed: Boolean, onSpotify: Boolean, onOpen: () -> Unit) {
    val head = BubbleHead("Podcast", podcast.title)
    val open = Slot.open { onOpen() }
    // Salvato su Spotify si segue da lì: c'è solo Apri.
    if (onSpotify && !followed) return ActionBubble.show(head, open)
    ActionBubble.show(head, open, Slot.follow(followed) { PodcastStore.toggleFollow(podcast) })
}

/** "12:05" o "1:02:03". */
internal fun clockLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
