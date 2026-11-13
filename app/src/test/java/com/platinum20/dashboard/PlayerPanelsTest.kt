package com.platinum20.dashboard

import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.youtube.player.YtQuality
import com.platinum20.dashboard.media.youtube.player.YtSubtitle
import com.platinum20.dashboard.ui.screens.QueueLook
import com.platinum20.dashboard.ui.screens.QueueSheet
import com.platinum20.dashboard.ui.screens.VideoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPanelsTest {
    @Test fun sidePanelStaysInsideTheScreen() {
        assertEquals(496f, QueueSheet.sideWidth(800f, 0.62f, 340f, 680f), 0.1f)
        assertEquals(680f, QueueSheet.sideWidth(1400f, 0.62f, 340f, 680f), 0.1f)
        // Schermo piccolo: resta un margine per toccare fuori.
        assertEquals(328f, QueueSheet.sideWidth(400f, 0.62f, 340f, 680f), 0.1f)
    }

    @Test fun twoColumnsOnlyWhenWide() {
        assertTrue(QueueSheet.twoColumns(600f))
        assertFalse(QueueSheet.twoColumns(440f))
    }

    @Test fun queueLines() {
        assertTrue(QueueLook.wide(ResumeKind.YOUTUBE))
        assertFalse(QueueLook.wide(ResumeKind.SPOTIFY))
        assertEquals("Canale · 3:07", QueueLook.line("Canale", "YouTube", 187_000))
        assertEquals("Radio", QueueLook.line("", "Radio", 0))
        assertEquals("1:02:45", QueueLook.duration(3_765_000))
        assertNull(QueueLook.count(0))
        assertEquals("4 dopo", QueueLook.count(4))
    }

    @Test fun videoSettingsValues() {
        assertEquals("1,5×", VideoSettings.speedLabel(1.5f))
        assertEquals("2×", VideoSettings.speedLabel(2f))
        assertEquals(listOf(1080, 720, 360), VideoSettings.qualities(listOf(YtQuality(360, "a"), YtQuality(1080, "b"), YtQuality(720, "c"), YtQuality(720, "d"))))
        assertEquals("Automatica · 720p ora", VideoSettings.qualityValue(true, 720, 1080))
        assertEquals("1080p", VideoSettings.qualityValue(false, 720, 1080))
    }

    @Test fun videoSettingsRows() {
        // Le più comuni prima (si vedono senza scorrere), poi le altre dalla più alta.
        assertEquals(listOf(1080, 720, 480, 360, 2160, 1440, 240, 144), VideoSettings.qualityRow(listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)))
        assertEquals(listOf(720, 360, 240), VideoSettings.qualityRow(listOf(720, 360, 240)))
        assertEquals("Normale", VideoSettings.speedChip(1f))
        assertEquals("0,75×", VideoSettings.speedChip(0.75f))
        val en = YtSubtitle("en", "Inglese", "u1", auto = false, original = true)
        val it = YtSubtitle("it", "Italiano", "u2", auto = false)
        val es = YtSubtitle("es", "Spagnolo", "u3", auto = false)
        val enAuto = YtSubtitle("en", "Inglese (automatici)", "u4", auto = true)
        assertEquals(listOf(en, it, es, enAuto), VideoSettings.subtitleRow(listOf(enAuto, es, it, en)))
    }
}
