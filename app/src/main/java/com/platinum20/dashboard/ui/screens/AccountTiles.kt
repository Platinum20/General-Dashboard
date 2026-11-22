package com.platinum20.dashboard.ui.screens

/** Lo stato di un collegamento, come lo dice la sua riga. */
enum class AccountState { OK, ATTENTION, OFF }

/** Quale account è: decide la riga e le azioni del fumetto. */
enum class AccountKind { WINDTRE, CREATORS, GMAIL, SPOTIFY, YOUTUBE }

/** [line] = il tasto quando c'è da fare ("Rientra", "Collega"). */
data class AccountTile(val name: String, val state: AccountState, val line: String, val kind: AccountKind)

/**
 * Gli account (1.576, Antonio giro 4): una riga "Account" in L'app che apre la pagina con una riga per
 * collegamento; quelli da sistemare per primi, in arancione. Pura, per i test.
 */
object AccountTiles {
    fun build(
        spotify: Boolean,
        youtube: Boolean,
        gmail: Boolean,
        gmailBroken: Boolean,
        sim: Boolean,
        simStale: Boolean,
        shifts: Boolean,
        shiftsExpired: Boolean,
    ): List<AccountTile> = listOf(
        when {
            sim && simStale -> AccountTile("WindTre", AccountState.ATTENTION, "Aggiorna", AccountKind.WINDTRE)
            sim -> AccountTile("WindTre", AccountState.OK, "Collegato", AccountKind.WINDTRE)
            else -> AccountTile("WindTre", AccountState.OFF, "Collega", AccountKind.WINDTRE)
        },
        when {
            shifts && shiftsExpired -> AccountTile("Creators Pro", AccountState.ATTENTION, "Rientra", AccountKind.CREATORS)
            shifts -> AccountTile("Creators Pro", AccountState.OK, "Collegato", AccountKind.CREATORS)
            else -> AccountTile("Creators Pro", AccountState.OFF, "Collega", AccountKind.CREATORS)
        },
        when {
            gmail && gmailBroken -> AccountTile("Gmail", AccountState.ATTENTION, "Ricollega", AccountKind.GMAIL)
            gmail -> AccountTile("Gmail", AccountState.OK, "Collegato", AccountKind.GMAIL)
            else -> AccountTile("Gmail", AccountState.OFF, "Collega", AccountKind.GMAIL)
        },
        AccountTile("Spotify", if (spotify) AccountState.OK else AccountState.OFF, if (spotify) "Collegato" else "Accedi", AccountKind.SPOTIFY),
        AccountTile("YouTube", if (youtube) AccountState.OK else AccountState.OFF, if (youtube) "Collegato" else "Accedi", AccountKind.YOUTUBE),
    ).sortedBy { if (it.state == AccountState.ATTENTION) -1 else it.state.ordinal }

    /** I gruppi della pagina Account, nell'ordine: da sistemare, collegati, da collegare (quelli vuoti spariscono). */
    fun groups(tiles: List<AccountTile>): List<Pair<String, List<AccountTile>>> = listOf(
        "Da sistemare" to tiles.filter { it.state == AccountState.ATTENTION },
        "Collegati" to tiles.filter { it.state == AccountState.OK },
        "Da collegare" to tiles.filter { it.state == AccountState.OFF },
    ).filter { it.second.isNotEmpty() }

    /** La riga "Account" delle Impostazioni, in una riga: prima quello che c'è da sistemare. */
    fun summary(tiles: List<AccountTile>): String {
        val fix = tiles.filter { it.state == AccountState.ATTENTION }
        val on = tiles.count { it.state != AccountState.OFF }
        return when {
            fix.size == 1 -> "${fix[0].name} da sistemare"
            fix.size > 1 -> "${fix.size} account da sistemare"
            on == 0 -> "Nessun account collegato"
            on == 1 -> "1 collegato su ${tiles.size}"
            else -> "$on collegati su ${tiles.size}"
        }
    }
}
