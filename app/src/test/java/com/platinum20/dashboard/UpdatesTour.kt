package com.platinum20.dashboard

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.UpdatesContent
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import com.platinum20.dashboard.update.GitHubRelease
import com.platinum20.dashboard.update.UpdateStatus
import com.platinum20.dashboard.update.UpdateUiState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Foto della pagina Aggiornamenti (1.511, bozza C) con versioni finte ma note vere dal CHANGELOG.
 * Parte solo con `-Ptour=true` (workflow "Screenshot").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class UpdatesTour {
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour", "aggiornamenti").apply { mkdirs() }

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        AppSettings.init(ApplicationProvider.getApplicationContext())
    }

    /** Le note vere di una versione, prese dal CHANGELOG come fa la release. */
    private fun notes(version: String): String {
        val file = File("../CHANGELOG.md").takeIf { it.exists() } ?: File("CHANGELOG.md")
        return file.readLines().dropWhile { it != "## $version" }.drop(1).takeWhile { !it.startsWith("## ") }.joinToString("\n")
    }

    private fun releases(): List<GitHubRelease> {
        val now = Instant.now()
        return listOf("1.507.0" to 20L, "1.503.0" to 28L, "1.502.0" to 36L, "1.501.0" to 190L, "1.500.0" to 330L).map { (v, minAgo) ->
            GitHubRelease("v$v", "Canvas $v", notes(v), now.minus(minAgo, ChronoUnit.MINUTES).toString(), "https://x/$v.apk", 1)
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun scuro() {
        val checked = System.currentTimeMillis() - 60_000
        shoot("scuro_pronta", UpdateUiState(currentVersion = "1.501.0", releases = releases(), checkedAt = checked))
        shoot("scuro_aggiornato", UpdateUiState(currentVersion = "1.507.0", releases = releases(), checkedAt = checked))
        val r = releases().first()
        shoot("scuro_scarico", UpdateUiState(currentVersion = "1.501.0", releases = releases(), checkedAt = checked, status = UpdateStatus.Downloading(r, 0.42f)))
    }

    @Test
    fun chiaro() {
        shoot("chiaro_pronta", UpdateUiState(currentVersion = "1.501.0", releases = releases(), checkedAt = System.currentTimeMillis() - 60_000))
    }

    private fun shoot(name: String, state: UpdateUiState) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { GeneralDashboardTheme(AppTheme.CANVAS) { UpdatesContent(state, {}, {}, {}) } }
        repeat(20) { ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        var copied = false
        runCatching {
            PixelCopy.request(activity.window, bitmap, { copied = it == PixelCopy.SUCCESS }, Handler(Looper.getMainLooper()))
            ShadowLooper.idleMainLooper()
        }
        if (!copied) view.draw(android.graphics.Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
