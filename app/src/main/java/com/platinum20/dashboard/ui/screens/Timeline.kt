package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.platinum20.dashboard.notes.ExpenseCategory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.calculateTargetValue
import com.platinum20.dashboard.capture.captureTarget
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.today.BriefKind

/**
 * Una riga della linea del tempo, uguale nella card "Prossimi giorni" di Oggi e in Note › Prossimi giorni:
 * a sinistra l'icona e il nome, a destra il giorno e poi la linea con il pallino, sotto il +.
 */
data class TimelineEntry(
    val key: String,
    val kind: BriefKind,
    val text: String,
    /** Il giorno scritto a destra, solo sulla prima riga di quel giorno. */
    val dayLabel: String?,
    /** Colore del calendario per gli eventi, 0 = quello normale. */
    val color: Int = 0,
    /** "Scaduti": il giorno in rosso. */
    val late: Boolean = false,
    /** Già passato: tutto più tenue. */
    val past: Boolean = false,
    /** L'icona a sinistra, se non è quella del tipo (le note). */
    val icon: ImageVector? = null,
    /** Sempre visibile dopo il nome, che si accorcia lui (l'importo delle spese). */
    val trailing: String? = null,
    /** Il mese che comincia con questa riga ("NOVEMBRE"): sopra si disegna il segno del mese. */
    val month: String? = null,
    /** Un promemoria fatto: spunta piena e nome barrato. */
    val done: Boolean = false,
    /** Quanto hai speso nel mese del segno ([month]), scritto a destra del nome (Antonio). */
    val monthSpend: String? = null,
    /** Il mese del segno: toccandolo si apre il riepilogo delle spese di quel mese. */
    val monthOf: java.time.YearMonth? = null,
    /** La miniatura al posto dell'icona: il video da guardare più tardi, la foto della nota. */
    val image: String? = null,
    /** La riga di Oggi: si disegna come il segno "OGGI" con accanto [trailing] (la spesa di oggi), non come una voce (Antonio). */
    val todayHeader: Boolean = false,
    /** La prima cosa dopo oggi nel mese in corso: sopra c'è il segno "PIÙ AVANTI" (Antonio). */
    val later: Boolean = false,
    /** Un evento con inizio e fine (treni, turni, impegni): la riga mostra inizio e fine uniti da una linea. */
    val trip: TripStops? = null,
    /** La prima riga di un giorno dopo un altro giorno: sopra c'è un sottile divisore (Antonio). */
    val divider: Boolean = false,
    /** L'ora, su una riga sua sotto il nome (Antonio): "09:30". Dal 1.423 sta nella colonna dell'icona. */
    val time: String? = null,
    /** L'importo delle spese, prima del nome in una colonna fissa allineata a destra (1.423, punto 9 B). */
    val amount: String? = null,
    /** Una riga piccola sotto il nome: età e quanto manca per le date ("compie 34 anni · tra 12 giorni", punto 23). */
    val detail: String? = null,
)

/**
 * Un viaggio in treno su una riga sola (Antonio): in alto l'ora e la stazione di partenza, in basso quelle di
 * arrivo, fra le due quanto dura e che treno è. [begin]/[end] servono per il pallino "sei qui" durante il viaggio.
 */
data class TripStops(
    val departAt: String,
    val from: String,
    val arriveAt: String,
    val to: String,
    val middle: String,
    val begin: Long,
    val end: Long,
    /** Fra inizio e fine, su quante righe servono: le creator di un turno. */
    val names: String? = null,
    /** Il nome su una riga sua sopra inizio e fine (Antonio); null per i treni, dove il nome è la stazione. */
    val title: String? = null,
    /** Un turno: quanti siamo, come icona fra la durata ([middle]) e con chi ([extra]: "In doppio con Marco"). */
    val people: Int? = null,
    val extra: String = "",
    /** Un turno: una seconda riga sotto, il venduto. */
    val below: String = "",
) {
    /**
     * Lo spazio (dp) fra inizio e fine: cresce col logaritmo della durata, come lo spazio fra i giorni
     * (Antonio). 15 min ≈ 22, 1 h = 30, 5 h ≈ 49, da 8 h in su 56: si vede quanto dura senza riempire lo schermo.
     */
    fun midDp(): Float {
        val hours = ((end - begin).coerceAtLeast(0) / 60_000L) / 60f
        val log2 = (kotlin.math.ln(1.0 + hours) / kotlin.math.ln(2.0)).toFloat()
        return (MID_MIN_DP + MID_STEP_DP * log2).coerceAtMost(MID_MAX_DP)
    }

    /**
     * L'altezza minima fra inizio e fine: quella della durata, ma con le creator in mezzo decide il testo,
     * che dice già quanto dura ("5 h"): niente buchi sopra e sotto (Antonio).
     */
    fun middleMinDp(): Float = if (names != null) 0f else midDp()

    /** Quanto del viaggio è fatto (0..1) adesso; null prima di partire e dopo l'arrivo. */
    fun progress(now: Long): Float? =
        if (end > begin && now in begin until end) (now - begin).toFloat() / (end - begin) else null

    companion object {
        const val MID_MIN_DP = 18f
        const val MID_STEP_DP = 12f
        const val MID_MAX_DP = 56f
    }
}

