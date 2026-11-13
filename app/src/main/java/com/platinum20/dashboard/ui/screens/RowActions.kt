package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarPermission
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tenendo premuta una riga del calendario: il fumetto di vetro con le sue azioni. Uguale nella card
 * della Home e nella pagina intera, così tenere premuto vuol dire sempre la stessa cosa.
 */
@Composable
internal fun rememberRowActions(onOpenEvent: ((CalendarEvent) -> Unit)? = null): (DueRow) -> Unit {
    val context = LocalContext.current
    val openItem = rememberOpenItem()
    val haptic = rememberHaptic()
    val inApp = LocalInAppNav.current
    val scope = rememberCoroutineScope()
    // Senza un'apertura data (card della Home, Per dopo) l'evento si apre come toccandolo.
    val openEvent by rememberUpdatedState(onOpenEvent ?: { e: CalendarEvent -> inApp?.openEvent?.invoke(e); Unit })
    fun offerUndo(message: String, restore: () -> Unit) {
        CaptureFeedback.show(
            CaptureNotice(CaptureMode.NOTE, message, undo = { restore() }, icon = Icons.Outlined.Delete, fly = false),
        )
    }
    fun offerUndo(removed: List<TaskItem>, message: String) {
        if (removed.isNotEmpty()) offerUndo(message) { NotesStore.restoreTasks(removed) }
    }
    fun share(text: String) = shareText(context, text)
    fun taskActions(t: TaskItem): List<BubbleAction> = listOfNotNull(
        if (!t.tickable) null else BubbleAction(Icons.Outlined.Check, if (t.done) "Segna da fare" else "Fatto", detail = t.text) {
            haptic(Haptic.CONFIRM)
            NotesStore.toggleTask(t.id)
        },
        snoozeAction(t),
        BubbleAction(Icons.Outlined.Share, "Condividi") { share(t.text) },
        BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
            NotesStore.deleteTask(t.id)
            offerUndo(listOf(t), "Promemoria eliminato")
        },
    )
    /**
     * Le stesse azioni della scheda dell'evento (Biglietto, zaino, Condividi, Modifica, Elimina), senza entrarci.
     * Se il calendario non si può scrivere (o manca il permesso) Modifica ed Elimina aprono la scheda, che lo chiede.
     */
    fun eventActions(e: CalendarEvent): List<BubbleAction> {
        val tickets = if (e.eventId > 0) com.platinum20.dashboard.tickets.TicketQrStore.cached(context, e.eventId) else emptyList()
        val pack = com.platinum20.dashboard.trips.TrainPackStore.files.value.filter { it.eventId == e.eventId }
        fun withWritable(action: () -> Unit) {
            scope.launch {
                val detail = CalendarRepository.event(context, e.eventId)
                if (detail?.writable == true && CalendarRepository.canWrite(context)) action() else openEvent(e)
            }
        }
        return listOfNotNull(
            BubbleAction(Icons.Outlined.Event, "Apri", detail = e.title) { openEvent(e) },
            tickets.takeIf { it.isNotEmpty() }?.let {
                BubbleAction(Icons.Outlined.QrCode2, "Biglietto") {
                    runCatching { context.startActivity(com.platinum20.dashboard.tickets.TicketActivity.intent(context, e.eventId)) }
                }
            },
            pack.takeIf { it.isNotEmpty() }?.let { files ->
                BubbleAction(
                    Icons.Outlined.Luggage, "Ascolta lo zaino",
                    detail = com.platinum20.dashboard.trips.TrainPack.readyLine(files.sumOf { it.sec }).orEmpty(),
                ) { com.platinum20.dashboard.media.CanvasPlayer.playList(context, files.map { it.item }) }
            },
            BubbleAction(Icons.Outlined.Share, "Condividi") { share(eventShareText(e)) },
            // I turni e le cose finte del calendario (id negativi) non si cambiano da qui.
            e.takeIf { it.eventId > 0 }?.let {
                BubbleAction(Icons.Outlined.Edit, "Modifica") { withWritable { inApp?.openRoute?.invoke(Routes.calendarEdit(e.eventId)) } }
            },
            e.takeIf { it.eventId > 0 }?.let {
                BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                    withWritable {
                        // Non si può annullare: si conferma nel solito fumetto, come nella scheda.
                        ActionBubble.show(
                            BubbleAction(
                                Icons.Outlined.Delete, "Elimina l'evento",
                                detail = "Sparisce anche dal calendario del telefono", danger = true,
                            ) {
                                scope.launch {
                                    if (CalendarRepository.delete(context, e.eventId)) haptic(Haptic.CONFIRM)
                                    else CaptureFeedback.tell(context, CalendarPermission.DELETE_FAILED, Icons.Outlined.Delete)
                                }
                            },
                        )
                    }
                }
            },
        )
    }
    fun dueActions(row: DueRow) {
        val actions = when {
            row.task != null -> taskActions(row.task)
            row.event != null -> eventActions(row.event)
            row.parcel != null -> listOfNotNull(
                BubbleAction(Icons.Outlined.LocalShipping, "Apri", detail = row.parcel.title) { openItem(ItemKind.PARCEL, row.parcel.id) },
                // Come nell'"Altro" della scheda del pacco: il corriere dentro Canvas, e condividi.
                row.parcel.takeIf { !it.delivered }?.trackingUrl()?.let { url ->
                    BubbleAction(Icons.Outlined.LocalShipping, "Traccia la spedizione", detail = row.parcel.carrier.label) {
                        inApp?.openWeb?.invoke(url, row.parcel.carrier.label)
                    }
                },
                BubbleAction(Icons.Outlined.Share, "Condividi") { share(row.parcel.title) },
                BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                    NotesStore.deleteParcel(row.parcel.id)
                    offerUndo("Pacco eliminato") { NotesStore.restoreParcel(row.parcel) }
                },
            )
            else -> emptyList()
        }
        if (actions.isNotEmpty()) ActionBubble.show(*actions.toTypedArray())
    }
    fun noteActions(n: Note) {
        ActionBubble.show(
            BubbleAction(
                if (n.pinned) Icons.Filled.PushPinFilled else Icons.Outlined.PushPin,
                if (n.pinned) "Togli da in alto" else "Fissa in alto",
                detail = n.title,
            ) { NotesStore.togglePin(n.id) },
            BubbleAction(Icons.Outlined.Share, "Condividi") { share(n.text) },
            BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                NotesStore.deleteNote(n.id)
                offerUndo("Nota eliminata") { NotesStore.restoreNote(n) }
            },
        )
    }
    return { row ->
        when {
            row.note != null -> noteActions(row.note)
            else -> dueActions(row)
        }
    }
}

/** Manda un testo con il foglio di condivisione del telefono. */
internal fun shareText(context: android.content.Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, text)
    context.startActivity(android.content.Intent.createChooser(send, null))
}

/** Come si condivide un evento: il nome e il giorno ("Cena · Domani"). */
internal fun eventShareText(e: CalendarEvent): String = eventShareText(e.title, e.begin)

internal fun eventShareText(title: String, begin: Long): String =
    title + " · " + com.platinum20.dashboard.today.Upcoming.dayLabel(
        java.time.Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now(),
    )

/** "Rimanda a domani" per una cosa con data non ancora fatta: stessa ora, domani. Uguale nel fumetto e nella scheda. */
internal fun snoozeAction(t: TaskItem): BubbleAction? = t.dueAt?.takeIf { !t.done }?.let { due ->
    BubbleAction(Icons.Outlined.Update, "Rimanda a domani") {
        val zone = ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalTime()
        NotesStore.snoozeTask(t.id, LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli())
    }
}
