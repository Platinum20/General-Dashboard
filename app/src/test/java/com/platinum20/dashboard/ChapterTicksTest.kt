package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.YtChapter
import com.platinum20.dashboard.ui.screens.ChapterTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTicksTest {
    private val chapters = listOf(YtChapter("Intro", 0), YtChapter("Parte uno", 60), YtChapter("Fine", 150))

    @Test
    fun ticksSkipTheFirstChapterAndStayInsideTheBar() {
        assertEquals(listOf(0.3f, 0.75f), ChapterTicks.fractions(chapters, 200_000))
        // Un capitolo oltre la fine (durata sbagliata) non disegna tacche fuori dalla barra.
        assertEquals(listOf(0.6f), ChapterTicks.fractions(chapters, 100_000))
    }

    @Test
    fun noTicksWithoutChaptersOrDuration() {
        assertEquals(emptyList<Float>(), ChapterTicks.fractions(chapters.take(1), 200_000))
        assertEquals(emptyList<Float>(), ChapterTicks.fractions(chapters, 0))
    }

    @Test
    fun labelNamesTheChapterUnderTheFinger() {
        assertEquals("1:30 · Parte uno", ChapterTicks.label("1:30", chapters, 90_000))
        assertEquals("0:10 · Intro", ChapterTicks.label("0:10", chapters, 10_000))
        assertEquals("3:00 · Fine", ChapterTicks.label("3:00", chapters, 180_000))
        assertEquals("1:30", ChapterTicks.label("1:30", emptyList(), 90_000))
        assertNull(ChapterTicks.at(chapters.take(1), 10_000))
    }
}
