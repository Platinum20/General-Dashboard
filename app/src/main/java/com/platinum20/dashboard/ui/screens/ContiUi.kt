package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SimCard
import com.platinum20.dashboard.capture.CaptureFeedback
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.sim.WindTreLink

/**
 * La card della SIM (dalla 1.447 da sola: niente più soldi nell'app). Dalla 1.450 (Antonio) mostra tutto:
 * giga, minuti e SMS ognuno con la sua linea, poi credito e rinnovo. Un solo "SIM": il titolo.
 * Il tocco apre la pagina della SIM.
 */
@Composable
internal fun ContiCard(onOpenSim: () -> Unit) {
    val context = LocalContext.current
    SimStore.init(context)
    val status by SimStore.status.collectAsStateWithLifecycle()
    val linked by SimStore.linked.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Si aggiorna da sola quando la Home è aperta e il dato ha più di un'ora.
    AutoRefresh(everyMs = SimStore.STALE_MS) { if (linked && SimStore.isStale()) WindTreLink.refresh(context) }
    val s = status
    // Lo stato vero (1.402): vecchio dopo tre ore, la riga lo dice e i numeri si sbiadiscono.
    val link = LinkStatus.of(linked, expired = false, readAt = s?.at ?: 0, now = System.currentTimeMillis(), staleAfter = 3 * SimStore.STALE_MS)
    CardSection(
        title = "SIM",
        modifier = Modifier.sharedItem(SharedKeys.SIM, rounded = true),
        onClick = { haptic(Haptic.TICK); onOpenSim() },
        // "Letto alle …" sulla riga del titolo, tutto a destra (1.460, Antonio): una riga in meno nella card.
        trailing = { link.line?.takeIf { s != null }?.let { SoftLine(it) } },
        // Tenendo premuto: le azioni della pagina SIM senza entrarci (Antonio, 2026-11-13).
        onLongClick = if (!linked) null else ({
            haptic(Haptic.LONG_PRESS)
            ActionBubble.show(
                BubbleAction(Icons.Outlined.SimCard, "Apri", detail = "Giga, minuti e credito") { onOpenSim() },
                BubbleAction(Icons.Outlined.Refresh, "Aggiorna adesso", detail = link.line.orEmpty()) {
                    scope.launch {
                        val ok = WindTreLink.refresh(context)
                        if (!ok) CaptureFeedback.tell(context, "WindTre non ha risposto: rifai l'accesso da Impostazioni", Icons.Outlined.SimCard)
                    }
                },
                BubbleAction(Icons.Outlined.Share, "Condividi i dati letti", detail = "Per correggere un numero sbagliato") {
                    shareTextFile(context, "windtre-dati-letti.txt", SimStore.capturesText())
                },
            )
        }),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            when {
                s == null && linked -> SimPlaceholder("Sto leggendo i dati di WindTre")
                s == null || !linked -> {
                    Text("Collega", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    SoftLine("giga, minuti, SMS e credito WindTre")
                }
                else -> {
                    SimPanel(s, faded = link.faded)
                }
            }
        }
    }
}

@Composable
private fun SoftLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SimPlaceholder(description: String) {
    Column(
        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { Placeholder(220.dp, 14.dp) }
        Placeholder(140.dp, 10.dp)
    }
}
