package com.platinum20.dashboard.today

/** Le cose da sistemare nelle Impostazioni: da attivare, o un accesso scaduto. */
enum class Invite(val label: String) {
    AGENDA("agenda"),
    AUTOFILL("compila da solo"),
    SHIFTS("turni scaduti"),
}

/**
 * Gli inviti non stanno in cima a Oggi: si raccolgono nella riga "Impostazioni" in fondo, col pallino arancione,
 * che c'è solo quando resta qualcosa da sistemare (punto 21 D). Funzioni pure.
 */
object Invites {
    fun pending(
        agendaVisible: Boolean,
        calendarGranted: Boolean,
        autoFillEnabled: Boolean,
        autoFillDismissed: Boolean,
        shiftsExpired: Boolean = false,
    ): List<Invite> = buildList {
        // Prima gli accessi scaduti: senza, i dati smettono di arrivare.
        if (shiftsExpired) add(Invite.SHIFTS)
        if (agendaVisible && !calendarGranted) add(Invite.AGENDA)
        if (!autoFillEnabled && !autoFillDismissed) add(Invite.AUTOFILL)
    }

    /** "2 cose da sistemare: agenda, compila da solo"; null se non c'è niente. */
    fun line(invites: List<Invite>): String? = when (invites.size) {
        0 -> null
        1 -> "1 cosa da sistemare: ${invites.single().label}"
        else -> "${invites.size} cose da sistemare: ${invites.joinToString(", ") { it.label }}"
    }
}
