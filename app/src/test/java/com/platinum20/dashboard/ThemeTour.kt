package com.platinum20.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.weather.GeoPlace
import com.platinum20.dashboard.weather.WeatherStore
import java.io.File
import java.util.TimeZone
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * La Home e le Impostazioni in ogni tema, chiaro e scuro: per controllare alone e vetro in tutte le combinazioni
 * prima di pubblicare (Antonio, 1.649: "non ha senso solo per un tema"). Solo con `-Ptour=true`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ThemeTour {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour").apply { mkdirs() }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
        System.setProperty("robolectric.screenshot.hwrdr.native", "true")
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        WeatherStore.setManualPlace(context, GeoPlace("Roma", "Lazio, Italia", 41.89, 12.48))
        NotesStore.init(context)
        if (NotesStore.data.value.tasks.isEmpty()) {
            NotesStore.addTask("Comprare il latte")
            NotesStore.addTask("Chiamare il dentista")
            NotesStore.saveNote(null, "Idee per le vacanze\nLisbona, Porto, oppure Sicilia in macchina.")
            NotesStore.saveHabit(null, "Bere 2 litri d'acqua")
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun temiChiari() = run("chiaro")

    @Test
    @Config(qualifiers = "+night")
    fun temiScuri() = run("scuro")

    private fun run(mode: String) {
        compose.mainClock.autoAdvance = false
        AppSettings.init(context)
        AppSettings.setTheme(AppTheme.DYNAMIC)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle(until = { WeatherStore.state.value.let { it.forecast != null || it.error != null } })
        for (theme in AppTheme.entries) {
            AppSettings.setTheme(theme)
            settle(2_000)
            shot("tema_${theme.name}_${mode}_home")
        }
        // Le Impostazioni: il tema scelto con il suo contorno.
        val gear = compose.onAllNodes(hasContentDescription("Impostazioni") and hasClickAction())
        if (gear.fetchSemanticsNodes().isNotEmpty()) {
            gear.onFirst().performSemanticsAction(SemanticsActions.OnClick)
            for (theme in AppTheme.entries) {
                AppSettings.setTheme(theme)
                settle(2_000)
                shot("tema_${theme.name}_${mode}_impostazioni")
            }
        }
    }

    private fun settle(maxMs: Long = 20_000, until: () -> Boolean = { false }) {
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
        scenario?.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            var copied = false
            runCatching {
                PixelCopy.request(activity.window, bitmap, { copied = it == PixelCopy.SUCCESS }, Handler(Looper.getMainLooper()))
                ShadowLooper.idleMainLooper()
            }
            if (!copied) runCatching { view.draw(Canvas(bitmap)) }
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
