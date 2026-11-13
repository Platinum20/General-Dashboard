package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.captureTarget
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.NotesStore

/**
 * Le cose senza scadenza (Antonio, 2026-11-12): cose da fare, note, foto e pacchi senza data stanno nella card
 * "Per dopo" della Home (1.490: prima erano in fondo al Calendario e sembravano un pezzo staccato), non nei giorni.
 * Quello salvato per dopo sta nella sua sezione: video, brani, radio e podcast in Media, articoli in News ("Salvati"). Le cose salvate restano note: la sincronia con "Guarda più tardi" di YouTube e lo
 * Zaino del treno le leggono da lì.
 */
internal object LaterText {
    /** La riga sotto il titolo: chi lo pubblica, altrimenti che cos'è. */
    fun subtitle(item: ResumeItem): String = item.subtitle.ifBlank {
        when (item.kind) {
            ResumeKind.YOUTUBE, ResumeKind.VIDEO -> "Video"
            ResumeKind.MUSIC, ResumeKind.SPOTIFY -> "Brano"
            ResumeKind.RADIO -> "Radio"
            ResumeKind.PODCAST -> "Podcast"
        }
    }
}

/**
 * La card "Per dopo": tutte le righe, niente nascosto, il titolo dentro come le altre card e il numero a destra.
 * Ogni riga ha a sinistra la spunta (cose da fare) o l'immagine, il nome e al massimo una riga sotto.
 */
