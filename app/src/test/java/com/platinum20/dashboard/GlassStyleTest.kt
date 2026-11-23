package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.Glass
import com.platinum20.dashboard.ui.screens.glassScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassStyleTest {
    @Test
    fun scaleFollowsTheGrowingGlass() {
        assertEquals(1f, glassScale(10f, 110f, 100f), 0.0001f)
        assertEquals(0.9f, glassScale(15f, 105f, 100f), 0.0001f)
    }

    @Test
    fun scaleFallsBackToOneWhenNotMeasurable() {
        assertEquals(1f, glassScale(0f, 0f, 0f), 0f)
        assertEquals(1f, glassScale(0f, 0f, 100f), 0f)
        assertEquals(1f, glassScale(0f, Float.NaN, 100f), 0f)
    }

    @Test
    fun oneTintForEveryGlass() {
        // Il vetro si vede sotto (velo leggero), quello colorato del + resta pieno di colore, senza sfocatura quasi pieno.
        assertTrue(Glass.tint(dark = false) < 0.5f && Glass.tint(dark = true) < 0.5f)
        assertTrue(Glass.tint(dark = false, colored = true) > 0.75f)
        assertTrue(Glass.solidTint() > 0.9f)
        assertTrue(Glass.scrim(dark = true) > Glass.scrim(dark = false))
    }
}
