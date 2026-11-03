package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.keepMissingChannels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class YtSubscriptionsMergeTest {
    private fun v(id: String, ch: String, at: Long) = YtVideo(id, "t$id", ch, ch, Instant.ofEpochSecond(at))
    private fun c(id: String) = YtChannel(id, id, "")

    @Test
    fun canaliSenzaFeedTengonoIVideoDiPrima() {
        val previous = listOf(v("a1", "A", 30), v("b1", "B", 20), v("c1", "C", 10))
        // Stavolta è arrivato solo il feed di A (gli altri rifiutati da YouTube).
        val fresh = listOf(v("a2", "A", 40), v("a1", "A", 30))
        val out = keepMissingChannels(fresh, previous, listOf(c("A"), c("B"), c("C")))
        assertEquals(listOf("a2", "a1", "b1", "c1"), out.map { it.id })
    }

    @Test
    fun canaleArrivatoSostituisceIVecchi() {
        val previous = listOf(v("a0", "A", 5))
        val out = keepMissingChannels(listOf(v("a2", "A", 40)), previous, listOf(c("A")))
        assertEquals(listOf("a2"), out.map { it.id })
    }

    @Test
    fun canaliNonPiuSeguitiSpariscono() {
        val previous = listOf(v("b1", "B", 20))
        val out = keepMissingChannels(listOf(v("a1", "A", 30)), previous, listOf(c("A")))
        assertEquals(listOf("a1"), out.map { it.id })
    }
}
