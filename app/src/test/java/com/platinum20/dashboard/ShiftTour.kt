package com.platinum20.dashboard

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.shiftEvent
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.shifts.PayMonth
import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftEarning
import com.platinum20.dashboard.shifts.ShiftParse
import com.platinum20.dashboard.shifts.ShiftPay
import com.platinum20.dashboard.shifts.ShiftStore
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.ui.screens.EventDetailScreen
import com.platinum20.dashboard.ui.screens.TimelineEntry
import com.platinum20.dashboard.ui.screens.TimelineRow
import com.platinum20.dashboard.ui.screens.tripStops
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Foto delle righe dei turni nel calendario e della scheda del turno, con dati finti.
 * Parte solo con `-Ptour=true` (workflow "Screenshot"), come il giro dell'app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-night-xxhdpi")
class ShiftTour {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour", "turni").apply { mkdirs() }
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.now(zone)
    private fun at(day: LocalDate, h: Int) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private val done = Shift("s1", at(today.minusDays(1), 17), at(today.minusDays(1), 22),
        "Turno · Chiara Stefane, Debora Martini, Francesca Mon Cherie", "cp:1", listOf("Vincenzo Rossi"))
    private val next = Shift("s2", at(today.plusDays(1), 7), at(today.plusDays(1), 12), "Turno · Debora Martini, Chiara Stefane", "cp:1")

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        val month = today.withDayOfMonth(1)
        context.getSharedPreferences("shifts", Context.MODE_PRIVATE).edit()
            .putString("shifts", ShiftParse.toJson(listOf(done, next)))
            .putString("pay", ShiftPay.toJson(listOf(
                ShiftEarning("s1", done.start, done.end, sold = 736.08, earned = 110.41, percent = 0.15, hours = 5.3),
                ShiftEarning("s2", next.start, next.end, sold = 0.0, earned = 0.0, percent = 0.10, hours = 0.0),
            )))
            .putString("payMonths", ShiftPay.monthsToJson(listOf(
                PayMonth("m1", at(month, 0), at(month.plusMonths(1), 0) - 1, 747.04, 4980.0, 7, 36.0, 604.0, 19, "open"),
            )))
            .commit()
        // Il negozio è un singleton già aperto da altri test: si riapre per leggere questi dati.
        ShiftStore::class.java.getDeclaredField("appContext").apply { isAccessible = true }.set(null, null)
        ShiftStore.init(context)
    }

    private fun save(name: String) {
        compose.waitForIdle()
        // Disegniamo la finestra a mano, come ScreenshotTour: captureToImage aspetta un frame che non arriva.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun righeDelCalendario() {
        val pay = ShiftStore.pay.value
        fun shiftRow(s: Shift, key: String, day: String?, past: Boolean, divider: Boolean = false) =
            shiftEvent(s, ShiftPay.of(s, pay)).let { e ->
                TimelineEntry(key, BriefKind.EVENT, e.title, day, color = e.color, past = past, divider = divider, trip = tripStops(e, today, zone))
            }
        val meeting = CalendarEvent(9, "Dentista", at(today.plusDays(1), 15), at(today.plusDays(1), 16), false, "Via Roma 3", 0xFF3B82F6.toInt())
        val rows = listOf(
            TimelineEntry("e1", BriefKind.MONEY, "5,00 €  Cavour Beer Food", "Dom 8", past = true),
            TimelineEntry("e2", BriefKind.MONEY, "4,99 €  windtre", "Ieri", past = true, divider = true),
            shiftRow(done, "s1", null, past = true),
            TimelineEntry("t0", BriefKind.MONEY, "Spesa di oggi", "Oggi", todayHeader = true, trailing = "9,20 €"),
            TimelineEntry("t1", BriefKind.TASK, "Chiamare il commercialista", null, time = "09:00"),
            shiftRow(next, "s2", "Domani", past = false, divider = true),
            shiftRow(next.copy(id = "s3", start = next.start + 86_400_000L, end = next.end + 86_400_000L), "s3", "Mer 11", past = false, divider = true),
            TimelineEntry(
                "m1", BriefKind.EVENT, meeting.title, null, color = meeting.color,
                trip = tripStops(meeting, today, zone),
            ),
        )
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        rows.forEachIndexed { i, r ->
                            TimelineRow(r, top = i > 0, bottom = i < rows.lastIndex, onClick = {}, start = 0.dp)
                        }
                    }
                }
            }
        }
        save("calendario_turni")
    }

    @Test
    fun schedaDelTurnoFatto() {
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EventDetailScreen(done.eventId, 0, 0, onBack = {}, onEdit = {})
                }
            }
        }
        Thread.sleep(1_500)
        compose.mainClock.advanceTimeBy(2_000)
        save("scheda_turno_fatto")
    }

    @Test
    fun schedaDelTurnoDaFare() {
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EventDetailScreen(next.eventId, 0, 0, onBack = {}, onEdit = {})
                }
            }
        }
        Thread.sleep(1_500)
        compose.mainClock.advanceTimeBy(2_000)
        save("scheda_turno_da_fare")
    }
}
