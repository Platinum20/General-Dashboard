package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Una scelta del fumetto delle azioni. */
internal data class BubbleAction(
    val icon: ImageVector,
    val title: String,
    val detail: String = "",
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * I menu di Canvas (tieni premuto su una nota, conferme): invece del menu a tendina sale lo stesso
 * fumetto di vetro di "Salva nelle note", in fondo al centro, e si chiude portandolo di lato.
 * Chi lo usa chiama solo [show]; lo disegna [ActionBubbleLayer] sopra tutte le schermate.
 */
internal object ActionBubble {
    var actions by mutableStateOf<List<BubbleAction>?>(null)
        private set

    /** La riga tenuta premuta che ha aperto il fumetto: resta accesa finché il fumetto è aperto. */
    var source by mutableStateOf<String?>(null)

    fun show(vararg choices: BubbleAction) {
        actions = choices.toList()
    }

    fun dismiss() {
        actions = null
        source = null
    }

    /** Questa riga ha aperto il fumetto che si vede adesso. */
    fun heldBy(key: String): Boolean = actions != null && source == key
}

@Composable
internal fun BoxScope.ActionBubbleLayer() {
    val actions = ActionBubble.actions
    val haptic = rememberHaptic()
    // Le ultime azioni restano disegnate mentre il fumetto si chiude.
    var last by remember { mutableStateOf<List<BubbleAction>>(emptyList()) }
    val swipe = rememberSwipeAwayState()
    LaunchedEffect(actions) {
        if (actions == null) return@LaunchedEffect
        last = actions
        swipe.reset()
    }
    BottomBubble(visible = actions != null, swipe = swipe, onDismiss = ActionBubble::dismiss) {
        last.forEachIndexed { index, a ->
            // Le voci salgono una dopo l'altra, dal basso, invece di comparire tutte insieme.
            val appear = remember(last) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(last) {
                kotlinx.coroutines.delay((last.size - 1 - index) * 30L)
                appear.animateTo(1f, Motion.lively())
            }
            if (index > 0) RowDivider(androidx.compose.ui.Modifier.padding(horizontal = 16.dp))
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 12.dp.toPx()
                },
            ) {
                BubbleRow(a.icon, a.title, a.detail, a.danger) {
                    haptic(Haptic.CONFIRM)
                    ActionBubble.dismiss()
                    a.onClick()
                }
            }
        }
    }
}
