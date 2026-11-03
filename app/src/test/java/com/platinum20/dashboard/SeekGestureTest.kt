package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SeekGesture
import com.platinum20.dashboard.ui.screens.SeekStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekGestureTest {
    @Test fun soloIlTrascinamentoDiLatoSposta() {
        assertTrue(SeekGesture.isSeek(20f, 5f))
        assertTrue(SeekGesture.isSeek(-20f, 5f))
        assertFalse(SeekGesture.isSeek(5f, 20f))
        assertFalse(SeekGesture.isSeek(0f, 0f))
    }

    @Test fun puntoDellaBarraLimitato() {
        assertEquals(0.5f, SeekGesture.fraction(50f, 100), 0.001f)
        assertEquals(0f, SeekGesture.fraction(-10f, 100), 0.001f)
        assertEquals(1f, SeekGesture.fraction(150f, 100), 0.001f)
        assertEquals(0f, SeekGesture.fraction(10f, 0), 0.001f)
    }

    @Test fun doppioToccoSiSomma() {
        assertEquals(10, SeekStack.next(0, true))
        assertEquals(20, SeekStack.next(10, true))
        assertEquals(40, SeekStack.next(30, true))
        assertEquals(-10, SeekStack.next(0, false))
        assertEquals(-20, SeekStack.next(-10, false))
        // Cambiando lato si riparte da 10.
        assertEquals(-10, SeekStack.next(30, false))
        assertEquals(10, SeekStack.next(-20, true))
    }
}
