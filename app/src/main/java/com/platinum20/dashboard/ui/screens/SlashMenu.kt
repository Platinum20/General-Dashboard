package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Il menu "/" come su Notion (1.589, /mnt/project-files/editor-slash/slash.png): un riquadro di vetro sotto la riga dove
 * si scrive. In cima le voci usate di recente, poi i gruppi; a destra la scorciatoia che fa lo stesso scrivendo.
 * Scrivendo dopo la "/" restano le voci giuste, la prima è blu e Invio la sceglie.
 */
class SlashEntry(
    val id: String,
    val group: String,
    val icon: ImageVector,
    val label: String,
    val key: String,
    /** Riceve il testo già senza la "/" scritta. */
    val run: (TextFieldValue) -> Unit,
)

/** Le voci usate di recente (le ultime tre), ricordate fra un'apertura e l'altra. */
object SlashRecent {
    private const val MAX = 3
    private val _ids = MutableStateFlow<List<String>>(emptyList())
    val ids: StateFlow<List<String>> = _ids.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _ids.value = context.getSharedPreferences("slash_menu", Context.MODE_PRIVATE).getString("recent", "")
            .orEmpty().split('\n').filter { it.isNotBlank() }
    }

    fun add(context: Context, id: String) {
        _ids.value = push(_ids.value, id)
        context.getSharedPreferences("slash_menu", Context.MODE_PRIVATE).edit().putString("recent", _ids.value.joinToString("\n")).apply()
    }

    /** Pura: [id] va in cima, senza doppioni, al massimo tre. */
    fun push(list: List<String>, id: String): List<String> = (listOf(id) + list.filter { it != id }).take(MAX)
}

/** Pura: le voci che vanno bene per [query], prima quelle che cominciano così, poi quelle che la contengono. */
fun slashOrder(labels: List<String>, query: String): List<Int> {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return labels.indices.toList()
    val starts = labels.indices.filter { i -> labels[i].lowercase().split(' ').any { it.startsWith(q) } }
    val contains = labels.indices.filter { i -> i !in starts && labels[i].lowercase().contains(q) }
    return starts + contains
}

