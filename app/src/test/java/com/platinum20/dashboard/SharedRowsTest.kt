package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SharedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedRowsTest {
    @Test
    fun `la stessa riga nella card e nella pagina non vola fra le due`() {
        assertNotEquals(SharedRows.key(SharedRows.HOME, "task-1"), SharedRows.key(SharedRows.PAGE, "task-1"))
    }

    @Test
    fun `la scheda aperta da una lista vola nella sua riga`() {
        assertEquals(SharedRows.key(SharedRows.HOME, "task-1"), SharedRows.key(SharedRows.HOME, "task-1"))
    }

    @Test
    fun `eventi e note non hanno titolo condiviso`() {
        assertNull(SharedRows.key(SharedRows.PAGE, "event-3-100"))
        assertNull(SharedRows.key(SharedRows.PAGE, "note-4"))
    }
}
