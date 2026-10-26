package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

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

    fun show(vararg choices: BubbleAction) {
        actions = choices.toList()
    }

    fun dismiss() {
        actions = null
    }
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
        last.forEach { a ->
            BubbleRow(a.icon, a.title, a.detail, a.danger) {
                haptic(Haptic.CONFIRM)
                ActionBubble.dismiss()
                a.onClick()
            }
        }
    }
}
