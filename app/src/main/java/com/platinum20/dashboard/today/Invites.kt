package com.platinum20.dashboard.today

/** Le cose che Oggi può fare in più se l'utente le attiva. */
enum class Invite(val label: String) {
    AGENDA("agenda"),
    AUTOFILL("compila da solo"),
}

/**
 * Gli inviti a impostare qualcosa non stanno in cima a Oggi: si raccolgono in una riga
 * in fondo che sparisce quando non resta niente da attivare. Funzioni pure.
 */
object Invites {
    fun pending(
        agendaVisible: Boolean,
        calendarGranted: Boolean,
        autoFillEnabled: Boolean,
        autoFillDismissed: Boolean,
    ): List<Invite> = buildList {
        if (agendaVisible && !calendarGranted) add(Invite.AGENDA)
        if (!autoFillEnabled && !autoFillDismissed) add(Invite.AUTOFILL)
    }

    /** "2 cose da attivare: agenda, compila da solo"; null se non c'è niente. */
    fun line(invites: List<Invite>): String? = when (invites.size) {
        0 -> null
        1 -> "1 cosa da attivare: ${invites.single().label}"
        else -> "${invites.size} cose da attivare: ${invites.joinToString(", ") { it.label }}"
    }
}
