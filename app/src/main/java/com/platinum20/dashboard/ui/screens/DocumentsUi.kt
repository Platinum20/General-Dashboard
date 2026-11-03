package com.platinum20.dashboard.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    Section(title = "Documenti") {
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
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
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
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * La scheda di un documento: fronte e retro (tocca per vederli grandi), numero e scadenza da correggere,
 * in basso a sinistra "Condividi PDF" (fronte e retro su un foglio A4, come una fotocopia).
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
            val kept = DocumentsStore.keep(context, uri) ?: return@launch
            DocumentsStore.addBack(id, kept, DocReader.read(PhotoAttach.rows(context, uri)))
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) setBack(ReceiptReader.photoUri(context))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { setBack(it) }

    if (doc == null) {
        DetailPage(onBack = onBack, onEdit = null, onDelete = null, meta = "Documento") {
            DetailMissing(Icons.Outlined.Badge, "Documento eliminato")
        }
        return
    }
    val more = buildList {
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
        primary = DetailAction(Icons.Outlined.Share, "Condividi PDF") {
            scope.launch {
                runCatching {
                    val file = DocumentsStore.pdf(context, doc)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, doc.title))
                }
            }
        },
        more = more,
    ) {
        EditableDetailTitle(doc.id, doc.title, MaterialTheme.colorScheme.primary) { DocumentsStore.rename(id, it) }
        DocPhoto(doc.front, "Fronte") { zoom = doc.front }
        doc.back?.let { back -> DocPhoto(back, "Retro") { zoom = back } }
        DetailFieldRow(doc.id, Icons.Outlined.Numbers, doc.number.orEmpty(), "Numero del documento") { DocumentsStore.setNumber(id, it) }
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

@Composable
private fun DocPhoto(path: String, label: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        coil3.compose.AsyncImage(
            model = File(path),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 320.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        )
    }
}
