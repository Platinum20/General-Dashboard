package com.platinum20.dashboard

import androidx.compose.ui.graphics.Color
import com.platinum20.dashboard.ui.screens.Urgent
import org.junit.Assert.assertTrue
import org.junit.Test

class UrgentColorTest {
    // Fondi del blocco "Adesso" (primaryContainer) dei temi Canvas chiaro e scuro (e AMOLED, uguale allo scuro).
    private val lightHero = Color(0xFFE3E8FF)
    private val darkHero = Color(0xFF26326E)

    @Test
    fun arancioLeggibileSulBloccoColorato() {
        assertTrue(Urgent.contrast(Urgent.color(darkTheme = false), lightHero) >= 4.5f)
        assertTrue(Urgent.contrast(Urgent.color(darkTheme = true), darkHero) >= 4.5f)
    }

    @Test
    fun contrastoNoto() {
        assertTrue(Urgent.contrast(Color.Black, Color.White) > 20.9f)
        assertTrue(Urgent.contrast(Color.White, Color.White) < 1.01f)
    }
}
