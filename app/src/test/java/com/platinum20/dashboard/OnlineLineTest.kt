package com.platinum20.dashboard

import com.platinum20.dashboard.net.Online
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineLineTest {
    @Test fun conLaReteLaRigaResta() = assertEquals("3 nuove", Online.subtitle(true, "3 nuove"))
    @Test fun senzaReteLoDiceDavanti() = assertEquals("Senza rete · 3 nuove", Online.subtitle(false, "3 nuove"))
    @Test fun senzaReteEsenzaRiga() = assertEquals("Senza rete", Online.subtitle(false, null))
}
