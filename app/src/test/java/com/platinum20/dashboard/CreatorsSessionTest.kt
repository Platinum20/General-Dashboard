package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.shifts.CreatorsLink
import com.platinum20.dashboard.shifts.CreatorsSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreatorsSessionTest {
    /** Un 401 seguito dai turni (token rinnovato dalla pagina) non è un accesso scaduto. */
    @Test
    fun refusalWithDataIsNotExpired() {
        assertFalse(CreatorsLink.isExpired(gotData = true, onLogin = false, refused = true))
        assertFalse(CreatorsLink.isExpired(gotData = true, onLogin = true, refused = true))
    }

    @Test
    fun noDataAndSiteSaysNoIsExpired() {
        assertTrue(CreatorsLink.isExpired(gotData = false, onLogin = true, refused = false))
        assertTrue(CreatorsLink.isExpired(gotData = false, onLogin = false, refused = true))
    }

    /** Rete lenta o pagina che non risponde: non si dice "scaduto", si riprova al giro dopo. */
    @Test
    fun silenceIsNotExpired() {
        assertFalse(CreatorsLink.isExpired(gotData = false, onLogin = false, refused = false))
    }

    @Test
    fun keepsMissingCookiesOnly() {
        assertEquals(listOf("refresh=b"), CreatorsSession.missing("sid=a; refresh=b", "sid=z"))
    }
}
