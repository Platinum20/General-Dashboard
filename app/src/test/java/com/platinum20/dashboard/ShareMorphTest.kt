package com.platinum20.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.BubbleAction
import com.platinum20.dashboard.ui.screens.BubbleHead
import com.platinum20.dashboard.ui.screens.Slot
import com.platinum20.dashboard.ui.screens.shareRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Condividi trasforma lo stesso fumetto in Copia · PDF · Manda a… (Antonio 2026-11-24). */
class ShareMorphTest {
    @Test
    fun rowsInFixedOrderAndPdfOnlyWhenGiven() {
        assertEquals(listOf("Salva come PDF", "Copia il testo", "Manda a…"), shareRows({}, "Copia il testo", "", {}, {}).map { it.title })
        assertEquals(listOf("Copia il link", "Manda a…"), shareRows({}, "Copia il link", "", null, {}).map { it.title })
    }

    @Test
    fun shareWithNothingToCopySendsDirectly() {
        var sent = false
        val a = Slot.share { sent = true }
        assertFalse(a.stay)
        a.onClick()
        assertTrue(sent)
    }

    @Test
    fun shareMorphsAndBackRestores() {
        val other = BubbleAction(Icons.Outlined.Share, "Archivia") {}
        val share = Slot.share(copy = {}) {}
        ActionBubble.show(BubbleHead("Nota", "Spesa"), other, share)
        assertTrue(share.stay)
        share.onClick()
        assertEquals("Condividi", ActionBubble.head?.kind)
        assertEquals("Spesa", ActionBubble.head?.name)
        assertEquals(listOf("Copia il testo", "Manda a…", "Indietro"), ActionBubble.actions?.map { it.title })
        assertTrue(ActionBubble.canGoBack)
        ActionBubble.back()
        assertEquals(listOf("Archivia", "Condividi"), ActionBubble.actions?.map { it.title })
        assertFalse(ActionBubble.canGoBack)
        ActionBubble.dismiss()
        assertNull(ActionBubble.actions)
    }
}
