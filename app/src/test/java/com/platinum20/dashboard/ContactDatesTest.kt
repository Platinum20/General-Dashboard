package com.platinum20.dashboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.ContactCandidate
import com.platinum20.dashboard.notes.ContactDate
import com.platinum20.dashboard.notes.ContactDates
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.ui.screens.contactDateLine
import com.platinum20.dashboard.ui.screens.showContactImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.LocalDate
import java.time.MonthDay

/** Compleanni dai contatti: formati delle date della rubrica, doppioni e lettura dal provider. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ContactDatesTest {
    private val today = LocalDate.of(2026, 10, 23)

    @Test fun dataConAnno() = assertEquals(MonthDay.of(3, 12) to 1990, ContactDates.parseDate("1990-03-12"))

    @Test fun dataSenzaAnno() = assertEquals(MonthDay.of(3, 12) to null, ContactDates.parseDate("--03-12"))

    @Test fun altriFormati() {
        assertEquals(MonthDay.of(3, 12) to 1990, ContactDates.parseDate("19900312"))
        assertEquals(MonthDay.of(3, 12) to 1990, ContactDates.parseDate("12.03.1990"))
        assertEquals(MonthDay.of(3, 2) to 1985, ContactDates.parseDate("2/3/1985"))
        assertEquals(MonthDay.of(7, 1) to 1979, ContactDates.parseDate("1979-07-01T00:00:00Z"))
    }

    @Test fun annoFintoDiventaSconosciuto() {
        // iOS scrive 1604 quando l'anno non c'è.
        assertEquals(MonthDay.of(5, 9) to null, ContactDates.parseDate("1604-05-09"))
    }

    @Test fun formatiNonValidiScartati() {
        assertNull(ContactDates.parseDate(""))
        assertNull(ContactDates.parseDate("marzo"))
        assertNull(ContactDates.parseDate("1990-13-40"))
        assertNull(ContactDates.parseDate("--02-30"))
    }

    @Test fun titoloEDataDaSalvare() {
        val marta = ContactDate("Marta Rossi", anniversary = false, monthDay = MonthDay.of(3, 12), year = 1990)
        assertEquals("Compleanno di Marta Rossi", marta.title)
        assertEquals(LocalDate.of(1990, 3, 12), marta.date(today))
        // Con l'anno vero Date sa dire quanti anni compie.
        assertEquals(37, Countdown(1, marta.title, marta.date(today), yearly = true).yearsAtNext(today))

        val nozze = ContactDate("Luca", anniversary = true, monthDay = MonthDay.of(6, 1), year = null)
        assertEquals("Anniversario di Luca", nozze.title)
        assertEquals(LocalDate.of(2026, 6, 1), nozze.date(today))
        assertNull(Countdown(2, nozze.title, nozze.date(today), yearly = true).yearsAtNext(today))
    }

    @Test fun ventinoveFebbraioSenzaAnno() {
        val d = ContactDate("Pia", anniversary = false, monthDay = MonthDay.of(2, 29), year = null)
        assertEquals(LocalDate.of(2026, 2, 28), d.date(today))
    }

    @Test fun doppioniPerNomeEGiorno() {
        val existing = listOf(
            Countdown(1, "Compleanno Marta", LocalDate.of(2026, 3, 12), yearly = true),
            Countdown(2, "compleanno di LUCA bianchi", LocalDate.of(2026, 9, 9), yearly = true),
        )
        val marta = ContactDate("Marta Rossi", false, MonthDay.of(3, 12), 1990)
        val luca = ContactDate("Luca Bianchi", false, MonthDay.of(1, 2), null)
        val altraMarta = ContactDate("Marta Verdi", false, MonthDay.of(8, 8), null)
        assertTrue(ContactDates.isDuplicate(marta, existing)) // stesso giorno, nome nel titolo
        assertTrue(ContactDates.isDuplicate(luca, existing)) // stesso titolo, maiuscole diverse
        assertFalse(ContactDates.isDuplicate(altraMarta, existing)) // stesso nome, altro giorno
    }

    @Test fun candidatiSenzaDoppioniEInOrdineDiGiorno() {
        val found = listOf(
            ContactDate("Zoe", false, MonthDay.of(12, 1), null),
            ContactDate("Anna", false, MonthDay.of(4, 4), 2000),
            ContactDate("Anna", false, MonthDay.of(4, 4), 2000), // stessa persona su due account
            ContactDate("Bruno", true, MonthDay.of(1, 15), 2010),
        )
        val existing = listOf(Countdown(1, "Compleanno di Zoe", LocalDate.of(2026, 12, 1), yearly = true))
        val list = ContactDates.candidates(found, existing)
        assertEquals(listOf("Anniversario di Bruno", "Compleanno di Anna", "Compleanno di Zoe"), list.map { it.date.title })
        assertEquals(listOf(false, false, true), list.map { it.already })
    }

    @Test fun leggeLaRubrica() {
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor =
                MatrixCursor(arrayOf(ContactsContract.Data.DISPLAY_NAME, Event.START_DATE, Event.TYPE)).apply {
                    addRow(arrayOf("Marta Rossi", "1990-03-12", Event.TYPE_BIRTHDAY))
                    addRow(arrayOf("Luca e Sara", "--06-01", Event.TYPE_ANNIVERSARY))
                    addRow(arrayOf("Senza data", "boh", Event.TYPE_BIRTHDAY))
                    addRow(arrayOf("", "2000-01-01", Event.TYPE_BIRTHDAY))
                }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
            override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
        }
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        val read = ContactDates.read(ApplicationProvider.getApplicationContext())
        assertEquals(
            listOf(
                ContactDate("Marta Rossi", false, MonthDay.of(3, 12), 1990),
                ContactDate("Luca e Sara", true, MonthDay.of(6, 1), null),
            ),
            read,
        )
    }

    @Test fun rigaSottoIlNome() {
        val marta = ContactDate("Marta", false, MonthDay.of(3, 12), 1990)
        assertEquals("12 marzo · classe 1990", contactDateLine(ContactCandidate(marta, already = false)))
        assertEquals("12 marzo · già in Date", contactDateLine(ContactCandidate(marta, already = true)))
        val nozze = ContactDate("Luca e Sara", true, MonthDay.of(6, 1), 2015)
        assertEquals("1 giugno · dal 2015", contactDateLine(ContactCandidate(nozze, already = false)))
        assertEquals("1 giugno", contactDateLine(ContactCandidate(nozze.copy(year = null), already = false)))
    }

    @Test fun invitoSoloConPocheDateDiOgniAnno() {
        fun yearly(n: Int) = List(n) { Countdown(it.toLong(), "C$it", LocalDate.of(1990, 1, 1 + it), yearly = true) }
        assertTrue(showContactImport(emptyList()))
        assertTrue(showContactImport(yearly(2) + Countdown(9, "Viaggio", today.plusDays(3))))
        assertFalse(showContactImport(yearly(3)))
    }
}