/** Tutte le voci del menu, nei loro gruppi. */
internal fun slashEntries(target: RichBar.Target, openPage: (Long, String) -> Unit = { _, _ -> }): List<SlashEntry> {
    fun kind(k: NoteLines.Kind): (TextFieldValue) -> Unit = { now ->
        val e = NoteLines.renumber(NoteLines.setKind(now.text, now.selection.min, now.selection.max, k))
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    val text = "Testo"
    val blocks = "Linee e tabelle"
    return listOf(
        SlashEntry("testo", text, Icons.AutoMirrored.Outlined.Notes, "Testo", "") { },
        SlashEntry("titolo", text, Icons.Outlined.Title, "Titolo", "#", kind(NoteLines.Kind.HEADING)),
        SlashEntry("punti", text, Icons.AutoMirrored.Outlined.FormatListBulleted, "Punti", "-", kind(NoteLines.Kind.BULLET)),
        SlashEntry("numeri", text, Icons.Outlined.FormatListNumbered, "Numeri", "1.", kind(NoteLines.Kind.NUMBER)),
        SlashEntry("spunta", text, Icons.Outlined.CheckBoxOutlineBlank, "Spunta", "[ ]", kind(NoteLines.Kind.CHECK)),
        SlashEntry("citazione", text, Icons.Outlined.FormatQuote, "Citazione", ">", kind(NoteLines.Kind.QUOTE)),
        SlashEntry("riquadro", text, Icons.Outlined.Lightbulb, "Riquadro", "!!", kind(NoteLines.Kind.CALLOUT)),
        SlashEntry("linea", blocks, Icons.Outlined.HorizontalRule, "Linea", "---") { now ->
            val e = NoteLines.insertDivider(now.text, now.selection.end)
            target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
        },
        SlashEntry("tabella", blocks, Icons.Outlined.TableChart, "Tabella", "|") { now ->
            val (t, line) = NoteTable.insert(now.text, now.selection.end)
            target.set(TextFieldValue(t, TextRange(now.selection.end.coerceAtMost(t.length))))
            RichBar.openTable = line
        },
        SlashEntry("colonne", blocks, Icons.Outlined.ViewColumn, "Due colonne", "‖") { now ->
            val e = NoteLines.insertBlock(now.text, now.selection.end, NoteLines.COLUMNS_NEW)
            target.set(TextFieldValue(e.text, TextRange(e.start)))
            RichBar.openTable = NoteLines.insertedBlockIndex(e)
        },
        SlashEntry("bacheca", blocks, Icons.Outlined.ViewKanban, "Bacheca", "") { now ->
            val e = NoteLines.insertBlock(now.text, now.selection.end, NoteLines.BOARD)
            target.set(TextFieldValue(e.text, TextRange(e.start)))
        },
    ) + target.extras.map { x -> SlashEntry(x.label, "Media", x.icon, x.label, "") { x.run() } } + listOf(
        SlashEntry("sottonota", "Collegamenti", Icons.Outlined.Description, "Sotto-nota", "") { now ->
            // Una nota nuova dentro questa (1.590): la riga che la apre, e si apre subito per scriverci.
            val title = "Nuova pagina"
            val id = com.platinum20.dashboard.notes.NotesStore.saveNote(null, title) ?: return@SlashEntry
            val e = NoteLines.insertBlock(now.text, now.selection.end, NoteLines.pageLine(title, id))
            target.set(TextFieldValue(e.text, TextRange(e.start)))
            openPage(id, title)
        },
        SlashEntry("collega", "Collegamenti", Icons.Outlined.AlternateEmail, "Nota o pagina di Canvas", "@") { now ->
            val at = now.selection.end
            target.set(TextFieldValue(now.text.substring(0, at) + "@" + now.text.substring(at), TextRange(at + 1)))
        },
    )
}

/** Toglie la "/" scritta (e quello dopo) dalla riga del cursore. */
internal fun withoutSlash(now: TextFieldValue): TextFieldValue {
    val l = NoteLines.lineAt(now.text, now.selection.end)
    val t = now.text.removeRange(l.contentStart, now.selection.end.coerceAtLeast(l.contentStart))
    return TextFieldValue(t, TextRange(l.contentStart))
}

/** Il riquadro: sotto la riga del cursore se c'è posto sopra la tastiera, se no sopra la riga. */
@Composable
internal fun SlashMenu(target: RichBar.Target, query: String) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val imeBottom = WindowInsets.ime.getBottom(density)
    val room = with(density) { 64.dp.roundToPx() }
    val gap = with(density) { 6.dp.roundToPx() }
    val width = (LocalConfiguration.current.screenWidthDp - 40).coerceAtMost(340).dp
    // Sotto la riga quando lì c'è posto per almeno quattro voci, se no sopra (1.604). Prima era alto al massimo metà dello
    // spazio sopra la tastiera e, con la riga in cima alla nota, finiva sopra il titolo con tre voci sole: la riga e lo
    // spazio vero sotto di lei decidono l'altezza.
    val view = androidx.compose.ui.platform.LocalView.current
    var anchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val windowH = view.rootView.height.takeIf { it > 0 } ?: with(density) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
    val topInset = WindowInsets.statusBars.getTop(density)
    val place = slashPlace(
        anchorTop = anchor?.top ?: 0f, anchorBottom = anchor?.bottom ?: 0f,
        limit = (windowH - imeBottom - room).toFloat(), top = topInset.toFloat(), gap = gap.toFloat(),
        minBelow = with(density) { 200.dp.toPx() }, max = with(density) { 380.dp.toPx() },
    )
    val maxHeight = with(density) { place.second.toDp() }.coerceAtLeast(120.dp)
    Box(Modifier.fillMaxSize().onGloballyPositioned { anchor = it.boundsInWindow() })
    LaunchedEffect(Unit) { SlashRecent.load(context) }
    val recent by SlashRecent.ids.collectAsState()
    val inApp = LocalInAppNav.current
    val entries = slashEntries(target) { id, title -> openNoteLink(inApp, "${com.platinum20.dashboard.notes.NoteFormat.NOTE_LINK}$id", title) }
    val found = if (query.isEmpty()) null else slashOrder(entries.map { it.label }, query).map { entries[it] }
    fun pick(e: SlashEntry) {
        haptic(Haptic.TICK)
        val clean = withoutSlash(target.value())
        target.set(clean)
        SlashRecent.add(context, e.id)
        e.run(clean)
    }
    val first = found?.firstOrNull()
    DisposableEffect(first?.id, target.owner) {
        RichBar.slashFirst = first?.let { e -> { pick(e) } }
        onDispose { RichBar.slashFirst = null }
    }
    val provider = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val below = anchorBounds.bottom + gap
            val above = anchorBounds.top - gap - popupContentSize.height
            val y = if (place.first) below else above.coerceAtLeast(topInset)
            val x = anchorBounds.left.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
            return IntOffset(x, y)
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Column(
            Modifier
                .width(width)
                .heightIn(max = maxHeight)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(colors.surfaceContainerHigh.copy(alpha = 0.97f))
                .border(cardBorder(), shape),
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                if (found == null) {
                    val rec = recent.mapNotNull { id -> entries.firstOrNull { it.id == id } }
                    if (rec.isNotEmpty()) {
                        SlashHeader("Usati di recente")
                        rec.forEach { SlashRow(it, false) { pick(it) } }
                        SlashSeparator()
                    }
                    entries.groupBy { it.group }.entries.forEachIndexed { i, (group, list) ->
                        if (i > 0) SlashSeparator()
                        SlashHeader(group)
                        list.forEach { SlashRow(it, false) { pick(it) } }
                    }
                } else if (found.isEmpty()) {
                    Text(
                        "Niente con \"/$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                } else {
                    found.forEachIndexed { i, e -> SlashRow(e, i == 0) { pick(e) } }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.6f)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { haptic(Haptic.TICK); target.set(withoutSlash(target.value())) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chiudi", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                Box(Modifier.weight(1f))
                if (first != null) Text("Invio = la prima", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SlashHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SlashSeparator() {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

@Composable
private fun SlashRow(entry: SlashEntry, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (on) Modifier.background(colors.primary.copy(alpha = 0.16f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(entry.icon, contentDescription = null, tint = if (on) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(entry.label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, modifier = Modifier.weight(1f))
        if (entry.key.isNotEmpty()) Text(entry.key, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
    }
}

/**
 * Pura (1.604): dove va il menu "/" e quanto può essere alto, in pixel della finestra. Sotto la riga se lì ci stanno
 * almeno [minBelow] (o comunque più che sopra), se no sopra; l'altezza è lo spazio di quel lato, al massimo [max].
 * first = sotto, second = altezza massima.
 */
internal fun slashPlace(anchorTop: Float, anchorBottom: Float, limit: Float, top: Float, gap: Float, minBelow: Float, max: Float): Pair<Boolean, Float> {
    val below = limit - anchorBottom - gap
    val above = anchorTop - gap - top
    val down = below >= minBelow || below >= above
    return down to (if (down) below else above).coerceIn(0f, max)
}
