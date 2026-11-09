package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.docs.DocCrop
import com.platinum20.dashboard.docs.DocCrop.Box
import com.platinum20.dashboard.docs.DocCrop.Seg
import com.platinum20.dashboard.docs.DocKind
import com.platinum20.dashboard.docs.Document
import com.platinum20.dashboard.docs.DocumentsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class DocCropTest {
    private fun seg(deg: Double, weight: Int = 10) =
        Seg(cos(Math.toRadians(deg)).toFloat() * 100, sin(Math.toRadians(deg)).toFloat() * 100, weight)

    @Test
    fun angleOfSlightlyTiltedText() {
        val a = DocCrop.textAngle(listOf(seg(3.0), seg(2.0), seg(4.0), seg(3.5, 20)))!!
        assertEquals(3.5f, a, 0.01f)
    }

    @Test
    fun sidewaysAndUpsideDown() {
        assertEquals(90f, DocCrop.textAngle(listOf(seg(89.0), seg(91.0, 30), seg(90.0)))!!, 1.1f)
        // Sottosopra le righe stanno a cavallo di ±180: non deve uscire 0.
        val flipped = DocCrop.textAngle(listOf(seg(179.0), seg(-179.0), seg(180.0, 30)))!!
        assertTrue(abs(abs(flipped) - 180f) < 1.1f)
    }

    @Test
    fun oneCrookedLineDoesNotWin() {
        // La firma storta sulla tessera non sposta le righe stampate.
        val a = DocCrop.textAngle(listOf(seg(1.0, 30), seg(1.5, 25), seg(25.0, 8)))!!
        assertTrue(a in 0.9f..1.6f)
    }

    @Test
    fun noTextNoAngle() {
        assertNull(DocCrop.textAngle(emptyList()))
    }

    /** Una tessera chiara su un tavolo scuro e un po' rumoroso, con la foto a sinistra e il testo. */
    private fun scene(card: Box, noise: Int = 18): IntArray {
        val w = 480
        val h = 360
        val rnd = Random(7)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x in card.left..card.right && y in card.top..card.bottom -> when {
                    // La foto della persona, scura.
                    x in card.left + 20..card.left + 90 && y in card.top + 40..card.top + 150 -> 40
                    // Righe di testo.
                    x in card.left + 110..card.right - 15 && y in card.top + 30..card.bottom - 20 && (y / 6) % 3 == 0 && (x / 3) % 2 == 0 -> 30
                    else -> 225
                }
                else -> 70 + rnd.nextInt(noise)
            }
        }
    }

    @Test
    fun findsTheCardEdges() {
        val card = Box(60, 70, 380, 271) // 321 × 202 ≈ tessera
        val text = Box(card.left + 110, card.top + 30, card.right - 15, card.bottom - 20)
        val found = DocCrop.edges(scene(card), 480, 360, text)
        assertNotNull(found)
        found!!
        assertTrue("sinistra ${found.left}", abs(found.left - card.left) <= 2)
        assertTrue("destra ${found.right}", abs(found.right - card.right) <= 2)
        assertTrue("alto ${found.top}", abs(found.top - card.top) <= 2)
        assertTrue("basso ${found.bottom}", abs(found.bottom - card.bottom) <= 2)
    }

    @Test
    fun cardTouchingTheFrameStillCrops() {
        // La tessera esce dal bordo a sinistra: tre bordi veri bastano.
        val card = Box(0, 60, 330, 268)
        val text = Box(card.left + 110, card.top + 30, card.right - 15, card.bottom - 20)
        val found = DocCrop.edges(scene(card), 480, 360, text)
        assertNotNull(found)
        assertTrue(abs(found!!.right - card.right) <= 2)
    }

    @Test
    fun noEdgesKeepsTheWholePhoto() {
        val flat = IntArray(480 * 360) { 200 }
        assertNull(DocCrop.edges(flat, 480, 360, Box(150, 120, 330, 240)))
    }

    @Test
    fun pdfCardAtRealSize() {
        // Tessera ritagliata: 85,6 mm = 242,6 punti, come una fotocopia.
        val (w, _) = DocumentsStore.pdfSize(DocKind.ID_CARD, cropped = true, width = 1600, height = 1010, slotW = 483f, slotH = 350f)
        assertEquals(242.6f, w, 0.5f)
        // Foto intera (non ritagliata): riempie la sua metà di foglio.
        val (fw, fh) = DocumentsStore.pdfSize(DocKind.ID_CARD, cropped = false, width = 1600, height = 1200, slotW = 483f, slotH = 350f)
        assertTrue(fw <= 483f && fh <= 350.01f && (abs(fw - 483f) < 0.5f || abs(fh - 350f) < 0.5f))
    }

    @Test
    fun oldDocumentsGetStraightenedOnce() {
        val old = """[{"id":1,"kind":"LICENSE","title":"Patente","front":"/f.jpg","back":null,"createdAt":5}]"""
        val parsed = DocumentsStore.parse(old).single()
        assertFalse(parsed.straight)
        val doc = Document(2, DocKind.ID_CARD, "Carta", "/c.jpg", createdAt = 1, frontFull = "/full.jpg")
        val back = DocumentsStore.parse(DocumentsStore.serialize(listOf(doc))).single()
        assertTrue(back.straight)
        assertEquals("/full.jpg", back.frontFull)
    }
}
