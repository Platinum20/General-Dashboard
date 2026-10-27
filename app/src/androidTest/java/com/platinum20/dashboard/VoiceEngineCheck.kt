package com.platinum20.dashboard

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.news.RecapSpeaker
import com.platinum20.dashboard.news.voice.NeuralVoice
import com.platinum20.dashboard.news.voice.ReadingVoice
import com.platinum20.dashboard.news.voice.VoiceStore
import com.platinum20.dashboard.settings.AppSettings
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Su un emulatore vero: scarica ogni voce, la fa parlare col motore Piper e poi prova il
 * percorso dell'app (RecapSpeaker con la voce scelta). Parte solo nel workflow voicecheck.yml.
 */
@RunWith(AndroidJUnit4::class)
class VoiceEngineCheck {
    private val tag = "VoiceCheck"

    /** Ogni voce col suo modello: Paola e Miro non partivano sul telefono mentre Riccardo sì. */
    @Test
    fun ogniVoceParla() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = ReadingVoice.entries.associateWith { voice ->
            val dir = File(context.filesDir, "voices/${voice.archive}")
            runCatching {
                val files = VoiceStore.install(voice.url, dir) {}
                Log.i(tag, "${voice.label} file: $files ${files.model.length()} byte")
                NeuralVoice.probe(voice, files, "Ciao, questa è una prova della voce.")
            }.also { Log.i(tag, "${voice.label} motore: ${it.getOrNull()} ${it.exceptionOrNull()?.stackTraceToString() ?: ""}") }
        }
        results.forEach { (voice, result) ->
            assertTrue("${voice.label} non genera: ${result.exceptionOrNull()}", (result.getOrNull()?.first ?: 0) > 0)
        }
    }

    @Test
    fun piperParla() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Miro suona a 22050 Hz: è il caso che sul telefono non apriva l'audio.
        val voice = ReadingVoice.MIRO
        val dir = File(context.filesDir, "voices/${voice.archive}")
        // La rete dell'emulatore a volte parte tardi: qualche tentativo prima di arrendersi.
        val files = (1..5).firstNotNullOfOrNull { attempt ->
            runCatching { VoiceStore.install(voice.url, dir) {} }
                .onFailure { Log.i(tag, "download $attempt: $it"); Thread.sleep(5_000) }.getOrNull()
        } ?: VoiceStore.install(voice.url, dir) {}
        Log.i(tag, "file: $files")

        val probe = runCatching { NeuralVoice.probe(voice, files, "Ciao, questa è una prova della voce.") }
        Log.i(tag, "motore: ${probe.getOrNull()} ${probe.exceptionOrNull()?.stackTraceToString() ?: ""}")

        // Il percorso vero: la voce scelta in Impostazioni, poi una lettura come dalla notizia.
        AppSettings.init(context)
        AppSettings.setReadingVoice(voice)
        VoiceStore.init(context)
        Log.i(tag, "voce pronta per l'app: ${VoiceStore.readyVoice()?.first}")
        Handler(Looper.getMainLooper()).post {
            RecapSpeaker.play(context, listOf("Prova del percorso vero dell'app, con la voce scelta."), title = "Prova")
        }
        var maxPosition = 0L
        repeat(80) {
            Thread.sleep(150)
            RecapSpeaker.state.value?.let { maxPosition = maxOf(maxPosition, it.positionMs) }
        }
        Log.i(tag, "app: posizione massima $maxPosition ms, errore Piper: ${NeuralVoice.failure.value}")
        Handler(Looper.getMainLooper()).post { RecapSpeaker.stop() }

        assertTrue("Il motore non genera: ${probe.exceptionOrNull()}", (probe.getOrNull()?.first ?: 0) > 0)
        assertNull("Piper non parte nell'app", NeuralVoice.failure.value)
    }
}
