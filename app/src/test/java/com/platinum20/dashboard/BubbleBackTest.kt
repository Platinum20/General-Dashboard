package com.platinum20.dashboard

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.platinum20.dashboard.ui.screens.BubbleBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Indietro chiude prima il fumetto aperto, anche se la pagina dietro ha un suo gestore registrato dopo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BubbleBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backClosesBubbleBeforePage() {
        var bubble by mutableStateOf(false)
        var showPage by mutableStateOf(false)
        var pageBacks = 0
        compose.setContent {
            // Il livello dei fumetti è composto all'avvio, la pagina con il suo gestore arriva dopo.
            BubbleBack(bubble) { bubble = false }
            if (showPage) BackHandler { pageBacks++ }
        }
        compose.runOnIdle { showPage = true }
        compose.runOnIdle { bubble = true }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertFalse(bubble)
            assertEquals(0, pageBacks)
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.runOnIdle { assertEquals(1, pageBacks) }
    }
}
