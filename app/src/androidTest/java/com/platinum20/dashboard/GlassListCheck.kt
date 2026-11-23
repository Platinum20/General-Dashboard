package com.platinum20.dashboard

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.ActionBubbleLayer
import com.platinum20.dashboard.ui.screens.Backdrop
import com.platinum20.dashboard.ui.screens.BubbleAction
import com.platinum20.dashboard.ui.screens.FeedList
import com.platinum20.dashboard.ui.screens.HeroStyle
import com.platinum20.dashboard.ui.screens.LocalBackdrop
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.ui.screens.MediaHero
import com.platinum20.dashboard.ui.screens.MediaRow
import com.platinum20.dashboard.ui.screens.backdropSource
import com.platinum20.dashboard.ui.screens.mediaGroup
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Il fumetto di vetro sopra una pagina come la playlist (MediaHero COVER con l'ombra + righe) e come l'artista
 * (MediaHero PHOTO), dentro un AnimatedContent come il NavHost. Antonio: nella playlist il vetro non sfoca.
 * Le foto sono della sola finestra dell'app (PixelCopy): i dialoghi di sistema dell'emulatore non coprono niente.
 */
@RunWith(AndroidJUnit4::class)
class GlassListCheck {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val instr get() = InstrumentationRegistry.getInstrumentation()

    private fun shot(name: String) {
        // La regola dei test tiene ferme le animazioni: il fumetto compare solo se il suo orologio va avanti.
        compose.mainClock.advanceTimeBy(1500)
        Thread.sleep(600)
        val dir = File(instr.targetContext.getExternalFilesDir(null), "glass-check").apply { mkdirs() }
        runCatching {
            val window = compose.activity.window
            val view = window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val done = CountDownLatch(1)
            PixelCopy.request(window, bitmap, { done.countDown() }, Handler(Looper.getMainLooper()))
            done.await(5, TimeUnit.SECONDS)
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { Log.e("GlassCheck", "shot $name failed", it) }
        Log.i("GlassCheck", "shot $name")
    }

    @Test
    fun bubbleOverPages() {
        lateinit var list: LazyListState
        var page by mutableStateOf("cover")
        // a = il vetro legge la pagina (AnimatedContent, come il NavHost); b = legge un Box attorno, come MainActivity ora;
        // c = solo il Box attorno, l'AnimatedContent senza livello suo.
        var mode by mutableStateOf("a")
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                val pages = remember { Backdrop() }
                val screen = remember { Backdrop() }
                CompositionLocalProvider(LocalBackdrop provides if (mode == "a") pages else screen) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.fillMaxSize().then(if (mode != "a") Modifier.backdropSource(screen) else Modifier)) {
                        AnimatedContent(page, Modifier.fillMaxSize().then(if (mode != "c") Modifier.backdropSource(pages) else Modifier), label = "pagine") { p ->
                            list = remember { LazyListState() }
                            FeedList(
                                Loadable(data = (0 until 60).toList()), empty = "", listState = list,
                                spacing = androidx.compose.ui.unit.Dp(0f), edge = androidx.compose.ui.unit.Dp(0f), top = androidx.compose.ui.unit.Dp(0f),
                                header = {
                                    MediaHero(
                                        title = if (p == "cover") "Discover Weekly" else "Daft Punk", image = "", onBack = {},
                                        style = if (p == "cover") HeroStyle.COVER else HeroStyle.PHOTO,
                                        line = "Scopri", detail = "30 brani", onPlay = {}, onShuffle = {}, onMore = {},
                                    )
                                },
                            ) {
                                mediaGroup(key = "t", title = "", items = (0 until 60).toList(), itemKey = { it }) { _, i ->
                                    MediaRow(title = "Brano numero $i", line = "Artista $i", click = Modifier, trailing = "3:${10 + i % 50}")
                                }
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
        fun close() { instr.runOnMainSync { ActionBubble.dismiss() }; compose.mainClock.advanceTimeBy(800); Thread.sleep(300) }
        fun scroll(index: Int) { compose.runOnUiThread { kotlinx.coroutines.MainScope().launch { list.scrollToItem(index) } }; compose.mainClock.advanceTimeBy(500); Thread.sleep(500) }
        for (m in listOf("a", "b", "c")) {
            compose.runOnUiThread { mode = m; page = "cover" }
            compose.mainClock.advanceTimeBy(1500); Thread.sleep(500)
            bubble(); shot("$m-1-in-cima"); close()
            scroll(25)
            bubble(); shot("$m-2-scorsa"); close()
            scroll(0)
        }
        Log.i("GlassCheck", "list done")
    }
}
