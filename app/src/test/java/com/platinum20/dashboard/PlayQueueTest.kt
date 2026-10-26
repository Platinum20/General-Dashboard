package com.platinum20.dashboard

import com.platinum20.dashboard.media.QueueOps
import com.platinum20.dashboard.media.QueueState
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La coda unica: mischia le fonti, si sposta, si toglie, segue quello che parte. */
class PlayQueueTest {
    private fun song(key: String, kind: ResumeKind = ResumeKind.SPOTIFY) = ResumeItem(kind, key, key, at = 0)
    private fun titles(s: QueueState) = s.entries.map { it.item.key }

    @Test
    fun quelloCheParteDiventaLaCorrente() {
        var s = QueueOps.started(QueueState(), song("a"), 1)
        s = QueueOps.add(s, song("b"), 2)
        s = QueueOps.started(s, song("v", ResumeKind.YOUTUBE), 3)
        assertEquals(listOf("a", "v", "b"), titles(s))
        assertEquals("v", s.now?.item?.key)
        // Lo stesso video ascoltato come musica non si duplica.
        s = QueueOps.started(s, song("v", ResumeKind.MUSIC), 4)
        assertEquals(3, s.entries.size)
    }

    @Test
    fun laProssimaPartitaDaSolaFaAvanzare() {
        var s = QueueOps.started(QueueState(), song("a"), 1)
        s = QueueOps.add(s, song("b"), 2)
        s = QueueOps.started(s, song("b"), 3)
        assertEquals(listOf("a", "b"), titles(s))
        assertEquals(1, s.current)
    }

    @Test
    fun spostaSuEGiuETogli() {
        var s = QueueOps.started(QueueState(), song("now"), 1)
        listOf("a", "b", "c").forEachIndexed { i, k -> s = QueueOps.add(s, song(k), 10L + i) }
        s = QueueOps.move(s, 12, -1)
        assertEquals(listOf("now", "a", "c", "b"), titles(s))
        s = QueueOps.move(s, 10, 1)
        assertEquals(listOf("now", "c", "a", "b"), titles(s))
        assertEquals(0, s.current)
        s = QueueOps.remove(s, 12)
        assertEquals(listOf("now", "a", "b"), titles(s))
        assertTrue(s.hasNext)
    }

    @Test
    fun unaListaSostituisceQuelloCheVenivaDopo() {
        var s = QueueOps.started(QueueState(), song("now"), 1)
        s = QueueOps.add(s, song("x"), 2)
        s = QueueOps.playList(s, listOf(song("1"), song("2"), song("3")), start = 1, firstId = 10)
        assertEquals(listOf("now", "1", "2", "3"), titles(s))
        assertEquals("2", s.now?.item?.key)
    }

    @Test
    fun svuotaTieneLaCorrente() {
        var s = QueueOps.started(QueueState(), song("now"), 1)
        s = QueueOps.add(s, song("x"), 2)
        s = QueueOps.clearUpcoming(s)
        assertEquals(listOf("now"), titles(s))
        assertFalse(s.hasNext)
    }

    @Test
    fun toltaLaCorrenteAvantiPortaAllaSeguente() {
        var s = QueueOps.started(QueueState(), song("a"), 1)
        s = QueueOps.add(s, song("b"), 2)
        s = QueueOps.add(s, song("c"), 3)
        s = QueueOps.jump(s, 2)
        s = QueueOps.remove(s, 2)
        assertEquals("a", s.now?.item?.key)
        assertEquals("c", s.entries[s.current + 1].item.key)
    }
}
