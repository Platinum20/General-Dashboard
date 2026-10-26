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
 * Su un emulatore vero: scarica la voce Riccardo, la fa parlare col motore Piper e poi prova il
 * percorso dell'app (RecapSpeaker con la voce scelta). Parte solo nel workflow voicecheck.yml.
 */
@RunWith(AndroidJUnit4::class)
class VoiceEngineCheck {
    private val tag = "VoiceCheck"

    @Test
    fun piperParla() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voice = ReadingVoice.RICCARDO
        val dir = File(context.filesDir, "voices/${voice.archive}")
        val files = VoiceStore.install(voice.url!!, dir) {}
        Log.i(tag, "file: $files")

        val probe = runCatching { NeuralVoice.probe(voice, files, "Ciao, questa è una prova della voce.") }
        Log.i(tag, "motore: ${probe.getOrNull()} ${probe.exceptionOrNull()?.stackTraceToString() ?: ""}")

        // Il percorso vero: Riccardo scelta in Impostazioni, poi una lettura come dalla notizia.
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
