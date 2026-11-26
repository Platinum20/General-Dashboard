package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotePdf
import com.platinum20.dashboard.notes.NotePhotoFiles
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.share.ShareTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/*
 * Il Condividi di ogni cosa (Antonio 2026-11-26, PDF "Condividi"): lo stesso fumetto PDF › Copia › Manda a…, e il
 * testo giusto per tipo da [ShareTexts]. Qui solo il collegamento con Android (file, PDF, foglio di condivisione).
 */

/** I PDF si fanno anche a fumetto chiuso: non dipendono dalla pagina che li ha chiesti. */
private val pdfScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Fa il PDF, lo mette nei Download e lo dice, con "Condividi" per mandarlo subito. */
internal fun pdfToDownloads(context: Context, title: String, body: String, photos: List<String> = emptyList()) {
    pdfScope.launch {
        val file = runCatching { NotePdf.make(context, title, body, photos) }.getOrNull()
        if (file == null) { CaptureFeedback.tell(context, "Il PDF non si è fatto"); return@launch }
        val saved = NotePdf.saveToDownloads(context, file)
        CaptureFeedback.tell(context, if (saved) "PDF nei Download" else "PDF pronto", Icons.Outlined.PictureAsPdf, "Condividi") {
            sendPdf(context, file, title)
        }
    }
}

