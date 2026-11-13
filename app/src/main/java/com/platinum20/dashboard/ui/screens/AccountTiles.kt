package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Lo stato di un collegamento, come lo dice la sua tessera. */
enum class AccountState { OK, ATTENTION, OFF }

/** Dove porta il tocco su una tessera: la pagina Account, la SIM o l'accesso ai turni. */
enum class AccountTarget { ACCOUNTS, SIM, SHIFTS }

data class AccountTile(val name: String, val state: AccountState, val line: String, val target: AccountTarget)

/**
 * Le tessere degli account in cima alle Impostazioni (1.528, Antonio scelta B): una per collegamento, con lo stato
 * vero; quelle da sistemare per prime, in arancione. Pura, per i test.
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
            sim && simStale -> AccountTile("WindTre", AccountState.ATTENTION, "Da aggiornare", AccountTarget.SIM)
            sim -> AccountTile("WindTre", AccountState.OK, "Collegato", AccountTarget.SIM)
            else -> AccountTile("WindTre", AccountState.OFF, "Collega", AccountTarget.SIM)
        },
        when {
            shifts && shiftsExpired -> AccountTile("Creators Pro", AccountState.ATTENTION, "Rientra", AccountTarget.SHIFTS)
            shifts -> AccountTile("Creators Pro", AccountState.OK, "Collegato", AccountTarget.SHIFTS)
            else -> AccountTile("Creators Pro", AccountState.OFF, "Collega", AccountTarget.SHIFTS)
        },
        when {
            gmail && gmailBroken -> AccountTile("Gmail", AccountState.ATTENTION, "Ricollega", AccountTarget.ACCOUNTS)
            gmail -> AccountTile("Gmail", AccountState.OK, "Collegato", AccountTarget.ACCOUNTS)
            else -> AccountTile("Gmail", AccountState.OFF, "Collega", AccountTarget.ACCOUNTS)
        },
        AccountTile("Spotify", if (spotify) AccountState.OK else AccountState.OFF, if (spotify) "Collegato" else "Collega", AccountTarget.ACCOUNTS),
        AccountTile("YouTube", if (youtube) AccountState.OK else AccountState.OFF, if (youtube) "Collegato" else "Collega", AccountTarget.ACCOUNTS),
    ).sortedBy { it.state.ordinal.let { o -> if (it.state == AccountState.ATTENTION) -1 else o } }
}

/** La fila delle tessere: tocca per rientrare o aprire il collegamento; scorre di lato se non ci stanno. */
@Composable
fun AccountTilesRow(tiles: List<AccountTile>, onOpen: (AccountTile) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val orange = Color(if (dark) TileAccent.ORANGE.dark else TileAccent.ORANGE.light)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.forEach { tile ->
            CanvasCard(
                modifier = Modifier.widthIn(min = 112.dp).bouncyPress(0.96f),
                onClick = { onOpen(tile) },
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(tile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        if (tile.state == AccountState.ATTENTION) {
                            Box(Modifier.size(7.dp).background(orange, CircleShape))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            tile.line,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (tile.state) {
                                AccountState.ATTENTION -> orange
                                AccountState.OK -> MaterialTheme.colorScheme.onSurfaceVariant
                                AccountState.OFF -> MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
