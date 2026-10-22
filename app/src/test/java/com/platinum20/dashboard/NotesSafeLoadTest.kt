package com.platinum20.dashboard

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.backup.Backup
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

/** Il file delle Note rovinato non deve mai essere cancellato dalla prima modifica (V17), e le fisse non aspettano il riavvio (V14). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotesSafeLoadTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.of(2026, 10, 22)

    private fun notesFile(text: String?): File =
        File(tmp.root, "notes.json").apply { if (text != null) writeText(text) }

    @Test fun fileRovinatoVieneCopiatoPrimaDiPartireVuoti() {
        val broken = "{\"tasks\": ["
        val file = notesFile(broken)
        val (data, copy) = NotesStore.load(file, today)
        assertEquals(NotesData(), data)
        assertNotNull(copy)
        assertEquals("notes.json.bad-2026-10-22", copy!!.name)
        assertEquals(broken, copy.readText())
        // L'originale resta dov'è finché non si salva qualcosa: la copia è al sicuro comunque.
        assertEquals(broken, file.readText())
    }

    @Test fun secondaCopiaLoStessoGiornoNonSovrascriveLaPrima() {
        val file = notesFile("non è json")
        val first = NotesStore.load(file, today).second!!
        file.writeText("{rotto")
        val second = NotesStore.load(file, today).second!!
        assertEquals("non è json", first.readText())
        assertEquals("{rotto", second.readText())
        assertEquals("notes.json.bad-2026-10-22-2", second.name)
    }

    @Test fun fileMancanteOVuotoNonFaCopie() {
        assertNull(NotesStore.load(notesFile(null), today).second)
        assertNull(NotesStore.load(notesFile("  "), today).second)
        assertEquals(1, tmp.root.listFiles()!!.size)
    }

    @Test fun fileBuonoSiLeggeSenzaCopie() {
        val data = NotesData(notes = listOf(Note(1, "Libri da leggere", updatedAt = 5)))
        val (loaded, copy) = NotesStore.load(notesFile(NotesStore.serialize(data)), today)
        assertEquals(data, loaded)
        assertNull(copy)
    }

    @Test fun ripristinoAggiungeLeFisseScaduteDopoIlBackup() {
        val now = LocalDate.now()
        val lastMonth = YearMonth.from(now).minusMonths(1)
        // Backup fatto il mese scorso: l'affitto del 1° di questo mese non c'era ancora.
        val f = FixedExpense(7, 650_00, "Affitto", ExpenseCategory.HOME, 1, lastMonth, lastMonth = lastMonth)
        NotesStore.replaceAll(NotesData(fixedExpenses = listOf(f)))
        val after = NotesStore.data.value
        assertEquals(1, after.expenses.size)
        assertEquals(now.withDayOfMonth(1), after.expenses.single().date)
        assertEquals(YearMonth.from(now), after.fixedExpenses.single().lastMonth)
        // Richiamata lo stesso giorno non la raddoppia.
        NotesStore.applyFixedExpenses(now)
        assertEquals(1, NotesStore.data.value.expenses.size)
        NotesStore.replaceAll(NotesData())
    }

    @Test fun esportazioneRipiegaSuWSeIlFornitoreRifiutaWt() {
        val modes = mutableListOf<String>()
        val out = ByteArrayOutputStream()
        val stream = Backup.write(Uri.parse("content://drive/backup.json")) { mode ->
            modes += mode
            if (mode == "wt") throw IOException("mode not supported") else out
        }
        stream.use { it.write("ok".toByteArray()) }
        assertEquals(listOf("wt", "w"), modes)
        assertEquals("ok", out.toString())
    }

    @Test fun esportazioneUsaWtQuandoFunziona() {
        val modes = mutableListOf<String>()
        Backup.write(Uri.parse("content://x/y")) { modes += it; ByteArrayOutputStream() }
        assertEquals(listOf("wt"), modes)
    }
}
