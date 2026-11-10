package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarPermission
import com.platinum20.dashboard.calendar.EditPage
import com.platinum20.dashboard.calendar.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPermissionTest {
    @Test fun concesso() = assertEquals(PermissionStatus.GRANTED, CalendarPermission.afterRequest(granted = true, showRationale = false))

    @Test fun negatoUnaVoltaSiPuoRichiedere() =
        assertEquals(PermissionStatus.DENIED, CalendarPermission.afterRequest(granted = false, showRationale = true))

    @Test fun negatoPerSempre() =
        assertEquals(PermissionStatus.BLOCKED, CalendarPermission.afterRequest(granted = false, showRationale = false))

    @Test fun pulsanteGiusto() {
        assertEquals("Consenti", CalendarPermission.actionLabel(PermissionStatus.DENIED))
        assertEquals("Consenti", CalendarPermission.actionLabel(PermissionStatus.GRANTED))
        assertEquals("Apri impostazioni", CalendarPermission.actionLabel(PermissionStatus.BLOCKED))
    }

    @Test fun messaggioDellaScrittura() {
        assertNull(CalendarPermission.writeDeniedMessage(PermissionStatus.GRANTED))
        assertEquals("Serve il permesso di scrittura del calendario", CalendarPermission.writeDeniedMessage(PermissionStatus.DENIED))
        assertTrue(CalendarPermission.writeDeniedMessage(PermissionStatus.BLOCKED)!!.contains("Info app"))
    }

    @Test fun nuovoEventoSenzaPermessoChiedeSempre() {
        // Anche prima della risposta al dialog: mai una pagina vuota.
        assertEquals(EditPage.ASK, CalendarPermission.editPage(granted = false, loaded = false, hasDraft = false))
        assertEquals(EditPage.LOADING, CalendarPermission.editPage(granted = true, loaded = false, hasDraft = false))
        assertEquals(EditPage.MISSING, CalendarPermission.editPage(granted = true, loaded = true, hasDraft = false))
        assertEquals(EditPage.FORM, CalendarPermission.editPage(granted = true, loaded = true, hasDraft = true))
    }
}
