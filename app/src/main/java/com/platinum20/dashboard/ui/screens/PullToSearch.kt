package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Trascinare giù dalla cima di una sezione apre "Cerca ovunque": scende una lente e, passata
 * la soglia, lasciando il dito si apre la ricerca. Vale per tutte le sezioni della barra, perché
 * ascolta lo scorrimento che le liste non usano più (sono già in cima).
 */
class PullToSearchState internal constructor(private val threshold: Float) {
    var offset by mutableFloatStateOf(0f)
        internal set
    val progress: Float get() = (offset / threshold).coerceIn(0f, 1f)
    internal var enabled = true
    internal var onOpen: () -> Unit = {}
    internal var onArm: () -> Unit = {}
    internal val settle = Animatable(0f)

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Tornando su mentre la lente è giù: prima rientra la lente, poi scorre la lista.
            if (offset > 0f && available.y < 0f) {
                val used = maxOf(available.y, -offset)
                move(offset + used)
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            // Più si tira più fa resistenza, come un elastico.
            move(offset + available.y * 0.45f * (1f - progress * 0.5f))
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset <= 0f) return Velocity.Zero
            if (offset >= threshold) onOpen()
            settle.snapTo(offset)
            settle.animateTo(0f) { offset = value }
            return available
        }
    }

    private fun move(to: Float) {
        val wasArmed = offset >= threshold
        offset = to.coerceIn(0f, threshold * 1.6f)
        if (!wasArmed && offset >= threshold) onArm()
    }
}

@Composable
fun rememberPullToSearch(enabled: Boolean, onOpen: () -> Unit): PullToSearchState {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val haptic = rememberHaptic()
    val open by rememberUpdatedState(onOpen)
    val state = remember(threshold) { PullToSearchState(threshold) }
    state.enabled = enabled
    state.onOpen = { haptic(Haptic.CONFIRM); open() }
    state.onArm = { haptic(Haptic.TICK) }
    // Uscendo da una sezione la lente sparisce subito.
    LaunchedEffect(enabled) { if (!enabled) state.offset = 0f }
    return state
}

/** La lente che scende dall'alto mentre si trascina; piena e più grande quando basta lasciare. */
@Composable
fun BoxScope.PullToSearchIndicator(state: PullToSearchState) {
    if (state.offset <= 0f) return
    val p = state.progress
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .graphicsLayer {
                translationY = state.offset - 8.dp.toPx()
                alpha = (p * 1.6f).coerceAtMost(1f)
                val s = 0.6f + 0.4f * p + if (p >= 1f) 0.08f else 0f
                scaleX = s
                scaleY = s
            },
    ) {
        val armed = p >= 1f
        Surface(
            shape = CircleShape,
            color = if (armed) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            border = cardBorder(),
            modifier = Modifier.size(48.dp).glass(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, contentDescription = "Lascia per cercare", modifier = Modifier.padding(12.dp))
            }
        }
    }
}
