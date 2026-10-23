package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.today.Invite
import com.platinum20.dashboard.today.Invites

/**
 * In fondo a Oggi: "2 cose da attivare: agenda, compila da solo ›". Un tocco apre i pulsanti;
 * concesso o "Non ora", l'invito sparisce. Così in cima resta quello che c'è da fare oggi.
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
    var open by remember { mutableStateOf(false) }
    CanvasCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(0.98f)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open && invites.isNotEmpty()) {
        CanvasDialog(
            onDismissRequest = { open = false },
            title = { Text("Da attivare") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    invites.forEach { invite ->
                        when (invite) {
                            Invite.AGENDA -> InviteItem(
                                icon = Icons.Outlined.CalendarMonth,
                                text = "Agenda: gli impegni di oggi in Oggi e in Adesso.",
                                action = "Consenti",
                                onAction = { open = false; onAllowCalendar() },
                                onLater = { hideAgenda(context) },
                            )
                            Invite.AUTOFILL -> InviteItem(
                                icon = Icons.Outlined.AutoAwesome,
                                text = "Compila da solo: pacchi, spese e scadenze dalle notifiche. Tutto resta sul telefono.",
                                action = "Attiva",
                                onAction = { open = false; onOpenAutoFill() },
                                onLater = onDismissAutoFill,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Chiudi") } },
        )
    }
}

@Composable
private fun InviteItem(icon: ImageVector, text: String, action: String, onAction: () -> Unit, onLater: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onLater) { Text("Non ora") }
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
