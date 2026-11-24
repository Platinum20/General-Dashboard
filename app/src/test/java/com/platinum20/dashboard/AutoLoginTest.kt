package com.platinum20.dashboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.platinum20.dashboard.net.AutoLogin
import com.platinum20.dashboard.sim.WindTreCode
import com.platinum20.dashboard.sim.WindTreLink
import com.platinum20.dashboard.today.Invite
import com.platinum20.dashboard.today.Invites
import com.platinum20.dashboard.ui.screens.AccountState
import com.platinum20.dashboard.ui.screens.AccountTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoLoginTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Utente e password entrano come stringhe JS quotate: non possono chiudere lo script. */
    @Test
    fun fillScriptQuotesTheValues() {
        val js = AutoLogin.fillScript("CanvasSim", "me@x.it", "a\"b</script>');x()")
        assertFalse(js.contains("</script>"))
        assertFalse(js.contains("a\"b"))
        assertTrue(js.contains("\"me@x.it\""))
        assertTrue(js.contains("CanvasSim.mayFill()"))
    }

    @Test
    fun captureAndCodeGoToTheGivenBridge() {
        assertTrue(AutoLogin.captureScript("CanvasShift").contains("CanvasShift.typed("))
        val code = AutoLogin.codeScript("CanvasSim")
        assertTrue(code.contains("CanvasSim.codeSeen()"))
        assertTrue(code.contains("CanvasSim.code()"))
    }

    /** Si salva solo dopo una lettura riuscita, e una password sbagliata non resta. */
    @Test
    fun savedOnlyAfterConfirm() {
        val auto = AutoLogin("test_login")
        auto.clear(context)
        auto.typed("antonio@x.it", "")
        auto.typed("", "segreta")
        assertNull(auto.saved(context))
        auto.confirm(context)
        assertEquals("antonio@x.it" to "segreta", auto.saved(context))
        // Password nuova, utente uguale (la pagina della password non ha il campo utente).
        auto.typed("", "nuova")
        auto.confirm(context)
        assertEquals("antonio@x.it" to "nuova", auto.saved(context))
        auto.clear(context)
        assertNull(auto.saved(context))
    }

    /** Al massimo tre invii per rilettura: con la password cambiata non si insiste. */
    @Test
    fun stopsAfterThreeTries() {
        val auto = AutoLogin("test_login2")
        auto.begin()
        assertTrue(auto.mayFill()); assertTrue(auto.mayFill()); assertTrue(auto.mayFill())
        assertFalse(auto.mayFill())
        auto.begin()
        assertTrue(auto.mayFill())
    }

    @Test
    fun codeSeenOnlyOncePerRead() {
        val auto = AutoLogin("test_login3")
        auto.begin()
        assertTrue(auto.codeSeen())
        assertFalse(auto.codeSeen())
        auto.begin()
        assertEquals(0L, auto.codeAskedAt)
        // Un campo "codice" qualsiasi senza aver inviato il modulo non fa cercare nella posta (batteria).
        assertFalse(auto.codeSeen(allowed = false))
        assertFalse(auto.submitted)
        auto.mayFill()
        assertTrue(auto.submitted)
    }

    /** La mail vera di WindTre (Antonio, 2026-11-24). */
    @Test
    fun windTreCodeFromTheMail() {
        assertEquals("981707", WindTreCode.fromText("981707 è il tuo codice di verifica."))
        assertEquals("505988", WindTreCode.fromText("Nuovo accesso all'App WINDTRE. Il tuo codice di verifica è 505988. Vale 5 minuti"))
        assertNull(WindTreCode.fromText("La tua offerta si rinnova il 12/12"))
    }

    @Test
    fun sessionGoneOnlyWhenTheSiteSaysSo() {
        assertTrue(WindTreLink.sessionGone(true, "https://ac.windtre.it/"))
        assertTrue(WindTreLink.sessionGone(false, "https://login.windtre.it/x"))
        assertFalse(WindTreLink.sessionGone(false, "https://ac.windtre.it/"))
        assertFalse(WindTreLink.sessionGone(false, null))
    }

    @Test
    fun simExpiredAsksToComeBackIn() {
        val t = AccountTiles.build(false, false, false, false, sim = true, simStale = false, shifts = false, shiftsExpired = false, simExpired = true)
        val sim = t.first { it.name == "WindTre" }
        assertEquals(AccountState.ATTENTION, sim.state)
        assertEquals("Rientra", sim.line)
        assertEquals(listOf(Invite.SIM), Invites.pending(false, true, true, true, simExpired = true))
    }
}
