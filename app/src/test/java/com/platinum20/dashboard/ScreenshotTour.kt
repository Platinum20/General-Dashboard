package com.platinum20.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
            NotesStore.addTask("ogni mese il 5 pagare l'affitto")
            NotesStore.addTask("domani alle 9 chiamare il commercialista")
            NotesStore.saveHabit(null, "Bere 2 litri d'acqua")
            NotesStore.saveHabit(null, "Camminare 30 minuti")
            NotesStore.data.value.habits.firstOrNull()?.let { h ->
                (1L..5L).forEach { NotesStore.toggleHabit(h.id, LocalDate.now().minusDays(it)) }
            }
            NotesStore.addExpense("spesa conad 63,40")
            NotesStore.addExpense("benzina 40€")
            NotesStore.addExpense("12,50 pizza")
            NotesStore.setBudget(60_000)
            NotesStore.saveParcel(null, "Cuffie", "TBA123456789012", LocalDate.now())
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    @Config(qualifiers = "+night")
    fun tourDark() = tour(prefix = "scuro_")

    @Test
    fun tour() = tour(prefix = "")

    private fun tour(prefix: String) {
        compose.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle(until = { WeatherStore.state.value.let { it.forecast != null || it.error != null } && NewsStore.result.value != null })
        val p = prefix
        shot(p + "01_oggi")
        // Dettagli raggiungibili da Oggi
        // Le scorciatoie sono nascoste: si entra dalle card (il testo del luogo è dentro la card del meteo).
        // "Adesso · Roma": il luogo è dentro la riga in maiuscoletto della card scura.
        visit("Roma", p + "02_meteo", substring = true)
        visit("Tutti", p + "03_punto")
        visit("Personalizza", p + "04_personalizza")
        scroll(down = true)
        shot(p + "05_oggi_fondo")
        // Il + della barra in basso: la cattura veloce.
        if (tap("Aggiungi")) {
            settle(1_000); shot(p + "06_aggiungi")
            tap("Annulla"); settle(500)
        }

        if (tap("News")) { settle(3_000); shot(p + "10_news") }
        if (tap("Note")) {
            settle(1_500); shot(p + "20_note")
            for ((tab, name) in listOf(
                "Abitudini" to "23_abitudini", "Spese" to "24_spese", "Note" to "21_note_libere",
                "Pacchi" to "25_pacchi", "Date" to "22_date",
            )) {
                // Il primo nodo è la scheda in alto; "Note" compare anche nella barra in basso.
                val nodes = compose.onAllNodes(hasText(tab) and hasClickAction())
                val count = nodes.fetchSemanticsNodes().size
                if (count > 0) {
                    nodes[0].performClick()
                    settle(1_000); shot(p + name)
                }
            }
            // L'editor di una nota, dalla scheda Note.
            compose.onAllNodes(hasText("Note") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
                compose.onAllNodes(hasText("Note") and hasClickAction())[0].performClick()
                settle(1_000)
                visit("Libri da leggere", p + "26_nota", substring = true)
            }
        }
        if (tap("Media")) {
            settle(2_000); shot(p + "30_media")
            visit("YouTube", p + "31_youtube")
            visit("Spotify", p + "32_spotify")
            visit("Video", p + "33_video")
        }
        if (tap("Impostazioni")) {
            settle(1_500); shot(p + "40_impostazioni")
            scroll(down = true)
            shot(p + "42_impostazioni_fondo")
            visit("Compila da solo", p + "43_compila_da_solo")
            visit("Località", p + "44_localita")
            visit("Aggiornamenti", p + "41_aggiornamenti", waitMs = 6_000)
        }
    }

    /** Apre una schermata toccando [label], la fotografa e torna indietro. */
    private fun visit(label: String, name: String, waitMs: Long = 3_000, substring: Boolean = false) {
        if (!tap(label, substring)) return
        settle(waitMs)
        shot(name)
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle(1_000)
    }

    /** Tocca il primo elemento cliccabile con quel testo; false se non c'è. */
    private fun tap(label: String, substring: Boolean = false): Boolean {
        val text = hasText(label, substring = substring, ignoreCase = substring)
        val nodes = compose.onAllNodes((text or hasContentDescription(label)) and hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        nodes.onFirst().performClick()
        return true
    }

    /** Scorre la lista della schermata verso il fondo (azione semantica: col clock fermo gli swipe non arrivano). */
    private fun scroll(down: Boolean) {
        val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
        if (!down) {
            runCatching { list.performScrollToIndex(0) }
        } else {
            for (index in 12 downTo 1) {
                if (runCatching { list.performScrollToIndex(index) }.isSuccess) break
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
