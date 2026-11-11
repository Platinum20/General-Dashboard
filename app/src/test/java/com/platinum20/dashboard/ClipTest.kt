package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.Clip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTest {
    @Test
    fun fumettoSoloPrimaDiAndroid13() {
        assertTrue(Clip.ownNotice(32))
        assertFalse(Clip.ownNotice(33))
        assertFalse(Clip.ownNotice(36))
    }
}
