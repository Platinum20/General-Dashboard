package com.platinum20.dashboard

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteText
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.ActionBubble
import com.platinum20.dashboard.ui.screens.ActionBubbleLayer
import com.platinum20.dashboard.ui.screens.CaptureFeedbackLayer
import com.platinum20.dashboard.ui.screens.NoteEditorScreen
import com.platinum20.dashboard.ui.screens.rememberHideKeyboardOnDrag
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * L'editor delle note su un emulatore vero, con la tastiera vera: ogni interazione che Antonio usa (scrivere, Invio,
 * liste, spunte, rientri, "/", Aa, Trasforma in, colori, blocchi, "@", annulla, fumetti, emoji, copertina, vocale) e
 * la tastiera che non deve finire sopra la riga né chiudersi da sola. Per ogni passo una foto dello schermo intero
 * (tastiera compresa) e il testo della nota nel log "NoteCheck". Parte solo nel workflow "notecheck.yml".
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NoteEditorDeviceCheck {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instr get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instr.targetContext
    private val outDir by lazy { File(context.getExternalFilesDir(null), "note-check").apply { mkdirs() } }
    private var id = 0L
    private var name = "x"
    private var step = 0

    @Before
    fun setUp() {
        NotesStore.init(context)
        NotesStore.data.value.notes.forEach { NotesStore.deleteNote(it.id) }
        ActionBubble.dismiss()
        compose.runOnUiThread {
            // Come MainActivity: da bordo a bordo, la pagina si stringe sopra la tastiera.
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @After
    fun tearDown() {
        ActionBubble.dismiss()
    }

    // ---------------------------------------------------------------- aiuti

    private fun open(text: String, test: String) {
        name = test
        step = 0
        id = NotesStore.saveNote(null, text)!!
        compose.setContent {
            GeneralDashboardTheme(AppTheme.CANVAS) {
                CompositionLocalProvider {
                    val hide = rememberHideKeyboardOnDrag()
                    Box(Modifier.fillMaxSize().then(hide.modifier())) {
                        NoteEditorScreen(id) {}
                        ActionBubbleLayer()
                        CaptureFeedbackLayer(onNotes = false)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun body(): String = NoteText.split(NotesStore.note(id)?.text.orEmpty()).second
    private fun kinds() = NoteLines.parse(body()).map { it.kind }

    /** Il campo del testo (il primo campo dentro "noteBody": tabelle e colonne vengono dopo). */
    private fun field(): SemanticsNodeInteraction =
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("noteBody"))).onFirst()

    /** Un comando come da adb: i tasti passano dalla tastiera vera del telefono, come quando scrive Antonio. */
    private fun shell(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instr.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }
    }

    /** Scrive [s] con i tasti (solo ASCII), dove c'è il cursore. */
    private fun type(s: String) {
        // Niente shell in mezzo: il testo arriva così com'è, gli spazi come %s.
        shell("input text " + s.replace(" ", "%s"))
        settle(250)
    }

    private fun key(k: Key) {
        val code = when (k) {
            Key.Enter -> 66
            Key.Backspace -> 67
            else -> error("tasto $k")
        }
        shell("input keyevent $code")
        settle(250)
    }

    private fun tap(description: String) {
        val n = compose.onNodeWithContentDescription(description, useUnmergedTree = true)
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun tapText(text: String, index: Int = 0, substring: Boolean = false) {
        val n = compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun clickLabel(label: String) = SemanticsMatcher("clic \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun imeOpen(): Boolean {
        var open = false
        compose.runOnUiThread {
            open = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return open
    }

    private fun imeTop(): Float {
        var top = 0f
        compose.runOnUiThread {
            val v = compose.activity.window.decorView
            val ime = ViewCompat.getRootWindowInsets(v)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            top = (v.height - ime).toFloat()
        }
        return top
    }

    private fun focusBody() {
        field().performClick()
        compose.waitUntil(8_000) { imeOpen() }
        compose.waitForIdle()
    }

    private fun settle(ms: Long) {
        Thread.sleep(ms)
        compose.waitForIdle()
    }

    /** Foto dello schermo intero (tastiera e fumetti compresi) e il testo della nota nel log. */
    private fun shot(label: String) {
        compose.waitForIdle()
        Thread.sleep(350)
        step++
        val file = File(outDir, "%s_%02d_%s.png".format(name, step, label))
        runCatching {
            instr.uiAutomation.takeScreenshot()?.let { b -> file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        Log.i(TAG, "[$name/$label] ime=${imeOpen()} body=${body().replace("\n", "⏎")}")
    }

    /** Il fondo della riga del cursore sullo schermo (in pixel della finestra). */
    private fun cursorBottom(): Float {
        val node = field().fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val l = results.first()
        val sel = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange)?.end ?: l.layoutInput.text.length
        // Il campo disegna il testo trasformato: dove cade il cursore lo dice la stessa trasformazione dell'editor.
        val shown = com.platinum20.dashboard.ui.screens.editorShown(body(), emptyList())
        val at = shown.toShown(sel.coerceIn(0, body().length)).coerceIn(0, l.layoutInput.text.length)
        return node.positionInWindow.y + l.getCursorRect(at).bottom
    }

    /** Il bordo alto della barra sopra la tastiera (Annulla e gli altri tasti). */
    private fun barTop(): Float = compose.onNodeWithContentDescription("Annulla", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow.top

    // ---------------------------------------------------------------- scrivere

    @Test
    fun titleEnterMovesToBody() {
        open("Spesa", "titolo")
        compose.onNode(hasSetTextAction() and hasTestTag("noteTitle")).performClick()
        compose.waitUntil(8_000) { imeOpen() }
        compose.onNode(hasSetTextAction() and hasTestTag("noteTitle")).performTextInputSelection(TextRange(5))
        type(" sabato")
        key(Key.Enter)
        type("latte")
        shot("invio_nel_titolo")
        val (t, b) = NoteText.split(NotesStore.note(id)!!.text)
        assertEquals("Spesa sabato", t)
        assertEquals("latte", b.trim())
    }

    @Test
    fun bulletsContinueAndEnterOnEmptyExits() {
        open("Lista\n", "punti")
        focusBody()
        tap("Elenco puntato")
        type("uno"); key(Key.Enter); type("due"); key(Key.Enter)
        shot("due_punti_e_vuoto")
        key(Key.Enter)
        type("testo")
        shot("uscito_dalla_lista")
        assertEquals(listOf(NoteLines.Kind.BULLET, NoteLines.Kind.BULLET, NoteLines.Kind.TEXT), kinds().take(3))
        assertTrue(body(), body().endsWith("testo"))
    }

    @Test
    fun markdownShortcutsAtLineStart() {
        open("Scorciatoie\n", "scorciatoie")
        focusBody()
        type("- a"); key(Key.Enter); key(Key.Enter)
        type("[] b"); key(Key.Enter); key(Key.Enter)
        type("1. c"); key(Key.Enter); type("d"); key(Key.Enter); key(Key.Enter)
        type("> e"); key(Key.Enter); key(Key.Enter)
        type("## f"); key(Key.Enter)
        type("---"); key(Key.Enter)
        type("fine")
        shot("scorciatoie")
        val k = kinds()
        Log.i(TAG, "scorciatoie: $k")
        assertTrue(k.toString(), NoteLines.Kind.BULLET in k)
        assertTrue(k.toString(), NoteLines.Kind.CHECK in k)
        assertTrue(k.toString(), k.count { it == NoteLines.Kind.NUMBER } == 2)
        assertTrue(k.toString(), NoteLines.Kind.QUOTE in k)
        assertTrue(k.toString(), NoteLines.Kind.HEADING in k)
        assertTrue(k.toString(), NoteLines.Kind.DIVIDER in k)
        assertTrue(body(), body().contains("2. d"))
    }

    @Test
    fun backspaceOnEmptyItemRemovesMark() {
        open("Lista\n- uno\n- ", "indietro")
        focusBody()
        field().performTextInputSelection(TextRange(body().length))
        key(Key.Backspace)
        shot("indietro_su_voce_vuota")
        assertEquals(NoteLines.Kind.BULLET, kinds()[0])
        assertTrue(body(), body() == "- uno\n" || body() == "- uno")
    }

    // ---------------------------------------------------------------- spunte e rientri

    @Test
    fun tickChecklistAndNestedTicked() {
        open("Valigia\n[ ] maglie\n  [ ] calze\n[ ] scarpe", "spunte")
        shot("prima")
        compose.onAllNodes(clickLabel("Spunta"))[1].performClick()
        compose.waitForIdle()
        shot("sotto_voce_spuntata")
        assertEquals("[ ] maglie\n  [x] calze\n[ ] scarpe", body())
        compose.onAllNodes(clickLabel("Togli la spunta"))[0].performClick()
        compose.waitForIdle()
        assertEquals("[ ] maglie\n  [ ] calze\n[ ] scarpe", body())
        // Rientro di una voce spuntata.
        compose.onAllNodes(clickLabel("Spunta"))[2].performClick()
        focusBody()
        field().performTextInputSelection(TextRange(body().length))
        compose.waitForIdle()
        tap("Sotto-voce")
        shot("spuntata_rientrata")
        val last = NoteLines.parse(body()).last()
        assertEquals(1, last.level)
        assertTrue(body(), last.done)
        tap("Su di un livello")
        assertEquals(0, NoteLines.parse(body()).last().level)
    }

    @Test
    fun tickWithKeyboardClosedDoesNotOpenIt() {
        open("Spesa\n[ ] pane\n[ ] latte", "spunta_senza_tastiera")
        compose.onAllNodes(clickLabel("Spunta"))[0].performClick()
        settle(800)
        shot("spuntata")
        assertTrue(body(), body().startsWith("[x] pane"))
        assertFalse("la tastiera si è aperta toccando la spunta", imeOpen())
    }

    @Test
    fun lastTickSaysAllDone() {
        open("Spesa\n[x] pane\n[ ] latte", "tutto_fatto")
        compose.onAllNodes(clickLabel("Spunta"))[0].performClick()
        settle(500)
        shot("tutto_fatto")
        Log.i(TAG, "tutto fatto: ${compose.onAllNodesWithText("Tutto fatto", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size} nodi")
    }

    // ---------------------------------------------------------------- "/" e Aa

    @Test
    fun slashMenuFiltersAndEnterPicks() {
        open("Menu\n", "slash")
        focusBody()
        type("/")
        shot("menu_aperto")
        compose.onAllNodesWithText("Tabella", useUnmergedTree = true).onFirst().assertExists()
        type("tit")
        shot("filtrato")
        key(Key.Enter)
        type("Capitolo")
        shot("titolo_scelto")
        assertEquals("## Capitolo", body().lines().first())
    }

    @Test
    fun slashBlocks() {
        open("Blocchi\n", "blocchi")
        focusBody()
        for (entry in listOf("Linea", "Due colonne", "Bacheca", "Tabella")) {
            field().performTextInputSelection(TextRange(body().length))
            if (!body().endsWith("\n") && body().isNotEmpty()) key(Key.Enter)
            type("/")
            tapText(entry)
            compose.waitForIdle()
            shot(entry.replace(' ', '_'))
            if (entry == "Tabella" || entry == "Due colonne") {
                type("Cella")
                compose.waitForIdle()
                shot(entry.replace(' ', '_') + "_scritta")
            }
            if (!imeOpen()) focusBody()
        }
        val k = kinds()
        Log.i(TAG, "blocchi: $k")
        assertTrue(k.toString(), NoteLines.Kind.DIVIDER in k)
        assertTrue(k.toString(), NoteLines.Kind.COLUMNS in k)
        assertTrue(k.toString(), NoteLines.Kind.BOARD in k)
        assertTrue(k.toString(), NoteLines.Kind.TABLE in k)
        assertTrue(body(), body().contains("| Cella"))
        assertTrue(body(), body().contains("‖ Cella") || body().contains("Cella ‖"))
    }

    @Test
    fun subNoteFromSlash() {
        open("Casa\n", "sottonota")
        focusBody()
        type("/sotto")
        tapText("Sotto-nota")
        compose.waitForIdle()
        shot("sottonota")
        assertTrue(body(), NoteLines.Kind.PAGE in kinds())
        assertEquals(2, NotesStore.data.value.notes.size)
    }

    @Test
    fun formatBarBoldColorAndBack() {
        open("Aa\nparola importante", "aa")
        focusBody()
        field().performTextInputSelection(TextRange(0, 6))
        compose.waitForIdle()
        shot("scelta")
        tap("Grassetto")
        shot("grassetto")
        assertTrue(body(), body().startsWith("**parola**"))
        val b = body()
        field().performTextInputSelection(TextRange(b.indexOf("importante"), b.indexOf("importante") + 10))
        compose.waitForIdle()
        tap("Colore del testo o dello sfondo")
        shot("colori")
        tapText("Rosso", 0)
        shot("rosso")
        assertTrue(body(), body() != b)
        // Tasto Aa senza testo scelto e X per tornare.
        field().performTextInputSelection(TextRange(body().length))
        compose.waitForIdle()
        tap("Formati, colori, link, titolo, citazione, riquadro")
        shot("aa_aperto")
        tap("Torna a punti e spunte")
        compose.onNodeWithContentDescription("Elenco puntato", useUnmergedTree = true).assertExists()
    }

    @Test
    fun transformFromBarAndFromLongPress() {
        open("Trasforma\n[ ] una voce", "trasforma")
        // Tieni premuto il quadrato: il fumetto con "Trasforma in".
        compose.onAllNodes(clickLabel("Spunta"))[0].performTouchInput { longClick() }
        compose.waitForIdle()
        shot("fumetto_voce")
        compose.onNodeWithText("Trasforma in", useUnmergedTree = true).assertExists()
        tapText("Numeri")
        shot("numeri")
        assertEquals(NoteLines.Kind.NUMBER, kinds()[0])
        // Dalla barra Aa.
        focusBody()
        tap("Formati, colori, link, titolo, citazione, riquadro")
        tap("Trasforma in")
        shot("fumetto_barra")
        tapText("Citazione")
        assertEquals(NoteLines.Kind.QUOTE, kinds()[0])
        tap("Riquadro con emoji")
        shot("riquadro")
        assertEquals(NoteLines.Kind.CALLOUT, kinds()[0])
    }

    @Test
    fun undoRedo() {
        open("Annulla\n", "annulla")
        focusBody()
        type("ciao")
        settle(1200)
        type(" mondo")
        tap("Annulla")
        shot("annullato")
        val after = body()
        assertTrue(after, after != "ciao mondo")
        tap("Ripeti")
        shot("ripetuto")
        assertEquals("ciao mondo", body())
    }

    // ---------------------------------------------------------------- @

    @Test
    fun mentionNoteAndCalendarEvent() {
        val other = NotesStore.saveNote(null, "Spesa settimanale\nlatte")!!
        NotesStore.setNoteEmoji(other, "🛒")
        val event = addCalendarEvent("Dentista Rossi")
        open("Tag\n", "tag")
        focusBody()
        type("vedi @Spe")
        shot("cerca_nota")
        tapText("Spesa settimanale", substring = true)
        shot("tag_nota")
        assertTrue(body(), body().contains("(nota:$other)"))
        type(" e @Dent")
        settle(1500)
        shot("cerca_evento")
        if (event) {
            tapText("Dentista Rossi", substring = true)
            shot("tag_evento")
            assertTrue(body(), body().contains("calendar/event"))
        } else Log.w(TAG, "calendario del telefono non disponibile: evento non provato")
        // Un tasto indietro dopo il tag lo toglie tutto insieme.
        val before = body()
        key(Key.Backspace)
        shot("indietro_sul_tag")
        Log.i(TAG, "indietro sul tag: '$before' -> '${body()}'")
    }

    /** Un calendario locale con un evento domani: per "@" sugli eventi del telefono. */
    private fun addCalendarEvent(title: String): Boolean = runCatching {
        val cr = context.contentResolver
        val calUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "prova")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val cal = cr.insert(calUri, ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "prova")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "prova")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Prova")
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "prova")
        })!!.lastPathSegment!!.toLong()
        val start = System.currentTimeMillis() + 24 * 3600_000L
        cr.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, cal)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + 3600_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Rome")
        }) != null
    }.onFailure { Log.w(TAG, "evento: $it") }.getOrDefault(false)

    // ---------------------------------------------------------------- link, foto, copertina, emoji, vocale

    @Test
    fun linkAloneBecomesPreview() {
        open("Link\n", "anteprima")
        focusBody()
        type("https://example.com")
        key(Key.Enter)
        settle(4000)
        shot("anteprima")
        assertTrue(body(), NoteLines.Kind.BOOKMARK in kinds())
    }

    @Test
    fun inlinePhotoAndCover() {
        val photo = File(context.filesDir, "prova.png")
        Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4C8DF6.toInt()) }
            .let { b -> photo.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        open("Foto\nsopra\n" + NoteLines.imageLine(photo.path) + "\nsotto", "foto")
        NotesStore.setNoteCover(id, photo.path)
        settle(1500)
        shot("foto_e_copertina")
        compose.onNodeWithContentDescription("Foto", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        shot("fumetto_foto")
        tapText("Togli la foto")
        settle(500)
        assertFalse(body(), NoteLines.Kind.IMAGE in kinds())
        compose.onNodeWithContentDescription("Copertina", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        shot("fumetto_copertina")
        tapText("Togli la copertina")
        settle(500)
        assertEquals(null, NotesStore.note(id)?.cover)
    }

    @Test
    fun noteEmojiFromAltro() {
        open("Viaggio\nPorto", "emoji")
        tap("Altro")
        shot("altro")
        tapText("🧳")
        settle(500)
        shot("emoji_nel_titolo")
        assertEquals("🧳", NotesStore.note(id)?.emoji)
        // Tocco sull'emoji: si cambia.
        compose.onNodeWithText("🧳", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        shot("cambia_emoji")
        tapText("Togli")
        settle(500)
        assertEquals(null, NotesStore.note(id)?.emoji)
    }

    @Test
    fun voiceNoteRecordAndPill() {
        open("Vocale\nprima", "vocale")
        tap("Aggiungi")
        shot("aggiungi")
        tapText("Registra un audio")
        settle(2500)
        shot("registro")
        tap("Ferma")
        settle(800)
        shot("vocale_nella_nota")
        assertTrue(body(), body().contains("(audio:"))
        assertTrue(body(), NoteLines.Kind.AUDIO in kinds())
    }

    @Test
    fun lineBubbleMoveAndSendToTodo() {
        open("Bolla\n[ ] prima\n[ ] seconda", "fumetto_riga")
        compose.onAllNodes(clickLabel("Spunta"))[1].performTouchInput { longClick() }
        compose.waitForIdle()
        shot("fumetto")
        tapText("Sposta su")
        assertEquals("[ ] seconda\n[ ] prima", body())
        compose.onAllNodes(clickLabel("Spunta"))[0].performTouchInput { longClick() }
        compose.waitForIdle()
        tapText("Manda in Da fare")
        settle(500)
        shot("mandata_in_da_fare")
        assertEquals("[ ] prima", body().trim())
        assertNotNull(NotesStore.data.value.tasks.firstOrNull { it.text == "seconda" })
    }

    // ---------------------------------------------------------------- tastiera

    @Test
    fun typingKeepsLineAboveKeyboard() {
        open("Lunga\n" + (1..25).joinToString("\n") { "riga $it" }, "tastiera_scrivi")
        focusBody()
        field().performTextInputSelection(TextRange(body().length))
        compose.waitForIdle()
        repeat(12) { key(Key.Enter); type("nuova riga $it") }
        settle(900)
        shot("dopo_12_righe")
        val bottom = cursorBottom()
        val top = barTop()
        Log.i(TAG, "cursore fondo=$bottom barra cima=$top")
        assertTrue("la riga del cursore ($bottom) è sotto la barra ($top)", bottom <= top)
        assertTrue(imeOpen())
    }

    /** Antonio (1.603): toccando una riga che la pagina sposta sopra la tastiera, la tastiera non deve chiudersi. */
    @Test
    fun tapThatScrollsKeepsKeyboard() {
        open("Lunga\n" + (1..40).joinToString("\n") { "riga numero $it" }, "tastiera_tocco")
        focusBody()
        // Un tocco in basso, appena sopra la barra della tastiera: la pagina scorre da sola per portare la riga in vista.
        repeat(3) { n ->
            val nodeTop = field().fetchSemanticsNode().boundsInWindow.top
            val y = imeTop() - 210f - nodeTop
            field().performTouchInput { click(Offset(centerX, y)) }
            settle(1500)
            shot("dopo_il_tocco_$n")
            assertTrue("la tastiera si è chiusa dopo il tocco ${n + 1}", imeOpen())
            val c = cursorBottom()
            val b = barTop()
            Log.i(TAG, "tocco $n: cursore fondo=$c barra cima=$b")
            assertTrue("dopo il tocco ${n + 1} la riga del cursore ($c) resta sotto la barra ($b)", c <= b - 10)
        }
    }

    @Test
    fun dragClosesKeyboard() {
        open("Lunga\n" + (1..40).joinToString("\n") { "riga $it" }, "tastiera_trascina")
        focusBody()
        compose.onNode(hasTestTag("noteBody")).performTouchInput { swipeDown(startY = centerY - 300f, endY = centerY + 200f) }
        settle(1200)
        shot("trascinata")
        assertFalse("trascinando la tastiera resta aperta", imeOpen())
    }

    @Test
    fun tapBelowLastLineContinuesList() {
        open("Sotto\n[ ] uno", "tocco_sotto")
        compose.onNode(hasTestTag("noteBody")).performTouchInput { click(Offset(centerX, bottom + 60f)) }
        settle(1500)
        shot("tocco_sotto")
        Log.i(TAG, "tocco sotto: ${kinds()}")
        assertEquals(2, kinds().size)
        assertEquals(NoteLines.Kind.CHECK, kinds()[1])
    }

    private companion object {
        const val TAG = "NoteCheck"
    }
}
