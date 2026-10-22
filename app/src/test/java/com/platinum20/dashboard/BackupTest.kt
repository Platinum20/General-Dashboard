package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.backup.Backup
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.weather.GeoPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupTest {
    private val today = LocalDate.now()
    private val data = NotesData(
        tasks = listOf(TaskItem(1, "Dentista", createdAt = 1, dueAt = 2), TaskItem(2, "Latte", createdAt = 3)),
        habits = listOf(Habit(3, "Palestra", setOf(today, today.minusDays(1)))),
        expenses = listOf(Expense(4, 1_250, "pizza", ExpenseCategory.EATING_OUT, today)),
        countdowns = listOf(Countdown(5, "Compleanno di Marco", LocalDate.of(1990, 3, 14), yearly = true)),
        budgetCents = 50_000,
    )
    private val settings = mapOf<String, Any?>(
        "theme" to "AMOLED",
        "morning_enabled" to true,
        "morning_hour" to 7,
        "last_seen" to 12L,
        "scale" to 1.5f,
        "home_card_hidden_set" to setOf("MODULES", "NEWS"),
    )
    private val place = GeoPlace("Bologna", "", 44.49, 11.34)

    @Test fun andataERitornoIdentico() {
        val raw = Backup.encode(NotesStore.serialize(data), settings, place, createdAt = 1_000)
        val back = Backup.decode(raw)!!
        assertEquals(data, back.notes)
        assertEquals(settings, back.settings)
        assertEquals(place, back.place)
        assertEquals(1_000L, back.createdAt)
    }

    @Test fun iTipiDelleImpostazioniRestanoQuelli() {
        val back = Backup.decode(Backup.encode(NotesStore.serialize(data), settings, null, 0))!!
        // Un Long piccolo non deve tornare Int: Android rifiuterebbe di leggerlo come Long.
        assertEquals(java.lang.Long::class.java, back.settings["last_seen"]!!::class.javaObjectType)
        assertEquals(java.lang.Float::class.java, back.settings["scale"]!!::class.javaObjectType)
        assertNull(back.place)
    }

    @Test fun ripristinoDiUnBackupConIlFileDelleNoteVecchio() {
        val old = """{"app":"canvas","format":1,"createdAt":5,"notes":{"tasks":[{"id":1,"text":"Chiamare","createdAt":1}]}}"""
        val back = Backup.decode(old)!!
        assertEquals(listOf("Chiamare"), back.notes.tasks.map { it.text })
        assertEquals(emptyMap<String, Any?>(), back.settings)
    }

    @Test fun fileNonValidoORifiutato() {
        assertNull(Backup.decode("non è json"))
        assertNull(Backup.decode("""{"tasks":[]}"""))
        assertNull(Backup.decode("""{"app":"altra","format":1,"notes":{}}"""))
        assertNull(Backup.decode("""{"app":"canvas","format":99,"notes":{}}"""))
        assertNull(Backup.decode("""{"app":"canvas","format":1}"""))
    }

    @Test fun riepilogo() {
        assertEquals("2 promemoria, 1 abitudine, 1 spesa, 1 data", Backup.summary(data))
        assertEquals("Nessun dato", Backup.summary(NotesData()))
    }

    @Test fun nomeDelFile() = assertEquals("canvas-backup-2026-10-22.json", Backup.fileName(LocalDate.of(2026, 10, 22)))
}
