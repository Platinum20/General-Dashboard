package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.CaptureTargets
import com.platinum20.dashboard.capture.icon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Quanto resta il fumetto con "Annulla" prima di volare via. */
private const val NOTICE_MS = 2_600L

/** Il volo del pallino: da dove parte, dove arriva e su quale chiave atterra. */
private data class Flight(val icon: ImageVector, val from: Offset, val to: Offset, val key: String)

/**
 * Sopra tutta l'app: il fumetto "Spesa: 12,50 € · Cibo · Annulla" sopra la barra e poi il pallino
 * che vola dove la cosa è finita. Nelle Note atterra sulla sezione giusta (se è a schermo),
 * altrimenti sulla scheda Note in basso (gli eventi su Oggi, dove c'è l'agenda).
 */
@Composable
internal fun BoxScope.CaptureFeedbackLayer(onNotes: Boolean) {
    var notice by remember { mutableStateOf<CaptureNotice?>(null) }
    // Ogni conferma nuova riparte da capo, anche se il testo è uguale alla precedente.
    var seq by remember { mutableStateOf(0) }
    // L'ultima mostrata: resta disegnata mentre il fumetto si chiude.
    var last by remember { mutableStateOf<CaptureNotice?>(null) }
    var noticeRect by remember { mutableStateOf<Rect?>(null) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val inNotes by rememberUpdatedState(onNotes)
    // Portato di lato si chiude come il mini player: la cosa resta salvata, solo senza volo.
    val swipe = rememberSwipeAwayState()

    fun target(n: CaptureNotice): Pair<String, Rect>? {
        n.target?.let { key -> return CaptureTargets.rects[key]?.let { key to it } }
        val mode = n.mode
        // Sempre sulla scheda in basso, anche se le Note sono già aperte (come vuole Antonio).
        // Le spese stanno in Oggi dalla 1.186, come gli impegni.
        val key = if (mode == CaptureMode.EVENT || mode == CaptureMode.EXPENSE) CaptureTargets.HOME_TAB else CaptureTargets.NOTES_TAB
        return CaptureTargets.rects[key]?.let { key to it }
    }

    LaunchedEffect(Unit) {
        CaptureFeedback.events.collect { n ->
            notice = n
            last = n
            noticeRect = null
            swipe.reset()
            seq++
        }
    }
    // Il fumetto resta un attimo, poi si chiude in un pallino che parte da dove era.
    LaunchedEffect(seq) {
        val n = notice ?: return@LaunchedEffect
        delay(NOTICE_MS)
        // Mentre il dito lo tiene di lato non vola via: decide il gesto.
        while (notice === n && swipe.x.value != 0f) delay(100)
        if (notice !== n) return@LaunchedEffect
        val from = noticeRect?.center?.takeIf { n.fly }
        val to = target(n)
        notice = null
        if (from != null && to != null) flight = Flight(n.icon, from, to.second.center, to.first)
    }

    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp)) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f) + slideInVertically { it / 3 },
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.4f, animationSpec = tween(160)),
        ) {
            val n = last ?: return@AnimatedVisibility
            Box(contentAlignment = Alignment.Center) {
            SwipeAwayMark(swipe)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                // Sopra la Scaffold nessuno dà il colore del testo: senza, nel tema scuro restava nero.
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = cardBorder(),
                modifier = Modifier
                    .swipeAway(swipe) { if (notice === n) notice = null }
                    .glass(RoundedCornerShape(22.dp))
                    .onGloballyPositioned { noticeRect = it.boundsInRoot() },
            ) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(n.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        n.message,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
                    )
                    val undo = n.undo
                    if (undo != null) {
                        TextButton(onClick = {
                            haptic(Haptic.TICK)
                            notice = null
                            scope.launch { undo() }
                        }) { Text(n.actionLabel) }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
            }
        }
    }

    flight?.let { f ->
        FlyingDot(f) {
            haptic(Haptic.TICK)
            CaptureTargets.arrived(f.key)
            flight = null
        }
    }
}

/** Il pallino col simbolo del tipo: vola ad arco, rimpicciolendo, e all'arrivo chiama [onArrived]. */
@Composable
private fun FlyingDot(f: Flight, onArrived: () -> Unit) {
    val progress = remember(f) { Animatable(0f) }
    val arrived by rememberUpdatedState(onArrived)
    LaunchedEffect(f) {
        progress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        arrived()
    }
    val density = LocalDensity.current
    val sizePx = with(density) { 36.dp.toPx() }
    val lift = with(density) { 120.dp.toPx() }
    val t = progress.value
    // Curva di Bézier: sale un po' e poi scende sul bersaglio.
    val control = Offset((f.from.x + f.to.x) / 2, minOf(f.from.y, f.to.y) - lift)
    val u = 1 - t
    val x = u * u * f.from.x + 2 * u * t * control.x + t * t * f.to.x
    val y = u * u * f.from.y + 2 * u * t * control.y + t * t * f.to.y
    val scale = 1f - 0.55f * t
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = Modifier
            .offset { IntOffset((x - sizePx / 2).roundToInt(), (y - sizePx / 2).roundToInt()) }
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(f.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}
