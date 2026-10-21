package com.platinum20.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.weather.GeoPlace
import com.platinum20.dashboard.weather.WeatherStore
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate

/**
 * Giro dell'app con foto di ogni schermata, per controllare l'aspetto senza un telefono.
 * Parte solo con `-Ptour=true` (workflow "Screenshot"): usa la rete vera per meteo e notizie.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTour {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour").apply { mkdirs() }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        // Dati di esempio: una città fissa (il GPS non c'è) e qualche promemoria.
        WeatherStore.setManualPlace(context, GeoPlace("Roma", "Lazio, Italia", 41.89, 12.48))
        NotesStore.init(context)
        if (NotesStore.data.value.tasks.isEmpty()) {
            NotesStore.addTask("Comprare il latte")
            NotesStore.addTask("Chiamare il dentista")
            NotesStore.addTask("Pagare la bolletta della luce")
            NotesStore.saveNote(null, "Idee per le vacanze\nLisbona, Porto, oppure Sicilia in macchina.")
            NotesStore.saveNote(null, "Libri da leggere\nIl nome della rosa, Le città invisibili")
            NotesStore.saveCountdown(null, "Compleanno di Marco", LocalDate.now().plusDays(12), yearly = true)
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun tour() {
        compose.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle(until = { WeatherStore.state.value.let { it.forecast != null || it.error != null } && NewsStore.result.value != null })
        shot("01_home")
        scroll(down = true)
        shot("02_home_fondo")

        val tabs = listOf("News", "Note", "Media", "Impostazioni")
        tabs.forEachIndexed { i, label ->
            if (tap(label)) {
                settle(3_000)
                shot("%02d_%s".format(10 + i, label.lowercase()))
            }
        }
    }

    /** Tocca il primo elemento cliccabile con quel testo; false se non c'è. */
    private fun tap(label: String): Boolean {
        val nodes = compose.onAllNodes((hasText(label) or hasContentDescription(label)) and hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        nodes.onFirst().performClick()
        return true
    }

    /** Scorre il primo contenitore scorrevole (la lista della schermata). */
    private fun scroll(down: Boolean) {
        runCatching {
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput {
                if (down) swipeUp(durationMillis = 300) else swipeDown(durationMillis = 300)
            }
        }
        settle(1_500)
    }

    /** Lascia girare l'app (rete compresa) per un po', o finché [until] è vero. */
    private fun settle(maxMs: Long = 25_000, until: () -> Boolean = { false }) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs && !until()) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
        }
    }

    private fun shot(name: String) {
        // Disegniamo la finestra a mano: captureToImage aspetterebbe un frame che col clock fermo non arriva.
        scenario?.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