/** L'icona di una categoria di spesa: la spesa si riconosce a colpo d'occhio sulla linea. */
fun ExpenseCategory.icon(): ImageVector = when (this) {
    ExpenseCategory.FOOD -> Icons.Outlined.ShoppingCart
    ExpenseCategory.EATING_OUT -> Icons.Outlined.Restaurant
    ExpenseCategory.TRANSPORT -> Icons.Outlined.DirectionsCar
    ExpenseCategory.HOME -> Icons.Outlined.Home
    ExpenseCategory.BILLS -> Icons.Outlined.Bolt
    ExpenseCategory.SHOPPING -> Icons.Outlined.ShoppingBag
    ExpenseCategory.HEALTH -> Icons.Outlined.LocalHospital
    ExpenseCategory.FUN -> Icons.Outlined.SportsEsports
    ExpenseCategory.OTHER -> Icons.AutoMirrored.Outlined.ReceiptLong
}

/** Il colore di una categoria (ARGB), lo stesso per l'icona sulla linea e per la barra del riepilogo. */
fun ExpenseCategory.color(): Int = when (this) {
    ExpenseCategory.FOOD -> 0xFF43A047.toInt()
    ExpenseCategory.EATING_OUT -> 0xFFFB8C00.toInt()
    ExpenseCategory.TRANSPORT -> 0xFF1E88E5.toInt()
    ExpenseCategory.HOME -> 0xFF8E24AA.toInt()
    ExpenseCategory.BILLS -> 0xFFFDD835.toInt()
    ExpenseCategory.SHOPPING -> 0xFFD81B60.toInt()
    ExpenseCategory.HEALTH -> 0xFFE53935.toInt()
    ExpenseCategory.FUN -> 0xFF00ACC1.toInt()
    ExpenseCategory.OTHER -> 0xFF8D8D8D.toInt()
}

/** L'icona piccola di una riga: evento, promemoria, data, pacco o addebito. */
fun BriefKind.timelineIcon(): ImageVector = when (this) {
    BriefKind.EVENT -> Icons.Outlined.Event
    BriefKind.TASK -> Icons.Outlined.Checklist
    BriefKind.DATE -> Icons.Outlined.Celebration
    BriefKind.PARCEL -> Icons.Outlined.LocalShipping
    BriefKind.MONEY -> Icons.AutoMirrored.Outlined.ReceiptLong
    else -> Icons.Outlined.Event
}

