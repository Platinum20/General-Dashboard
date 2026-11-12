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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.NotesStore

/**
 * Le cose senza scadenza (Antonio, 2026-11-12): cose da fare, note, foto, pacchi senza data e quello salvato per
 * dopo (video, brani, radio, podcast, articoli) stanno tutte in una piccola sezione in fondo alla card Calendario
 * della Home, non nei giorni. Le cose salvate restano note: la sincronia con "Guarda più tardi" di YouTube e lo
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
 * La sezione "Senza scadenza" in fondo alla card Calendario: tutte le righe, niente nascosto. Ogni riga ha a
 * sinistra la spunta (cose da fare) o l'immagine, il nome e al massimo una riga sotto.
 */
@Composable
internal fun LooseSection(items: List<LooseItem>, onLongOpen: () -> Unit) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val open = rememberDueOpen()
    val haptic = rememberHaptic()
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        RowDivider()
        SectionHeader(looseTitle(items.size), Modifier.padding(start = 12.dp, end = 16.dp, top = 4.dp))
        items.forEach { item ->
            LooseRow(
                item,
                onClick = {
                    haptic(Haptic.TICK)
                    when {
                        item.later != null -> openResumeItem(context, item.later.item) { inApp?.openRoute?.invoke(it) }
                        // Aprendo un articolo esce dalla lista (l'hai letto), con "Annulla" se era per sbaglio.
                        item.toRead != null -> {
                            SavedLinks.open(context, item.toRead.article) { inApp?.openRoute?.invoke(it) }
                            SavedLinks.forget(item.toRead.note, "Letto: ${item.title}")
                        }
                        item.row != null -> open(item.row)
                    }
                },
                // Le cose salvate non hanno una scheda: tenendole premute si tolgono. Il resto apre il calendario intero.
                onLongClick = {
                    val note = item.later?.note ?: item.toRead?.note
                    if (note != null) forgetAction(note, "Togli") else { haptic(Haptic.LONG_PRESS); onLongOpen() }
                },
                onTick = item.row?.task?.let { task -> { haptic(Haptic.TICK); NotesStore.toggleTask(task.id) } },
            )
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
    LooseKind.MEDIA -> item.later?.item?.icon() ?: Icons.AutoMirrored.Outlined.StickyNote2
    LooseKind.ARTICLE -> Icons.AutoMirrored.Outlined.Article
    LooseKind.PARCEL -> Icons.Outlined.Inventory2
    else -> Icons.AutoMirrored.Outlined.StickyNote2
}

private fun forgetAction(note: com.platinum20.dashboard.notes.Note, title: String) {
    ActionBubble.show(BubbleAction(Icons.Rounded.BookmarkRemove, title, danger = true, onClick = { SavedLinks.forget(note, "Tolto: ${note.title}") }))
}
