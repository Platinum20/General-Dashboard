package com.platinum20.dashboard

import android.graphics.Bitmap
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.mail.MailMessage
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteTable
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.WhenText
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.ActionBubbleLayer
import com.platinum20.dashboard.ui.screens.BubbleHead
import com.platinum20.dashboard.ui.screens.CaptureFeedbackLayer
import com.platinum20.dashboard.ui.screens.CaptureWhenAsk
import com.platinum20.dashboard.ui.screens.ItemDetailScreen
import com.platinum20.dashboard.ui.screens.ItemKind
import com.platinum20.dashboard.ui.screens.NoteEditorScreen
import com.platinum20.dashboard.ui.screens.PlaylistManage
import com.platinum20.dashboard.ui.screens.Slot
import com.platinum20.dashboard.ui.screens.captureActions
import com.platinum20.dashboard.ui.screens.mailNoteActions
import com.platinum20.dashboard.ui.screens.rememberHideKeyboardOnDrag
import com.platinum20.dashboard.ui.screens.showMediaBubble
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Le foto dei fumetti della 1.614 su un emulatore vero, una per proposta del PDF "Fila-unica", per il documento
 * "mockup contro app". I fumetti si aprono come nell'app: dall'editor vero (Altro, «/», tieni premuto) o chiamando le
 * stesse funzioni che chiamano le pagine. Solo da bubblecheck.yml; foto in bubble-check/.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BubbleShotsCheck {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instr get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instr.targetContext
    private val outDir by lazy { File(context.getExternalFilesDir(null), "bubble-check").apply { mkdirs() } }
    private var id = 0L

    @Before
    fun setUp() {
        NotesStore.init(context)
        NotesStore.data.value.notes.forEach { NotesStore.deleteNote(it.id) }
        ActionBubble.dismiss()
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @After
    fun tearDown() {
        ActionBubble.dismiss()
    }

    // ---------------------------------------------------------------- aiuti

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                val hide = rememberHideKeyboardOnDrag()
                Box(Modifier.fillMaxSize().then(hide.modifier())) {
                    content()
                    ActionBubbleLayer()
                    CaptureFeedbackLayer(onNotes = false)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun note(text: String) {
        id = NotesStore.saveNote(null, text)!!
        screen { NoteEditorScreen(id) {} }
        settle(800)
    }

    private fun settle(ms: Long) {
        Thread.sleep(ms)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        // L'emulatore disegna piano: si aspetta che il fumetto sia salito tutto.
        settle(4000)
        runCatching {
            instr.uiAutomation.takeScreenshot()?.let { b -> File(outDir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        log("foto $name")
    }

    private fun log(line: String) {
        Log.i("BubbleCheck", line)
        runCatching { File(outDir, "resoconto.txt").appendText(line + "\n") }
    }

    /** Ogni foto per conto suo: se un passo non riesce lo scrive e va avanti. */
    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { log("NON RIUSCITA $name: ${it.javaClass.simpleName} ${it.message?.take(200)}") }
    }

    private fun show(block: () -> Unit) {
        instr.runOnMainSync(block)
        compose.waitForIdle()
    }

    private fun tap(description: String) {
        val n = compose.onNodeWithContentDescription(description, useUnmergedTree = true)
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun tapText(text: String, index: Int = 0) {
        val n = compose.onAllNodesWithText(text, useUnmergedTree = true)[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun clickLabel(label: String) = SemanticsMatcher("clic \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun field() = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("noteBody"))).onFirst()

    private fun imeOpen(): Boolean {
        var open = false
        compose.runOnUiThread {
            open = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return open
    }

    private fun focusBody() {
        field().performClick()
        runCatching { compose.waitUntil(8_000) { imeOpen() } }
        compose.waitForIdle()
    }

    private fun type(s: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand("input text " + s.replace(" ", "%s"))).use { it.readBytes() }
        settle(400)
    }

    private val now get() = System.currentTimeMillis()

    // ---------------------------------------------------------------- media e dati (stesse funzioni delle pagine)

    @Test
    fun media() {
        note("Spesa\npane\nlatte")
        step("01-coda") {
            show { showMediaBubble(context, ResumeItem(ResumeKind.SPOTIFY, "spotify:track:0VjIjW4GlUZAMYd2vXMi3b", "Blinding Lights", "The Weeknd", at = now), openRoute = {}) }
            shot("01-coda")
            show { ActionBubble.dismiss() }
        }
        step("02-raccolte") {
            show { showMediaBubble(context, ResumeItem(ResumeKind.SPOTIFY, "spotify:album:4yP0hdKOZPNshxUOjY0cZj", "After Hours", "The Weeknd", at = now), openRoute = {}) }
            shot("02-raccolte")
            show { ActionBubble.dismiss() }
        }
        step("04-brano-riordino") {
            val pl = SpItem("p1", "spotify:playlist:p1", SpKind.PLAYLIST, "Corsa", "", "")
            val tr = SpItem("t1", "spotify:track:t1", SpKind.TRACK, "Levitating", "Dua Lipa", "")
            show { PlaylistManage.trackMenu(context, pl, tr, 2, 6) }
            shot("04-brano-riordino")
            show { ActionBubble.dismiss() }
        }
        step("18-account") {
            // Lo stesso fumetto di SimUi (Account › WindTre).
            show {
                ActionBubble.show(
                    BubbleHead("Account", "WindTre"),
                    Slot.open {},
                    *captureActions(context, "windtre-dati-letti") { "" },
                    Slot.logout(confirm = "Tocca ancora: Canvas dimentica l'accesso a WindTre") {},
                )
            }
            shot("18-account")
            show { ActionBubble.dismiss() }
        }
        step("22-quando") {
            show { CaptureWhenAsk.askDay(context, MainScope(), "Cena con Marta") }
            shot("22-quando")
            show { ActionBubble.dismiss() }
        }
        step("27-mail-nota") {
            NotesStore.saveNote(null, "Viaggio a Roma\nbiglietti")
            NotesStore.saveNote(null, "Casa\nbollette")
            val m = MailMessage("m1", "Trenitalia", "noreply@trenitalia.it", "Il tuo biglietto Milano › Roma", "Frecciarossa 9517", now, emptySet(), "Frecciarossa 9517 carrozza 5 posto 11D")
            // Lo stesso fumetto della riga "Dalla posta" in Home.
            show { ActionBubble.show(BubbleHead("Mail", m.subject), *mailNoteActions(context, m, null).toTypedArray(), Slot.open {}) }
            shot("27-mail-nota")
            show { ActionBubble.dismiss() }
        }
    }

    // ---------------------------------------------------------------- editor delle note (dal vivo)

    @Test
    fun noteAltro() {
        id = NotesStore.saveNote(null, "Spesa\n[x] pane\n[ ] latte\n[ ] uova")!!
        NotesStore.setNoteEmoji(id, "🛒")
        screen { NoteEditorScreen(id) {} }
        settle(1500)
        step("07-nota-altro") {
            tap("Altro")
            shot("07-nota-altro")
        }
        step("17-in-un-posto") {
            tapText("In un posto")
            shot("17-in-un-posto")
            show { ActionBubble.dismiss() }
        }
        step("10-emoji") {
            settle(500)
            compose.onAllNodes(clickLabel("Cambia l'emoji")).onFirst().performClick()
            shot("10-emoji")
            show { ActionBubble.dismiss() }
        }
    }

    @Test
    fun slashMenu() {
        note("Menu\n")
        step("08-slash") {
            focusBody()
            type("/")
            shot("08-slash")
        }
        step("09-slash-ta") {
            type("ta")
            shot("09-slash-ta")
        }
    }

    @Test
    fun lineBubbles() {
        note("Righe\n[ ] una voce\n[ ] seconda voce")
        step("11-riga") {
            compose.onAllNodes(clickLabel("Spunta"))[0].performTouchInput { longClick() }
            shot("11-riga")
            show { ActionBubble.dismiss() }
        }
        step("12-trasforma") {
            focusBody()
            tap("Formati, colori, link, titolo, citazione, riquadro")
            tap("Trasforma in")
            shot("12-trasforma")
            show { ActionBubble.dismiss() }
        }
    }

    @Test
    fun colors() {
        note("Colori\nparola importante")
        step("15-colore") {
            focusBody()
            field().performTextInputSelection(TextRange(0, 6))
            settle(300)
            tap("Colore del testo o dello sfondo")
            shot("15-colore")
        }
    }

    @Test
    fun callout() {
        val base = "Riquadro\nRicorda il pane"
        val e = NoteLines.setKind(base, base.length - 2, base.length - 2, NoteLines.Kind.CALLOUT)
        note(e.text)
        step("13-riquadro") {
            val marks = compose.onAllNodesWithText("💡", substring = true, useUnmergedTree = true)
            marks.onFirst().performTouchInput { longClick() }
            shot("13-riquadro")
        }
    }

    @Test
    fun table() {
        val (t, _) = NoteTable.insert("Tabella\n", "Tabella\n".length)
        note(t)
        step("14-tabella") {
            val cells = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("noteBody")))
            cells[1].performTouchInput { longClick() }
            shot("14-tabella")
        }
    }

    @Test
    fun board() {
        note("Bacheca\n${NoteLines.BOARD}\n[ ] Comprare il pane\n[ ] Chiamare Luca")
        step("25-scheda") {
            tapText("Comprare il pane")
            shot("25-scheda")
        }
    }

    // ---------------------------------------------------------------- pagine delle date e delle abitudini (dal vivo)

    @Test
    fun taskLines() {
        val task = NotesStore.addTaskAt("Dentista", now + 2 * 86_400_000L)
        screen { ItemDetailScreen(ItemKind.TASK, task.id) {} }
        settle(1200)
        step("19-si-ripete") {
            tapText(WhenText.repeat(null))
            shot("19-si-ripete")
            show { ActionBubble.dismiss() }
        }
        step("20-avviso") {
            tapText(WhenText.alert(NotesStore.data.value.tasks.first { it.id == task.id }))
            shot("20-avviso")
            show { ActionBubble.dismiss() }
        }
    }

    @Test
    fun habitLines() {
        val hid = NotesStore.saveHabit(null, "Palestra", 3)!!
        screen { ItemDetailScreen(ItemKind.HABIT, hid) {} }
        settle(1200)
        step("21-quante-volte") {
            tapText("3 volte a settimana")
            shot("21-quante-volte")
        }
    }
}
