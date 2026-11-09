package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.endOfHome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home di YouTube infinita: ci si ferma solo quando YouTube non dà più niente. */
class YouTubeHomeInfiniteTest {
    @Test fun conIlSegnoSiContinua() {
        assertFalse(endOfHome("tok", before = 20, after = 20))
        assertFalse(endOfHome("tok", before = 20, after = 40))
    }

    @Test fun senzaSegnoMaConVideoNuoviSiRiprova() {
        // Home dalla copia salvata: la rilettura porta video nuovi, quindi non è finita.
        assertFalse(endOfHome(null, before = 20, after = 32))
    }

    @Test fun senzaSegnoENienteDiNuovoEFinita() {
        assertTrue(endOfHome(null, before = 20, after = 20))
    }
}
