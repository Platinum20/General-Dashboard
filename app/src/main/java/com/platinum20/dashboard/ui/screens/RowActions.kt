package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Widgets
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
    fun taskBubble(t: TaskItem) {
        val dated = t.dueAt != null
        ActionBubble.show(
            if (dated) BubbleHead("Promemoria", t.text + (t.dueAt?.let { " · " + dayAndTime(it) } ?: "")) else BubbleHead("Da fare", t.text),
            *listOfNotNull(
                if (!t.tickable) null else BubbleAction(
                    if (t.done) Icons.Outlined.CheckBoxOutlineBlank else Icons.Outlined.CheckBox,
                    if (t.done) "Segna da fare" else "Fatto",
                    if (t.done) "torna in Da fare" else "esce da Da fare, resta fra le fatte",
                    group = BubbleGroup.SEGNA,
                ) { haptic(Haptic.CONFIRM); NotesStore.toggleTask(t.id) },
                snoozeAction(t),
                Slot.open { openItem(ItemKind.TASK, t.id) },
                if (!t.tickable) null else Slot.pin(t.pinned) { haptic(if (t.pinned) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON); NotesStore.toggleTaskPin(t.id) },
                Slot.share { share(t.text) },
                Slot.delete {
                    NotesStore.deleteTask(t.id)
                    offerUndo(listOf(t), "Promemoria eliminato")
                },
            ).toTypedArray(),
        )
    }
    /**
     * Le stesse azioni della scheda dell'evento, senza entrarci. Se il calendario non si può scrivere (o manca
     * il permesso) Modifica ed Elimina aprono la scheda, che lo chiede. Turni e treni (id negativi) non si cambiano.
     */
    fun eventBubble(e: CalendarEvent) {
        val tickets = if (e.eventId > 0) com.platinum20.dashboard.tickets.TicketQrStore.cached(context, e.eventId) else emptyList()
        fun withWritable(action: () -> Unit) {
            scope.launch {
                val detail = CalendarRepository.event(context, e.eventId)
                if (detail?.writable == true && CalendarRepository.canWrite(context)) action() else openEvent(e)
            }
        }
        val head = when {
            e.people != null -> BubbleHead("Turno", e.title + " · " + clockRange(e.begin, e.end))
            e.trip != null -> BubbleHead("Treno", e.title + " · " + clock(e.begin))
            else -> BubbleHead("Evento", e.title + if (e.allDay) "" else " · " + clock(e.begin))
        }
        val editable = e.eventId > 0 && e.people == null
        ActionBubble.show(
            head,
            *listOfNotNull(
                if (!editable) null else act(BubbleGroup.CAMBIA, Icons.Outlined.Edit, "Modifica", "titolo, orario, luogo, avviso") {
                    withWritable { inApp?.openRoute?.invoke(Routes.calendarEdit(e.eventId)) }
                },
                tickets.takeIf { it.isNotEmpty() }?.let {
                    act(BubbleGroup.VAI, Icons.Outlined.QrCode2, "Biglietto", "il QR da mostrare all'ingresso") {
                        runCatching { context.startActivity(com.platinum20.dashboard.tickets.TicketActivity.intent(context, e.eventId)) }
                    }
                },
                Slot.open { openEvent(e) },
                Slot.share { share(eventShareText(e)) },
                // Non si può annullare: si tocca due volte, nello stesso punto.
                if (!editable) null else Slot.delete(confirm = "Tocca ancora: sparisce anche dal telefono") {
                    withWritable {
                        scope.launch {
                            if (CalendarRepository.delete(context, e.eventId)) haptic(Haptic.CONFIRM)
                            else CaptureFeedback.tell(context, CalendarPermission.DELETE_FAILED, Icons.Outlined.Delete)
                        }
                    }
                },
            ).toTypedArray(),
        )
    }
    fun dueActions(row: DueRow) {
        when {
            row.task != null -> taskBubble(row.task)
            row.event != null -> eventBubble(row.event)
            row.parcel != null -> parcelBubble(row.parcel, openItem = { openItem(ItemKind.PARCEL, row.parcel.id) }, openWeb = { u, t -> inApp?.openWeb?.invoke(u, t) }, share = { share(it) }) { msg, restore -> offerUndo(msg, restore) }
        }
    }
    fun noteActions(n: Note) = showNoteBubble(context, n, openRoute = { r -> inApp?.openRoute?.invoke(r) })
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
    act(BubbleGroup.CAMBIA, Icons.Outlined.Update, "Rimanda a domani", dayAndTime(due + 86_400_000L)) {
        val zone = ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalTime()
        NotesStore.snoozeTask(t.id, LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli())
    }
}

/** "9:00" di un istante. */
internal fun clock(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))

internal fun clockRange(begin: Long, end: Long): String = clock(begin) + "-" + clock(end)

/** "sabato 29 alle 9:00": per le testate e "Rimanda a domani". */
internal fun dayAndTime(ms: Long): String {
    val z = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val day = z.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN)
    return "$day ${z.dayOfMonth} alle ${clock(ms)}"
}

