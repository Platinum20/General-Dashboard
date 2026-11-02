package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.QueueSheet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSheetTest {
    @Test fun sidewaysOnlyWhenWider() {
        assertTrue(QueueSheet.sideways(2400f, 1080f))
        assertFalse(QueueSheet.sideways(1080f, 2400f))
    }

    @Test fun closesPastAThirdOrOnFling() {
        assertTrue(QueueSheet.closes(400f, 0f, 900f))
        assertFalse(QueueSheet.closes(200f, 0f, 900f))
        assertTrue(QueueSheet.closes(40f, 2000f, 900f))
        assertFalse(QueueSheet.closes(0f, 2000f, 900f))
        assertFalse(QueueSheet.closes(40f, -2000f, 900f))
    }
}
