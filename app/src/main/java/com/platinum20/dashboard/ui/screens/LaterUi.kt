package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.rounded.BookmarkRemove
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.captureTarget
import com.platinum20.dashboard.capture.SavedLinks
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
internal fun LaterCard(items: List<LooseItem>) {
    if (items.isEmpty()) return
    val open = rememberDueOpen()
    val haptic = rememberHaptic()
    CanvasCard(Modifier.fillMaxWidth().captureTarget(CaptureMode.NOTE)) {
        CardTitle("Per dopo", trailing = {
            Text(looseCount(items.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        })
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            items.forEach { item ->
                val onClick = {
                    haptic(Haptic.TICK)
                    item.row?.let(open)
                    Unit
                }
                LooseRow(
                    item,
                    onClick = onClick,
                    // Tenendo premuto: la sua scheda, come il tocco (le azioni stanno nel suo "Altro").
                    onLongClick = onClick,
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
    }
}

private fun looseIcon(item: LooseItem) = when (item.kind) {
    LooseKind.PARCEL -> Icons.Outlined.Inventory2
    else -> Icons.AutoMirrored.Outlined.StickyNote2
}

/** Tenendo premuta una cosa salvata (Media, News): il fumetto "Togli dai salvati". */
internal fun forgetSaved(note: com.platinum20.dashboard.notes.Note) {
    ActionBubble.show(BubbleAction(Icons.Rounded.BookmarkRemove, "Togli dai salvati", danger = true, onClick = { SavedLinks.forget(note, "Tolto: ${note.title}") }))
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
    Column(modifier) {
        SectionHeader("Salvati · ${toRead.size}")
        toRead.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .bouncyPress()
                    .combinedClickable(
                        onClick = {
                            SavedLinks.open(context, entry.article) { inApp?.openRoute?.invoke(it) }
                            SavedLinks.forget(entry.note, "Letto: tolto dai salvati")
                        },
                        onLongClick = { forgetSaved(entry.note) },
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
