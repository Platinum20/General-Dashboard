package com.platinum20.dashboard

import com.platinum20.dashboard.work.UpdateCheckWorker
import com.platinum20.dashboard.work.UpdateCheckWorker.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateAutoInstallTest {
    private fun step(silent: Boolean = true, inUse: Boolean = false, waitWifi: Boolean = false, tries: Int = 0, notified: Boolean = false, waited: Long = 0) =
        UpdateCheckWorker.step(silent, inUse, waitWifi, tries, notified, waited)

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

    /** Se Canvas resta "in uso" per ore (player attivo), dopo un'ora avvisa comunque, una volta. */
    @Test
    fun dopoUnOraAvvisaAnche() {
        val wait = UpdateCheckWorker.WAIT_NOTIFY_MINUTES
        assertEquals(Step.WAIT, step(inUse = true, waited = wait - 1))
        assertEquals(Step.NOTIFY_WAITING, step(inUse = true, waited = wait))
        assertEquals(Step.NOTIFY_WAITING, step(waitWifi = true, waited = wait))
        assertEquals(Step.WAIT, step(inUse = true, waited = wait, notified = true))
        assertEquals(Step.INSTALL, step(waited = wait, notified = true))
    }

    /** Il lettore di notifiche tiene Canvas a FOREGROUND_SERVICE: non è "in uso", si aggiorna. */
    @Test
    fun servizioDiSfondoNonBlocca() {
        assertEquals(false, UpdateCheckWorker.inUse(125, playing = false))
        assertEquals(true, UpdateCheckWorker.inUse(100, playing = false))
        assertEquals(true, UpdateCheckWorker.inUse(200, playing = false))
        assertEquals(true, UpdateCheckWorker.inUse(125, playing = true))
        assertEquals(false, UpdateCheckWorker.inUse(400, playing = false))
    }
}
