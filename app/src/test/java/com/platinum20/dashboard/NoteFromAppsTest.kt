package com.platinum20.dashboard

import com.platinum20.dashboard.mail.MailFacts
import com.platinum20.dashboard.mail.MailMessage
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteInbox
import com.platinum20.dashboard.notes.NoteSeek
import com.platinum20.dashboard.notes.NoteSpoken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Note che arrivano da player, News, posta e altre app (1.606, gruppo D). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoteFromAppsTest {

    private val video = ResumeItem(ResumeKind.YOUTUBE, "abc123", "Come cambiare la catena", "Officina", "https://img/x.jpg", positionMs = 5000, at = 99)

    @Test
    fun appendedGoesAtTheEndOnNewLines() {
        assertEquals("Titolo\nuno\ndue", NoteInbox.appended("Titolo\nuno\n\n", listOf("due")))
        assertEquals("a\nb", NoteInbox.appended("", listOf("a", " ", "b")))
        assertEquals("x", NoteInbox.appended("x", listOf(" ")))
    }

    @Test
    fun byTitleSkipsArchived() {
        val notes = listOf(
            Note(1, "Dalle news\nvecchia", archivedAt = 5),
            Note(2, "dalle news\nnuova"),
        )
        assertEquals(2L, NoteInbox.byTitle(notes, "Dalle news")?.id)
        assertNull(NoteInbox.byTitle(notes, "Altro"))
    }

    @Test
    fun recentIsNewestFirstWithoutSaved() {
        val notes = listOf(Note(1, "A", updatedAt = 1), Note(2, "B", updatedAt = 3), Note(3, "C", updatedAt = 2), Note(4, "", updatedAt = 9))
        assertEquals(listOf(2L, 3L), NoteInbox.recent(notes, 2).map { it.id })
        assertEquals(listOf(3L, 1L), NoteInbox.recent(notes, 3) { it.id == 2L }.map { it.id })
    }

    @Test
    fun quoteHasPhraseAndArticleLink() {
        val lines = NoteInbox.quoteLines("  Musei gratis\nla prima domenica ", "Musei (gratis)", "news/reader?url=x&source=Il%20Post")
        assertEquals("> Musei gratis la prima domenica", lines[0])
        assertEquals("📰 [Musei (gratis)](app:news/reader?url=x&source=Il%20Post)", lines[1])
        assertTrue(NoteInbox.quoteLines("  ", "T", "r").isEmpty())
    }

    @Test
    fun seekClock() {
        assertEquals("0:00", NoteSeek.clock(0))
        assertEquals("6:52", NoteSeek.clock(412_000))
        assertEquals("1:02:05", NoteSeek.clock(3_725_000))
    }

    @Test
    fun seekLinkRoundTrips() {
        val line = NoteSeek.line(video, 412_000)
        assertTrue(line.startsWith("[▶ 6:52](seek:412000/"))
        val url = line.substringAfter("](").substringBeforeLast(")")
        assertFalse(url.any { it.isWhitespace() || it == '(' || it == ')' })
        val (item, ms) = NoteSeek.parse(url)!!
        assertEquals(412_000L, ms)
        assertEquals(ResumeKind.YOUTUBE, item.kind)
        assertEquals("abc123", item.key)
        assertEquals("Come cambiare la catena", item.title)
        assertEquals(0L, item.positionMs)
        assertNull(NoteSeek.parse("seek:abc"))
        assertNull(NoteSeek.parse("app:home"))
    }

    @Test
    fun seekOnlyWhereThereAreMinutes() {
        assertTrue(NoteSeek.canAnnotate(video))
        assertFalse(NoteSeek.canAnnotate(video.copy(kind = ResumeKind.RADIO)))
        assertFalse(NoteSeek.canAnnotate(null))
        assertEquals("Appunti · Come cambiare la catena", NoteSeek.noteTitle(video))
    }

    @Test
    fun spokenLinesDropMarksAndBlocks() {
        val body = "[ ] Montare 4 tuorli\n- **zucchero** 100 g\n---\n[ricetta](https://x.it/a) qui\n| a | b |\n|---|---|\n\n## Fine"
        assertEquals(listOf("Montare 4 tuorli", "zucchero 100 g", "ricetta qui", "a, b", "Fine"), NoteSpoken.lines(body))
        assertEquals("Tiramisù", NoteSpoken.script("Tiramisù", body).first())
    }

    @Test
    fun songsNeedAtLeastTwo() {
        val body = "Bruno Mars – Die With A Smile\n[ ] Elisa - Dancing\nCalcutta — Paracetamolo\nComprare il pane"
        assertEquals(listOf("Bruno Mars Die With A Smile", "Elisa Dancing", "Calcutta Paracetamolo"), NoteSpoken.songs(body))
        assertTrue(NoteSpoken.songs("Elisa - Dancing\nLatte").isEmpty())
        assertTrue(NoteSpoken.songs("Ore 10: dentista - studio\nOre 11: posta - centro").isEmpty())
    }

    @Test
    fun mailKeepsOnlyUsefulLines() {
        val body = """
            Gentile Antonio, grazie per aver scelto il nostro B&B.
            Codice prenotazione: 48TX92
            Check-in: 5 dic dalle 15:00
            Ci trovi in Via della Lungaretta 12, Roma
            A presto!
        """.trimIndent()
        val facts = MailFacts.facts("Conferma prenotazione", body)
        assertTrue(facts.toString(), "Codice: 48TX92" in facts)
        assertTrue(facts.toString(), facts.any { it.startsWith("Check-in") && "15:00" in it })
        assertTrue(facts.toString(), facts.any { it.startsWith("Indirizzo: Via della Lungaretta 12") })
        assertFalse(facts.any { "presto" in it })
    }

    @Test
    fun mailWithoutFactsKeepsTheStart() {
        val m = MailMessage("1", "Sara", "s@x.it", "Ciao", "Ci vediamo dopo  al bar", 0, emptySet(), "")
        val lines = MailFacts.noteLines(m)
        assertEquals(listOf("Ci vediamo dopo al bar", "✉ Da Sara"), lines)
        assertEquals("Ciao", MailFacts.noteTitle(m))
        assertNotNull(MailFacts.noteTitle(m.copy(subject = " ")))
    }
}