@Composable
internal fun LaterCard(items: List<LooseItem>, onOpenArchive: () -> Unit) {
    val open = rememberDueOpen()
    val haptic = rememberHaptic()
    val rowActions = rememberRowActions()
    // Solo le cose ancora da fare (Antonio, 2026-11-12); toccando la card, fuori dalle righe, si apre l'archivio.
    CanvasCard(Modifier.fillMaxWidth().captureTarget(CaptureMode.NOTE), onClick = onOpenArchive) {
        CardTitle("Per dopo", trailing = {
            if (items.isNotEmpty()) Text(looseCount(items.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        })
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (items.isEmpty()) {
                Text("Tutto fatto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items.forEach { item ->
                val onClick = {
                    haptic(Haptic.TICK)
                    item.row?.let(open)
                    Unit
                }
                LooseRow(
                    item,
                    onClick = onClick,
                    // Tenendo premuto: il fumetto con le sue azioni, come nel Calendario (Antonio, 2026-11-12).
                    onLongClick = { item.row?.let { haptic(Haptic.LONG_PRESS); rowActions(it) } ?: onClick() },
                    onTick = item.row?.task?.let { task -> { haptic(Haptic.TICK); NotesStore.toggleTask(task.id) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LooseRow(item: LooseItem, onClick: () -> Unit, onLongClick: () -> Unit, onTick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when {
                onTick != null -> Icon(
                    if (item.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (item.done) "Da fare" else "Fatto",
                    tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onTick),
                )
                item.image.isNotBlank() -> AsyncImage(
                    model = item.image.let { if (it.startsWith("/")) java.io.File(it) else it },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                else -> IconTile(looseIcon(item), TileAccent.NEUTRAL, size = 32.dp, iconSize = 18.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium.let { if (item.done) it.copy(textDecoration = TextDecoration.LineThrough) else it },
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.line?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Fissata: la puntina a destra dice perché sta in cima (Antonio, 2026-11-13).
        if (item.pinned) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.PushPin, contentDescription = "Fissata", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

private fun looseIcon(item: LooseItem) = when (item.kind) {
    LooseKind.PARCEL -> Icons.Outlined.Inventory2
    else -> Icons.AutoMirrored.Outlined.StickyNote2
}

/**
 * L'archivio di Per dopo: le cose fatte, dalla più recente. Toccando il cerchio pieno tornano da fare (e quindi
 * nella card); toccando la riga si apre la sua scheda.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LaterArchiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val items = remember(data.tasks) { archivedItems(data.tasks) }
    val open = rememberDueOpen()
    val rowActions = rememberRowActions()
    val haptic = rememberHaptic()
    val bar = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Archivio", subtitle = if (items.isEmpty()) "Per dopo" else "Per dopo · ${items.size} fatte", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            if (items.isEmpty()) item {
                Text("Qui finiscono le cose che spunti.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(items.size, key = { items[it].key }) { i ->
                val item = items[i]
                LooseRow(
                    item,
                    onClick = { haptic(Haptic.TICK); item.row?.let(open) },
                    // Tenendo premuto: il fumetto delle sue azioni (torna da fare, condividi, elimina), come nella card (Antonio, 2026-11-13).
                    onLongClick = { item.row?.let { haptic(Haptic.LONG_PRESS); rowActions(it) } },
                    onTick = item.row?.task?.let { task -> { haptic(Haptic.TICK); NotesStore.toggleTask(task.id) } },
                )
            }
        }
    }
}

/** "Togli dai salvati", ultima riga di ogni fumetto delle cose salvate. */
private fun forgetAction(note: com.platinum20.dashboard.notes.Note, title: String) =
    BubbleAction(Icons.Rounded.BookmarkRemove, "Togli dai salvati", danger = true, onClick = { SavedLinks.forget(note, "Tolto: $title") })

/**
 * Tenendo premuto un video, un brano o un episodio salvato (Antonio, 2026-11-13): le stesse azioni degli altri media.
 * Riproduci adesso, subito dopo quello che suona o in fondo alla coda; per i brani di Spotify anche Radio e playlist.
 */
internal fun savedMediaActions(context: android.content.Context, later: SavedLinks.Later, openRoute: (String) -> Unit) {
    val item = later.item
    val seed = com.platinum20.dashboard.media.spotify.MadeStore.seedOf(item)
    val queue = SavedLinks.queueable(item)
    ActionBubble.show(
        *listOfNotNull(
            BubbleAction(Icons.Rounded.PlayArrow, "Riproduci", detail = item.title) {
                SavedLinks.pageRoute(item)?.let(openRoute) ?: openResumeItem(context, item, openRoute = openRoute)
            },
            if (!queue) null else BubbleAction(Icons.Rounded.PlaylistPlay, "Riproduci dopo", detail = "Subito dopo quello che suona") {
                CanvasPlayer.addNext(item)
                CaptureFeedback.tell(context, "Dopo questo: ${item.title}", Icons.Rounded.PlaylistPlay)
            },
            if (!queue) null else BubbleAction(Icons.Rounded.QueueMusic, "Aggiungi alla coda", detail = "In fondo") {
                CanvasPlayer.add(item)
                CaptureFeedback.tell(context, "In coda: ${item.title}", Icons.Rounded.QueueMusic)
            },
            seed?.let {
                BubbleAction(Icons.Rounded.Radio, "Radio", detail = "Brani simili a ${item.title}") {
                    CaptureFeedback.tell(context, "Preparo la radio di ${item.title}", Icons.Rounded.Radio)
                    com.platinum20.dashboard.media.spotify.MadeStore.startRadio(context, it) { error -> CaptureFeedback.tell(context, error) }
                }
            },
            seed?.let { BubbleAction(Icons.Rounded.PlaylistAdd, "Aggiungi a una playlist", detail = "Una delle tue o una nuova") { PlaylistPicker.open(context, it) } },
            goToCreatorAction(context, item, openRoute),
            forgetAction(later.note, item.title),
        ).toTypedArray(),
    )
}

/** Tenendo premuto un articolo salvato: leggilo, mandalo a qualcuno o toglilo. */
private fun savedArticleActions(context: android.content.Context, entry: SavedLinks.ToRead, read: () -> Unit) {
    val title = entry.article.title.ifBlank { "Articolo" }
    ActionBubble.show(
        BubbleAction(Icons.AutoMirrored.Rounded.Article, "Leggi", detail = title, onClick = read),
        BubbleAction(Icons.Rounded.Share, "Condividi") { shareText(context, entry.article.url) },
        forgetAction(entry.note, title),
    )
}

/**
 * "Salvati" in cima a News: gli articoli messi da parte. Aprendone uno esce dalla lista (l'hai letto),
 * con "Annulla" nel fumetto se era un tocco per sbaglio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SavedArticlesBlock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val toRead = remember(data.notes) { SavedLinks.toRead(data.notes) }
    if (toRead.isEmpty()) return
    // Aprendolo esce dai salvati (l'hai letto), con Annulla nel fumetto.
    val read: (SavedLinks.ToRead) -> Unit = { entry ->
        SavedLinks.open(context, entry.article) { inApp?.openRoute?.invoke(it) }
        SavedLinks.forget(entry.note, "Letto: tolto dai salvati")
    }
    Column(modifier) {
        SectionHeader("Salvati · ${toRead.size}")
        toRead.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .bouncyPress()
                    .combinedClickable(
                        onClick = { read(entry) },
                        onLongClick = { savedArticleActions(context, entry) { read(entry) } },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (entry.article.image.isNotBlank()) {
                        AsyncImage(model = entry.article.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.article.title.ifBlank { "Articolo" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val source = listOf(entry.article.source, "salvato " + relativeTime(java.time.Instant.ofEpochMilli(entry.note.createdAt))).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
