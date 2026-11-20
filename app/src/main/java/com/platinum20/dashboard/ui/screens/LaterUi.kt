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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * Le cose senza scadenza (Antonio, 2026-11-12; 1.532 divise in "Da fare" e "Appunti", i pacchi senza data in OGGI) stanno
 * nelle card della Home (1.490: prima erano in fondo al Calendario e sembravano un pezzo staccato), non nei giorni.
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
 * Le due card senza data (1.532, Antonio 2026-11-20): "Da fare" = le cose da spuntare, "Appunti" = note e foto.
 * Prima stavano insieme in "Per dopo" e non si capiva cosa si spunta e cosa no. Ognuna ha il suo archivio.
 */
enum class LooseCardKind(
    val title: String,
    val empty: String,
    /** La riga in fondo alla card e il sottotitolo dell'archivio: "Fatte · 12", "Archiviate · 8". */
    val archived: String,
    val archiveEmpty: String,
    internal val mode: CaptureMode,
) {
    TODO("Da fare", "Tutto fatto.", "Fatte", "Qui finiscono le cose che spunti.", CaptureMode.TASK),
    JOTTINGS("Appunti", "Nessun appunto.", "Archiviati", "Tieni premuto un appunto e scegli Archivia: finisce qui.", CaptureMode.NOTE),
}

/** La riga in fondo alla card: quante cose ci sono nell'archivio. Vuoto = non si mostra. */
internal fun archiveLine(kind: LooseCardKind, count: Int): String? = if (count <= 0) null else "${kind.archived} · $count"

/**
 * Una card senza data: tutte le righe, niente nascosto, il titolo dentro come le altre card e il numero a destra.
 * Ogni riga ha a sinistra la spunta (cose da fare) o l'immagine, il nome e al massimo una riga sotto.
 */
@Composable
internal fun LaterCard(
    kind: LooseCardKind,
    items: List<LooseItem>,
    archiveCount: Int,
    onOpenArchive: () -> Unit,
    habits: List<com.platinum20.dashboard.notes.Habit> = emptyList(),
) {
    val open = rememberDueOpen()
    val openItem = rememberOpenItem()
    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate()
    // Le abitudini di oggi ancora da fare contano come le altre cose da fare.
    val count = items.size + habits.count { !it.isDone(today) }
    val haptic = rememberHaptic()
    val rowActions = rememberRowActions()
    // Toccando la card, fuori dalle righe, si apre il suo archivio.
    CanvasCard(Modifier.fillMaxWidth().captureTarget(kind.mode), onClick = onOpenArchive) {
        CardTitle(kind.title, trailing = {
            if (count > 0) Text(looseCount(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        })
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            // In cima le abitudini (1.534): tornano ogni giorno, spuntate si spengono fino a domani e non vanno in "Fatte".
            habits.forEach { h ->
                HabitTodoRow(
                    h, now,
                    onClick = { haptic(Haptic.TICK); openItem(ItemKind.HABIT, h.id) },
                    onLongClick = { haptic(Haptic.LONG_PRESS); ActionBubble.show(*habitActions(h, today) { openItem(ItemKind.HABIT, it.id) }) },
                    onTick = { haptic(if (h.isDone(today)) Haptic.TICK else Haptic.CONFIRM); NotesStore.toggleHabit(h.id, today) },
                )
            }
            if (items.isEmpty() && habits.isEmpty()) {
                Text(kind.empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
            archiveLine(kind, archiveCount)?.let { line ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenArchive).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
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

/** Un'abitudine in "Da fare": la spunta di oggi, il nome, la serie a destra. Fatta = spenta fino a domani. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitTodoRow(
    habit: com.platinum20.dashboard.notes.Habit,
    now: java.time.LocalDateTime,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTick: () -> Unit,
) {
    val done = habit.isDone(now.toLocalDate())
    val line = habitTodoLine(habit, now)
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer { alpha = if (done) 0.5f else 1f }
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (done) "Togli da oggi" else "Fatta oggi",
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onTick),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            line?.let {
                Text(
                    it.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        habitFlame(habit, now.toLocalDate())?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun looseIcon(item: LooseItem) = when (item.kind) {
    LooseKind.PARCEL -> Icons.Outlined.Inventory2
    else -> Icons.AutoMirrored.Outlined.StickyNote2
}

/**
 * L'archivio di una card: per Da fare le cose fatte (toccando il cerchio pieno tornano da fare), per Appunti le note
 * archiviate (tenendo premuto: Rimetti negli appunti o Elimina). Toccando la riga si apre la sua scheda.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LaterArchiveScreen(kind: LooseCardKind = LooseCardKind.TODO, onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val items = remember(data, kind) { if (kind == LooseCardKind.TODO) archivedItems(data.tasks) else archivedNotes(data.notes) }
    val open = rememberDueOpen()
    val rowActions = rememberRowActions()
    val haptic = rememberHaptic()
    val bar = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Archivio", subtitle = archiveLine(kind, items.size)?.let { "${kind.title} · ${it.lowercase()}" } ?: kind.title, scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            if (items.isEmpty()) item {
                Text(kind.archiveEmpty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(items.size, key = { items[it].key }) { i ->
                val item = items[i]
                LooseRow(
                    item,
                    onClick = { haptic(Haptic.TICK); item.row?.let(open) },
                    // Tenendo premuto: il fumetto delle sue azioni, come nella card (Antonio, 2026-11-13).
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
