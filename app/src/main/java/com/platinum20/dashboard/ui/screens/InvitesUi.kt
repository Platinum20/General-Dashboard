package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.today.Invite
import com.platinum20.dashboard.today.Invites

/**
 * In cima a Oggi, sotto il titolo, solo quando c'è qualcosa da sistemare: "● 2 cose da sistemare: turni, agenda ›"
 * in arancione. Il tocco apre il fumetto con le scelte; sistemato tutto, sparisce.
 */
@Composable
fun InvitesRow(
    invites: List<Invite>,
    onAllowCalendar: () -> Unit,
    onOpenAutoFill: () -> Unit,
    onDismissAutoFill: () -> Unit,
) {
    val line = Invites.line(invites) ?: return
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val haptic = rememberHaptic()
    // Una riga arancione sotto il titolo (1.528, Antonio scelta B): si vede subito, sparisce quando è sistemato.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val orange = Color(if (dark) TileAccent.ORANGE.dark else TileAccent.ORANGE.light)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable {
                haptic(Haptic.TICK)
                val choices = invites.map { invite ->
                    when (invite) {
                        Invite.SHIFTS -> BubbleAction(Icons.Outlined.Work, "Rientra nei turni", "L'accesso a Creators Pro è scaduto") {
                            inApp?.openRoute?.invoke(Routes.SHIFTS)
                        }
                        Invite.AGENDA -> BubbleAction(Icons.Outlined.CalendarMonth, "Consenti l'agenda", "Gli impegni del telefono nel Calendario", onClick = onAllowCalendar)
                        Invite.AUTOFILL -> BubbleAction(Icons.Outlined.AutoAwesome, "Attiva Compila da solo", "Pacchi e scadenze dalle notifiche", onClick = onOpenAutoFill)
                    }
                }
                // "Non ora" toglie solo gli inviti: un accesso scaduto resta finché non rientri.
                val later = if (Invite.AGENDA in invites || Invite.AUTOFILL in invites) {
                    listOf(BubbleAction(Icons.Outlined.Close, "Non ora", "Agenda e Compila da solo restano nelle Impostazioni") {
                        if (Invite.AGENDA in invites) hideAgenda(context)
                        if (Invite.AUTOFILL in invites) onDismissAutoFill()
                    })
                } else emptyList()
                ActionBubble.show(*(choices + later).toTypedArray())
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Box(Modifier.size(8.dp).background(orange, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = orange,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = orange,
            modifier = Modifier.size(18.dp),
        )
    }
}
