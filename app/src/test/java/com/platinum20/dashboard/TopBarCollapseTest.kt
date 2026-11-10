package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.TopBarCollapse
import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarCollapseTest {
    @Test fun segueIlDitoSenzaScatti() {
        assertEquals(0f, TopBarCollapse.fraction(0f, 60f), 0.001f)
        assertEquals(0.5f, TopBarCollapse.fraction(-30f, 60f), 0.001f)
        assertEquals(1f, TopBarCollapse.fraction(-90f, 60f), 0.001f)
        assertEquals(0f, TopBarCollapse.fraction(10f, 60f), 0.001f)
        assertEquals(0f, TopBarCollapse.fraction(-10f, 0f), 0.001f)
    }

    @Test fun altezzaTitoloERigaSopra() {
        assertEquals(76f, TopBarCollapse.height(0f), 0.001f)
        assertEquals(64f, TopBarCollapse.height(0.5f), 0.001f)
        assertEquals(52f, TopBarCollapse.height(1f), 0.001f)
        assertEquals(1f, TopBarCollapse.titleScale(0f), 0.001f)
        assertEquals(19f / 30f, TopBarCollapse.titleScale(1f), 0.001f)
        assertEquals(1f, TopBarCollapse.subtitleAlpha(0f), 0.001f)
        assertEquals(0f, TopBarCollapse.subtitleAlpha(0.5f), 0.001f)
    }
}
