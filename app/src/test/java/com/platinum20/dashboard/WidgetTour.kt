package com.platinum20.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.weather.GeoPlace
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.widget.Widgets
import com.platinum20.dashboard.widget.WidgetSize
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.TimeZone

/**
 * Foto dei widget della schermata Home, chiari e scuri, per vederli senza telefono.
 * Parte solo con `-Ptour=true` (workflow "Screenshot"), come il giro dell'app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class WidgetTour {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour", "widget").apply { mkdirs() }

    /** Le misure di ogni widget sulla Home (dp), come li metterebbe Antonio. */
    private val sizes = mapOf(
        "DashboardWidget" to WidgetSize(360, 200),
        "TasksWidget" to WidgetSize(360, 300),
        "NewsWidget" to WidgetSize(360, 240),
        "SimWidget" to WidgetSize(170, 170),
        "WeatherWidget" to WidgetSize(360, 200),
        "CaptureWidget" to WidgetSize(360, 72),
    )

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        WeatherStore.setManualPlace(context, GeoPlace("Roma", "Lazio, Italia", 41.89, 12.48))
        runCatching { runBlocking { WeatherStore.fetch(context, freshLocation = false) } }
        NotesStore.init(context)
        if (NotesStore.data.value.tasks.isEmpty()) {
            val now = System.currentTimeMillis()
            NotesStore.addTaskAt("Pagare la bolletta della luce", now - 26 * 3_600_000)
            NotesStore.addTaskAt("Chiamare il commercialista", now + 2 * 3_600_000)
            NotesStore.addTask("Comprare il latte")
            NotesStore.addTask("domani alle 9 dentista")
            NotesStore.saveDate(null, "Compleanno di Marco", LocalDate.now().plusDays(2), yearly = true)
            NotesStore.saveParcel(null, "Cuffie", "TBA123456789012", LocalDate.now())
        }
        context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
            .putString(
                "headlines",
                """[{"title":"Manovra, via libera del Consiglio dei ministri al testo finale","link":"https://a","source":"ANSA","n":5,"at":${System.currentTimeMillis() - 3_600_000}},""" +
                    """{"title":"Maltempo al Nord, allerta arancione in Liguria e Piemonte","link":"https://b","source":"Il Post","n":3,"at":${System.currentTimeMillis() - 7_200_000}},""" +
                    """{"title":"Champions, l'Inter vince ancora e vola agli ottavi","link":"https://c","source":"Sky TG24","n":2,"at":${System.currentTimeMillis() - 10_800_000}}]""",
            )
            .putInt("fresh", 7)
            .apply()
    }

    @Test
    fun chiari() = shoot("")

    @Test
    @Config(qualifiers = "+night")
    fun scuri() = shoot("scuro_")

    private fun shoot(prefix: String) {
        val density = context.resources.displayMetrics.density
        for ((cls, widget) in Widgets.all) {
            val size = sizes[cls.simpleName] ?: WidgetSize(250, 180)
            val views = runBlocking { widget.renderer(context, size) }
            val w = (size.widthDp * density).toInt()
            val h = (size.heightDp * density).toInt()
            val pad = (16 * density).toInt()
            val parent = FrameLayout(context)
            val view = views.apply(context, parent)
            parent.addView(view, FrameLayout.LayoutParams(w, h))
            parent.setPadding(pad, pad, pad, pad)
            // Uno sfondo come una carta da parati qualsiasi, per vedere bordi e angoli.
            parent.setBackgroundColor(if (prefix.isEmpty()) 0xFF8FA3B8.toInt() else 0xFF2B3440.toInt())
            parent.measure(
                View.MeasureSpec.makeMeasureSpec(w + 2 * pad, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h + 2 * pad, View.MeasureSpec.EXACTLY),
            )
            parent.layout(0, 0, w + 2 * pad, h + 2 * pad)
            val bitmap = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
            parent.draw(Canvas(bitmap))
            File(outDir, "$prefix${cls.simpleName}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
