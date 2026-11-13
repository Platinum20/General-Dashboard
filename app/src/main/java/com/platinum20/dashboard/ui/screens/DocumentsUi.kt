package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.outlined.Fullscreen
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
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

/**
 * Tenendo premuto un documento nella Home: le azioni della sua scheda senza entrarci (Antonio, 2026-11-13).
 * Elimina chiede conferma: con il documento vanno via anche le foto.
 */
internal fun docActions(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, doc: Document, open: () -> Unit): Array<BubbleAction> =
    listOfNotNull(
        BubbleAction(Icons.Outlined.Badge, "Apri", detail = doc.title, onClick = open),
        doc.number?.takeIf { it.isNotBlank() }?.let { number ->
            BubbleAction(Icons.Outlined.ContentCopy, "Copia il numero", detail = number) {
                Clip.copy(context, "Numero del documento", number, "Numero copiato")
            }
        },
        BubbleAction(Icons.Outlined.Share, "Condividi PDF", "Fronte e retro su un foglio A4") { exportDocPdf(context, scope, doc, save = false) },
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            BubbleAction(Icons.Outlined.Download, "Salva PDF in Download") { exportDocPdf(context, scope, doc, save = true) }
        } else null,
        BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
            ActionBubble.show(
                BubbleAction(Icons.Outlined.Delete, "Elimina ${doc.title}", detail = "Con le sue foto", danger = true) {
                    DocumentsStore.delete(doc.id)
                },
            )
        },
    ).toTypedArray()

/** Card "Documenti" della Home: compare solo quando c'è almeno un documento fotografato con la graffetta. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DocumentsCard(onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    DocumentsStore.init(context)
    val docs by DocumentsStore.docs.collectAsStateWithLifecycle()
    // Le tessere in fila come nel portafoglio (1.528, Antonio scelta B): foto, nome, anno di scadenza.
    CardSection(title = "Documenti", trailing = {
        Text("${docs.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Prima quelli che scadono prima; senza scadenza in fondo.
            docs.sortedBy { it.expiry ?: LocalDate.MAX }.forEach { doc ->
                val soon = doc.expiry?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) <= 60 } == true
                Column(
                    Modifier
                        .width(112.dp)
                        .bouncyPress(0.96f)
                        .combinedClickable(onClick = { onOpen(doc.id) }, onLongClick = {
                            haptic(Haptic.LONG_PRESS)
                            ActionBubble.show(*docActions(context, scope, doc) { onOpen(doc.id) })
                        }),
                ) {
                    coil3.compose.AsyncImage(
                        model = File(doc.front),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // La miniatura vola fino alla foto del fronte nella pagina del documento (1.357).
                        modifier = Modifier
                            .size(width = 112.dp, height = 72.dp)
                            .sharedItem(SharedKeys.document(doc.id))
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(doc.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        doc.expiry?.let { if (soon) "scade " + it.format(docDate) else "scade " + it.year } ?: "senza scadenza",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (soon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Il PDF del documento (fronte e retro su un A4): da condividere o da salvare in Download. Uguale nella scheda e nel fumetto. */
internal fun exportDocPdf(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, doc: Document, save: Boolean) {
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

/** Copia il numero del documento negli appunti (avviso: vedi [Clip]). */
@Composable
internal fun CopyCodeButton(code: String) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    androidx.compose.material3.IconButton(
        onClick = {
            haptic(Haptic.CONFIRM)
            Clip.copy(context, "Numero del documento", code, "Numero copiato")
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
    fun exportPdf(save: Boolean) = exportDocPdf(context, scope, doc, save)
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
        // "Mostra" (1.528): la foto a tutto schermo con la luminosità al massimo, per farla vedere a chi la chiede.
        primary = DetailAction(Icons.Outlined.Fullscreen, "Mostra") { zoom = doc.front },
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
        // Come nel portafoglio (1.528, Antonio scelta B): il fronte davanti, il retro che spunta dietro; tocca il
        // retro (o scorri) e si scambiano. Il pulsante per ruotare resta sulla foto davanti.
        DocWallet(
            front = doc.front,
            back = doc.back,
            frontModifier = Modifier.sharedItem(SharedKeys.document(doc.id)),
            onLongClick = { back -> sideActions(back) },
            onRotate = { back -> scope.launch { DocumentsStore.turn(context, id, back) } },
            onOpen = { path -> zoom = path },
        )
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
        // Luminosità al massimo mentre è aperta; tocca per passare all'altro lato, Indietro per chiudere.
        Dialog(onDismissRequest = { zoom = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            FullBrightness()
            val other = if (path == doc.front) doc.back else doc.front
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable { zoom = if (other != null && path == doc.front) other else null },
                contentAlignment = Alignment.Center,
            ) {
                coil3.compose.AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}

/**
 * Fronte e retro come due tessere nel portafoglio (1.528): quella davanti grande, l'altra dietro più piccola e
 * sbiadita che spunta in basso. Tocca quella dietro o scorri su/giù e si scambiano; tocca quella davanti per vederla
 * grande; tieni premuto per ruotarla o tornare alla foto intera.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DocWallet(
    front: String,
    back: String?,
    frontModifier: Modifier,
    onLongClick: (back: Boolean) -> Unit,
    onRotate: (back: Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    var backOnTop by remember(back) { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val swap: () -> Unit = {
        if (back != null) {
            haptic(Haptic.TICK)
            backOnTop = !backOnTop
        }
    }
    // 0 = fronte davanti, 1 = retro davanti: le due tessere si scambiano di posto con la stessa molla.
    val t by animateFloatAsState(if (backOnTop) 1f else 0f, Motion.settle(), label = "wallet")
    val peek = 44.dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = if (back != null) peek else 0.dp)
            .pointerInput(back) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (kotlin.math.abs(drag) > 40.dp.toPx()) swap() },
                ) { _, dy -> drag += dy }
            },
    ) {
        @Composable
        fun Side(path: String, isBack: Boolean, depth: Float) {
            // depth 0 = davanti, 1 = dietro.
            val mod = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 1f - 0.06f * depth
                    scaleX = s
                    scaleY = s
                    translationY = peek.toPx() * depth
                    alpha = 1f - 0.45f * depth
                }
                .zIndex(1f - depth)
            Box(mod) {
                coil3.compose.AsyncImage(
                    model = File(path),
                    contentDescription = if (isBack) "Retro" else "Fronte",
                    contentScale = ContentScale.Fit,
                    modifier = (if (isBack) Modifier else frontModifier)
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 300.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = { if (depth > 0.5f) swap() else onOpen(path) },
                            onLongClick = { onLongClick(isBack) },
                        ),
                )
                if (depth < 0.5f) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                    RoundIconButton(Icons.Outlined.Rotate90DegreesCw, if (isBack) "Ruota retro" else "Ruota fronte", onClick = { onRotate(isBack) })
                }
            }
        }
        if (back != null) {
            // Prima si disegna quella dietro.
            if (t < 0.5f) {
                Side(back, isBack = true, depth = 1f - t)
                Side(front, isBack = false, depth = t)
            } else {
                Side(front, isBack = false, depth = t)
                Side(back, isBack = true, depth = 1f - t)
            }
        } else {
            Side(front, isBack = false, depth = 0f)
        }
    }
    if (back != null) {
        Text(
            if (backOnTop) "Retro · tocca dietro per il fronte" else "Fronte · tocca dietro per il retro",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
