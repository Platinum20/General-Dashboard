package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.QueueEntry
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpotifyAuth

private fun ResumeItem.queueIcon(): ImageVector = when (kind) {
    ResumeKind.VIDEO -> Icons.Outlined.PlayCircle
    ResumeKind.YOUTUBE -> Icons.Outlined.OndemandVideo
    ResumeKind.MUSIC -> Icons.Outlined.MusicNote
    ResumeKind.SPOTIFY -> Icons.Outlined.GraphicEq
    ResumeKind.RADIO -> Icons.Outlined.Radio
    ResumeKind.PODCAST -> Icons.Outlined.Mic
}

private fun ResumeItem.queueAccent(): TileAccent = when (kind) {
    ResumeKind.VIDEO -> TileAccent.BLUE
    ResumeKind.YOUTUBE -> TileAccent.ORANGE
    ResumeKind.MUSIC, ResumeKind.SPOTIFY -> TileAccent.GREEN
    ResumeKind.RADIO, ResumeKind.PODCAST -> TileAccent.PURPLE
}

private fun ResumeItem.sourceLabel(): String = when (kind) {
    ResumeKind.VIDEO -> "Video"
    ResumeKind.YOUTUBE -> "YouTube"
    ResumeKind.MUSIC -> "YouTube Music"
    ResumeKind.SPOTIFY -> "Spotify"
    ResumeKind.RADIO -> "Radio"
    ResumeKind.PODCAST -> "Podcast"
}

/**
 * La coda unica: in cima quello che suona, sotto quello che viene dopo, di qualunque fonte.
 * Un tocco fa partire quella voce (le altre restano); la maniglia sceglie cosa viene dopo; portandola
 * di lato si toglie, con "Annulla". In fondo "Riempi in automatico" per non restare senza musica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val queue by CanvasPlayer.queue.collectAsState()
    val now = queue.now
    val upcoming = queue.upcoming
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopLevelBar(
                title = "Coda",
                subtitle = if (upcoming.isEmpty()) null else "${upcoming.size} dopo questo",
                onBack = onBack,
            ) {
                if (upcoming.isNotEmpty()) TextButton(onClick = { CanvasPlayer.clearUpcoming() }) { Text("Svuota") }
            }
        },
    ) { inner ->
        if (now == null && upcoming.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconTile(Icons.Outlined.QueueMusic, TileAccent.BLUE, size = 64.dp, iconSize = 32.dp)
                Spacer(Modifier.size(12.dp))
                Text("La coda è vuota", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tieni premuto un video, una canzone, una radio o un episodio e scegli \"Aggiungi alla coda\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val view = androidx.compose.ui.platform.LocalView.current
        val upcomingIds = upcoming.map { it.id }.toSet()
        val reorder = rememberReorderState(listState, canMove = { it in upcomingIds }) { from, to ->
            view.haptic(Haptic.TICK)
            CanvasPlayer.moveTo(from as Long, to as Long)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (now != null) {
                item(key = "h-now") { SectionHeader("In riproduzione") }
                item(key = now.id) {
                    QueueRow(now, current = true, modifier = Modifier.animateItem(), onTap = {})
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "h-next") { SectionHeader("Dopo", Modifier.padding(top = 8.dp)) }
                items(upcoming, key = { it.id }) { entry ->
                    SwipeQueueRow(
                        entry,
                        modifier = (if (reorder.isDragging(entry.id)) Modifier else Modifier.animateItem()).reorderItem(reorder, entry.id),
                        handle = Modifier.reorderHandle(reorder, entry.id) { view.haptic(Haptic.GRAB) },
                    )
                }
            }
        }
    }
}

/**
 * Le prossime voci della coda in Media, al posto della card "Riprendi": si vede cosa viene dopo senza
 * aprire la coda. Un tocco salta lì; "Apri" porta alla coda intera.
 */
@Composable
internal fun QueuePeek(onOpenQueue: () -> Unit, modifier: Modifier = Modifier) {
    val queue by CanvasPlayer.queue.collectAsState()
    val next = queue.upcoming
    if (next.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("In coda · ${next.size}", Modifier.weight(1f))
            TextButton(onClick = onOpenQueue) { Text("Apri") }
        }
        next.take(3).forEach { entry -> androidx.compose.runtime.key(entry.id) { SwipeQueueRow(entry) } }
    }
}

/** Una voce che viene dopo: un tocco la fa partire, portata di lato esce dalla coda (con "Annulla"). */
@Composable
private fun SwipeQueueRow(entry: QueueEntry, modifier: Modifier = Modifier, handle: Modifier? = null) {
    val context = LocalContext.current
    val swipe = rememberSwipeAwayState()
    Box(modifier) {
        SwipeAwayMark(swipe)
        QueueRow(
            entry,
            current = false,
            modifier = Modifier.swipeAway(swipe) {
                val index = CanvasPlayer.queue.value.entries.indexOfFirst { it.id == entry.id }
                CanvasPlayer.remove(entry.id)
                CaptureFeedback.tell(context, "Tolto dalla coda", Icons.Outlined.RemoveCircleOutline, "Annulla") {
                    CanvasPlayer.restore(entry, index)
                }
            },
            handle = handle,
            onTap = { CanvasPlayer.jump(entry.id) },
        )
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    current: Boolean,
    modifier: Modifier = Modifier,
    /** La maniglia per trascinare la riga (nella coda intera). */
    handle: Modifier? = null,
    onTap: () -> Unit,
) {
    val item = entry.item
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card)
            .clickable(enabled = !current, onClick = onTap)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            IconTile(item.queueIcon(), item.queueAccent(), size = 52.dp)
            if (item.image.isNotBlank()) {
                AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.subtitle, item.sourceLabel()).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!current) {
            if (handle != null) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Trascina per spostare",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = handle.padding(8.dp).size(24.dp),
                )
            }
        }
    }
}
