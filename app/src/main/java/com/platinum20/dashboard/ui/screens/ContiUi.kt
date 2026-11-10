package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.bank.Pocket
import com.platinum20.dashboard.bank.RevolutPockets
import com.platinum20.dashboard.bank.RevolutStore
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.sim.WindTreLink
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** I testi della card Conti (1.404), puri per i test: brevi perché stiano in mezza card. */
object ContiText {
    /** "3.342 €": nella card i centesimi non servono, la pagina li ha. */
    fun euros(cents: Long): String =
        NumberFormat.getIntegerInstance(Locale.ITALIAN).format(Math.round(cents / 100.0)) + " €"

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

    /** "842 € da spendere": il conto principale; gli altri sono salvadanai. Con una tasca sola niente riga. */
    fun spendable(pockets: List<Pocket>): String? =
        pockets.takeIf { it.size > 1 }?.firstOrNull { it.main }?.let { euros(it.cents) + " da spendere" }
}

/**
 * SIM e Revolut in una card sola (punto 22): a sinistra i giga, a destra il saldo. Ogni metà apre la sua pagina,
 * come le due card di prima. Una metà spenta in Card della Home sparisce, e l'altra prende tutta la card.
 */
@Composable
internal fun ContiCard(showSim: Boolean, showBank: Boolean, onOpenSim: () -> Unit, onOpenBank: () -> Unit) {
    CardSection(title = "Conti") {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 6.dp)) {
            if (showSim) SimHalf(onOpenSim)
            if (showSim && showBank) VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (showBank) RevolutHalf(onOpenBank)
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
    Half("SIM", Modifier.sharedItem(SharedKeys.SIM, rounded = true), onOpen) {
        when {
            s == null && linked -> HalfPlaceholder("Sto leggendo i dati di WindTre")
            s == null || !linked -> HalfValue("Collega", "giga e credito WindTre")
            else -> HalfValue(ContiText.giga(s), ContiText.simLine(s))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RevolutHalf(onOpen: () -> Unit) {
    val context = LocalContext.current
    RevolutStore.init(context)
    val linked by RevolutStore.linked.collectAsStateWithLifecycle()
    val expired by RevolutStore.expired.collectAsStateWithLifecycle()
    val pockets by RevolutStore.pockets.collectAsStateWithLifecycle()
    val balance by RevolutStore.balance.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    // Prima che arrivi il wallet c'è almeno il saldo del conto (lettura delle versioni prima).
    val shown = pockets.ifEmpty { balance?.let { listOf(Pocket("main", "Conto", it, main = true)) } ?: emptyList() }
    Half(
        "Revolut",
        Modifier.sharedItem(SharedKeys.BANK, rounded = true),
        onClick = { if (!linked || expired) inApp?.openRoute?.invoke(Routes.REVOLUT) else onOpen() },
    ) {
        when {
            linked && !expired && shown.isEmpty() -> HalfPlaceholder("Sto leggendo il conto Revolut")
            !linked || shown.isEmpty() -> HalfValue(if (expired) "Rientra" else "Collega", if (expired) "accesso scaduto" else "saldo e tasche", warn = expired)
            else -> HalfValue(
                ContiText.euros(RevolutPockets.total(shown)),
                if (expired) "accesso scaduto" else ContiText.spendable(shown),
                warn = expired,
            )
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
private fun HalfValue(value: String, line: String?, warn: Boolean = false) {
    RollingText(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    line?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
