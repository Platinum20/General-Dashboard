package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.sim.WindTreLink
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** I testi della card della SIM (1.404), puri per i test: brevi perché stiano in mezza card. */
object ContiText {
    /** "62 GB", "illimitati". */
    fun giga(s: SimStatus): String = s.data?.let { if (it.unlimited) "Illimitati" else gbLabel(it.left) } ?: "–"

    /** "di 150 · rinnovo tra 9 g": il totale e quando si rinnova, una riga corta. */
    fun simLine(s: SimStatus, today: LocalDate = LocalDate.now()): String? = listOfNotNull(
        s.data?.takeIf { !it.unlimited }?.total?.let { "di " + gbLabel(it).removeSuffix(" GB") },
        s.renewal?.let { day ->
            when (val d = ChronoUnit.DAYS.between(today, day)) {
                0L -> "rinnovo oggi"
                1L -> "rinnovo domani"
                else -> if (d > 0) "rinnovo tra $d g" else null
            }
        },
    ).joinToString(" · ").ifEmpty { null }
}

/** La card della SIM (dalla 1.447 da sola: niente più soldi nell'app). Il tocco apre la pagina della SIM. */
@Composable
internal fun ContiCard(onOpenSim: () -> Unit) {
    CardSection(title = "SIM") {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 6.dp)) {
            SimHalf(onOpenSim)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SimHalf(onOpen: () -> Unit) {
    val context = LocalContext.current
    SimStore.init(context)
    val status by SimStore.status.collectAsStateWithLifecycle()
    val linked by SimStore.linked.collectAsStateWithLifecycle()
    // Si aggiorna da sola quando la Home è aperta e il dato ha più di un'ora.
    AutoRefresh(everyMs = SimStore.STALE_MS) { if (linked && SimStore.isStale()) WindTreLink.refresh(context) }
    val s = status
    // Lo stato vero (1.402): vecchio dopo tre ore, la riga lo dice e il numero si sbiadisce.
    val link = LinkStatus.of(linked, expired = false, readAt = s?.at ?: 0, now = System.currentTimeMillis(), staleAfter = 3 * SimStore.STALE_MS)
    Half("SIM", Modifier.sharedItem(SharedKeys.SIM, rounded = true), onOpen) {
        when {
            s == null && linked -> HalfPlaceholder("Sto leggendo i dati di WindTre")
            s == null || !linked -> HalfValue("Collega", "giga e credito WindTre")
            else -> HalfValue(ContiText.giga(s), link.line ?: ContiText.simLine(s), faded = link.faded)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Half(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = rememberHaptic()
    Column(
        modifier
            .weight(1f)
            .bouncyPress(0.97f)
            .clickable { haptic(Haptic.TICK); onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        content()
    }
}

@Composable
private fun HalfValue(value: String, line: String?, warn: Boolean = false, faded: Boolean = false) {
    // Il dato vecchio resta, sbiadito (1.402): c'è, ma non è di adesso.
    androidx.compose.foundation.layout.Box(Modifier.then(if (faded) Modifier.alpha(FADED_ALPHA) else Modifier)) {
        RollingText(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
    line?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            // Arancio = attenzione (regola dei colori): l'accesso scaduto.
            color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HalfPlaceholder(description: String) {
    Column(
        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Placeholder(80.dp, 24.dp)
        Placeholder(110.dp, 10.dp)
    }
}
