package com.platinum20.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.BubbleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionBubbleTest {
    @Test
    fun showKeepsOrderAndDismissClears() {
        val a = BubbleAction(Icons.Outlined.Share, "Uno") {}
        val b = BubbleAction(Icons.Outlined.Share, "Due", danger = true) {}
        ActionBubble.show(a, b)
        assertEquals(listOf("Uno", "Due"), ActionBubble.actions?.map { it.title })
        ActionBubble.dismiss()
        assertNull(ActionBubble.actions)
    }
}

/** Il linguaggio dei fumetti (PDF Fila-unica): righe di chip da quattro al massimo, barra nell'ordine fisso. */
class BubbleLayoutTest {
    private fun chip(n: String, icon: Boolean = false) =
        com.platinum20.dashboard.ui.screens.BubbleChip(n, icon = if (icon) Icons.Outlined.Share else null) {}

    @Test
    fun chipRowsNeverMoreThanFourAndEven() {
        val rows = { n: Int -> com.platinum20.dashboard.ui.screens.chipRows((1..n).toList()).map { it.size } }
        assertEquals(listOf(2), rows(2))
        assertEquals(listOf(4), rows(4))
        assertEquals(listOf(3, 2), rows(5))
        assertEquals(listOf(4, 3), rows(7))
        assertEquals(listOf(4, 4), rows(8))
        assertEquals(listOf(3, 3, 3), rows(9))
        assertEquals(listOf(4, 4, 4), rows(12))
    }

    @Test
    fun selectorOnOneRowOnlyWithIcons() {
        assertEquals(true, com.platinum20.dashboard.ui.screens.selectorFitsOneRow((1..7).map { chip("$it", icon = true) }))
        assertEquals(false, com.platinum20.dashboard.ui.screens.selectorFitsOneRow(listOf(chip("Giorno"), chip("Mese"))))
    }

    @Test
    fun barGoesInFixedOrderAndRowsByGroup() {
        val s = com.platinum20.dashboard.ui.screens.Slot
        val layout = com.platinum20.dashboard.ui.screens.bubbleLayout(
            listOf(
                s.delete {},
                com.platinum20.dashboard.ui.screens.act(com.platinum20.dashboard.ui.screens.BubbleGroup.METTI, Icons.Outlined.Share, "Metti", "") {},
                s.share {},
                com.platinum20.dashboard.ui.screens.choice("Coda", chip("Subito dopo"), chip("In fondo")),
                com.platinum20.dashboard.ui.screens.act(com.platinum20.dashboard.ui.screens.BubbleGroup.SEGNA, Icons.Outlined.Share, "Segna", "") {},
                s.open {},
            ),
        )
        assertEquals(listOf("Apri", "Condividi", "Elimina"), layout.bar.map { it.title })
        assertEquals(listOf("Segna", "Metti"), layout.rows.map { it.title })
        assertEquals(listOf("Coda"), layout.chips.map { it.title })
    }
}
