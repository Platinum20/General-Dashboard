package com.platinum20.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.platinum20.dashboard.ui.screens.shouldHideKeyboard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDismissTest {
    @Test fun dragWithKeyboardHidesIt() =
        assertTrue(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(0f, -12f), imeOpen = true, fingerDragging = true))

    /** 1.604: un tocco fa scorrere la pagina da sola (riga sopra la tastiera): la tastiera resta aperta. */
    @Test fun autoScrollAfterTapKeepsIt() =
        assertFalse(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(0f, -80f), imeOpen = true, fingerDragging = false))

    @Test fun flingAndCodeScrollKeepIt() {
        assertFalse(shouldHideKeyboard(NestedScrollSource.SideEffect, Offset(0f, -40f), imeOpen = true, fingerDragging = true))
    }

    @Test fun noKeyboardOrSidewaysDoesNothing() {
        assertFalse(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(0f, -12f), imeOpen = false, fingerDragging = true))
        assertFalse(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(30f, 0f), imeOpen = true, fingerDragging = true))
    }
}
