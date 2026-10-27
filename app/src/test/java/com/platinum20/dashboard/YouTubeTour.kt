package com.platinum20.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtWatched
import com.platinum20.dashboard.media.youtube.player.YtChapter
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtQuality
import com.platinum20.dashboard.media.youtube.player.YtStream
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.YouTubeFeedScreen
import com.platinum20.dashboard.ui.screens.YouTubeWatchScreen
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Foto della sezione YouTube con dati di esempio (feed e player), senza account né rete.
 * Parte con `-Ptour=true` insieme al giro dell'app (workflow "Screenshot").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class YouTubeTour {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour").apply { mkdirs() }

    private val videos = listOf(
        video("dQw4w9WgXcQ", "Never Gonna Give You Up (4K Remaster)", "Rick Astley", 2, 213),
        video("jfKfPfyJRdk", "lofi hip hop radio 📚 beats to relax/study to", "Lofi Girl", 1, 0, live = true),
        video("9bZkp7q19f0", "PSY - GANGNAM STYLE (강남스타일) M/V", "officialpsy", 5, 252),
        video("kJQP7kiw5Fk", "Luis Fonsi - Despacito ft. Daddy Yankee", "Luis Fonsi", 26, 282),
        video("aaaaaaaaaaa", "Un minuto di cucina veloce", "Chef in 60", 3, 45, short = true),
        video("OPf0YbXqDm0", "Mark Ronson - Uptown Funk ft. Bruno Mars", "Mark Ronson", 50, 271),
    )

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        YouTubeAuth.previewConnected()
        YouTubeStore.preview(home = videos.shuffled(java.util.Random(4)), subscriptions = videos)
        YtHistory.preview(
            listOf(YtWatched("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", "", 85_000, 213_000, System.currentTimeMillis()))
        )
    }

    @Test
    fun feed() {
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) { YouTubeFeedScreen(onBack = {}, onPlay = {}, onLogin = {}, onOpenPhone = {}) }
        }
        settle()
        shot("youtube_iscrizioni")
    }

    @Test
    fun player() {
        YtPlayer.preview(
            YtStream(
                id = "dQw4w9WgXcQ",
                title = "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
                channel = "Rick Astley",
                thumbnail = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                videoUrl = null, audioUrl = null, muxedUrl = null, hlsUrl = null,
                height = 1080,
                qualities = listOf(1080, 720, 480, 360).map { YtQuality(it, "") },
                durationSec = 213,
                chapters = listOf(YtChapter("Intro", 0), YtChapter("Ritornello", 43), YtChapter("Ponte", 150)),
                views = 1_700_000_000,
                uploaded = "16 anni fa",
                subscribers = 4_200_000,
                likes = 18_000_000,
                description = "The official video for “Never Gonna Give You Up” by Rick Astley.\nRemastered in 4K.",
            )
        )
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) { YouTubeWatchScreen(url = null, onBack = {}) }
        }
        settle()
        shot("youtube_player")
    }

    private fun video(id: String, title: String, channel: String, hoursAgo: Long, seconds: Int, live: Boolean = false, short: Boolean = false) =
        YtVideo(
            id = id, title = title, channel = channel, channelId = "", published = Instant.now().minus(hoursAgo, ChronoUnit.HOURS),
            isShort = short, durationSec = seconds, live = live,
        )

    private fun settle() {
        repeat(30) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
        }
        Thread.sleep(2_000)
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
        }
    }

    private fun shot(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        // Dalla 1.268 il video è una TextureView: Robolectric non ha il renderer per disegnarla, si nasconde per la foto.
        val textures = textureViews(view).filter { it.visibility == View.VISIBLE }
        textures.forEach { it.visibility = View.INVISIBLE }
        try {
            view.draw(Canvas(bitmap))
        } finally {
            textures.forEach { it.visibility = View.VISIBLE }
        }
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun textureViews(view: View): List<TextureView> = when (view) {
        is TextureView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textureViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
