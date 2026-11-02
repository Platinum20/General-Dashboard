package com.platinum20.dashboard

import com.platinum20.dashboard.media.QueueFill
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
        var s = QueueOps.playNow(QueueState(), song("a"), 1)
        s = QueueOps.add(s, song("b"), 2)
        s = QueueOps.playNow(s, song("v", ResumeKind.YOUTUBE), 3)
        assertEquals(listOf("a", "v", "b"), titles(s))
        assertEquals("v", s.now?.item?.key)
        // Lo stesso video ascoltato come musica non si duplica.
        s = QueueOps.playNow(s, song("v", ResumeKind.MUSIC), 4)
        assertEquals(3, s.entries.size)
    }

    @Test
    fun laProssimaPartitaDaSolaFaAvanzare() {
        var s = QueueOps.playNow(QueueState(), song("a"), 1)
        s = QueueOps.add(s, song("b"), 2)
        s = QueueOps.playNow(s, song("b"), 3)
        assertEquals(listOf("a", "b"), titles(s))
        assertEquals(1, s.current)
    }

    @Test
    fun spostaSuEGiuETogli() {
        var s = QueueOps.playNow(QueueState(), song("now"), 1)
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
        var s = QueueOps.playNow(QueueState(), song("now"), 1)
        s = QueueOps.add(s, song("x"), 2)
        s = QueueOps.playList(s, listOf(song("1"), song("2"), song("3")), start = 1, firstId = 10)
        assertEquals(listOf("now", "1", "2", "3"), titles(s))
        assertEquals("2", s.now?.item?.key)
    }

    @Test
    fun svuotaTieneLaCorrente() {
        var s = QueueOps.playNow(QueueState(), song("now"), 1)
        s = QueueOps.add(s, song("x"), 2)
        s = QueueOps.clearUpcoming(s)
        assertEquals(listOf("now"), titles(s))
        assertFalse(s.hasNext)
    }

    @Test
    fun toltaLaCorrenteAvantiPortaAllaSeguente() {
        var s = QueueOps.playNow(QueueState(), song("a"), 1)
        s = QueueOps.add(s, song("b"), 2)
        s = QueueOps.add(s, song("c"), 3)
        s = QueueOps.jump(s, 2)
        s = QueueOps.remove(s, 2)
        assertEquals("a", s.now?.item?.key)
        assertEquals("c", s.entries[s.current + 1].item.key)
    }

    @Test
    fun toccataUnaVoceParteLeiELeAltreRestano() {
        var s = QueueOps.playNow(QueueState(), song("now"), 1)
        listOf("a", "b", "c").forEachIndexed { i, k -> s = QueueOps.add(s, song(k), 10L + i) }
        s = QueueOps.pick(s, 12)
        assertEquals("c", s.now?.item?.key)
        // "a" e "b" non si saltano: vengono ancora dopo.
        assertEquals(listOf("a", "b"), s.upcoming.map { it.item.key })
        assertEquals(listOf("now", "c", "a", "b"), titles(s))
    }

    @Test
    fun annullaRimetteLaVoceAlSuoPosto() {
        var s = QueueOps.playNow(QueueState(), song("now"), 1)
        listOf("a", "b").forEachIndexed { i, k -> s = QueueOps.add(s, song(k), 10L + i) }
        val entry = s.entries[1]
        val removed = QueueOps.remove(s, entry.id)
        val back = QueueOps.restore(removed, entry, 1)
        assertEquals(titles(s), titles(back))
        assertEquals(s.current, back.current)
        // Due volte "Annulla" non la duplica.
        assertEquals(3, QueueOps.restore(back, entry, 1).entries.size)
    }

    @Test
    fun riempieSoloLaCodaDiSpotifyQuasiFinita() {
        var s = QueueOps.playNow(QueueState(), song("now"), 1)
        assertTrue(QueueFill.needed(s))
        listOf("a", "b", "c").forEachIndexed { i, k -> s = QueueOps.add(s, song(k), 10L + i) }
        assertFalse(QueueFill.needed(s))
        val video = QueueOps.playNow(QueueState(), song("v", ResumeKind.YOUTUBE), 1)
        assertFalse(QueueFill.needed(video))
    }

    @Test
    fun ilRiempimentoNonRipeteEPreferisceLoStessoArtista() {
        fun track(key: String, artist: String) = ResumeItem(ResumeKind.SPOTIFY, key, key, artist, at = 0)
        val s = QueueOps.playNow(QueueState(), track("now", "Mina"), 1)
        val pool = listOf(track("now", "Mina"), track("m1", "Mina"), track("m2", "Mina, Celentano")) +
            (1..20).map { track("x$it", "Altri") }
        val picked = QueueFill.pick(s, pool, seed = 7)
        assertEquals(QueueFill.BATCH, picked.size)
        assertFalse(picked.any { it.key == "now" })
        assertEquals(picked.size, picked.map { it.key }.distinct().size)
        assertTrue(picked.any { it.key == "m1" } && picked.any { it.key == "m2" })
    }
}
