package com.platinum20.dashboard

import com.platinum20.dashboard.news.voice.ReadingVoice
import com.platinum20.dashboard.news.voice.SpeechPieces
import com.platinum20.dashboard.news.voice.VoiceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NeuralVoiceTest {
    @Test
    fun `le frasi tengono la loro posizione nel testo`() {
        val text = "Piove a Roma. Domani sole! Poi?"
        val pieces = SpeechPieces.sentences(text)
        assertEquals(listOf("Piove a Roma.", "Domani sole!", "Poi?"), pieces.map { it.second })
        pieces.forEach { (start, sentence) -> assertEquals(sentence, text.substring(start, start + sentence.length)) }
    }

    @Test
    fun `i decimali e le sigle non spezzano la frase`() {
        assertEquals(1, SpeechPieces.sentences("Il PIL cresce dello 0.5 per cento").size)
    }

    @Test
    fun `una frase lunghissima si spezza a una virgola`() {
        val long = "parola ".repeat(40).trim() + ", e poi il resto della frase."
        val pieces = SpeechPieces.sentences(long)
        assertEquals(2, pieces.size)
        assert(pieces.first().second.endsWith(","))
    }

    @Test
    fun `si riparte dal carattere scelto e si prosegue con le parti dopo`() {
        val parts = listOf("Primo tema: la pioggia. Arriva stasera.", "Secondo tema: il treno.")
        val pieces = SpeechPieces.from(parts, 0, 24)
        assertEquals("Arriva stasera.", pieces.first().text)
        assertEquals(0, pieces.first().part)
        assertEquals(24, pieces.first().start)
        assertEquals(1, pieces.last().part)
        assertEquals(0, pieces.last().start)
    }

    @Test
    fun `l'archivio non scrive fuori dalla cartella della voce`() {
        val root = File("voce")
        assertEquals(File(root, "tokens.txt"), VoiceStore.entryTarget(root, "vits-piper-it_IT-paola-medium/tokens.txt"))
        assertEquals(File(root, "espeak-ng-data${File.separator}it_dict"), VoiceStore.entryTarget(root, "./vits/espeak-ng-data/it_dict"))
        assertNull(VoiceStore.entryTarget(root, "vits/../../etc/passwd"))
        assertNull(VoiceStore.entryTarget(root, "vits"))
    }

    @Test
    fun `una voce è pronta solo con modello token e dati di pronuncia`() {
        val dir = Files.createTempDirectory("voce").toFile()
        try {
            File(dir, "it_IT-paola-medium.onnx").writeText("x")
            File(dir, "tokens.txt").writeText("x")
            assertNull(VoiceStore.locate(dir))
            File(dir, "espeak-ng-data").mkdirs()
            assertNotNull(VoiceStore.locate(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `tre voci con Paola di serie e il ripiego del telefono senza download`() {
        assertEquals(3, ReadingVoice.entries.size)
        assertEquals(ReadingVoice.PAOLA, ReadingVoice.of(null))
        assertNull(ReadingVoice.SYSTEM.url)
        assert(ReadingVoice.RICCARDO.url!!.endsWith("vits-piper-it_IT-riccardo-x_low.tar.bz2"))
    }
}
