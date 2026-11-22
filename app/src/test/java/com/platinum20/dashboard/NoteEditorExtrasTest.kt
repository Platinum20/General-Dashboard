package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteAudio
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NotePdf
import com.platinum20.dashboard.notes.NoteTable
import com.platinum20.dashboard.notes.NoteText
import com.platinum20.dashboard.notes.NoteVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le ultime cose dell'editor (1.588): tabella, righe che si chiudono, "@" per tutta l'app, audio, versioni, PDF. */
class NoteEditorExtrasTest {
    @Test
    fun tableRowsAreHiddenAndEditable() {
        val t = "Taglie\n| Nome | Scarpe |\n| Marco | 42 |\nfine"
        assertEquals(NoteLines.Kind.TABLE, NoteLines.kindOf("| Nome | Scarpe |").first)
        assertEquals(1..2, NoteTable.blockAt(t, 2))
        assertNull(NoteTable.blockAt(t, 0))
        assertEquals(listOf(listOf("Nome", "Scarpe"), listOf("Marco", "42")), NoteTable.grid(t, 1..2))
        val g = listOf(listOf("Nome", "Scarpe", "Maglia"), listOf("Marco", "42", "M"))
        assertEquals("Taglie\n| Nome | Scarpe | Maglia |\n| Marco | 42 | M |\nfine", NoteTable.replace(t, 1..2, g))
        assertEquals("Taglie\nfine", NoteTable.replace(t, 1..2, emptyList()))
        assertEquals("Taglie\nfine", NoteLines.Shown(t).shown.replace(NoteLines.PLACE, "").replace("\n\n\n", "\n"))
    }

    @Test
    fun newTableGoesBelowTheLine() {
        val (t, line) = NoteTable.insert("ciao", 2)
        assertEquals("ciao\n| Nome | Valore |\n|  |  |", t)
        assertEquals(1, line)
    }

    @Test
    fun foldedHeadingsHideTheirLines() {
        val t = "## Venerdì\nTreno\nHotel\n## Sabato\nMusei"
        val r = NoteLines.foldRanges(t, setOf("Venerdì"))
        assertEquals(1, r.size)
        assertEquals("## Venerdì\n## Sabato\nMusei", NoteLines.Shown(t, r).shown)
        assertEquals(2, NoteLines.sectionSize(t, 0))
        assertEquals("## Venerdì\nTreno\nHotel\n## Sabato", NoteLines.Shown(t, NoteLines.foldRanges(t, setOf("Sabato"))).shown)
    }

    @Test
    fun mentionsLinkToAppPages() {
        val e = NoteFormat.mentionLink("vedi @met", 5, 9, "Meteo", NoteFormat.APP_LINK + "home/weather")
        assertEquals("vedi [Meteo](app:home/weather) ", e.text)
        assertEquals("app:home/weather", NoteFormat.linkAt(e.text, 7)?.first)
    }

    @Test
    fun audioLineIsALink() {
        assertEquals("0:12", NoteAudio.duration(12_400))
        assertEquals("1:05", NoteAudio.duration(65_000))
        val line = NoteAudio.line("/data/x/1.m4a", 3000)
        assertEquals("audio:/data/x/1.m4a", NoteFormat.linkAt(line, 2)?.first)
        assertEquals("🎙 Audio 0:03", NoteFormat.plain(line))
    }

    @Test
    fun versionsKeepOnlyChanges() {
        var list = NoteVersions.add(emptyList(), "a", 1)
        list = NoteVersions.add(list, "a", 2)
        assertEquals(1, list.size)
        list = NoteVersions.add(list, "", 3)
        assertEquals(1, list.size)
        repeat(20) { list = NoteVersions.add(list, "v$it", 10L + it) }
        assertEquals(NoteVersions.MAX, list.size)
        assertEquals("v19", list.first().text)
    }

    @Test
    fun lineInsertsAndVersionSubtitle() {
        assertEquals("uno\nAUDIO\ndue" to 9, NoteText.insertLine("uno\ndue", 1, "AUDIO"))
        assertEquals("uno\nAUDIO" to 9, NoteText.insertLine("uno\n", 4, "AUDIO"))
        assertEquals("\"Pane\"", NoteText.versionLine("Spesa\n- Pane", "Spesa\n- Latte"))
    }

    @Test
    fun pdfRowsReadLikeTheNote() {
        val rows = NotePdf.rows("## Titolo\n- uno\n[x] fatto\n1. primo\n| a | b |")
        assertEquals(listOf("Titolo", "•  uno", "☑  fatto", "1.  primo", "a   ·   b"), rows.map { it.second })
        assertEquals("Spesa casa.pdf", NotePdf.fileName("Spesa/casa"))
    }
}

/** Le versioni si salvano in JSON (org.json vero solo con Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoteVersionsJsonTest {
    @Test
    fun versionsSurviveSaving() {
        val list = listOf(NoteVersions.Version(2, "b"), NoteVersions.Version(1, "a\n\"x\""))
        assertEquals(list, NoteVersions.parse(NoteVersions.write(mapOf(7L to list)))[7L])
    }
}
