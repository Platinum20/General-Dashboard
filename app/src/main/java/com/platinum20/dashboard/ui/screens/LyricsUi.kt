package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Playing
import com.platinum20.dashboard.media.lyrics.Lrc
import com.platinum20.dashboard.media.lyrics.Lyrics
import com.platinum20.dashboard.media.lyrics.SongLyrics

/** Titolo e artista per cercare il testo: solo la musica (Spotify e YouTube Music), non radio, podcast e video. */
internal fun lyricsQuery(now: Playing): Pair<String, String>? {
    if (now.source != PlaySource.SPOTIFY && now.source != PlaySource.MUSIC) return null
    val artist = now.subtitle.substringBefore(" · ").substringBefore(", ").removeSuffix(" - Topic").trim()
    if (now.title.isBlank() || artist.isBlank()) return null
    return now.title to artist
}

/** Il testo del brano che suona, se LRCLIB ce l'ha (null mentre si cerca o se non c'è). */
@Composable
internal fun rememberLyrics(now: Playing?): SongLyrics? {
    val query = now?.let(::lyricsQuery)
    val cache by Lyrics.cache.collectAsState()
    LaunchedEffect(query) { query?.let { (title, artist) -> Lyrics.load(title, artist, now.durationMs) } }
    return query?.let { (title, artist) -> cache[Lyrics.keyOf(title, artist)] }
}

/**
 * Il testo al posto della copertina (1.491): la riga cantata adesso in chiaro e al centro, le altre sbiadite; un
 * tocco su una riga va a quel punto del brano. Dietro, la copertina sfocata, così resta il colore del brano.
 */
@Composable
internal fun LyricsPanel(now: Playing, lyrics: SongLyrics, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (now.artwork.isNotBlank()) {
            AsyncImage(
                model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.35f),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        if (lyrics.instrumental || lyrics.lines.isEmpty()) {
            Text(
                "Brano strumentale", style = MaterialTheme.typography.titleMedium, color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        val position = livePosition(now)
        val current = if (lyrics.synced) Lrc.index(lyrics.lines, position) else -1
        val list = rememberLazyListState()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val third = with(LocalDensity.current) { (maxHeight / 3).roundToPx() }
            // La riga di adesso sta a un terzo dall'alto, con quella prima ancora visibile.
            LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem(current, -third) }
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = maxHeight / 3),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
                    val past = lyrics.synced && i < current
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (i == current || !lyrics.synced) FontWeight.SemiBold else FontWeight.Medium,
                        color = Color.White.copy(alpha = if (i == current || !lyrics.synced) 1f else if (past) 0.45f else 0.6f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (lyrics.synced && now.canSeek) Modifier.clickable { Playback.seekTo(line.atMs) } else Modifier)
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}
