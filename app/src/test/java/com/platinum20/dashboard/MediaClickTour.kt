package com.platinum20.dashboard

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Collaudo di Media: apre ogni pagina (Media, Musica, Video, Radio e podcast) e tocca uno per uno
 * tutti gli elementi toccabili, controllando che ognuno faccia qualcosa (la schermata cambia, si
 * apre una pagina, un menu o un'altra app). Scrive l'elenco in build/tour/collaudo-media.md.
 * Parte solo con `-Ptour=true`, insieme al giro fotografico (usa la rete vera).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class MediaClickTour {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("tour.dir") ?: "build/tour").apply { mkdirs() }
    private var scenario: ActivityScenario<MainActivity>? = null
    private val report = StringBuilder("# Collaudo di Media\n\n")
    private val dead = mutableListOf<String>()

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("tour") == "true")
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
    }

    @After
    fun tearDown() {
        scenario?.close()
        report.append("\n**Senza effetto: ${dead.size}**\n")
        dead.forEach { report.append("- $it\n") }
        File(outDir, "collaudo-media.md").writeText(report.toString())
        println(report)
    }

    @Test
    fun ogniTastoFaQualcosa() {
        compose.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle(4_000)
        val pages = listOf(
            "Media" to listOf("Media"),
            "Musica" to listOf("Media", "Musica"),
            "Video" to listOf("Media", "Video"),
            "Radio e podcast" to listOf("Media", "Radio e podcast"),
        )
        for ((name, path) in pages) checkPage(name, path)
    }

    /** Tocca tutto quello che si può toccare nella pagina [name], riaprendola ogni volta dalla barra in basso. */
    private fun checkPage(name: String, path: List<String>) {
        if (!open(path)) {
            report.append("## $name\n- ❌ pagina non raggiungibile\n\n")
            dead += "$name: pagina non raggiungibile"
            return
        }
        settle(3_000)
        val labels = clickableLabels().distinct().filter { it !in navLabels }.take(45)
        report.append("## $name (${labels.size} elementi)\n")
        for (label in labels) {
            if (!open(path)) break
            settle(1_200)
            val before = signature()
            shadowOf(context as Application).clearNextStartedActivities()
            val clicked = runCatching { tap(label) }.getOrDefault(false)
            if (!clicked) {
                report.append("- ⚪ $label (non più a schermo)\n")
                continue
            }
            settle(1_500)
            val started = shadowOf(context as Application).nextStartedActivity
            val dialog = ShadowDialog.getLatestDialog()?.isShowing == true
            val changed = signature() != before
            when {
                started != null -> report.append("- ✅ $label → apre ${started.action ?: started.component?.shortClassName}\n")
                dialog -> report.append("- ✅ $label → finestra\n")
                changed -> report.append("- ✅ $label\n")
                else -> {
                    report.append("- ❌ $label: nessun effetto visibile\n")
                    dead += "$name › $label"
                }
            }
            ShadowDialog.getLatestDialog()?.dismiss()
        }
        report.append('\n')
    }

    private val navLabels = setOf("Oggi", "News", "Note", "Media", "Aggiungi", "Impostazioni")

    /** Torna alla barra in basso e segue il percorso fino alla pagina. */
    private fun open(path: List<String>): Boolean {
        repeat(4) {
            if (exists("Oggi") && exists("Media")) return@repeat
            scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            settle(600)
        }
        for (step in path) {
            if (!tap(step)) return false
            settle(1_200)
        }
        return true
    }

    private fun clickableLabels(): List<String> =
        compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        }

    /** Tutto quello che si vede e il suo stato: se un tocco non cambia niente di questo, non ha fatto nulla. */
    private fun signature(): String {
        val out = StringBuilder()
        fun walk(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.let { out.append(it.joinToString()).append('|') }
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.let { out.append(it.joinToString()).append('|') }
            n.config.getOrNull(SemanticsProperties.Selected)?.let { out.append("sel=").append(it).append('|') }
            n.config.getOrNull(SemanticsProperties.ToggleableState)?.let { out.append("tog=").append(it).append('|') }
            n.config.getOrNull(SemanticsProperties.EditableText)?.let { out.append("ed=").append(it).append('|') }
            n.children.forEach(::walk)
        }
        runCatching { compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::walk) }
        return out.toString()
    }

    private fun matcher(label: String) = (hasText(label) or hasContentDescription(label)) and hasClickAction()

    private fun exists(label: String) = compose.onAllNodes(matcher(label)).fetchSemanticsNodes().isNotEmpty()

    private fun tap(label: String): Boolean {
        val nodes = compose.onAllNodes(matcher(label))
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        nodes.onFirst().performClick()
        return true
    }

    private fun settle(maxMs: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
            Thread.sleep(30)
        }
    }
}