/**
 * Una riga della linea del tempo. [top]/[bottom]: se la linea continua sopra e sotto il pallino.
 * [onIcon]: per i promemoria in Note, toccando il cerchio a sinistra si segna fatto.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    top: Boolean,
    bottom: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onIcon: (() -> Unit)? = null,
    /** 16 dp dentro una card (Oggi); 0 nelle pagine, dove la linea sta sullo sfondo senza card. */
    start: Dp = 16.dp,
    /** Tenendo premuto: di solito il fumetto di vetro con le azioni ([ActionBubble]). */
    onLongClick: (() -> Unit)? = null,
    /** Vibrare al cambio di mese: solo nelle pagine aperte, non nelle card di anteprima (Antonio). */
    monthTick: Boolean = true,
    /** Toccando il segno del mese: il riepilogo delle spese di quel mese. */
    onMonth: ((java.time.YearMonth) -> Unit)? = null,
    /** Il segno del mese è già stato disegnato sopra (sopra "OGGI"): qui non si ripete. */
    showMonth: Boolean = true,
    /** La lista in cui sta la riga ([SharedRows]): il titolo vola solo verso la scheda aperta da qui. */
    sharedScope: String = SharedRows.PAGE,
) {
    val haptic = rememberHaptic()
    val line = MaterialTheme.colorScheme.outlineVariant
    val headed = entry.dayLabel != null && !entry.todayHeader
    val monthShown = showMonth && entry.month != null
    val laterShown = entry.later && !monthShown
    // Sotto un segno (mese, PIÙ AVANTI) il giorno sta sulla riga del segno, non su una riga vuota sua (Antonio).
    // Dal 1.389 il giorno ha sempre la sua riga, col nome a sinistra e la linea fino al pallino (Antonio).
    val dayOnMarker = false
    val dayColor = dayLabelColor(entry.late, entry.past)
    if (monthShown) entry.month?.let {
        TimelineMonthMarker(it, start = start, top = top, tick = monthTick, total = entry.monthSpend,
            onClick = entry.monthOf?.let { m -> onMonth?.let { open -> { haptic(Haptic.TICK); open(m) } } },
            day = entry.dayLabel.takeIf { dayOnMarker }, dayColor = dayColor)
    }
    if (laterShown) TimelineMonthMarker("PIÙ AVANTI", start = start, top = top, tick = monthTick,
        day = entry.dayLabel.takeIf { dayOnMarker }, dayColor = dayColor)
    // Fra un giorno e l'altro un filo sottile: si capisce dove finisce un giorno. Dove c'è già un segno, no.
    // Il giorno su una riga sua, con il divisorio sulla stessa riga: "———— Ieri ●" (Antonio). Così la linea non
    // passa sopra il titolo della prima cosa del giorno. Dove c'è già un segno (mese, PIÙ AVANTI) niente filo.
    if (headed && !dayOnMarker) TimelineDayHeader(
        entry.dayLabel!!, start, line, top,
        rule = true,
        late = entry.late, past = entry.past,
    )
    if (entry.todayHeader) {
        TimelineNowMarker(modifier.padding(start = start), total = entry.trailing, onClick = { haptic(Haptic.TICK); onClick() })
        return
    }
    // La riga appena cambiata (spuntata, salvata, spostata) si accende un attimo nel suo nuovo posto.
    val flash = androidx.compose.runtime.remember(entry.key) { androidx.compose.animation.core.Animatable(0f) }
    val flashAt = RowFlash.marks[entry.key]
    androidx.compose.runtime.LaunchedEffect(flashAt) {
        if (flashAt == null || !RowFlash.fresh(entry.key)) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, androidx.compose.animation.core.tween(RowFlash.FADE_MS, delayMillis = RowFlash.HOLD_MS, easing = Motion.Standard))
    }
    // Spuntando, la riga resta dov'è un attimo già barrata: si vede cosa hai fatto prima che vada al suo posto.
    var ticking by androidx.compose.runtime.remember(entry.key, entry.done) { androidx.compose.runtime.mutableStateOf(false) }
    val pop = androidx.compose.runtime.remember(entry.key) { androidx.compose.animation.core.Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shownDone = entry.done != ticking
    val tick: (() -> Unit)? = onIcon?.let { act ->
        {
            if (!ticking) {
                ticking = true
                haptic(Haptic.CONFIRM)
                scope.launch {
                    pop.animateTo(1.3f, androidx.compose.animation.core.tween(Motion.EXIT_MS / 2, easing = Motion.Decelerate))
                    pop.animateTo(1f, Motion.lively())
                }
                RowFlash.later(entry.key, act)
            }
        }
    }
    val flashColor = MaterialTheme.colorScheme.primary
    val held = ActionBubble.heldBy(entry.key)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // Una riga appena comparsa è il posto dove atterra il pallino del +, se è a schermo.
            .then(if (flashAt != null) Modifier.captureTarget(RowFlash.target(entry.key)) else Modifier)
            .drawBehind {
                val f = maxOf(flash.value, if (held) 1f else 0f)
                if (f > 0f) drawRoundRect(
                    flashColor.copy(alpha = 0.14f * f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                )
            }
            .bouncyPress(0.98f)
            .combinedClickable(
                onClick = { SharedRows.origin = sharedScope; onClick() },
                onLongClick = onLongClick?.let { long ->
                    {
                        SharedRows.origin = sharedScope
                        haptic(Haptic.LONG_PRESS)
                        long()
                        // Finché il fumetto è aperto, questa riga resta accesa: si vede di chi sono le azioni.
                        ActionBubble.source = entry.key
                    }
                },
            )
            .padding(start = start, end = LINE_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = entry.color.takeIf { it != 0 }?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
        if (entry.trip != null) {
            TripBlock(entry.trip, tint, entry.past, Modifier.weight(1f), Modifier.sharedItem(SharedRows.key(sharedScope, entry.key)))
        } else {
        if (onIcon != null || entry.done) {
            Icon(
                if (shownDone) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (shownDone) "Da fare" else "Fatto",
                tint = if (shownDone) MaterialTheme.colorScheme.primary else tint,
                modifier = Modifier.size(20.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape).then(if (tick != null) Modifier.clickable(onClick = tick) else Modifier),
            )
            Spacer(Modifier.width(8.dp))
        } else if (entry.image != null) {
            // La miniatura (video, foto) al posto dell'icona: si riconosce subito.
            coil3.compose.AsyncImage(
                model = entry.image.let { if (it.startsWith("/")) java.io.File(it) else it },
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.padding(vertical = 3.dp).width(40.dp).height(24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
        } else if (entry.time != null) {
            // Le cose con un orario hanno l'ora nella colonna dell'icona (1.423): una colonna sola a sinistra.
            Text(
                entry.time,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 26.dp),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(entry.icon ?: entry.kind.timelineIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        entry.amount?.let {
            // Importo prima del nome, in colonna: le cifre si confrontano a colpo d'occhio (Antonio, 9 B).
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = if (entry.past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(min = AMOUNT_COLUMN),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(
                entry.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.past || shownDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (shownDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Lo stesso titolo della scheda: toccando la riga vola lì ([sharedItem]).
                modifier = Modifier.sharedItem(SharedRows.key(sharedScope, entry.key)),
            )
            entry.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        entry.trailing?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        }
        Spacer(Modifier.width(8.dp))
        // Il giorno e il suo pallino grande stanno nella riga sopra ([TimelineDayHeader]).
        Spacer(Modifier.width(4.dp))
        TimelineGutter(
            Modifier.fillMaxHeight(),
            line,
            dot = MaterialTheme.colorScheme.outline to 4.dp,
            top = top || headed,
            bottom = bottom,
            dotY = if (entry.trip != null) TRIP_PAD + TRIP_LINE / 2 else null,
        )
    }
}

private val TRIP_LINE = 26.dp

/** Larga quanto "123,45 €": gli importi fino a mille stanno tutti nella stessa colonna. */
private val AMOUNT_COLUMN = 64.dp
private val TRIP_PAD = 4.dp

/**
 * Il viaggio: a sinistra la linea dalla partenza (cerchio vuoto) all'arrivo (pallino pieno), col pallino
 * "sei qui" mentre sei in treno; a destra ore e stazioni, e in mezzo durata e treno.
 * Gli altri eventi hanno il nome su una riga sua sopra ([TripStops.title]), poi solo gli orari (Antonio).
 */
@Composable
internal fun TripBlock(trip: TripStops, tint: Color, past: Boolean, modifier: Modifier, title: Modifier = Modifier) {
    val text = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val here = MaterialTheme.colorScheme.primary
    val progress = trip.progress(System.currentTimeMillis())
    Column(modifier.padding(vertical = TRIP_PAD)) {
        trip.title?.let {
            Box(Modifier.height(TRIP_LINE), contentAlignment = Alignment.CenterStart) {
                // Il nome dell'evento: toccando la riga vola sul titolo della scheda ([sharedItem]).
                Text(it, style = MaterialTheme.typography.bodyMedium, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = title)
            }
        }
        Row(Modifier.height(IntrinsicSize.Min)) {
            // La linea va dall'inizio alla fine anche quando in mezzo ci sono più righe (le creator).
            Box(
                Modifier.width(16.dp).fillMaxHeight().heightIn(min = TRIP_LINE * 2 + trip.middleMinDp().dp).drawBehind {
                    val x = size.width / 2
                    val top = TRIP_LINE.toPx() / 2
                    val bottom = size.height - TRIP_LINE.toPx() / 2
                    val r = 4.dp.toPx()
                    val stroke = 2.dp.toPx()
                    drawLine(tint.copy(alpha = 0.55f), Offset(x, top + r), Offset(x, bottom - r), stroke)
                    drawCircle(tint, r - stroke / 2, Offset(x, top), style = Stroke(stroke))
                    drawCircle(tint, r, Offset(x, bottom))
                    progress?.let { drawCircle(here, 4.5.dp.toPx(), Offset(x, top + (bottom - top) * it)) }
                },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                TripStop(trip.departAt, trip.from, text, if (trip.title == null) title else Modifier)
                // In mezzo tutto allineato agli orari: le creator e poi durata e guadagno. L'altezza è quella
                // della durata, o di più se il testo non ci sta: niente spazi vuoti con il testo che galleggia.
                Box(Modifier.fillMaxWidth().heightIn(min = trip.middleMinDp().dp), contentAlignment = Alignment.CenterStart) {
                    Column(Modifier.padding(vertical = if (trip.names != null) 4.dp else 0.dp)) {
                        // Una riga sola, anche con tre creator: tutti i turni alti uguali (Antonio).
                        trip.names?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (trip.people != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(trip.middle + " · ", style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1)
                            Icon(peopleIcon(trip.people), contentDescription = null, tint = sub, modifier = Modifier.size(14.dp))
                            if (trip.extra.isNotEmpty()) Text(
                                " " + trip.extra,
                                style = MaterialTheme.typography.labelMedium,
                                color = sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (trip.middle.isNotEmpty()) Text(
                            trip.middle,
                            style = MaterialTheme.typography.labelMedium,
                            color = sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Il venduto su una riga sua, sotto: le due righe insieme restano al centro fra inizio e fine.
                        if (trip.below.isNotEmpty()) Text(
                            trip.below,
                            style = MaterialTheme.typography.labelMedium,
                            color = sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TripStop(trip.arriveAt, trip.to, text)
            }
        }
    }
}

/** Una persona, due o un gruppo: da solo, in doppio, in tre o più. */
internal fun peopleIcon(people: Int) = when {
    people <= 1 -> Icons.Outlined.Person
    people == 2 -> Icons.Outlined.People
    else -> Icons.Outlined.Groups
}

/** Per TalkBack, al posto dell'icona. */
internal fun peopleLabel(people: Int) = when {
    people <= 1 -> "Da solo"
    people == 2 -> "In doppio"
    else -> "In $people"
}

@Composable
private fun TripStop(time: String, station: String, color: Color, title: Modifier = Modifier) {
    Row(Modifier.height(TRIP_LINE), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1)
        if (station.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(station, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = title)
        }
    }
}

private val GUTTER = 22.dp

/** La linea del mese: più spessa di quella dei giorni (1 dp). */
private val MONTH_RULE = 3.dp

/**
 * Il margine destro dopo la linea: con la card a 16 dp dal bordo, il centro della linea cade a 46 dp
 * dal bordo dello schermo, cioè sul centro del + rotondo in basso a destra (60 dp, a 16 dp dal bordo).
 */
private val LINE_END = 19.dp

/** Il colore del nome del giorno: rosso se in ritardo, tenue se passato, altrimenti inchiostro (blu solo OGGI). */
@Composable
private fun dayLabelColor(late: Boolean, past: Boolean): Color = when {
    late -> MaterialTheme.colorScheme.error
    past -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

/**
 * L'intestazione di un giorno: a sinistra il nome, poi la linea che arriva fino al pallino grande sulla linea
 * del tempo, come OGGI e i mesi: separa dal giorno prima.
 */
@Composable
private fun TimelineDayHeader(label: String, start: Dp, line: Color, top: Boolean, rule: Boolean, late: Boolean, past: Boolean) {
    val color = dayLabelColor(late, past)
    val dot = when {
        past -> MaterialTheme.colorScheme.outline
        late -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().height(26.dp).padding(start = start, end = LINE_END).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Il nome tutto a sinistra e la linea che arriva fino al pallino: "Domani ——————— ●" (Antonio).
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).drawBehind { if (rule) drawRect(line) })
        TimelineGutter(Modifier.fillMaxHeight(), line, dot = dot to 7.dp, top = top, bottom = true, joinLeft = rule)
    }
}

/** Il pezzo di linea di una riga: sopra e/o sotto il centro, con il pallino (colore e grandezza) se c'è. */
@Composable
private fun TimelineGutter(modifier: Modifier, line: Color, dot: Pair<Color, Dp>?, top: Boolean, bottom: Boolean, dotY: Dp? = null, joinLeft: Boolean = false) {
    Box(
        modifier.width(GUTTER).drawBehind {
            val x = size.width / 2
            val mid = dotY?.toPx() ?: (size.height / 2)
            val stroke = 2.dp.toPx()
            // La linea del giorno entra fino al pallino.
            if (joinLeft) drawLine(line, Offset(0f, mid), Offset(x, mid), 1.dp.toPx())
            if (top) drawLine(line, Offset(x, 0f), Offset(x, mid), stroke)
            if (bottom) drawLine(line, Offset(x, mid), Offset(x, size.height), stroke)
            dot?.let { (color, r) -> drawCircle(color, r.toPx() / 2, Offset(x, mid)) }
        },
    )
}

/**
 * Il confine fra passato e adesso nelle pagine con il passato sopra: "Oggi" ben visibile e una riga che
 * arriva fino alla linea, con il pallino grande. Lì la lista fa un po' di resistenza (vedi [PastBarrier]).
 */
@Composable
fun TimelineNowMarker(
    modifier: Modifier = Modifier,
    /** La spesa di oggi, a destra prima della linea, come il totale accanto al mese (Antonio). */
    total: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    // Arrivando su OGGI, oltre alla vibrazione, "OGGI" pulsa e una freccetta in su dice che il passato sta sopra.
    val pulse = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val tick = NowPulse.ticks.intValue
    androidx.compose.runtime.LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        pulse.animateTo(1f, androidx.compose.animation.core.tween(Motion.EXIT_MS, easing = Motion.Decelerate))
        pulse.animateTo(0f, androidx.compose.animation.core.tween(Motion.ENTER_MS * 2, easing = Motion.Standard))
    }
    Row(
        (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).then(modifier).fillMaxWidth().height(32.dp).padding(end = LINE_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "OGGI",
            style = MaterialTheme.typography.labelMedium,
            color = primary,
            modifier = Modifier.graphicsLayer {
                val k = 1f + 0.15f * pulse.value
                scaleX = k
                scaleY = k
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
        Icon(
            Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.padding(start = 4.dp).size(14.dp).graphicsLayer {
                alpha = pulse.value
                translationY = (1f - pulse.value) * 4.dp.toPx()
            },
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.weight(1f).height(2.dp).drawBehind {
                drawLine(primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            },
        )
        total?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = primary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                drawLine(primary, Offset(0f, size.height / 2), Offset(size.width / 2, size.height / 2), 2.dp.toPx())
                drawCircle(primary, 6.dp.toPx() * (1f + 0.4f * pulse.value), Offset(size.width / 2, size.height / 2))
            },
        )
    }
}

/**
 * Il cambio di mese, come "OGGI" ma più tenue: il nome del mese, una riga fino alla linea e un pallino.
 * Scorrendoci sopra vibra appena (Antonio), così si sente quando si passa da un mese all'altro.
 */
@Composable
fun TimelineMonthMarker(
    label: String,
    start: Dp = 16.dp,
    top: Boolean = true,
    tick: Boolean = true,
    /** La spesa del mese, a destra prima della linea (Antonio). */
    total: String? = null,
    onClick: (() -> Unit)? = null,
    /** Il giorno della prima riga sotto il segno, a destra prima del pallino (Antonio). */
    day: String? = null,
    dayColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    val month = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().height(30.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = start, end = LINE_END).then(if (tick) Modifier.monthTick() else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Il mese pesa più dei giorni: scritta in grassetto e linea più spessa (Antonio).
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(4.dp).drawBehind {
                drawLine(month, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), MONTH_RULE.toPx())
            },
        )
        total?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        day?.let {
            Spacer(Modifier.width(if (total != null) 6.dp else 10.dp))
            if (total != null) Text("·", style = MaterialTheme.typography.labelLarge, color = color)
            if (total != null) Spacer(Modifier.width(6.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = dayColor, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                val x = size.width / 2
                val mid = size.height / 2
                if (top) drawLine(line, Offset(x, 0f), Offset(x, mid), 2.dp.toPx())
                drawLine(line, Offset(x, mid), Offset(x, size.height), 2.dp.toPx())
                drawLine(month, Offset(0f, mid), Offset(x, mid), MONTH_RULE.toPx())
                drawCircle(color, 4.5.dp.toPx(), Offset(x, mid))
            },
        )
    }
}

/** Quando un segno del mese attraversa la riga immaginaria a [MonthCross.LINE] dello schermo. Logica pura. */
object MonthCross {
    /** Dove sta la riga: un terzo dall'alto, sotto la barra del titolo, dove l'occhio legge. */
    const val LINE = 0.3f

    /** [previous] null = appena comparso: niente vibrazione all'apertura. */
    fun crossed(previous: Float?, now: Float, line: Float): Boolean =
        previous != null && (previous < line) != (now < line)
}

/** Una vibrazione leggera quando questo segno passa la riga di [MonthCross], in su o in giù. */
@Composable
private fun Modifier.monthTick(): Modifier {
    val haptic = rememberHaptic()
    val last = androidx.compose.runtime.remember { floatArrayOf(Float.NaN) }
    return onGloballyPositioned { c ->
        val y = c.positionInWindow().y + c.size.height / 2f
        val line = c.findRootCoordinates().size.height * MonthCross.LINE
        if (MonthCross.crossed(last[0].takeIf { !it.isNaN() }, y, line)) haptic(Haptic.TICK)
        last[0] = y
    }
}

/**
 * La piccola resistenza fra adesso e passato (Antonio): scorrendo verso il passato la lista si ferma sul
 * confine "Oggi" con una vibrazione leggera; se il dito insiste per [threshold] px si passa, con un'altra
 * vibrazione. Un lancio (fling) si ferma sul confine. Logica pura, per i test.
 */
object PastBarrier {
    /** [consumed] = quanto scorrimento trattiene la barriera; [bump]/[through] = quando vibrare. */
    data class Step(val consumed: Float, val pull: Float, val unlocked: Boolean, val bump: Boolean = false, val through: Boolean = false)

    /**
     * [boundary]: dove sta il confine rispetto al bordo alto della lista (px; 0 = proprio in cima, negativo = già
     * passato sopra, positivo = si sta guardando il passato). [delta] > 0 = si scorre verso il passato.
     */
    fun step(boundary: Float?, delta: Float, pull: Float, unlocked: Boolean, threshold: Float, fling: Boolean): Step {
        if (boundary == null) return Step(0f, pull, unlocked)
        // Già nel passato: niente resistenza finché non si torna ad adesso.
        if (boundary > 1f) return Step(0f, 0f, true)
        // Tornati ben dentro adesso: la resistenza si riarma.
        val armed = if (boundary < -threshold) false else unlocked
        if (delta <= 0f || armed) return Step(0f, if (delta <= 0f) 0f else pull, armed)
        val room = -boundary
        if (delta <= room) return Step(0f, pull, false)
        val excess = delta - room
        if (fling) return Step(excess, pull, false)
        val total = pull + excess
        if (total >= threshold) return Step(0f, 0f, true, through = true)
        return Step(excess, total, false, bump = pull == 0f)
    }
}

/**
 * Le righe da accendere un attimo: quella appena spuntata, salvata o spostata. Chi cambia una cosa
 * chiama [mark] con la chiave della sua riga; la riga, dove si trova adesso, si illumina e si spegne.
 */
object RowFlash {
    /** Quanto resta accesa prima di spegnersi, e quanto dura lo spegnimento. */
    const val HOLD_MS = 500
    const val FADE_MS = 900

    /** Quanto la riga spuntata resta al suo posto, barrata, prima di andare tra le cose fatte. */
    const val TICK_MS = 650L

    /** Una riga che compare entro questo tempo si accende ancora (per esempio scorrendo fino a lei). */
    private const val FRESH_MS = 3_000L

    val marks = androidx.compose.runtime.mutableStateMapOf<String, Long>()

    // Fuori dalla composizione: se la riga sparisce dallo schermo intanto, la spunta arriva comunque.
    private val scope by lazy { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate) }

    fun mark(key: String, now: Long = android.os.SystemClock.uptimeMillis()) {
        marks[key] = now
    }

    fun fresh(key: String, now: Long = android.os.SystemClock.uptimeMillis()): Boolean = isFresh(marks[key], now)

    /** Logica pura: una riga segnata da poco. */
    fun isFresh(markedAt: Long?, now: Long): Boolean = markedAt != null && now - markedAt in 0..FRESH_MS

    /** La riga segnata più di recente, se è ancora fresca: dove deve atterrare il pallino del +. */
    fun freshest(now: Long = android.os.SystemClock.uptimeMillis()): String? =
        marks.entries.filter { isFresh(it.value, now) }.maxByOrNull { it.value }?.key

    /** La chiave con cui una riga si offre come arrivo del pallino ([com.platinum20.dashboard.capture.CaptureTargets]). */
    fun target(key: String) = "row:$key"

    /**
     * Le righe nuove fra un elenco e il successivo. Al primo elenco niente (la pagina si sta aprendo), e
     * nemmeno quando ne arrivano tante insieme (si è caricato un altro pezzo di calendario): solo le poche
     * cose appena aggiunte.
     */
    fun newKeys(previous: Set<String>?, now: List<String>, armed: Boolean = true): List<String> {
        // Solo subito dopo un salvataggio col +: eventi del telefono, movimenti della banca e righe che
        // entrano nella card perché un'altra è stata spuntata arrivano da soli e non devono accendersi.
        if (previous == null || !armed) return emptyList()
        val added = now.filter { it !in previous }
        return if (added.size > MAX_NEW) emptyList() else added
    }

    private const val MAX_NEW = 3

    /** Quando è stato salvato qualcosa col + l'ultima volta (vedi [arm]). */
    @Volatile private var armedAt = Long.MIN_VALUE / 2

    /** Per quanto dopo un salvataggio una riga nuova si accende. */
    private const val ARMED_MS = 4_000L

    /** Un salvataggio appena fatto: la prossima riga nuova del calendario è la sua. */
    fun arm(now: Long = android.os.SystemClock.uptimeMillis()) {
        armedAt = now
    }

    fun armed(now: Long = android.os.SystemClock.uptimeMillis()): Boolean = isArmed(armedAt, now)

    /** Logica pura: c'è stato un salvataggio da poco. */
    fun isArmed(at: Long, now: Long): Boolean = now - at in 0..ARMED_MS

    /** Esegue [action] dopo [TICK_MS] e poi accende la riga nel suo nuovo posto. */
    fun later(key: String, action: () -> Unit) {
        scope.launch {
            kotlinx.coroutines.delay(TICK_MS)
            action()
            mark(key)
        }
    }
}

/** Le righe che hanno una scheda con il titolo condiviso (vedi [SharedKeys.item]). */
internal fun String.isSharedRow(): Boolean =
    startsWith("task-") || startsWith("date-") || startsWith("parcel-") || startsWith("expense-")

/** Accende le righe che compaiono in [keys] dopo la prima volta (vedi [RowFlash.newKeys]). */
@Composable
fun FlashNewRows(keys: List<String>, vararg resetOn: Any?) {
    val previous = androidx.compose.runtime.remember(*resetOn) { arrayOfNulls<Set<String>>(1) }
    androidx.compose.runtime.LaunchedEffect(keys) {
        RowFlash.newKeys(previous[0], keys, armed = RowFlash.armed()).forEach { RowFlash.mark(it) }
        previous[0] = keys.toSet()
    }
}

/**
 * Il lancio verso il passato che arriverebbe oltre OGGI: invece di fermarsi di colpo contro il confine,
 * la lista frena fino a posarsi su OGGI. Restituisce quanti px scorrere (verso il passato) o null se il
 * lancio non arriva al confine, è già sbloccato o va verso il futuro. Logica pura, per i test.
 */
internal fun softStopAtNow(boundary: Float?, velocity: Float, naturalDistance: Float, unlocked: Boolean): Float? {
    if (boundary == null || unlocked || velocity <= 0f) return null
    val room = -boundary
    if (room <= 1f || naturalDistance <= room) return null
    return room
}

/** Quanto dura la frenata su OGGI: come una decelerazione costante dalla velocità del lancio. */
internal fun softStopMs(distance: Float, velocity: Float): Int =
    if (velocity <= 0f) 400 else (2f * distance / velocity * 1000f).toInt().coerceIn(180, 600)

/** Ogni volta che una lista arriva su OGGI: i [TimelineNowMarker] a schermo pulsano. */
object NowPulse {
    val ticks = androidx.compose.runtime.mutableIntStateOf(0)
    fun fire() { ticks.intValue++ }
}

/** La [PastBarrier] collegata a una lista: [itemIndex] è l'item con la linea, [offset] dove sta "Oggi" dentro l'item. */
@Composable
fun rememberPastBarrier(listState: androidx.compose.foundation.lazy.LazyListState, itemIndex: Int, offset: Int?): androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    val haptic = rememberHaptic()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }
    val decay = androidx.compose.animation.rememberSplineBasedDecay<Float>()
    val index by androidx.compose.runtime.rememberUpdatedState(itemIndex)
    val at by androidx.compose.runtime.rememberUpdatedState(offset)
    return androidx.compose.runtime.remember(listState, haptic, threshold, decay) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var pull = 0f
            var unlocked = false
            var flingStopped = false
            fun boundary(): Float? {
                val o = at ?: return null
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                return when {
                    item != null -> (item.offset + o).toFloat()
                    listState.firstVisibleItemIndex > index -> -1e9f
                    else -> 1e9f
                }
            }
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                val s = PastBarrier.step(
                    boundary(), available.y, pull, unlocked, threshold,
                    fling = source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
                )
                pull = s.pull
                unlocked = s.unlocked
                if (s.bump) { haptic(Haptic.TICK); NowPulse.fire() }
                // Un lancio che si ferma su OGGI: niente vibrazione, ma OGGI pulsa una volta.
                if (s.consumed > 0f && source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && !flingStopped) {
                    flingStopped = true
                    NowPulse.fire()
                }
                if (s.through) haptic(Haptic.THRESHOLD)
                return Offset(0f, s.consumed)
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                pull = 0f
                flingStopped = false
                val v = available.y
                val stop = softStopAtNow(boundary(), v, decay.calculateTargetValue(0f, v), unlocked)
                    ?: return androidx.compose.ui.unit.Velocity.Zero
                // Frena fino a OGGI invece di sbatterci contro: il lancio lo prendiamo tutto noi.
                listState.animateScrollBy(-stop, androidx.compose.animation.core.tween(softStopMs(stop, v), easing = Motion.Decelerate))
                NowPulse.fire()
                return available
            }
        }
    }
}
