package com.platinum20.dashboard

import com.platinum20.dashboard.today.Invite
import com.platinum20.dashboard.today.Invites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvitesTest {
    @Test fun primoAvvioDueInviti() = assertEquals(
        listOf(Invite.AGENDA, Invite.AUTOFILL),
        Invites.pending(agendaVisible = true, calendarGranted = false, autoFillEnabled = false, autoFillDismissed = false),
    )

    @Test fun permessoConcessoTogliLAgenda() = assertEquals(
        listOf(Invite.AUTOFILL),
        Invites.pending(agendaVisible = true, calendarGranted = true, autoFillEnabled = false, autoFillDismissed = false),
    )

    @Test fun agendaNascostaNonSiChiedePiu() = assertEquals(
        emptyList<Invite>(),
        Invites.pending(agendaVisible = false, calendarGranted = false, autoFillEnabled = false, autoFillDismissed = true),
    )

    @Test fun compilaDaSoloAttivoONonOra() {
        assertEquals(emptyList<Invite>(), Invites.pending(true, true, autoFillEnabled = true, autoFillDismissed = false))
        assertEquals(emptyList<Invite>(), Invites.pending(true, true, autoFillEnabled = false, autoFillDismissed = true))
    }

    @Test fun riga() {
        assertEquals("2 cose da attivare: agenda, compila da solo", Invites.line(listOf(Invite.AGENDA, Invite.AUTOFILL)))
        assertEquals("1 cosa da attivare: agenda", Invites.line(listOf(Invite.AGENDA)))
        assertNull(Invites.line(emptyList()))
    }
}
