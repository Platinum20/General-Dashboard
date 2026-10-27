package com.platinum20.dashboard

import com.platinum20.dashboard.news.voice.NeuralVoice
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
    fun `tre voci Piper con Leonardo per prima e di serie, le voci tolte tornano a Leonardo`() {
        assertEquals(listOf(ReadingVoice.LEONARDO, ReadingVoice.PAOLA, ReadingVoice.RICCARDO), ReadingVoice.entries)
        assertEquals(ReadingVoice.LEONARDO, ReadingVoice.of(null))
        assertEquals(ReadingVoice.LEONARDO, ReadingVoice.of("SYSTEM"))
        assertEquals(ReadingVoice.LEONARDO, ReadingVoice.of("MIRO"))
        assertEquals(
            "https://github.com/Platinum20/General-Dashboard/releases/download/voci/vits-piper-it_IT-leonardo-kirys.tar.bz2",
            ReadingVoice.LEONARDO.url,
        )
        assert(ReadingVoice.RICCARDO.url.endsWith("vits-piper-it_IT-riccardo-x_low.tar.bz2"))
    }

    @Test
    fun `il buffer audio è sempre un numero intero di campioni float`() {
        assertEquals(22048, NeuralVoice.bufferBytes(22050, 0))
        assertEquals(16000, NeuralVoice.bufferBytes(16000, 0))
        assertEquals(32768, NeuralVoice.bufferBytes(22050, 32768))
        listOf(16000, 22050, 24000, 44100).forEach { assertEquals(0, NeuralVoice.bufferBytes(it, 0) % 4) }
    }
}
