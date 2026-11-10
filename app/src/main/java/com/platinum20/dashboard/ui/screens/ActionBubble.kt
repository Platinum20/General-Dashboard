package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

/** Una scelta del fumetto delle azioni. */
internal data class BubbleAction(
    val icon: ImageVector,
    val title: String,
    val detail: String = "",
    val danger: Boolean = false,
    /** Scelte rapide in fila (le categorie di una spesa, punto 32): la riga diventa una fila di chip, [title] sopra. */
    val chips: List<BubbleChip> = emptyList(),
    val onClick: () -> Unit = {},
)

/** Un chip dentro una riga del fumetto: [selected] = quello di adesso. */
internal data class BubbleChip(val label: String, val selected: Boolean = false, val onClick: () -> Unit)

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
            if (index > 0) RowDivider()
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 12.dp.toPx()
                },
            ) {
                if (a.chips.isNotEmpty()) {
                    BubbleChips(a.title, a.chips) { chip ->
                        haptic(Haptic.CONFIRM)
                        ActionBubble.dismiss()
                        chip.onClick()
                    }
                } else BubbleRow(a.icon, a.title, a.detail, a.danger) {
                    haptic(Haptic.CONFIRM)
                    ActionBubble.dismiss()
                    a.onClick()
                }
            }
        }
    }
}

/**
 * Una riga del fumetto fatta di chip: un titolo piccolo e le scelte in fila, che vanno a capo. Sta dentro lo
 * stesso blocco di vetro delle altre righe, senza un riquadro suo (Antonio: "una bolla sopra l'altra").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BubbleChips(title: String, chips: List<BubbleChip>, onPick: (BubbleChip) -> Unit) {
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        if (title.isNotBlank()) {
            androidx.compose.material3.Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { c -> CanvasChip(selected = c.selected, onClick = { onPick(c) }, label = c.label) }
        }
    }
}
