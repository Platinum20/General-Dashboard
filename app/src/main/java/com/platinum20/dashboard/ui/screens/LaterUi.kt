package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkRemove
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.NotesStore

/**
 * Le cose salvate "per dopo" stanno dove si usano (1.482, Antonio scelta B: ognuno al suo posto):
 * video, brani, radio e podcast in Media ("Più tardi", dopo Continua), gli articoli in cima a News ("Da leggere").
 * Restano note sotto: la sincronia con "Guarda più tardi" di YouTube e lo Zaino del treno le leggono da lì.
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

/** La fila "Più tardi" di Media: le stesse copertine di Continua; tieni premuto per toglierla. */
@Composable
internal fun LaterShelf(onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val later = remember(data.notes) { SavedLinks.later(data.notes) }
    if (later.isEmpty()) return
    Column {
        SectionHeader("Più tardi · ${later.size}", Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(later, key = { "${it.item.kind}-${it.item.key}" }) { entry ->
                LaterTile(
                    entry.item,
                    onClick = { openResumeItem(context, entry.item) { onOpenRoute(it) } },
                    onLongClick = { forgetAction(entry.note, "Togli da Più tardi") },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LaterTile(item: ResumeItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .width(168.dp)
            .clip(MaterialTheme.shapes.large)
            .bouncyPress()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CaptionedCover(item.title, LaterText.subtitle(item), titleLines = MediaShelves.TITLE_LINES, textModifier = Modifier.padding(horizontal = 4.dp)) {
            Box(
                Modifier.fillMaxWidth().height(94.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                IconTile(item.icon(), item.accent())
                if (item.image.isNotBlank()) {
                    AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
        }
    }
}

/**
 * "Da leggere" in cima a News: gli articoli salvati. Aprendone uno esce dalla lista (l'hai letto),
 * con "Annulla" nel fumetto se era un tocco per sbaglio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToReadBlock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsState()
    val toRead = remember(data.notes) { SavedLinks.toRead(data.notes) }
    if (toRead.isEmpty()) return
    Column(modifier) {
        SectionHeader("Da leggere · ${toRead.size}")
        toRead.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .bouncyPress()
                    .combinedClickable(
                        onClick = {
                            SavedLinks.open(context, entry.article) { inApp?.openRoute?.invoke(it) }
                            SavedLinks.forget(entry.note, "Letto: tolto da Da leggere")
                        },
                        onLongClick = { forgetAction(entry.note, "Togli da Da leggere") },
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

private fun forgetAction(note: com.platinum20.dashboard.notes.Note, title: String) {
    ActionBubble.show(BubbleAction(Icons.Rounded.BookmarkRemove, title, danger = true, onClick = { SavedLinks.forget(note, "Tolto: ${note.title}") }))
}
