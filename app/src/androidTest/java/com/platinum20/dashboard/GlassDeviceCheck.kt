package com.platinum20.dashboard

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.BubbleAction
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Il vetro dei fumetti su un emulatore vero: si apre senza chiudere l'app? Foto in glass-check/. Solo da glasscheck.yml. */
@RunWith(AndroidJUnit4::class)
class GlassDeviceCheck {
    private val instr get() = InstrumentationRegistry.getInstrumentation()

    private fun shot(name: String) {
        val dir = File(instr.targetContext.getExternalFilesDir(null), "glass-check").apply { mkdirs() }
        runCatching {
            instr.uiAutomation.takeScreenshot()?.let { b ->
                File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        Log.i("GlassCheck", "shot $name")
    }

    @Test
    fun bubbleOverPages() {
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(6000)
            shot("01-home")
            instr.runOnMainSync {
                ActionBubble.show(
                    BubbleAction(Icons.Rounded.Star, "Prova del vetro", "Una riga") {},
                    BubbleAction(Icons.Rounded.Star, "Seconda riga", "Ancora") {},
                )
            }
            Thread.sleep(3000)
            shot("02-bubble")
            Log.i("GlassCheck", "bubble shown, app alive")
            instr.runOnMainSync { ActionBubble.dismiss() }
            Thread.sleep(1500)
            shot("03-after")
            Log.i("GlassCheck", "done")
        }
    }
}
