package com.platinum20.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.platinum20.dashboard.ui.screens.shouldHideKeyboard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDismissTest {
    @Test fun dragWithKeyboardHidesIt() =
        assertTrue(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(0f, -12f), imeOpen = true))

    @Test fun flingAndCodeScrollKeepIt() {
        assertFalse(shouldHideKeyboard(NestedScrollSource.SideEffect, Offset(0f, -40f), imeOpen = true))
    }

    @Test fun noKeyboardOrSidewaysDoesNothing() {
        assertFalse(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(0f, -12f), imeOpen = false))
        assertFalse(shouldHideKeyboard(NestedScrollSource.UserInput, Offset(30f, 0f), imeOpen = true))
    }
}
