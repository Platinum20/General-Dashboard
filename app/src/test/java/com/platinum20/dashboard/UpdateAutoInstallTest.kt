package com.platinum20.dashboard

import com.platinum20.dashboard.work.UpdateCheckWorker
import com.platinum20.dashboard.work.UpdateCheckWorker.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateAutoInstallTest {
    private fun step(silent: Boolean = true, inUse: Boolean = false, waitWifi: Boolean = false, tries: Int = 0, notified: Boolean = false) =
        UpdateCheckWorker.step(silent, inUse, waitWifi, tries, notified)

    /** Con Android 12+ e il permesso si installa da sola, senza notifica. */
    @Test
    fun installaDaSola() = assertEquals(Step.INSTALL, step())

    /** Mai mentre la stai usando o ascoltando: si riprova al giro dopo. */
    @Test
    fun aspettaSeInUso() {
        assertEquals(Step.WAIT, step(inUse = true))
        assertEquals(Step.WAIT, step(waitWifi = true))
    }

    /** Senza installazione silenziosa, o dopo troppi tentativi, resta l'avviso, una volta sola. */
    @Test
    fun altrimentiAvvisa() {
        assertEquals(Step.NOTIFY, step(silent = false))
        assertEquals(Step.NOTIFY, step(tries = UpdateCheckWorker.MAX_TRIES))
        assertEquals(Step.NOTHING, step(silent = false, notified = true))
    }
}
