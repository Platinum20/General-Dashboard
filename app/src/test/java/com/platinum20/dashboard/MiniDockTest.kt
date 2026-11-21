package com.platinum20.dashboard

import androidx.compose.ui.geometry.Rect
import com.platinum20.dashboard.ui.screens.MiniDock
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il video chiuso vola proprio sul riquadro del mini player, nel suo angolo. */
class MiniDockTest {
    private val frame = Rect(48f, 100f, 1032f, 2000f)

    /** 1.546: il video riempie il riquadro 200 × 113, nell'angolo dove sta; in alto sotto il titolo. */
    @Test
    fun thumbIsTheWholeBoxInItsCorner() {
        assertEquals(Rect(432f, 1661f, 1032f, 2000f), MiniDock.thumb(frame, atTop = false, atRight = true, density = 3f))
        assertEquals(Rect(48f, 388f, 648f, 727f), MiniDock.thumb(frame, atTop = true, atRight = false, density = 3f))
    }

    @Test
    fun shortScreenKeepsTheTopBoxInside() {
        val short = Rect(0f, 0f, 1000f, 500f)
        val top = MiniDock.thumb(short, atTop = true, atRight = true, density = 3f)
        assertEquals(500f - 339f, top.top, 0.01f)
    }

    @Test
    fun dragFollowsTheFingerAndShrinksTowardTheThumb() {
        val start = Rect(0f, 80f, 1080f, 687.5f)
        val target = Rect(72f, 1814f, 324f, 1958f)
        assertEquals(start, MiniDock.dragged(start, target, 0f, 2000f))
        val half = MiniDock.dragged(start, target, 500f, 2000f)
        assertEquals(580f, half.top, 0.01f)
        assertEquals((1080f + 252f) / 2, half.width, 0.01f)
        val far = MiniDock.dragged(start, target, 1500f, 2000f)
        assertEquals(target.width, far.width, 0.01f)
        assertEquals(target.left, far.left, 0.01f)
    }

    @Test
    fun flightEndsExactlyOnTheThumb() {
        val from = Rect(200f, 900f, 700f, 1180f)
        val to = Rect(72f, 1814f, 324f, 1958f)
        assertEquals(from, MiniDock.fly(from, to, 0f))
        assertEquals(to, MiniDock.fly(from, to, 1f))
        val layer = MiniDock.layer(Rect(0f, 80f, 1080f, 687.5f), to)
        assertEquals(252f / 1080f, layer.scaleX, 0.0001f)
        assertEquals(72f, layer.dx, 0.01f)
        assertEquals(1814f - 80f, layer.dy, 0.01f)
    }

    /** Beta 1.271: in Aggiornamenti il mini player in basso copriva "Controlla e aggiorna". */
    @Test
    fun fixedBottomBarLeavesRoomForTheMiniPlayer() {
        assertEquals(119f, MiniDock.bottomReserveDp(playing = true, atTop = false), 0f)
        assertEquals(0f, MiniDock.bottomReserveDp(playing = true, atTop = true), 0f)
        assertEquals(0f, MiniDock.bottomReserveDp(playing = false, atTop = false), 0f)
    }

    @Test
    fun editorLeavesRoomOnlyForABottomMiniPlayer() {
        assertEquals(76f, MiniDock.bottomRoom(playing = true, atTop = false, barHeight = 210f, density = 3f), 0.01f)
        assertEquals(119f, MiniDock.bottomRoom(playing = true, atTop = false, barHeight = 0f, density = 3f), 0.01f)
        assertEquals(0f, MiniDock.bottomRoom(playing = true, atTop = true, barHeight = 210f, density = 3f), 0.01f)
        assertEquals(0f, MiniDock.bottomRoom(playing = false, atTop = false, barHeight = 210f, density = 3f), 0.01f)
    }

    @Test
    fun miniPlayerSitsAboveTheBottomButtons() {
        assertEquals(0f, MiniDock.floor(emptyList()), 0f)
        assertEquals(300f, MiniDock.floor(listOf(120f, 300f)), 0f)
        // Pagina con i tasti: si alza di quanto stanno sopra la barra dello Scaffold (qui assente).
        assertEquals(300f, MiniDock.lift(floor = 300f, scaffoldBottom = 0f), 0f)
        // Con la barra delle sezioni più alta dei tasti non si alza di più.
        assertEquals(0f, MiniDock.lift(floor = 100f, scaffoldBottom = 240f), 0f)
    }
}
