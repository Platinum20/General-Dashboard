package com.platinum20.dashboard

import androidx.compose.ui.geometry.Rect
import com.platinum20.dashboard.ui.screens.MiniDock
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il video chiuso vola proprio sulla miniatura del mini player, in alto o in basso. */
class MiniDockTest {
    private val frame = Rect(48f, 100f, 1032f, 2000f)

    @Test
    fun thumbSitsInsideTheBarAtTheBottomOrTop() {
        val bottom = MiniDock.thumb(frame, barHeight = 210f, atTop = false, density = 3f)
        assertEquals(Rect(72f, 1814f, 324f, 1958f), bottom)
        val top = MiniDock.thumb(frame, barHeight = 210f, atTop = true, density = 3f)
        assertEquals(Rect(72f, 124f, 324f, 268f), top)
    }

    @Test
    fun unknownBarUsesAnEstimate() {
        val thumb = MiniDock.thumb(frame, barHeight = 0f, atTop = false, density = 3f)
        assertEquals(2000f - 210f + 24f, thumb.top, 0.01f)
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
}
