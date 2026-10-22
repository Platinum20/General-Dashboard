package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.ui.screens.InAppLinks
import com.platinum20.dashboard.ui.screens.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InAppLinksTest {
    @Test
    fun paginaWebNelLettoreInterno() {
        assertEquals(LinkTarget.Web("https://www.poste.it/cerca/#/risultati-spedizioni/ABC"), InAppLinks.target(" https://www.poste.it/cerca/#/risultati-spedizioni/ABC "))
    }

    @Test
    fun videoYouTubeNelPlayer() {
        assertEquals(LinkTarget.YouTube("https://m.youtube.com/watch?v=abc"), InAppLinks.target("https://youtu.be/abc"))
        assertEquals(LinkTarget.YouTube("https://m.youtube.com/watch?v=abc"), InAppLinks.target("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun nellaPaginaInternaRestanoSoloLePagineNormali() {
        assertTrue(InAppLinks.staysInPage("https://www.repubblica.it/cronaca/2026/10/22/articolo"))
        assertFalse(InAppLinks.staysInPage("https://youtu.be/abc"))
        assertFalse(InAppLinks.staysInPage("https://www.youtube.com/watch?v=abc"))
        assertFalse(InAppLinks.staysInPage("https://open.spotify.com/track/1"))
    }

    @Test
    fun spotifyNelSuoPlayer() {
        assertEquals(LinkTarget.Spotify("https://open.spotify.com/track/1"), InAppLinks.target("https://open.spotify.com/track/1"))
    }

    @Test
    fun altriSchemiNonEsconoDallApp() {
        assertEquals(LinkTarget.None, InAppLinks.target("whatsapp://send?text=ciao"))
        assertEquals(LinkTarget.None, InAppLinks.target("market://details?id=x"))
        assertEquals(LinkTarget.None, InAppLinks.target(""))
    }

    @Test
    fun invitoAdAprireUnAppUsaLIndirizzoDiRiserva() {
        val intent = "intent://article/123#Intent;scheme=giornale;package=it.giornale;" +
            "S.browser_fallback_url=https%3A%2F%2Fwww.giornale.it%2Farticolo%2F123;end"
        assertEquals("https://www.giornale.it/articolo/123", InAppLinks.intentFallback(intent))
        assertNull(InAppLinks.intentFallback("intent://x#Intent;package=it.app;end"))
        assertNull(InAppLinks.intentFallback("https://www.giornale.it"))
        assertNull(InAppLinks.intentFallback("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
    }
}
