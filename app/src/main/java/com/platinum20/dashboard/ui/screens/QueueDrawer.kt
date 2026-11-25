package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.QueueEntry
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * La coda del player della musica come UN foglio di vetro (Antonio 2026-11-25: "dovrebbe esserci semplicemente una
 * parte della coda in vetro, tu la tiri fuori e basta"). Chiuso ne spunta solo la cima dal bordo in basso
 * ("Dopo · il prossimo"); tirato su segue il dito ed è la coda intera, tirato giù torna a spuntare.
 */
internal object QueueDrawer {
    /** Quanto spunta la coda chiusa, sopra la barra di sistema: maniglia + una riga. */
    val PEEK = 64.dp

    /**
     * Lasciata a [offset] px dalla posizione aperta (0 = aperta, [hidden] = chiusa) con velocità [velocity] (px/s,
     * positiva = verso il basso): true se resta aperta. Uno scatto decide, se no conta la metà.
     */
    fun staysOpen(offset: Float, velocity: Float, hidden: Float): Boolean = when {
        velocity < -FLING -> true
        velocity > FLING -> false
        else -> offset < hidden / 2f
    }

    private const val FLING = 900f

    /** La riga della coda chiusa: "Dopo · titolo · artista · altri N". */
    fun peekLine(next: QueueEntry?, upcoming: Int): String = buildString {
        append(if (next == null) "la coda è vuota" else listOf(next.item.title, next.item.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
        if (upcoming > 1) append(" · altri ${upcoming - 1}")
    }
}

/** Il foglio in fondo al player della musica: disegnato in MainActivity (vetro vero), solo mentre il player è aperto. */
@Composable
internal fun QueueDrawerSheet(open: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val queue by CanvasPlayer.queue.collectAsState()
    val now = queue.now
    val upcoming = queue.upcoming
    BackHandler(enabled = open, onBack = onClose)
    val isOpen by androidx.compose.runtime.rememberUpdatedState(open)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val sheetHeight = maxHeight * 0.78f
        val hidden = with(density) { (sheetHeight - QueueDrawer.PEEK - bottomInset).toPx() }.coerceAtLeast(0f)
        val y = remember { Animatable(if (open) 0f else hidden) }
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        // Aperta o chiusa da fuori (tirando su la pagina, Indietro): il foglio va lì da solo, se non ci sta già andando.
        LaunchedEffect(open, hidden) {
            val target = if (open) 0f else hidden
            if (y.targetValue != target || (!y.isRunning && y.value != target)) y.animateTo(target, Motion.settle())
        }
        val shown by remember(hidden) { derivedStateOf { hidden > 0f && y.value < hidden - 1f } }
        if (shown) {
            // Il velo sale con il foglio; toccandolo la coda torna giù.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (hidden > 0f) (1f - y.value / hidden).coerceIn(0f, 1f) else 0f }
                    .background(Color.Black.copy(alpha = Glass.scrim(MaterialTheme.colorScheme.background.luminance() < 0.5f)))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        val tracker = remember { VelocityTracker() }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, y.value.roundToInt()) }
                .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), overScrim = true)
                // I tocchi sul foglio restano al foglio, non arrivano al player sotto.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                // La cima è la maniglia: si trascina su e giù; chiusa, un tocco la apre.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(QueueDrawer.PEEK)
                        .pointerInput(hidden) {
                            detectTapGestures { if (!isOpen) { view.haptic(Haptic.SNAP); onOpen() } }
                        }
                        .pointerInput(hidden) {
                            detectVerticalDragGestures(
                                onDragStart = { tracker.resetTracking() },
                                onDragEnd = {
                                    val v = tracker.calculateVelocity().y
                                    val stay = QueueDrawer.staysOpen(y.value, v, hidden)
                                    view.haptic(Haptic.SNAP)
                                    scope.launch { y.animateTo(if (stay) 0f else hidden, Motion.gesture(), initialVelocity = v) }
                                    if (stay) onOpen() else onClose()
                                },
                                onDragCancel = { scope.launch { y.animateTo(if (isOpen) 0f else hidden, Motion.gesture()) } },
                            ) { change, amount ->
                                change.consume()
                                // Si misura sullo schermo, non sul foglio che si sposta col dito.
                                tracker.addPosition(change.uptimeMillis, androidx.compose.ui.geometry.Offset(change.position.x, change.position.y + y.value))
                                scope.launch { y.snapTo((y.value + amount).coerceIn(0f, hidden)) }
                            }
                        }
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                    )
                    // Chiusa "Dopo · il prossimo", aperta "Coda · N dopo": la stessa cima, che sfuma dall'una all'altra.
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Dopo") }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(" · " + QueueDrawer.peekLine(upcoming.firstOrNull(), upcoming.size))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (hidden > 0f) (y.value / hidden).coerceIn(0f, 1f) else 0f },
                        )
                        Row(
                            Modifier.fillMaxWidth().graphicsLayer { alpha = if (hidden > 0f) (1f - y.value / hidden).coerceIn(0f, 1f) else 1f },
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text("Coda", style = MaterialTheme.typography.headlineSmall)
                            QueueLook.count(upcoming.size)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                                )
                            }
                        }
                    }
                }
                val listPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp + bottomInset)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    QueueList(now, upcoming, Modifier.fillMaxSize(), listPadding)
                }
            }
        }
    }
}
