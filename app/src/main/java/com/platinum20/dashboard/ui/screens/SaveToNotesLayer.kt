package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeItem
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import kotlinx.coroutines.delay

/**
 * Indietro chiude prima il fumetto, poi la pagina dietro. Il gestore entra in composizione solo
 * quando il fumetto si apre: così è l'ultimo registrato e vince su quelli delle schermate
 * (che col semplice `BackHandler(visible)` venivano dopo e chiudevano la pagina al suo posto).
 */
@Composable
internal fun BubbleBack(open: Boolean, onBack: () -> Unit) {
    if (open) BackHandler(onBack = onBack)
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
    BubbleBack(visible, onDismiss)
    // Le prime volte che si vede un fumetto, una riga piccola dice come si chiude (poi mai più).
    val context = LocalContext.current
    val hint = remember(visible) { visible && GestureHints.stillShow(context, GestureHints.SWIPE_BUBBLE) }
    LaunchedEffect(visible) { if (visible) GestureHints.mark(context, GestureHints.SWIPE_BUBBLE) }
    AnimatedVisibility(visible, enter = fadeIn(Motion.settle()), exit = fadeOut(tween(Motion.EXIT_MS)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = Glass.scrim(MaterialTheme.colorScheme.background.luminance() < 0.5f)))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible,
        enter = fadeIn(Motion.settle()) + scaleIn(Motion.lively(), initialScale = 0.92f) + slideInVertically(Motion.lively()) { it / 3 },
        exit = fadeOut(tween(Motion.EXIT_MS - 20)) + slideOutVertically(tween(Motion.EXIT_MS, easing = Motion.Accelerate)) { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            // Sopra la tastiera quando è aperta (1.594): prima il fumetto finiva sotto, mentre si scriveva una nota.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(top = 16.dp, bottom = 96.dp, start = 16.dp, end = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SwipeAwayMark(swipe)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.swipeAway(swipe) { if (visible) onDismiss() },
            ) {
                // Un solo blocco di vetro con le righe dentro (1.402): prima ogni azione era una pillola a sé.
                val shape = RoundedCornerShape(22.dp)
                Surface(
                    shape = shape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().glass(shape, overScrim = true),
                ) {
                    // Con la tastiera aperta lo spazio è poco: le righe scorrono invece di uscire dallo schermo.
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp), content = content)
                }
                if (hint) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).glass(CircleShape, overScrim = true),
                    ) {
                        Text(
                            "Portalo di lato per chiuderlo",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Una riga del fumetto: icona in inchiostro, titolo e una riga di dettaglio (1.402: niente pallini colorati).
 * [danger] = rosso, per le azioni che tolgono qualcosa. Le righe stanno nel blocco di [BottomBubble].
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun BubbleRow(
    icon: ImageVector,
    title: String,
    detail: String = "",
    danger: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
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

