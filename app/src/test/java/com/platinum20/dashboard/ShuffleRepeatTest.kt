package com.platinum20.dashboard

import com.platinum20.dashboard.media.QueueEntry
import com.platinum20.dashboard.media.QueueOps
import com.platinum20.dashboard.media.QueueState
import com.platinum20.dashboard.media.Repeat
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** Casuale e Ripeti del player (1.563): si mescola solo quello che viene dopo, e si torna all'ordine di prima. */
class ShuffleRepeatTest {
    private fun state(n: Int, current: Int) = QueueState(
        (1..n).map { QueueEntry(it.toLong(), ResumeItem(ResumeKind.SPOTIFY, "t$it", "t$it", at = 0)) },
        current,
    )
    private fun ids(s: QueueState) = s.entries.map { it.id }

    @Test
    fun casualeMescolaSoloQuelleDopo() {
        val s = state(8, 2)
        val mixed = QueueOps.shuffleUpcoming(s, Random(7))
        assertEquals(listOf(1L, 2L, 3L), ids(mixed).take(3))
        assertEquals((4L..8L).toSet(), ids(mixed).drop(3).toSet())
        assertEquals(2, mixed.current)
    }

    @Test
    fun spentoTornaComEra() {
        val s = state(8, 2)
        val order = s.upcoming.map { it.id }
        val mixed = QueueOps.shuffleUpcoming(s, Random(3))
        // Arriva un brano nuovo mentre è mescolata: resta in fondo.
        val added = QueueOps.add(mixed, ResumeItem(ResumeKind.SPOTIFY, "n", "n", at = 0), 99)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 99L), ids(QueueOps.unshuffle(added, order)))
    }

    @Test
    fun ripetiTuttoRicominciaDallaPrima() {
        val end = state(3, 2)
        assertNull(QueueOps.nextIndex(end, Repeat.OFF))
        assertEquals(0, QueueOps.nextIndex(end, Repeat.ALL))
        assertEquals(2, QueueOps.nextIndex(state(3, 1), Repeat.OFF))
    }

    @Test
    fun ripetiGiraTraITreStati() {
        assertEquals(Repeat.ALL, Repeat.OFF.next())
        assertEquals(Repeat.ONE, Repeat.ALL.next())
        assertEquals(Repeat.OFF, Repeat.ONE.next())
    }
}
