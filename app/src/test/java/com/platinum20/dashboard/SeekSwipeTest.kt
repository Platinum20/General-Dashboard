package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.YtStoryboard
import com.platinum20.dashboard.ui.screens.SeekSwipe
import com.platinum20.dashboard.ui.screens.VideoSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scorrimento di lato sul video, anteprima dei fotogrammi e niente miniatura dopo un salto. */
class SeekSwipeTest {
    @Test
    fun tuttaLaLarghezzaETreMinuti() {
        assertEquals(190_000L, SeekSwipe.target(10_000, 1000f, 1000f, 600_000))
        assertEquals(100_000L, SeekSwipe.target(10_000, 500f, 1000f, 600_000))
        assertEquals(0L, SeekSwipe.target(10_000, -1000f, 1000f, 600_000))
        assertEquals(30_000L, SeekSwipe.target(10_000, 1000f, 1000f, 30_000))
    }

    @Test
    fun parteSoloDiLatoEDopoLaSoglia() {
        assertFalse(SeekSwipe.armed(5f, 0f, 8f))
        assertTrue(SeekSwipe.armed(20f, 4f, 8f))
        assertFalse(SeekSwipe.armed(20f, 18f, 8f))
        assertFalse(SeekSwipe.canStart(10f, 1000f, 50f, 50f))
        assertFalse(SeekSwipe.canStart(980f, 1000f, 50f, 50f))
        assertTrue(SeekSwipe.canStart(500f, 1000f, 50f, 50f))
    }

    @Test
    fun vibraOgniCinqueSecondiEAiBordi() {
        assertTrue(SeekSwipe.ticks(4_900, 5_100, 60_000))
        assertFalse(SeekSwipe.ticks(5_100, 6_000, 60_000))
        assertTrue(SeekSwipe.ticks(1_000, 0, 60_000))
        assertFalse(SeekSwipe.ticks(0, 0, 60_000))
    }

    @Test
    fun scriveQuantoSiSposta() {
        assertEquals("+0:15", SeekSwipe.deltaLabel(15_400))
        assertEquals("−1:05", SeekSwipe.deltaLabel(-65_000))
    }

    @Test
    fun storyboardTrovaFoglioColonnaERiga() {
        val board = YtStoryboard(listOf("a", "b"), 160, 90, 5, 5, 40, 2_000)
        assertEquals(YtStoryboard.Spot("a", 0, 0), board.frameAt(0))
        assertEquals(YtStoryboard.Spot("a", 3, 1), board.frameAt(16_500))
        assertEquals(YtStoryboard.Spot("b", 0, 0), board.frameAt(50_000))
        // Oltre la fine resta l'ultimo fotogramma.
        assertEquals(YtStoryboard.Spot("b", 4, 2), board.frameAt(999_000))
        assertNull(board.copy(urls = emptyList()).frameAt(0))
    }

    @Test
    fun miniaturaSoloPrimaDelPrimoFotogramma() {
        assertTrue(VideoSwipe.showsPoster("v1", null))
        assertFalse(VideoSwipe.showsPoster("v1", "v1"))
        assertTrue(VideoSwipe.showsPoster("v2", "v1"))
    }
}
