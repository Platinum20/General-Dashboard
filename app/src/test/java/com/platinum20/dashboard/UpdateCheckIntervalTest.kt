package com.platinum20.dashboard

import androidx.work.PeriodicWorkRequest
import com.platinum20.dashboard.work.UpdateCheckWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateCheckIntervalTest {
    /** L'avviso deve arrivare subito: il controllo gira al minimo intervallo che WorkManager accetta. */
    @Test
    fun controllaAlMinimoConsentito() {
        assertEquals(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MINUTES.toMillis(UpdateCheckWorker.CHECK_MINUTES))
    }
}