/** Manda un PDF già fatto con il foglio di condivisione del telefono. */
internal fun sendPdf(context: Context, file: java.io.File, title: String) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(android.content.Intent.createChooser(send, title.ifBlank { "PDF" }).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun today() = LocalDate.now()
private fun zone() = ZoneId.systemDefault()

/** Un evento del calendario: il testo, e in Manda a… anche l'invito .ics. */
internal fun eventShare(context: Context, id: Long, title: String, begin: Long, end: Long, allDay: Boolean, location: String): BubbleAction {
    val text = ShareTexts.event(title, begin, end, allDay, location, zone(), today())
    return Slot.shareText(context, text, sendDetail = "il testo e l'invito da calendario") {
        val ics = ShareTexts.ics("canvas-$id-$begin@canvas", title, begin, end, allDay, location, System.currentTimeMillis())
        shareTextWithFile(context, text, title, fileName(title, "ics"), "text/calendar", ics)
    }
}

/** Un turno: con chi e quando, mai il guadagno. */
internal fun shiftShare(context: Context, begin: Long, end: Long, mates: List<String>): BubbleAction =
    Slot.shareText(context, ShareTexts.shift(begin, end, mates, zone(), today()))

/** Un treno: tratta, orari, treno, carrozza e posto. */
internal fun trainShare(context: Context, trip: com.platinum20.dashboard.calendar.Trip, begin: Long, end: Long): BubbleAction =
    Slot.shareText(context, ShareTexts.train(trip.from, trip.to, trip.train, trip.coach, trip.seat, begin, end, zone(), today()))

/** Una riga del calendario: turno, treno o evento, ognuno col suo testo. */
internal fun calendarShare(context: Context, e: CalendarEvent): BubbleAction = when {
    e.people != null -> shiftShare(context, e.begin, e.end, e.mates)
    e.trip != null -> trainShare(context, e.trip, e.begin, e.end)
    else -> eventShare(context, e.eventId, e.title, e.begin, e.end, e.allDay, e.location)
}

/** Un promemoria (o una cosa da fare): testo, giorno e ora, luogo. */
internal fun taskShare(context: Context, t: TaskItem): BubbleAction =
    Slot.shareText(context, ShareTexts.task(t.text, t.dueAt, t.allDay, t.place, zone(), today()))

/** Un pacco: nome, corriere e codice, il link per seguirlo. */
internal fun parcelShare(context: Context, p: Parcel): BubbleAction =
    Slot.shareText(context, ShareTexts.parcel(p.title, if (p.code.isBlank()) "" else p.carrier.label, p.code, p.trackingUrl()))

/** Una nota, uguale ovunque: PDF con le foto, Copia il testo, Manda a… col testo e le foto. */
internal fun noteShare(context: Context, n: Note): BubbleAction? {
    val text = ShareTexts.note(n.text)
    if (text.isBlank() && n.photos.isEmpty()) return null
    val photos = n.photos.size
    return Slot.share(
        copy = if (text.isBlank()) null else { { Clip.copy(context, "Nota", text, "Testo copiato") } },
        copyDetail = ShareTexts.preview(text),
        pdf = { pdfToDownloads(context, n.plainTitle.ifEmpty { "Nota" }, n.text.substringAfter('\n', ""), n.photos) },
        pdfDetail = if (photos > 0) "con le foto, nei Download" else "nei Download, da mandare o stampare",
        sendDetail = when (photos) { 0 -> "WhatsApp, Gmail e le altre app"; 1 -> "il testo e la foto"; else -> "il testo e le $photos foto" },
    ) { runCatching { NotePhotoFiles.share(context, text, n.photos) } }
}

/**
 * Una notizia, anche senza aprirla: il PDF dell'articolo intero (Canvas lo scarica se non l'ha già), poi titolo,
 * fonte e link. Se l'articolo non si scarica lo dice, e si può sempre aprirlo e riprovare.
 */
internal fun articleShare(context: Context, url: String, title: String, source: String): BubbleAction =
    Slot.shareText(
        context, ShareTexts.article(title, source, url),
        pdf = { articlePdfFromWeb(context, url, title) },
        pdfDetail = "l'articolo intero, nei Download",
    )

internal fun articlePdfFromWeb(context: Context, url: String, title: String) {
    CaptureFeedback.tell(context, "Preparo il PDF…", Icons.Outlined.PictureAsPdf)
    pdfScope.launch {
        val article = com.platinum20.dashboard.news.reader.ArticleCache.get(url) ?: runCatching {
            val page = com.platinum20.dashboard.news.reader.PageLoader.load(url)
            page?.let { withContext(Dispatchers.Default) { com.platinum20.dashboard.news.reader.ArticleExtractor.extractBest(it) } }
        }.getOrNull()?.takeIf { it.textLength >= 200 }
        if (article == null) {
            CaptureFeedback.tell(context, "L'articolo non si scarica: aprilo e riprova")
            return@launch
        }
        pdfToDownloads(context, article.title.ifBlank { title }, article.spokenParagraphs().joinToString("\n\n"))
    }
}

/** Una mail: PDF, poi chi, oggetto, giorno e il testo. */
internal fun mailShare(context: Context, m: com.platinum20.dashboard.mail.MailMessage): BubbleAction {
    val text = ShareTexts.mail(m.from, m.subject, m.at, m.body.ifEmpty { m.snippet }, zone(), today())
    return Slot.shareText(
        context, text, copyTitle = "Copia il testo",
        pdf = { pdfToDownloads(context, m.subject.ifBlank { "Mail" }, text) },
        pdfDetail = "la mail come pagina, nei Download",
    )
}

/** Brano, album, video, episodio, radio: titolo, chi, link che si apre anche fuori. Null se non c'è niente da dire. */
internal fun mediaShare(context: Context, title: String, by: String, url: String?): BubbleAction? =
    title.takeIf { it.isNotBlank() }?.let { Slot.shareText(context, ShareTexts.media(it, by, url)) }

/** Il link di un media salvato o in Riprendi che si apre fuori da Canvas. */
internal fun mediaUrl(item: com.platinum20.dashboard.media.ResumeItem): String? = when (item.kind) {
    com.platinum20.dashboard.media.ResumeKind.YOUTUBE, com.platinum20.dashboard.media.ResumeKind.MUSIC -> "https://youtu.be/${item.key}"
    com.platinum20.dashboard.media.ResumeKind.SPOTIFY -> ShareTexts.spotifyUrl(item.key)
    com.platinum20.dashboard.media.ResumeKind.PODCAST -> item.link.takeIf { it.startsWith("http") }
    com.platinum20.dashboard.media.ResumeKind.RADIO -> item.station?.homepage?.takeIf { it.startsWith("http") }
    com.platinum20.dashboard.media.ResumeKind.VIDEO -> item.key.takeIf { it.startsWith("http") }
}
