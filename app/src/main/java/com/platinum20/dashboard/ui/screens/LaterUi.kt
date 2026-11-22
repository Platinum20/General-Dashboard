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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.graphics.luminance
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
 * Quello salvato per dopo sta nella sua sezione: video, brani, radio e podcast in Media (e da 2026-11-21 anche in Appunti),
 * articoli in News ("Salvati"). Le cose salvate restano note: la sincronia con "Guarda più tardi" e i download le leggono da lì.
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
    internal val mode: CaptureMode,
) {
    TODO("Da fare", "Tutto fatto.", CaptureMode.TASK),
    JOTTINGS("Appunti", "Nessun appunto.", CaptureMode.NOTE),
}


/**
 * Una card senza data: tutte le righe, niente nascosto, il titolo dentro come le altre card e il numero a destra.
 * Ogni riga ha a sinistra la spunta (cose da fare) o l'immagine, il nome e al massimo una riga sotto.
 */
@Composable
internal fun LaterCard(
    kind: LooseCardKind,
    items: List<LooseItem>,
    onOpen: () -> Unit,
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
    val (savedOpen, savedLong) = rememberSavedTaps()
    // Toccando la card, fuori dalle righe, si apre la sua pagina intera: attive e vecchie insieme (1.539).
    CanvasCard(Modifier.fillMaxWidth().captureTarget(kind.mode), onClick = onOpen) {
        CardTitle(kind.title, opens = true, trailing = {
            if (count > 0) Text(looseCount(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        })
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            // In cima le abitudini (1.535): tornano ogni giorno, spuntate si spengono fino a domani e non vanno in "Fatte".
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
                    item.saved?.let(savedOpen) ?: item.row?.let(open)
                    Unit
                }
                LooseRow(
                    item,
                    onClick = onClick,
                    // Tenendo premuto: il fumetto con le sue azioni, come nel Calendario (Antonio, 2026-11-12).
                    onLongClick = { item.saved?.let { haptic(Haptic.LONG_PRESS); savedLong(it) } ?: item.row?.let { haptic(Haptic.LONG_PRESS); rowActions(it) } ?: onClick() },
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
                onTick != null -> CanvasCheck(
                    item.done,
                    size = 21.dp,
                    description = if (item.done) "Da fare" else "Fatto",
                    onToggle = onTick,
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
            CanvasCheck(
                done,
                size = 21.dp,
                description = if (done) "Togli da oggi" else "Fatta oggi",
                onToggle = onTick,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            line?.let {
                Text(
                    it.text,
                    style = MaterialTheme.typography.bodySmall,
                    // Arancione vero in ogni tema: con Material You il terziario viene dallo sfondo e sembrava grigio.
                    color = if (it.urgent) Urgent.color(MaterialTheme.colorScheme.background.luminance() < 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        habitFlame(habit, now.toLocalDate())?.let { days ->
            Spacer(Modifier.width(8.dp))
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            androidx.compose.material3.Icon(HabitFlameIcon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(3.dp))
            Text("$days", style = MaterialTheme.typography.labelLarge, color = ink,
                modifier = Modifier.semantics { contentDescription = "$days di fila" })
        }
    }
}

/**
 * La fiamma della serie delle abitudini (1.545, Antonio: "disegna tu un'icona in stile Canvas" al posto di 🔥):
 * contorno a tratto arrotondato come le icone Outlined, con la fiammella interna; colore = inchiostro (regola 2).
 */
internal val HabitFlameIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    val stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "HabitFlame", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        // Il corpo: punta in alto, lingua a sinistra, base tonda.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathBuilder().apply {
                moveTo(12f, 2.8f)
                curveTo(15.5f, 6.2f, 18.5f, 9.6f, 18.5f, 14.2f)
                arcTo(6.5f, 6.5f, 0f, false, true, 5.5f, 14.2f)
                curveTo(5.5f, 11.4f, 7f, 9.2f, 8.6f, 7.6f)
                curveTo(8.8f, 9.4f, 9.6f, 10.6f, 10.8f, 11.2f)
                curveTo(10.6f, 8f, 11f, 5.2f, 12f, 2.8f)
                close()
            }.nodes,
            stroke = stroke, strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        // La fiammella dentro, piena: si legge anche a 16 dp.
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathBuilder().apply {
                moveTo(12f, 14.2f)
                curveTo(13.4f, 15.3f, 14.4f, 16.4f, 14.4f, 17.6f)
                arcTo(2.4f, 2.4f, 0f, false, true, 9.6f, 17.6f)
                curveTo(9.6f, 16.4f, 10.6f, 15.3f, 12f, 14.2f)
                close()
            }.nodes,
            fill = stroke,
        )
    }.build()
}

/** Tocco e pressione lunga di un media salvato in Appunti: si apre fermo come in Media, e ha le stesse azioni. */
@Composable
private fun rememberSavedTaps(): Pair<(SavedLinks.Later) -> Unit, (SavedLinks.Later) -> Unit> {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val route: (String) -> Unit = { inApp?.openRoute?.invoke(it) }
    return remember(context, inApp) {
        Pair(
            { l: SavedLinks.Later -> SavedLinks.open(context, com.platinum20.dashboard.capture.SavedLink.Media(l.item), route) },
            { l: SavedLinks.Later -> savedMediaActions(context, l, route) },
        )
    }
}

private fun looseIcon(item: LooseItem) = when (item.kind) {
    LooseKind.PARCEL -> Icons.Outlined.Inventory2
    LooseKind.SAVED -> Icons.Rounded.PlayArrow
    else -> Icons.AutoMirrored.Outlined.StickyNote2
}

/**
 * La pagina intera di Da fare o Appunti (1.539, Antonio, mockup archivi-card 1): come il Calendario, le cose vecchie
 * sopra (fatte per giorno, archiviati per mese, sbiadite), la linea ADESSO e sotto quelle attive. Si apre su ADESSO.
 * Toccando il cerchio pieno una fatta torna da fare; tenendo premuto ogni riga ha le sue azioni (Archivia/Rimetti, Elimina).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LaterArchiveScreen(kind: LooseCardKind = LooseCardKind.TODO, onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val todo = kind == LooseCardKind.TODO
    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate()
    val archived = remember(data, kind) { if (todo) archivedItems(data.tasks) else archivedNotes(data.notes) }
    val groups = remember(archived, today) { archiveGroups(archived, byMonth = !todo, today = today) }
    // Le attive come nella card della Home: stessa lista, stesso ordine.
    val active = remember(data, kind, today) {
        val loose = looseItems(dueDays(data.tasks, data.parcels, emptyList(), withToday = true, notes = data.notes), data.notes, today).pending()
        if (todo) loose.todos() else loose.jottings()
    }
    val habits = if (todo) data.habits else emptyList()
    val open = rememberDueOpen()
    val openItem = rememberOpenItem()
    val rowActions = rememberRowActions()
    val (savedOpen, savedLong) = rememberSavedTaps()
    val haptic = rememberHaptic()
    // Si apre sulla linea ADESSO: sopra c'è il passato, come nel Calendario.
    val marker = groups.sumOf { it.items.size + 1 }
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = if (groups.isEmpty()) 0 else marker)
    val bar = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    val activeCount = active.size + habits.count { !it.isDone(today) }
    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = kind.title, subtitle = loosePageLine(todo, activeCount, archived.size), scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(inner), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp)) {
            groups.forEach { g ->
                item(key = "g-${g.label}") { SectionHeader(g.label, modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp)) }
                g.items.forEach { item ->
                    item(key = item.key) {
                        Box(Modifier.graphicsLayer { alpha = FADED_ALPHA }) {
                            LooseRow(
                                item,
                                onClick = { haptic(Haptic.TICK); item.row?.let(open) },
                                onLongClick = { item.row?.let { haptic(Haptic.LONG_PRESS); rowActions(it) } },
                                onTick = item.row?.task?.let { task -> { haptic(Haptic.TICK); NotesStore.toggleTask(task.id) } },
                            )
                        }
                    }
                }
            }
            if (groups.isNotEmpty()) item(key = "adesso") { NewsVisitMarker("Adesso", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            habits.forEach { h ->
                item(key = "habit-${h.id}") {
                    HabitTodoRow(
                        h, now,
                        onClick = { haptic(Haptic.TICK); openItem(ItemKind.HABIT, h.id) },
                        onLongClick = { haptic(Haptic.LONG_PRESS); ActionBubble.show(*habitActions(h, today) { openItem(ItemKind.HABIT, it.id) }) },
                        onTick = { haptic(if (h.isDone(today)) Haptic.TICK else Haptic.CONFIRM); NotesStore.toggleHabit(h.id, today) },
                    )
                }
            }
            if (active.isEmpty() && habits.isEmpty()) item(key = "empty") {
                Text(kind.empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            active.forEach { item ->
                item(key = item.key) {
                    LooseRow(
                        item,
                        onClick = { haptic(Haptic.TICK); item.saved?.let(savedOpen) ?: item.row?.let(open) },
                        onLongClick = { haptic(Haptic.LONG_PRESS); item.saved?.let(savedLong) ?: item.row?.let(rowActions) },
                        onTick = item.row?.task?.let { task -> { haptic(Haptic.TICK); NotesStore.toggleTask(task.id) } },
                    )
                }
            }
        }
    }
}

/** "Togli dai salvati", ultima riga di ogni fumetto delle cose salvate. */
private fun forgetAction(note: com.platinum20.dashboard.notes.Note, title: String) =
    BubbleAction(Icons.Rounded.BookmarkRemove, "Togli dai salvati", "sparisce dagli Appunti", danger = true, onClick = { SavedLinks.forget(note, "Tolto: $title") })

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
        BubbleAction(Icons.Rounded.Share, "Condividi", "il link dell'articolo") { shareText(context, entry.article.url) },
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
