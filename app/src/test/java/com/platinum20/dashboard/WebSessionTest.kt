package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.net.WebSession
import com.platinum20.dashboard.sim.SimStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebSessionTest {
    /** Il sessionStorage salvato entra come stringa JS: virgolette e tag non possono chiudere lo script. */
    @Test
    fun restoreScriptKeepsDataAsAString() {
        val saved = """{"https://app.revolut.com":{"token":"a\"b</script><script>x()","n":"1"}}"""
        val js = WebSession.restoreScript(saved)
        assertTrue(js.contains("JSON.parse(\""))
        assertFalse(js.contains("</script>"))
        assertFalse(js.contains("a\"b"))
        // Solo le chiavi che mancano: un token più nuovo della pagina non si tocca.
        assertTrue(js.contains("sessionStorage.getItem(k) === null"))
    }

    @Test
    fun snapshotGoesToTheGivenBridge() {
        val js = WebSession.snapshotScript("CanvasSim")
        assertTrue(js.contains("CanvasSim.storage(location.origin"))
    }

    /** Il giro in sottofondo passa ogni 15 minuti: WindTre va toccato almeno ogni mezz'ora, non ogni ora. */
    @Test
    fun windTreIsTouchedOftenInBackground() {
        assertTrue(SimStore.KEEP_MS < 30 * 60 * 1000L)
        assertTrue(SimStore.KEEP_MS < SimStore.STALE_MS)
    }
}
