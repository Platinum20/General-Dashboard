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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.setValue
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
import com.platinum20.dashboard.notes.NoteLists
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
 * La card "Da fare" (1.616, Antonio 2026-11-24, mockup da-fare-appunti A3): una card sola con, dall'alto, le cose da
 * fare (al massimo [HOME_TODO_MAX], poi "altre N ›"), le abitudini, le liste delle note e, dopo una riga sottile, gli
 * appunti (note, foto, salvati). Nessuna intestazione in mezzo; una nota con le spunte compare una volta sola, come lista.
 * Il numero a destra conta quello che ha ancora qualcosa da spuntare.
 */
@Composable
internal fun LaterCard(
    items: List<LooseItem>,
    jottings: List<LooseItem>,
    onOpen: () -> Unit,
    habits: List<com.platinum20.dashboard.notes.Habit> = emptyList(),
    /** Le note: quelle con delle spunte aperte diventano una riga di lista (2026-11-23, idea 4). */
    notes: List<com.platinum20.dashboard.notes.Note> = emptyList(),
) {
    val kind = LooseCardKind.TODO
    val open = rememberDueOpen()
    val openItem = rememberOpenItem()
    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate()
    // Le liste già in vista restano anche finite: spuntando l'ultima voce la riga non sparisce sotto il dito.
    var keptLists by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(emptyList<Long>()) }
    val lists = remember(notes, keptLists) { NoteLists.rows(notes, keptLists.toSet()) }
    androidx.compose.runtime.LaunchedEffect(lists) {
        val ids = lists.map { it.noteId }
        if (!keptLists.containsAll(ids)) keptLists = (keptLists + ids).distinct()
    }
    val notesBelow = remember(jottings, lists) { jottings.withoutLists(lists.map { it.noteId }.toSet()) }
    // Le abitudini di oggi ancora da fare contano come le altre cose da fare, e ogni lista aperta come una.
    val count = items.size + habits.count { !it.isDone(today) } + lists.count { it.open > 0 }
    val shown = items.take(HOME_TODO_MAX)
    val more = items.size - shown.size
    val haptic = rememberHaptic()
    val rowActions = rememberRowActions()
    val (savedOpen, savedLong) = rememberSavedTaps()
    val rowFor: @Composable (LooseItem) -> Unit = { item ->
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
    // Toccando la card, fuori dalle righe, si apre la sua pagina intera: attive e vecchie insieme (1.539).
    CanvasCard(Modifier.fillMaxWidth().captureTarget(kind.mode), onClick = onOpen) {
        CardTitle(kind.title, opens = true, trailing = {
            if (count > 0) Text(looseCount(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        })
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (items.isEmpty() && habits.isEmpty() && lists.isEmpty() && notesBelow.isEmpty()) {
                Text(kind.empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            // 1. Le cose da fare.
            shown.forEach { rowFor(it) }
            if (more > 0) {
                Text(
                    "altre $more ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 60.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            // 2. Le abitudini: tornano ogni giorno, spuntate si spengono fino a domani e non vanno in "Fatte".
            if (shown.isNotEmpty() && habits.isNotEmpty()) Spacer(Modifier.size(6.dp))
            habits.forEach { h ->
                HabitTodoRow(
                    h, now,
                    onClick = { haptic(Haptic.TICK); openItem(ItemKind.HABIT, h.id) },
                    onLongClick = { haptic(Haptic.LONG_PRESS); showHabit(h, today) { openItem(ItemKind.HABIT, it.id) } },
                    onTick = { haptic(if (h.isDone(today)) Haptic.TICK else Haptic.CONFIRM); NotesStore.toggleHabit(h.id, today) },
                )
            }
            // 3. Le liste delle note: una riga sola che si apre sotto, le spunte si mettono lì.
            if ((shown.isNotEmpty() || habits.isNotEmpty()) && lists.isNotEmpty()) Spacer(Modifier.size(6.dp))
            lists.forEach { row -> NoteListRow(row) }
            // 4. Gli appunti, dopo una riga sottile e senza titolo.
            if (notesBelow.isNotEmpty()) {
                if (shown.isNotEmpty() || habits.isNotEmpty() || lists.isNotEmpty()) RowDivider(Modifier.padding(vertical = 6.dp))
                notesBelow.forEach { rowFor(it) }
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
                item.image.isNotBlank() -> Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = item.image.let { if (it.startsWith("/")) java.io.File(it) else it },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    // Un video o un brano salvato ha il ▶ sulla copertina: si capisce che si guarda o si ascolta (1.616).
                    if (item.kind == LooseKind.SAVED) Icon(
                        Icons.Rounded.PlayArrow, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(18.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), CircleShape).padding(2.dp),
                    )
                }
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

/**
 * Una lista di una nota in "Da fare" (Antonio, 2026-11-23, idea 4): "Spesa · 3 da fare su 5 · dalla nota". Toccando si
 * apre sotto con le voci da spuntare (quelle spuntate adesso restano, sbiadite); tenendo premuto il fumetto con Apri la
 * nota, Ricomincia, Duplica e Mostra nel widget.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteListRow(row: NoteLists.Row) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(row.noteId) { androidx.compose.runtime.mutableStateOf(false) }
    // Le voci spuntate da qui restano in vista finché la riga è aperta (non spariscono sotto il dito).
    var ticked by remember(row.noteId) { androidx.compose.runtime.mutableStateOf(emptySet<Int>()) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, Motion.settle(), label = "listArrow")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyPress()
                .combinedClickable(
                    onClick = { haptic(Haptic.TICK); expanded = !expanded; if (!expanded) ticked = emptySet() },
                    onLongClick = { haptic(Haptic.LONG_PRESS); noteListActions(context, row) { inApp?.openRoute?.invoke(it) } },
                )
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                IconTile(Icons.Outlined.Checklist, TileAccent.NEUTRAL, size = 32.dp, iconSize = 18.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(NoteLists.line(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // La barretta si riempie man mano che spunti (1.616): si vede a colpo d'occhio che è una lista.
                val fill by androidx.compose.animation.core.animateFloatAsState(NoteLists.progress(row), Motion.settle(), label = "listFill")
                Box(
                    Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).height(3.dp)
                        .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(Modifier.fillMaxWidth(fill.coerceAtLeast(0.03f)).height(3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Chiudi la lista" else "Apri la lista",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
            )
        }
        androidx.compose.animation.AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 4.dp)) {
                row.entries.filter { !it.done || it.index in ticked }.forEach { e ->
                    val tick = {
                        haptic(if (e.done) Haptic.TICK else Haptic.CONFIRM)
                        ticked = ticked + e.index
                        NotesStore.editNoteText(row.noteId) { com.platinum20.dashboard.notes.Checklist.toggle(it, e.index) }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = tick)
                            .graphicsLayer { alpha = if (e.done) 0.5f else 1f }
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CanvasCheck(e.done, size = 19.dp, description = if (e.done) "Da fare" else "Fatto", onToggle = tick)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            e.text,
                            style = MaterialTheme.typography.bodyMedium.let { if (e.done) it.copy(textDecoration = TextDecoration.LineThrough) else it },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.open == 0 && ticked.isEmpty()) {
                    Text("Tutto fatto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp))
                }
            }
        }
    }
}

/**
 * Le azioni di una lista (tenendo premuto, in Da fare e nell'editor): apri, Ricomincia, Duplica, Mostra nel widget.
 * Stesse parole ovunque (regola della pressione lunga).
 */
internal fun noteListActions(context: android.content.Context, row: NoteLists.Row, openRoute: (String) -> Unit) {
    val note = NotesStore.note(row.noteId) ?: return
    showNoteBubble(context, note, openRoute)
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
            // Le frecce in cerchio sull'angolo (Antonio, 2026-11-24): è un'abitudine, si ripete. La serie resta a destra.
            Icon(
                Icons.Rounded.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).size(14.dp)
                    .background(MaterialTheme.colorScheme.card, CircleShape).padding(1.dp),
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
    // Un foglio con l'angolo piegato: è una nota, non si spunta (1.616).
    else -> Icons.Outlined.Description
}

/**
 * La pagina intera di Da fare (1.539; dalla 1.616 con gli appunti, Antonio 2026-11-24): come il Calendario, le cose
 * vecchie sopra (fatte e archiviate per giorno, sbiadite), la linea ADESSO e sotto quelle attive nello stesso ordine
 * della card: cose da fare, abitudini, liste delle note, una riga sottile e gli appunti. Si apre su ADESSO.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LaterArchiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate()
    val archived = remember(data) { (archivedItems(data.tasks) + archivedNotes(data.notes)) }
    val groups = remember(archived, today) { archiveGroups(archived, byMonth = false, today = today) }
    val loose = remember(data, today) {
        looseItems(dueDays(data.tasks, data.parcels, emptyList(), withToday = true, notes = data.notes), data.notes, today).pending()
    }
    val lists = remember(data) { NoteLists.rows(data.notes) }
    val active = remember(loose) { loose.todos() }
    val jottings = remember(loose, lists) { loose.jottings().withoutLists(lists.map { it.noteId }.toSet()) }
    val habits = data.habits
    val open = rememberDueOpen()
    val openItem = rememberOpenItem()
    val rowActions = rememberRowActions()
    val (savedOpen, savedLong) = rememberSavedTaps()
    val haptic = rememberHaptic()
    // Si apre sulla linea ADESSO: sopra c'è il passato, come nel Calendario.
    val marker = groups.sumOf { it.items.size + 1 }
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = if (groups.isEmpty()) 0 else marker)
    val bar = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    val activeCount = active.size + habits.count { !it.isDone(today) } + lists.count { it.open > 0 }
    val rowFor: @Composable (LooseItem) -> Unit = { item ->
        LooseRow(
            item,
            onClick = { haptic(Haptic.TICK); item.saved?.let(savedOpen) ?: item.row?.let(open) },
            onLongClick = { haptic(Haptic.LONG_PRESS); item.saved?.let(savedLong) ?: item.row?.let(rowActions) },
            onTick = item.row?.task?.let { task -> { haptic(Haptic.TICK); NotesStore.toggleTask(task.id) } },
        )
    }
    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = LooseCardKind.TODO.title, subtitle = laterPageLine(activeCount, jottings.size, archived.size), scrollBehavior = bar, onBack = onBack) },
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
            if (active.isEmpty() && habits.isEmpty() && lists.isEmpty() && jottings.isEmpty()) item(key = "empty") {
                Text(LooseCardKind.TODO.empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            active.forEach { item -> item(key = item.key) { rowFor(item) } }
            habits.forEach { h ->
                item(key = "habit-${h.id}") {
                    HabitTodoRow(
                        h, now,
                        onClick = { haptic(Haptic.TICK); openItem(ItemKind.HABIT, h.id) },
                        onLongClick = { haptic(Haptic.LONG_PRESS); showHabit(h, today) { openItem(ItemKind.HABIT, it.id) } },
                        onTick = { haptic(if (h.isDone(today)) Haptic.TICK else Haptic.CONFIRM); NotesStore.toggleHabit(h.id, today) },
                    )
                }
            }
            lists.forEach { row -> item(key = "list-${row.noteId}") { NoteListRow(row) } }
            if (jottings.isNotEmpty() && (active.isNotEmpty() || habits.isNotEmpty() || lists.isNotEmpty())) {
                item(key = "jottings-line") { RowDivider(Modifier.padding(vertical = 6.dp)) }
            }
            jottings.forEach { item -> item(key = item.key) { rowFor(item) } }
        }
    }
}

/**
 * Tenendo premuto un video, un brano o un episodio salvato (Antonio, 2026-11-13): lo stesso fumetto che ha in Media,
 * con ② "Salvato" (toccandolo esce dagli Appunti, con Annulla).
 */
internal fun savedMediaActions(context: android.content.Context, later: SavedLinks.Later, openRoute: (String) -> Unit) =
    showMediaBubble(context, later.item, openRoute)

/** Tenendo premuto un articolo salvato: lo stesso fumetto della notizia, già Salvato. */
private fun savedArticleActions(context: android.content.Context, entry: SavedLinks.ToRead, read: () -> Unit) =
    showArticleBubble(context, entry.article, null, read = read)

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
