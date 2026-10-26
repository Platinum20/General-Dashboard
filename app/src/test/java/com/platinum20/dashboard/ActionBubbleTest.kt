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
