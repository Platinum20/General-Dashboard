package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeKind
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.notes.NotesStore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import kotlinx.coroutines.delay

/** Quanto resta il fumetto "Salva nelle note" se non lo si tocca. */
private const val OFFER_MS = 6_000L

/**
 * Tenendo premuta una cosa (un brano, un video, una radio, un articolo) sale dal basso il fumetto
 * di vetro con "Salva nelle note" (e, per i media, "Aggiungi alla coda"). Toccandolo la nota è fatta e il pallino vola alle Note.
 * Portandolo di lato (come il mini player) si annulla.
 * Chi vuole offrirlo chiama solo [SavedLinks.offer] nel suo onLongClick.
 */
@Composable
internal fun BoxScope.SaveToNotesLayer() {
    val offered = SavedLinks.offered
    val context = LocalContext.current
    val haptic = rememberHaptic()
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsStateWithLifecycle()
    // L'ultima offerta resta disegnata mentre il fumetto si chiude.
    var last by remember { mutableStateOf<SavedLink?>(null) }
    // Portando il fumetto di lato si annulla, con lo stesso gesto che chiude il mini player.
    val swipe = rememberSwipeAwayState()
    LaunchedEffect(offered) {
        if (offered == null) return@LaunchedEffect
        last = offered
        swipe.reset()
        haptic(Haptic.LONG_PRESS)
        delay(OFFER_MS)
        if (SavedLinks.offered === offered) SavedLinks.dismiss()
    }
    BottomBubble(visible = offered != null, swipe = swipe, onDismiss = { SavedLinks.dismiss() }) {
        val link = last ?: return@BottomBubble
        val media = (link as? SavedLink.Media)?.item?.takeIf { it.kind != ResumeKind.VIDEO }
        // Video, canzoni, radio ed episodi si possono anche mettere in coda, dopo quello che suona.
        if (media != null) {
            BubbleRow(icon = Icons.Rounded.QueueMusic, title = "Aggiungi alla coda", detail = media.title) {
                haptic(Haptic.CONFIRM)
                SavedLinks.dismiss()
                CanvasPlayer.add(media)
                android.widget.Toast.makeText(context, "In coda: ${media.title}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // Se la cosa è già nelle note, lo stesso posto la toglie: mai due copie.
        val saved = remember(notes.notes, link) { SavedLinks.notesWith(notes.notes, link).isNotEmpty() }
        BubbleRow(
            icon = if (saved) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
            title = if (saved) "Rimuovi dalle note" else "Salva nelle note",
            detail = listOf(link.title, link.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
        ) {
            haptic(Haptic.CONFIRM)
            if (saved) SavedLinks.remove(context, link) else SavedLinks.save(context, link)
        }
    }
}

/**
 * Il fumetto di vetro in fondo al centro, comune a tutti i pop-up di Canvas: velo leggero dietro
 * (toccandolo si chiude), indietro lo chiude, e portandolo di lato si chiude come il mini player.
 * Va disegnato fuori dal NavHost, se no il vetro non ha niente dietro da sfocare.
 */
@Composable
internal fun BoxScope.BottomBubble(
    visible: Boolean,
    swipe: SwipeAwayState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(visible) { onDismiss() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(tween(160)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f) + slideInVertically { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SwipeAwayMark(swipe)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.swipeAway(swipe) { if (visible) onDismiss() },
                content = content,
            )
        }
    }
}

/** Una riga del fumetto: pallino con l'icona, titolo e una riga di dettaglio. [danger] per le azioni che tolgono qualcosa. */
@Composable
internal fun BubbleRow(icon: ImageVector, title: String, detail: String = "", danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val onTint = if (danger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth().glass(shape).bouncyPress(0.97f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = tint, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = onTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
