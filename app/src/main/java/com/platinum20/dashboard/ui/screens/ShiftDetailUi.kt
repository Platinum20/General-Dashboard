package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.shifts.PayMonth
import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftEarning
import com.platinum20.dashboard.shifts.ShiftParse
import com.platinum20.dashboard.shifts.ShiftPay
import com.platinum20.dashboard.shifts.ShiftStore

/** Il turno della scheda, dal suo id negativo nel calendario. */
@Composable
internal fun rememberShift(eventId: Long): Shift? {
    val shifts by ShiftStore.shifts.collectAsStateWithLifecycle()
    return shifts.firstOrNull { it.eventId == eventId }
}

/**
 * Nella card dell'orario, sotto inizio e fine: con quali creator e con chi (da solo, in doppio…).
 * Niente "Non si ripete" o "Nessun avviso": per un turno non dicono niente (Antonio).
 */
@Composable
internal fun ShiftWhoRows(shift: Shift) {
    val creators = ShiftParse.creators(shift.title)
    if (creators.isNotEmpty()) {
        RowDivider()
        // Una creator per riga: si leggono tutte intere senza andare a capo a metà nome.
        DetailRow(Icons.Outlined.Face, creators.joinToString("\n"), maxLines = Int.MAX_VALUE)
    }
    RowDivider()
    DetailRow(peopleIcon(shift.mates.size + 1), ShiftParse.company(shift.mates), maxLines = Int.MAX_VALUE)
}

/**
 * Com'è andato il turno e il mese, dalla busta paga: righe vere al posto del blocco di testo (Antonio).
 * Un turno ancora da fare non ha numeri: solo il mese.
 */
@Composable
internal fun ShiftPayCards(shift: Shift) {
    val pay by ShiftStore.pay.collectAsStateWithLifecycle()
    val months by ShiftStore.payMonths.collectAsStateWithLifecycle()
    val earning = ShiftPay.of(shift, pay)?.takeIf(ShiftPay::worked)
    val month = ShiftPay.monthOf(shift.start, months)
    earning?.let { ShiftEarningCard(it) }
    month?.let { ShiftMonthCard(it) }
}

@Composable
private fun ShiftEarningCard(e: ShiftEarning) {
    CanvasCard(Modifier.fillMaxWidth()) {
        PayHeadline(if (e.current) "Guadagnato finora" else "Guadagnato", ShiftPay.money(e.earned), ShiftPay.percent(e.percent))
        RowDivider()
        PayLine("Venduto", ShiftPay.money(e.sold))
        if (e.hours > 0) PayLine("Ore lavorate", ShiftPay.hours(e.hours))
    }
}

@Composable
private fun ShiftMonthCard(m: PayMonth) {
    CanvasCard(Modifier.fillMaxWidth()) {
        PayHeadline(ShiftPay.monthName(m), ShiftPay.money(m.total), if (m.shifts == 1) "1 turno" else "${m.shifts} turni")
        if (m.guaranteed > 0 && m.minShifts > 0) {
            RowDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Minimo garantito ${ShiftPay.money(m.guaranteed)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${minOf(m.shifts, m.minShifts)} su ${m.minShifts} turni", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                PayProgress(m.shifts.toFloat() / m.minShifts)
            }
        }
    }
}

/** La cifra grande con sopra cosa è e a destra il dettaglio (la percentuale, i turni). */
@Composable
private fun PayHeadline(label: String, value: String, side: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RollingText(value, style = MaterialTheme.typography.headlineSmall)
        }
        Text(side, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun PayLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** La barra verso il minimo garantito: piena quando i turni bastano. */
@Composable
private fun PayProgress(fraction: Float) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val f = fraction.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            if (f > 0f) drawRoundRect(fill, size = Size(size.width * f, size.height), cornerRadius = r)
        },
    )
}
