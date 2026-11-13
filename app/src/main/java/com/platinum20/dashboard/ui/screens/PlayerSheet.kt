package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Dove sta, quanto è grande e quando si chiude un pannello sopra il player (coda, impostazioni del video). Pura per i test.
 */
internal object QueueSheet {
    /** Schermo più largo che alto (video a schermo intero): pannello di lato, così il video resta in orizzontale. */
    fun sideways(width: Float, height: Float): Boolean = width > height

    /** Trascinato verso il bordo da cui è entrato: oltre un terzo, o lanciato, si chiude. */
    fun closes(dragged: Float, velocity: Float, extent: Float): Boolean =
        dragged > extent / 3f || (velocity > 1_200f && dragged > 0f)

    /**
     * Larghezza del pannello di lato (in dp): [share] dello schermo ma mai più stretto di [min] né più largo di [max],
     * e comunque non oltre lo schermo meno un margine per toccare fuori e chiudere.
     */
    fun sideWidth(screen: Float, share: Float, min: Float, max: Float): Float =
        (screen * share).coerceIn(min, max).coerceAtMost(screen - 72f).coerceAtLeast(0f)

    /** Nella coda di lato c'è posto per due colonne (in riproduzione a sinistra, dopo a destra). */
    fun twoColumns(panelWidth: Float): Boolean = panelWidth >= 520f
}

/**
 * Il pannello comune sopra il player (1.455): in verticale sale dal basso, in orizzontale entra da destra a tutta altezza.
 * Si chiude toccando fuori, col gesto indietro o portando l'intestazione verso il bordo. [fixedHeight]: in verticale
 * occupa sempre quella parte dello schermo (la coda); se no è alto quanto il contenuto (le impostazioni).
 */
@Composable
internal fun PlayerSheet(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    subtitle: String? = null,
    fixedHeight: Float? = null,
    sideShare: Float = 0.45f,
    sideMax: Dp = 440.dp,
    /** Larghezza fissa in orizzontale (la coda: quanto le impostazioni video), al posto della parte di schermo. */
    sideWidth: Dp? = null,
    content: @Composable ColumnScope.(sideways: Boolean, panelWidth: Dp) -> Unit,
) {
    androidx.activity.compose.BackHandler(enabled = open, onBack = onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideways = QueueSheet.sideways(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val width = sideWidth ?: QueueSheet.sideWidth(maxWidth.value, sideShare, 340f, sideMax.value).dp
        val density = LocalDensity.current
        val extent = with(density) { if (sideways) width.toPx() else maxHeight.toPx() * (fixedHeight ?: 0.6f) }
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Velo leggero: sotto al vetro il video si deve ancora vedere.
                    .background(Color.Black.copy(alpha = 0.18f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        val maxH = maxHeight
        AnimatedVisibility(
            visible = open,
            enter = if (sideways) slideInHorizontally(Motion.settle()) { it } else slideInVertically(Motion.settle()) { it },
            exit = if (sideways) slideOutHorizontally(Motion.settle()) { it } else slideOutVertically(Motion.settle()) { it },
            modifier = Modifier
                .align(if (sideways) Alignment.CenterEnd else Alignment.BottomCenter)
                // In orizzontale un fumetto staccato dai bordi, come le impostazioni del video.
                .then(
                    if (sideways) Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                        .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                    else Modifier,
                ),
        ) {
            val scope = rememberCoroutineScope()
            val drag = remember { Animatable(0f) }
            val view = LocalView.current
            val shape = if (sideways) RoundedCornerShape(26.dp)
            else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            val size = when {
                sideways -> Modifier.width(width).fillMaxHeight()
                fixedHeight != null -> Modifier.fillMaxWidth().height(maxH * fixedHeight)
                else -> Modifier.fillMaxWidth().heightIn(max = maxH * 0.85f)
            }
            Column(
                size
                    .offset { if (sideways) IntOffset(drag.value.roundToInt(), 0) else IntOffset(0, drag.value.roundToInt()) }
                    .border(cardBorder(), shape)
                    .glass(shape)
                    // I tocchi sul pannello restano al pannello, non chiudono.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(
                        if (sideways) Modifier
                        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                    ),
            ) {
              // Fuori dal NavHost nessuno dà il colore del testo: lo dà il pannello.
              androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                // L'intestazione è la maniglia: portata verso il bordo chiude.
                val tracker = remember { VelocityTracker() }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(sideways) {
                            val onDrag: (PointerInputChange, Float) -> Unit = { change, amount ->
                                // Il pannello si sposta col dito: si misura la posizione sullo schermo, non sul pannello.
                                val p = change.position
                                tracker.addPosition(change.uptimeMillis, if (sideways) Offset(p.x + drag.value, p.y) else Offset(p.x, p.y + drag.value))
                                scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                            }
                            val onEnd: () -> Unit = {
                                val v = tracker.calculateVelocity()
                                val velocity = if (sideways) v.x else v.y
                                if (QueueSheet.closes(drag.value, velocity, extent)) {
                                    view.haptic(Haptic.SNAP)
                                    onClose()
                                } else {
                                    scope.launch { drag.animateTo(0f, Motion.gesture()) }
                                }
                            }
                            val onStart: (Offset) -> Unit = { tracker.resetTracking() }
                            if (sideways) {
                                detectHorizontalDragGestures(onDragStart = onStart, onDragEnd = onEnd) { change, amount -> onDrag(change, amount) }
                            } else {
                                detectVerticalDragGestures(onDragStart = onStart, onDragEnd = onEnd) { change, amount -> onDrag(change, amount) }
                            }
                        }
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
                ) {
                    // In orizzontale niente maniglia in alto: il pannello si porta a destra, la lineetta sta di lato.
                    if (!sideways) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = if (sideways) 6.dp else 12.dp), verticalAlignment = Alignment.Bottom) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                            )
                        }
                    }
                }
                content(sideways, width)
              }
            }
        }
    }
}
