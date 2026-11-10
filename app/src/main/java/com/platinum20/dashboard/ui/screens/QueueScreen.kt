package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.QueueEntry
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind

/** Come si mostra una voce della coda (1.455). Pura per i test. */
internal object QueueLook {
    /** Video e YouTube hanno la miniatura larga (16:9), il resto la copertina quadrata. */
    fun wide(kind: ResumeKind): Boolean = kind == ResumeKind.VIDEO || kind == ResumeKind.YOUTUBE

    /** La riga sotto il titolo: autore, poi durata (se c'è); la fonte solo se manca l'autore. */
    fun line(subtitle: String, source: String, durationMs: Long): String {
        val who = subtitle.ifBlank { source }
        val length = if (durationMs > 0) duration(durationMs) else ""
        return listOf(who, length).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** "3:07", "1:02:45". */
    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Il numero accanto al titolo "Coda". */
    fun count(upcoming: Int): String? = when (upcoming) {
        0 -> null
        1 -> "1 dopo"
        else -> "$upcoming dopo"
    }
}

private fun ResumeItem.queueIcon(): ImageVector = when (kind) {
    ResumeKind.VIDEO -> Icons.Outlined.PlayCircle
    ResumeKind.YOUTUBE -> Icons.Outlined.OndemandVideo
    ResumeKind.MUSIC -> Icons.Outlined.MusicNote
    ResumeKind.SPOTIFY -> Icons.Outlined.GraphicEq
    ResumeKind.RADIO -> Icons.Outlined.Radio
    ResumeKind.PODCAST -> Icons.Outlined.Mic
}

private fun ResumeItem.sourceLabel(): String = when (kind) {
    ResumeKind.VIDEO -> "Video"
    ResumeKind.YOUTUBE -> "YouTube"
    ResumeKind.MUSIC -> "Musica"
    ResumeKind.SPOTIFY -> "Spotify"
    ResumeKind.RADIO -> "Radio"
    ResumeKind.PODCAST -> "Podcast"
}

private fun ResumeItem.queueLine(): String = QueueLook.line(subtitle, sourceLabel(), durationMs)

/** Miniatura della voce: larga per i video, quadrata per il resto; l'icona se non c'è immagine. */
@Composable
private fun QueueCover(item: ResumeItem, width: Dp, modifier: Modifier = Modifier) {
    val wide = QueueLook.wide(item.kind)
    Box(
        modifier
            .width(width)
            .aspectRatio(if (wide) 16f / 9f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(item.queueIcon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        if (item.image.isNotBlank()) {
            AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * La voce che suona, in alto (l'unico blocco colorato del pannello). [stacked]: copertina grande sopra il titolo,
 * per la colonna sinistra della coda in orizzontale; se no copertina a sinistra e titolo accanto.
 */
@Composable
private fun NowPlayingHero(entry: QueueEntry, stacked: Boolean, modifier: Modifier = Modifier) {
    val item = entry.item
    val colors = MaterialTheme.colorScheme
    val texts: @Composable () -> Unit = {
        Text(
            "IN RIPRODUZIONE",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onPrimaryContainer,
            maxLines = if (stacked) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            item.queueLine(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val box = modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.extraLarge)
        .background(colors.primaryContainer)
        .padding(12.dp)
    if (stacked) {
        Column(box) {
            val wide = QueueLook.wide(item.kind)
            QueueCover(
                item,
                width = Dp.Unspecified,
                modifier = Modifier.fillMaxWidth(if (wide) 1f else 0.72f).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.size(12.dp))
            texts()
        }
    } else {
        Row(box, verticalAlignment = Alignment.CenterVertically) {
            QueueCover(item, width = if (QueueLook.wide(item.kind)) 128.dp else 80.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { texts() }
        }
    }
}

@Composable
private fun EmptyQueue(modifier: Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(Icons.Outlined.QueueMusic, TileAccent.NEUTRAL, size = 64.dp, iconSize = 32.dp)
        Spacer(Modifier.size(12.dp))
        Text("La coda è vuota", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tieni premuto un video, una canzone, una radio o un episodio e scegli \"Aggiungi alla coda\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Le voci dopo quella che suona: righe libere sul fondo (niente card né separatori, 1.455), trascinabili dalla maniglia.
 * [now]: la voce che suona come prima riga della lista (in verticale), null se sta in un'altra colonna.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun UpcomingList(now: QueueEntry?, upcoming: List<QueueEntry>, modifier: Modifier, contentPadding: PaddingValues) {
    val listState = rememberLazyListState()
    val view = LocalView.current
    val upcomingIds = upcoming.map { it.id }.toSet()
    val reorder = rememberReorderState(listState, canMove = { it in upcomingIds }) { from, to ->
        view.haptic(Haptic.TICK)
        CanvasPlayer.moveTo(from as Long, to as Long)
    }
    LazyColumn(modifier, state = listState, contentPadding = contentPadding) {
        if (now != null) {
            item(key = now.id) { NowPlayingHero(now, stacked = false, modifier = Modifier.animateItem().padding(bottom = 8.dp)) }
        }
        if (upcoming.isNotEmpty()) {
            // "Svuota" non è un tasto (1.357): si tiene premuto "Dopo", come "Elimina i completati".
            item(key = "h-next") {
                SectionHeader(
                    "Dopo",
                    Modifier.padding(top = 8.dp, start = 8.dp).combinedClickable(onClick = {}, onLongClick = {
                        view.haptic(Haptic.LONG_PRESS)
                        ActionBubble.show(
                            BubbleAction(
                                Icons.Outlined.ClearAll,
                                "Svuota la coda",
                                detail = if (upcoming.size == 1) "1 voce dopo questa" else "${upcoming.size} voci dopo questa",
                                danger = true,
                            ) { CanvasPlayer.clearUpcoming() },
                        )
                    }),
                )
            }
            items(upcoming, key = { it.id }) { entry ->
                SwipeQueueRow(
                    entry,
                    modifier = (if (reorder.isDragging(entry.id)) Modifier else Modifier.animateItem()).reorderItem(reorder, entry.id),
                    handle = Modifier.reorderHandle(reorder, entry.id) { view.haptic(Haptic.GRAB) },
                )
            }
        } else if (now != null) {
            item(key = "nothing-next") { NothingNext() }
        }
    }
}

@Composable
private fun NothingNext() {
    Text(
        "Dopo questo non c'è altro. Tieni premuto qualcosa e scegli \"Aggiungi alla coda\".",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
    )
}

/**
 * La coda come pannello sopra il player (video o audio) e sopra Media, invece di un'altra pagina.
 * In verticale sale dal basso: in alto la voce che suona, sotto quelle dopo. In orizzontale entra da destra
 * a tutta altezza e, se c'è posto, si divide in due colonne: a sinistra cosa suona con la copertina grande,
 * a destra cosa viene dopo. Il video dietro resta a schermo intero.
 */
@Composable
internal fun QueuePopup(open: Boolean, onClose: () -> Unit, bottomExtra: Dp = 0.dp) {
    val queue by CanvasPlayer.queue.collectAsState()
    val now = queue.now
    val upcoming = queue.upcoming
    PlayerSheet(
        open = open,
        onClose = onClose,
        title = "Coda",
        subtitle = QueueLook.count(upcoming.size),
        fixedHeight = 0.78f,
        sideShare = 0.62f,
        sideMax = 680.dp,
    ) { sideways, width ->
        val listPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp + bottomExtra)
        when {
            now == null && upcoming.isEmpty() -> EmptyQueue(Modifier.fillMaxWidth().weight(1f))
            sideways && now != null && QueueSheet.twoColumns(width.value) -> Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 4.dp, bottom = 16.dp),
                ) {
                    NowPlayingHero(now, stacked = true)
                }
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    if (upcoming.isEmpty()) {
                        NothingNext()
                    } else {
                        UpcomingList(null, upcoming, Modifier.fillMaxSize(), listPadding)
                    }
                }
            }
            else -> UpcomingList(now, upcoming, Modifier.fillMaxWidth().weight(1f), listPadding)
        }
    }
}

/**
 * Le prossime voci della coda in Media, al posto della card "Riprendi": si vede cosa viene dopo senza
 * aprire la coda. Un tocco salta lì; il titolo apre la coda intera (lo stesso pannello del player).
 */
@Composable
internal fun QueuePeek(onOpenQueue: () -> Unit, modifier: Modifier = Modifier) {
    val queue by CanvasPlayer.queue.collectAsState()
    val next = queue.upcoming
    if (next.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Il titolo stesso apre la coda, con lo stesso aspetto dei titoli delle file di Media.
        OpenableHeader("In coda · ${next.size}", onOpenQueue)
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

/**
 * Riga Canvas della coda: a sinistra la miniatura (sempre larga uguale, così i titoli sono in colonna),
 * al centro titolo e una riga (autore · durata), a destra la maniglia per spostarla.
 */
@Composable
private fun QueueRow(
    entry: QueueEntry,
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
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(80.dp), contentAlignment = Alignment.Center) {
            QueueCover(item, width = if (QueueLook.wide(item.kind)) 80.dp else 48.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                item.queueLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (handle != null) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Trascina per spostare",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handle.padding(start = 4.dp).padding(8.dp).size(24.dp),
            )
        }
    }
}
