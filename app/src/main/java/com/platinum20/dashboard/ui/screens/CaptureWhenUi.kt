package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.CaptureWhen
import com.platinum20.dashboard.capture.VoiceCapture
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * "Calendario" nel "+" senza un giorno (1.536, Antonio): il fumetto di vetro chiede quando, prima il giorno poi l'ora,
 * con il testo già scritto. "Altro giorno"/"Altra ora" aprono i selettori di vetro di Pickers.kt, disegnati da
 * [CaptureWhenPickers]. Portando il fumetto di lato non si salva niente.
 */
internal object CaptureWhenAsk {
    /** Il selettore aperto adesso: testo, giorno già scelto (null = si sceglie il giorno). */
    internal data class Picking(val text: String, val day: LocalDate?)

    var picking by mutableStateOf<Picking?>(null)

    fun askDay(context: Context, scope: CoroutineScope, text: String) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val chips = CaptureWhen.days(today).take(3).map { day ->
            BubbleChip(CaptureWhen.dayLabel(day, today)) { askTime(context, scope, text, day) }
        } + BubbleChip("Scegli") { picking = Picking(text, null) }
        ActionBubble.show(BubbleHead("Quando?", text.trim()), choice("", *chips.toTypedArray()))
    }

    fun askTime(context: Context, scope: CoroutineScope, text: String, day: LocalDate) {
        val now = LocalDateTime.now()
        val chips = listOf(BubbleChip("Tutto il giorno") { save(context, scope, text, day, null) }) +
            CaptureWhen.hours(day, now).map { t -> BubbleChip(CaptureWhen.hourLabel(t)) { save(context, scope, text, day, t) } } +
            BubbleChip("Altra ora") { picking = Picking(text, day) }
        ActionBubble.show(BubbleHead("A che ora?", CaptureWhen.dayLabel(day, now.toLocalDate())), choice("", *chips.toTypedArray()))
    }

    fun save(context: Context, scope: CoroutineScope, text: String, day: LocalDate, time: LocalTime?) {
        scope.launch {
            when (val outcome = VoiceCapture.saveAt(context, text, day, time)) {
                is VoiceCapture.Outcome.Saved -> {
                    AppSettings.countCaptureSave()
                    CaptureFeedback.show(CaptureNotice(outcome.mode, outcome.message, outcome.undo))
                }
                else -> CaptureFeedback.tell(context, "Non sono riuscito a salvarlo", Icons.Outlined.CalendarMonth)
            }
        }
    }
}

/** I selettori di "Altro giorno" e "Altra ora", quando servono. */
@Composable
internal fun CaptureWhenPickers() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picking = CaptureWhenAsk.picking ?: return
    val day = picking.day
    if (day == null) {
        DatePickDialog(
            initial = LocalDate.now(),
            onDismiss = { CaptureWhenAsk.picking = null },
            onConfirm = { CaptureWhenAsk.picking = null; CaptureWhenAsk.askTime(context, scope, picking.text, it) },
        )
    } else {
        TimePickDialog(
            initial = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0),
            title = "Alle",
            onDismiss = { CaptureWhenAsk.picking = null },
            onConfirm = { CaptureWhenAsk.picking = null; CaptureWhenAsk.save(context, scope, picking.text, day, it) },
        )
    }
}
