package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.trips.PackFile
import com.platinum20.dashboard.trips.PackKind
import com.platinum20.dashboard.trips.TrainPack
import com.platinum20.dashboard.trips.TrainPackStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Lo zaino del treno: quanto portare, cosa no, quando buttarlo, la riga sotto il treno, l'indice dei file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrainPackTest {
    private val trip = 4 * 3600L

    @Test
    fun siPortaUnQuartoInPiuDelViaggio() {
        assertEquals(5 * 3600L, TrainPack.target(trip))
        assertTrue(TrainPack.keep(PackKind.PODCAST, trip, packedSec = 4 * 3600, packedBytes = 0, itemSec = 3600, itemBytes = 1))
        assertFalse(TrainPack.keep(PackKind.PODCAST, trip, packedSec = 5 * 3600, packedBytes = 0, itemSec = 60, itemBytes = 1))
    }

    @Test
    fun maiOltreDueGiga() {
        assertFalse(TrainPack.keep(PackKind.PODCAST, trip, 0, TrainPack.MAX_BYTES - 10, 60, 11))
        assertTrue(TrainPack.keep(PackKind.PODCAST, trip, 0, TrainPack.MAX_BYTES - 10, 60, 10))
    }

    @Test
    fun unVideoPiuLungoDelViaggioNo() {
        assertFalse(TrainPack.keep(PackKind.VIDEO, 3600, 0, 0, 3700, 1))
        assertTrue(TrainPack.keep(PackKind.PODCAST, 3600, 0, 0, 3700, 1))
    }

    @Test
    fun pesoStimato() {
        assertEquals(57_600_000L, TrainPack.estimateBytes(PackKind.PODCAST, 3600))
        assertEquals(288_000_000L, TrainPack.estimateBytes(PackKind.VIDEO, 3600))
    }

    @Test
    fun siButtaSeiOreDopoIlRitorno() {
        val end = 1_000_000_000L
        assertFalse(TrainPack.expired(end, end + TrainPack.KEEP_AFTER_MS))
        assertTrue(TrainPack.expired(end, end + TrainPack.KEEP_AFTER_MS + 1))
    }

    @Test
    fun rigaSottoIlTreno() {
        assertEquals("Pronti 3 h 20 per il viaggio", TrainPack.readyLine(3 * 3600 + 20 * 60))
        assertEquals("Pronti 2 h per il viaggio", TrainPack.readyLine(2 * 3600))
        assertEquals("Pronti 45 min per il viaggio", TrainPack.readyLine(45 * 60 - 30))
        assertNull(TrainPack.readyLine(0))
    }

    @Test
    fun indiceDeiFile() {
        val item = ResumeItem(ResumeKind.PODCAST, "ep1", "Episodio", "Podcast", at = 5, link = "https://x/ep1.mp3", durationMs = 1_800_000)
        val list = listOf(PackFile(item, PackKind.PODCAST, "pod-1.audio", 1800, eventId = 42, until = 99))
        val back = TrainPackStore.read(TrainPackStore.write(list))
        assertEquals(1, back.size)
        assertEquals("ep1", back[0].item.key)
        assertEquals(PackKind.PODCAST, back[0].kind)
        assertEquals(42L, back[0].eventId)
        assertEquals(99L, back[0].until)
        assertEquals(1800L, back[0].sec)
        assertTrue(TrainPackStore.read("""[{"kind":"BOH"}]""").isEmpty())
    }
}
