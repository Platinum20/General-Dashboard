package com.platinum20.dashboard

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.ActionBubbleLayer
import com.platinum20.dashboard.ui.screens.Backdrop
import com.platinum20.dashboard.ui.screens.BubbleAction
import com.platinum20.dashboard.ui.screens.LocalBackdrop
import com.platinum20.dashboard.ui.screens.MediaRow
import com.platinum20.dashboard.ui.screens.backdropSource
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Il fumetto di vetro sopra una lista lunga come quella di una playlist (righe sullo sfondo, nessuna card):
 * ferma in cima e dopo averla fatta scorrere. Antonio: nella playlist il vetro non sfoca, nella pagina artista sì.
 */
@RunWith(AndroidJUnit4::class)
class GlassListCheck {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val instr get() = InstrumentationRegistry.getInstrumentation()

    private fun shot(name: String) {
        Thread.sleep(1200)
        val dir = File(instr.targetContext.getExternalFilesDir(null), "glass-check").apply { mkdirs() }
        runCatching {
            instr.uiAutomation.takeScreenshot()?.let { b ->
                File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        Log.i("GlassCheck", "shot $name")
    }

    @Test
    fun bubbleOverLongList() {
        lateinit var list: LazyListState
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                val backdrop = remember { Backdrop() }
                list = remember { LazyListState() }
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                            Column(Modifier.fillMaxSize()) {
                                LazyColumn(state = list) {
                                    items(60) { i -> MediaRow(title = "Brano numero $i", line = "Artista $i", click = Modifier, trailing = "3:${10 + i % 50}") }
                                }
                            }
                        }
                        ActionBubbleLayer()
                    }
                }
            }
        }
        compose.waitForIdle()
        fun bubble() = instr.runOnMainSync {
            ActionBubble.show(
                BubbleAction(Icons.Rounded.Star, "Aggiungi alla coda", "Una riga") {},
                BubbleAction(Icons.Rounded.Star, "Radio", "Brani simili") {},
                BubbleAction(Icons.Rounded.Star, "Vai all'artista", "Artista") {},
            )
        }
        bubble(); shot("10-lista-in-cima")
        instr.runOnMainSync { ActionBubble.dismiss() }
        Thread.sleep(800)
        compose.runOnUiThread { kotlinx.coroutines.MainScope().launch { list.scrollToItem(20) } }
        Thread.sleep(1500)
        bubble(); shot("11-lista-scorsa-codice")
        instr.runOnMainSync { ActionBubble.dismiss() }
        Thread.sleep(800)
        instr.uiAutomation.executeShellCommand("input swipe 540 1800 540 600 400").close()
        Thread.sleep(1500)
        bubble(); shot("12-lista-scorsa-dito")
        Log.i("GlassCheck", "list done")
    }
}
