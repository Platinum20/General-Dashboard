package com.platinum20.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.ui.screens.LocalRouteScope
import com.platinum20.dashboard.ui.screens.LocalSharedScope
import com.platinum20.dashboard.ui.screens.SharedKeys
import com.platinum20.dashboard.ui.screens.TimelineCard
import com.platinum20.dashboard.ui.screens.TimelineEntry
import com.platinum20.dashboard.ui.screens.sharedItem
import com.platinum20.dashboard.ui.screens.tripStops
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * La card Calendario in Home non deve mai finire sotto la card dopo (Antonio, 2026-11-12: la SIM copriva
 * il turno di notte). Stessa struttura della Home: elementi condivisi, animateItem, testo grande.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-night-xxhdpi")
class CalendarCardOverlapTest {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val outDir = java.io.File(System.getProperty("tour.dir") ?: "build/tour", "calendario-card").apply { mkdirs() }

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun shift(id: Long, day: LocalDate) = CalendarEvent(
        id, "Turno · Debora Martini, Chiara Stefane", at(day, 22), at(day.plusDays(1), 3), false, "", 0xFF6D5DF6.toInt(),
    )

    private fun entries(full: Boolean): List<TimelineEntry> = buildList {
        add(TimelineEntry("today", BriefKind.EVENT, "Oggi", "Oggi", todayHeader = true))
        val visit = CalendarEvent(3, "Psichiatra", at(today, 18, 30), at(today, 19, 30), false, "", 0xFF39B6E5.toInt())
        add(TimelineEntry("e3", BriefKind.EVENT, visit.title, null, color = visit.color, trip = tripStops(visit, today, zone)))
        add(TimelineEntry("s1", BriefKind.EVENT, "Turno su Debora e Chiara", null, color = 0xFF6D5DF6.toInt(), trip = tripStops(shift(-5, today), today, zone)))
        add(TimelineEntry("n1", BriefKind.FREE, "Foto", null, detail = "Foto · Ciao", image = "/nessuna/foto.jpg"))
        add(TimelineEntry("todo", BriefKind.TASK, "Da fare · 1", null))
        if (full) {
            add(TimelineEntry("d1", BriefKind.DATE, "Claude Pro termina", "Domani"))
            add(TimelineEntry("s2", BriefKind.EVENT, "Turno su Debora e Chiara", null, color = 0xFF6D5DF6.toInt(), trip = tripStops(shift(-6, today.plusDays(1)), today, zone)))
        }
    }

    @org.junit.Before
    fun hardware() {
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
        System.setProperty("robolectric.screenshot.hwrdr.native", "true")
    }

    @Test
    fun laCardDopoNonCopreIlCalendario() {
        var full by mutableStateOf(false)
        var page by mutableStateOf(false)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) {
                GeneralDashboardTheme(AppTheme.CANVAS) {
                    SharedTransitionLayout(Modifier.fillMaxSize()) {
                        androidx.compose.animation.AnimatedContent(page, label = "pagina") { onPage ->
                            CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalRouteScope provides this) {
                                if (onPage) Box(Modifier.sharedItem(SharedKeys.CALENDAR, rounded = true).fillMaxSize())
                                else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(listOf("cal", "sim"), key = { it }) { key ->
                                        Box(Modifier.animateItem(placementSpec = null)) {
                                            if (key == "cal") TimelineCard(
                                                null, entries(full), onOpen = {}, empty = "Niente",
                                                modifier = Modifier.testTag("cal").sharedItem(SharedKeys.CALENDAR, rounded = true),
                                                footer = "Altre 3 a novembre",
                                            ) else Box(Modifier.testTag("sim").sharedItem(SharedKeys.SIM, rounded = true).fillMaxWidth().height(200.dp).background(androidx.compose.ui.graphics.Color.Red))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        fun check(step: String) {
            compose.waitForIdle()
            val cal = compose.onNodeWithTag("cal").getUnclippedBoundsInRoot()
            val sim = compose.onNodeWithTag("sim").getUnclippedBoundsInRoot()
            println("CARDPROBE $step cal=$cal sim=$sim")
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            runCatching {
                android.view.PixelCopy.request(compose.activity.window, bitmap, {}, android.os.Handler(android.os.Looper.getMainLooper()))
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
            }
            java.io.File(outDir, "$step.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("$step: Calendario finisce a ${cal.bottom}, la SIM comincia a ${sim.top}", cal.bottom <= sim.top)
        }
        check("prima")
        full = true
        compose.mainClock.advanceTimeBy(1_000)
        check("dopo")
        full = false
        compose.mainClock.advanceTimeBy(1_000)
        check("dinuovo")
        compose.mainClock.autoAdvance = false
        // Calendario aperto e chiuso; mentre torna arrivano i dati e la card cresce.
        page = true
        compose.mainClock.advanceTimeBy(1_000)
        page = false
        compose.mainClock.advanceTimeBy(120)
        full = true
        compose.mainClock.advanceTimeBy(2_000)
        check("ritorno")
        // Indietro interrotto a metà, poi la card si accorcia.
        page = true
        compose.mainClock.advanceTimeBy(150)
        page = false
        compose.mainClock.advanceTimeBy(2_000)
        full = false
        compose.mainClock.advanceTimeBy(1_000)
        check("interrotto")
    }
}