/**
 * Il fumetto del pacco, uguale ovunque (riga del calendario, scheda In arrivo, card Home): Segna consegnato,
 * Traccia, poi Apri · Fissa · Condividi · Elimina. Chi lo apre dalla pagina del pacco passa [openItem] = null.
 */
internal fun parcelBubble(
    p: com.platinum20.dashboard.notes.Parcel,
    openItem: (() -> Unit)?,
    openWeb: (String, String) -> Unit,
    share: ((String) -> Unit)? = null,
    offerUndo: (String, () -> Unit) -> Unit,
) {
    ActionBubble.show(
        BubbleHead("Pacco", p.title + " · " + p.carrier.label),
        *listOfNotNull(
            if (p.delivered) null else act(BubbleGroup.SEGNA, Icons.Outlined.Inventory2, "Segna consegnato", "esce da In arrivo") {
                NotesStore.toggleDelivered(p.id)
                offerUndo("Consegnato") { NotesStore.toggleDelivered(p.id) }
            },
            p.takeIf { !it.delivered }?.trackingUrl()?.let { url ->
                act(BubbleGroup.VAI, Icons.Outlined.LocalShipping, "Traccia la spedizione", "dove si trova adesso, in Canvas") { openWeb(url, p.carrier.label) }
            },
            openItem?.let { Slot.open(it) },
            Slot.pin(p.pinned) { NotesStore.toggleParcelPin(p.id) },
            share?.let { s -> Slot.share { s(p.title) } },
            Slot.delete {
                NotesStore.deleteParcel(p.id)
                offerUndo("Pacco eliminato") { NotesStore.restoreParcel(p) }
            },
        ).toTypedArray(),
    )
}

/**
 * Il fumetto di una nota, uguale in Appunti, nel Calendario e in Da fare (le liste): Ricomincia, Duplica, Archivia,
 * Mostra nel widget, poi Apri · Fissa · Condividi · Elimina. Il nome sta solo nella testata.
 */
internal fun showNoteBubble(context: android.content.Context, n: Note, openRoute: (String) -> Unit, open: Boolean = true) {
    val list = com.platinum20.dashboard.notes.NoteLists.of(n)
    val done = com.platinum20.dashboard.notes.NoteLists.doneCount(n.text)
    fun undo(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, restore: () -> Unit) =
        CaptureFeedback.tell(context, message, icon, "Annulla") { restore() }
    ActionBubble.show(
        BubbleHead(if (list != null) "Lista" else "Nota", n.title + if (list != null) " · $done di ${list.total}" else ""),
        *listOfNotNull(
            if (list == null || done == 0) null else act(BubbleGroup.CAMBIA, Icons.Outlined.Replay, "Ricomincia", if (done == 1) "toglie la spunta, il testo resta" else "toglie le $done spunte, il testo resta") {
                NotesStore.editNoteText(n.id) { com.platinum20.dashboard.notes.NoteLists.restart(it) }
                undo("Lista da rifare", Icons.Outlined.Replay) { NotesStore.editNoteText(n.id) { n.text } }
            },
            if (list == null) null else act(BubbleGroup.CREA, Icons.Outlined.FileCopy, "Duplica", "una copia con tutto da rifare") {
                NotesStore.duplicateNote(n.id)?.let { openRoute(Routes.noteEdit(it)) }
            },
            // L'archivio di Appunti (Antonio, 2026-11-20): la nota esce dalla card senza perdersi.
            if (n.archivedAt == null) act(BubbleGroup.METTI, Icons.Outlined.Archive, "Archivia", "esce dalla card, resta nell'archivio") {
                NotesStore.toggleNoteArchive(n.id)
                undo("Archiviata", Icons.Outlined.Archive) { NotesStore.toggleNoteArchive(n.id) }
            } else act(BubbleGroup.METTI, Icons.Outlined.Unarchive, "Rimetti negli appunti", "torna nella card della Home") { NotesStore.toggleNoteArchive(n.id) },
            if (list == null) null else row(
                BubbleGroup.METTI, Icons.Outlined.Widgets, "Mostra nel widget",
                if (com.platinum20.dashboard.widget.NoteWidget.placed(context)) "il widget Nota mostra questa" else "poi aggiungi il widget Nota",
            ) {
                com.platinum20.dashboard.widget.NoteWidget.choose(context, n.id)
                CaptureFeedback.tell(context, "Nel widget Nota", Icons.Outlined.Widgets)
            },
            if (open) Slot.open { openRoute(Routes.noteEdit(n.id)) } else null,
            Slot.pin(n.pinned) { NotesStore.togglePin(n.id) },
            Slot.share { shareText(context, n.text) },
            Slot.delete {
                NotesStore.deleteNote(n.id)
                undo("Nota eliminata", Icons.Outlined.Delete) { NotesStore.restoreNote(n) }
            },
        ).toTypedArray(),
    )
}
