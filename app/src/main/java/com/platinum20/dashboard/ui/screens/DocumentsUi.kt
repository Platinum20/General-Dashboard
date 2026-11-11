package com.platinum20.dashboard.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.docs.DocReader
import com.platinum20.dashboard.docs.Document
import com.platinum20.dashboard.docs.DocumentsStore
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.PhotoAttach
import com.platinum20.dashboard.notes.ReceiptReader
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val docDate = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ITALIAN)

/** La riga piccola sotto il nome: numero e scadenza, o quanto manca se è vicina. */
internal fun docLine(doc: Document, today: LocalDate = LocalDate.now()): String {
    val expiry = doc.expiry?.let { date ->
        val days = ChronoUnit.DAYS.between(today, date)
        when {
            days < 0 -> "scaduto"
            days == 0L -> "scade oggi"
            days <= 60 -> "scade tra $days giorni"
            else -> "scade il " + date.format(docDate)
        }
    }
    return listOfNotNull(doc.number, expiry).joinToString(" · ").ifEmpty { if (doc.back == null) "Solo fronte" else "Fronte e retro" }
}

/** Card "Documenti" della Home: compare solo quando c'è almeno un documento fotografato con la graffetta. */
@Composable
internal fun DocumentsCard(onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    DocumentsStore.init(context)
    val docs by DocumentsStore.docs.collectAsStateWithLifecycle()
    CardSection(title = "Documenti") {
        // Prima quelli che scadono prima; senza scadenza in fondo.
        docs.sortedBy { it.expiry ?: LocalDate.MAX }.forEachIndexed { i, doc ->
            if (i > 0) RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(doc.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                coil3.compose.AsyncImage(
                    model = File(doc.front),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // La miniatura vola fino alla foto del fronte nella pagina del documento (1.357).
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .sharedItem(SharedKeys.document(doc.id))
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val soon = doc.expiry?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) <= 60 } == true
                    Text(
                        docLine(doc),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (soon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Col numero, a destra il tasto che lo copia (Antonio, 2026-11-11): la riga lo tronca spesso.
                val number = doc.number?.takeIf { it.isNotBlank() }
                if (number != null) CopyCodeButton(number)
                else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Copia il numero del documento negli appunti e lo dice nel fumetto in basso. */
@Composable
internal fun CopyCodeButton(code: String) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    androidx.compose.material3.IconButton(
        onClick = {
            haptic(Haptic.CONFIRM)
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Numero del documento", code))
            CaptureFeedback.tell(context, "Numero copiato", Icons.Outlined.ContentCopy)
        },
        modifier = Modifier.size(40.dp).bouncyPress(0.9f),
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copia il numero", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * La scheda di un documento: fronte e retro (tocca per vederli grandi), numero e scadenza da correggere,
 * e in "Altro" il PDF (fronte e retro ritagliati su un foglio A4, come una fotocopia) da condividere o salvare.
 */
@Composable
internal fun DocumentScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    DocumentsStore.init(context)
    val docs by DocumentsStore.docs.collectAsStateWithLifecycle()
    val doc = docs.firstOrNull { it.id == id }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    fun setBack(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val kept = DocumentsStore.keep(context, uri, doc?.kind ?: com.platinum20.dashboard.docs.DocKind.OTHER) ?: return@launch
            DocumentsStore.addBack(id, kept, DocReader.read(PhotoAttach.rows(context, uri)))
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) setBack(ReceiptReader.photoUri(context))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { setBack(it) }

    // I documenti fotografati prima della 1.358 si raddrizzano e ritagliano la prima volta che si aprono.
    LaunchedEffect(id, doc?.straight) {
        if (doc != null && !doc.straight) DocumentsStore.straighten(context, id)
    }
    if (doc == null) {
        DetailPage(onBack = onBack, onEdit = null, onDelete = null, meta = "Documento") {
            DetailMissing(Icons.Outlined.Badge, "Documento eliminato")
        }
        return
    }
    fun exportPdf(save: Boolean) {
        scope.launch {
            if (save) {
                val ok = DocumentsStore.savePdf(context, doc)
                CaptureFeedback.tell(context, if (ok) "${doc.pdfName} salvato in Download" else "Non riesco a salvare il PDF", Icons.Outlined.PictureAsPdf)
                return@launch
            }
            runCatching {
                val file = DocumentsStore.pdf(context, doc)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, doc.title)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Il ClipData fa arrivare il permesso di lettura anche a chi riceve dal menu di condivisione.
                send.clipData = android.content.ClipData.newUri(context.contentResolver, doc.pdfName, uri)
                context.startActivity(Intent.createChooser(send, doc.pdfName).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.onFailure { CaptureFeedback.tell(context, "Non riesco a creare il PDF", Icons.Outlined.PictureAsPdf) }
        }
    }
    val more = buildList {
        add(BubbleAction(Icons.Outlined.Share, "Condividi PDF", "Fronte e retro su un foglio A4") { exportPdf(save = false) })
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            add(BubbleAction(Icons.Outlined.Download, "Salva PDF in Download") { exportPdf(save = true) })
        }
        add(BubbleAction(Icons.Outlined.PhotoCamera, if (doc.back == null) "Fotografa il retro" else "Rifai il retro") {
            runCatching { camera.launch(ReceiptReader.photoUri(context)) }
        })
        add(BubbleAction(Icons.Outlined.PhotoLibrary, "Retro dalla galleria") {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        if (doc.back != null) add(BubbleAction(Icons.Outlined.RemoveCircleOutline, "Togli il retro") { DocumentsStore.removeBack(id) })
    }
    DetailPage(
        onBack = onBack,
        onEdit = null,
        onDelete = {
            onBack()
            DocumentsStore.delete(id)
        },
        meta = "Documento",
        more = more,
    ) {
        EditableDetailTitle(doc.id, doc.title, MaterialTheme.colorScheme.primary) { DocumentsStore.rename(id, it) }
        fun sideActions(back: Boolean) {
            val cut = if (back) doc.backFull else doc.frontFull
            ActionBubble.show(*listOfNotNull(
                BubbleAction(Icons.Outlined.Rotate90DegreesCw, "Ruota") { scope.launch { DocumentsStore.turn(context, id, back) } },
                cut?.let { BubbleAction(Icons.Outlined.CropFree, "Foto intera", "Togli il ritaglio") { DocumentsStore.useFull(id, back) } },
            ).toTypedArray())
        }
        // Il pulsante per ruotare sta sulla foto, sempre visibile (Antonio, 2026-11-11): un quarto di giro a ogni tocco.
        DocPhoto(doc.front, "Fronte", Modifier.sharedItem(SharedKeys.document(doc.id)), onLongClick = { sideActions(back = false) },
            onRotate = { scope.launch { DocumentsStore.turn(context, id, back = false) } }) { zoom = doc.front }
        doc.back?.let { back ->
            DocPhoto(back, "Retro", onLongClick = { sideActions(back = true) },
                onRotate = { scope.launch { DocumentsStore.turn(context, id, back = true) } }) { zoom = back }
        }
        DetailFieldRow(
            doc.id, Icons.Outlined.Numbers, doc.number.orEmpty(), "Numero del documento",
            onCommit = { DocumentsStore.setNumber(id, it) },
            trailing = doc.number?.takeIf { it.isNotBlank() }?.let { n -> { CopyCodeButton(n) } },
        )
        DetailTapRow(
            Icons.Outlined.EventBusy,
            doc.expiry?.let { "Scade il " + it.format(docDate) + " · nel calendario" } ?: "Aggiungi la scadenza",
            onClick = { pickDate = true },
            onClear = doc.expiry?.let { { DocumentsStore.setExpiry(id, null) } },
        )
    }
    if (pickDate) {
        DatePickDialog(doc.expiry ?: LocalDate.now().plusYears(10), onDismiss = { pickDate = false }) {
            DocumentsStore.setExpiry(id, it)
            pickDate = false
        }
    }
    zoom?.let { path ->
        // A tutto schermo su nero, per mostrarlo o leggerlo: si chiude toccando.
        Dialog(onDismissRequest = { zoom = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { zoom = null }, contentAlignment = Alignment.Center) {
                coil3.compose.AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Tocca per vederla grande; il pulsante in basso la ruota; tieni premuto per ruotarla o tornare alla foto intera. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DocPhoto(path: String, label: String, photo: Modifier = Modifier, onLongClick: () -> Unit, onRotate: () -> Unit, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
        coil3.compose.AsyncImage(
            model = File(path),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = photo
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 320.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            RoundIconButton(Icons.Outlined.Rotate90DegreesCw, "Ruota ${label.lowercase()}", onClick = onRotate)
        }
        }
    }
}
