package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Il linguaggio unico dei fumetti (Antonio, 2026-11-23, PDF "Fumetti-Canvas"): dall'alto in basso sempre
 * testata › scelte (file di chip) › righe nei gruppi fissi › barra dei tasti. Chi chiama passa le azioni
 * in qualsiasi ordine: è il fumetto a metterle al loro posto, così l'ordine è uguale ovunque.
 */

/** I tasti della barra, sempre in quest'ordine da sinistra; quelli che mancano non lasciano buchi. */
internal enum class BubbleSlot {
    /** Quello che fa il tocco sulla cosa: Apri, Riproduci, Leggi. */
    MAIN,
    /** Tenerla vicino: Fissa, Salva, Segui, Iscriviti. */
    KEEP,
    /** Mandarla fuori: Condividi o Copia. */
    SEND,
    /** Toglierla: Elimina, Togli, Esci, Svuota. L'unico tasto rosso, sempre l'ultimo a destra. */
    AWAY,
}

/** I gruppi delle righe, sempre in quest'ordine (senza titolini: si riconoscono dal posto). */
internal enum class BubbleGroup { SEGNA, CAMBIA, VAI, CREA, METTI }

/** La testata: che cosa hai premuto ("NOTA") e il suo nome, scritto una volta sola. */
internal data class BubbleHead(val kind: String, val name: String = "")

/** Una scelta del fumetto: una riga, una fila di chip ([chips]) o un tasto della barra ([slot]). */
internal data class BubbleAction(
    val icon: ImageVector,
    val title: String,
    val detail: String = "",
    val danger: Boolean = false,
    /** Scelte rapide in fila: la riga diventa una fila di chip, [title] sopra. */
    val chips: List<BubbleChip> = emptyList(),
    /** Tenendo premuta la riga: di solito il fumetto con le azioni di quella cosa (negli elenchi). Prima di [onClick], che resta l'ultimo per la lambda in coda. */
    val onLongClick: (() -> Unit)? = null,
    /** Un tasto della barra invece di una riga: [title] è la sua parola (corta). */
    val slot: BubbleSlot? = null,
    /** Il gruppo della riga, per l'ordine fisso. */
    val group: BubbleGroup = BubbleGroup.CAMBIA,
    /** Per ciò che non si può annullare: il primo tocco fa diventare il tasto "Ancora" e mostra questa riga, il secondo esegue. */
    val confirm: String? = null,
    val onClick: () -> Unit = {},
)

/**
 * I tasti della barra, scritti una volta sola: stessa parola e stessa icona in tutta l'app
 * (Apri/Riproduci/Leggi · Fissa/Salva/Segui/Iscriviti · Condividi/Copia · Elimina/Togli/Esci/Svuota).
 */
internal object Slot {
    fun open(onClick: () -> Unit) = BubbleAction(Icons.AutoMirrored.Outlined.OpenInNew, "Apri", slot = BubbleSlot.MAIN, onClick = onClick)
    fun play(onClick: () -> Unit) = BubbleAction(Icons.Filled.PlayArrow, "Riproduci", slot = BubbleSlot.MAIN, onClick = onClick)
    fun read(onClick: () -> Unit) = BubbleAction(Icons.AutoMirrored.Outlined.Article, "Leggi", slot = BubbleSlot.MAIN, onClick = onClick)
    fun pin(pinned: Boolean, onClick: () -> Unit) =
        BubbleAction(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (pinned) "Fissato" else "Fissa", slot = BubbleSlot.KEEP, onClick = onClick)
    fun save(saved: Boolean, onClick: () -> Unit) =
        BubbleAction(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd, if (saved) "Salvato" else "Salva", slot = BubbleSlot.KEEP, onClick = onClick)
    fun follow(following: Boolean, onClick: () -> Unit) =
        BubbleAction(if (following) Icons.Outlined.CheckCircle else Icons.Outlined.AddCircleOutline, if (following) "Seguito" else "Segui", slot = BubbleSlot.KEEP, onClick = onClick)
    fun subscribe(subscribed: Boolean, onClick: () -> Unit) =
        BubbleAction(if (subscribed) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive, if (subscribed) "Iscritto" else "Iscriviti", slot = BubbleSlot.KEEP, onClick = onClick)
    fun share(onClick: () -> Unit) = BubbleAction(Icons.Outlined.Share, "Condividi", slot = BubbleSlot.SEND, onClick = onClick)
    fun copy(onClick: () -> Unit) = BubbleAction(Icons.Outlined.ContentCopy, "Copia", slot = BubbleSlot.SEND, onClick = onClick)
    /** [confirm] solo per ciò che non si può annullare: allora si tocca due volte ("Ancora"). */
    fun delete(confirm: String? = null, onClick: () -> Unit) =
        BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true, slot = BubbleSlot.AWAY, confirm = confirm, onClick = onClick)
    /** Esce da qui ma resta altrove (una playlist, la coda, un posto). */
    fun remove(confirm: String? = null, onClick: () -> Unit) =
        BubbleAction(Icons.Outlined.RemoveCircleOutline, "Togli", danger = true, slot = BubbleSlot.AWAY, confirm = confirm, onClick = onClick)
    fun logout(confirm: String? = null, onClick: () -> Unit) =
        BubbleAction(Icons.AutoMirrored.Outlined.Logout, "Esci", danger = true, slot = BubbleSlot.AWAY, confirm = confirm, onClick = onClick)
    fun clear(confirm: String? = null, onClick: () -> Unit) =
        BubbleAction(Icons.Outlined.ClearAll, "Svuota", danger = true, slot = BubbleSlot.AWAY, confirm = confirm, onClick = onClick)
}

/** Una riga nel suo gruppo, in breve. */
internal fun act(group: BubbleGroup, icon: ImageVector, title: String, detail: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) =
    BubbleAction(icon, title, detail, group = group, onLongClick = onLongClick, onClick = onClick)

/** Una fila di scelte della stessa cosa: tocchi un chip, si applica e il fumetto si chiude. */
internal fun choice(title: String, vararg chips: BubbleChip) =
    BubbleAction(Icons.Outlined.Tune, title, chips = chips.toList())

/** Un chip dentro una riga del fumetto: [selected] = quello di adesso. */
internal data class BubbleChip(val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/** Le parti del fumetto già al loro posto: pura, così l'ordine si controlla nei test. */
internal data class BubbleLayout(val chips: List<BubbleAction>, val rows: List<BubbleAction>, val bar: List<BubbleAction>)

internal fun bubbleLayout(actions: List<BubbleAction>): BubbleLayout = BubbleLayout(
    chips = actions.filter { it.chips.isNotEmpty() && it.slot == null },
    rows = actions.filter { it.chips.isEmpty() && it.slot == null }.sortedBy { it.group.ordinal },
    bar = actions.filter { it.slot != null }.sortedBy { it.slot!!.ordinal }.take(4),
)

/**
 * I menu di Canvas (tieni premuto su qualsiasi cosa, Altro delle pagine): sale il fumetto di vetro in fondo al
 * centro e si chiude toccando fuori, con indietro o portandolo di lato. Mai da solo.
 */
internal object ActionBubble {
    var actions by mutableStateOf<List<BubbleAction>?>(null)
        private set
    var head by mutableStateOf<BubbleHead?>(null)
        private set

    /** La riga tenuta premuta che ha aperto il fumetto: resta accesa finché il fumetto è aperto. */
    var source by mutableStateOf<String?>(null)

    fun show(vararg choices: BubbleAction) = show(null, *choices)

    fun show(head: BubbleHead?, vararg choices: BubbleAction) {
        this.head = head
        actions = choices.toList()
    }

    fun dismiss() {
        actions = null
        source = null
    }

    /** Questa riga ha aperto il fumetto che si vede adesso. */
    fun heldBy(key: String): Boolean = actions != null && source == key
}

@Composable
internal fun BoxScope.ActionBubbleLayer() {
    val actions = ActionBubble.actions
    val haptic = rememberHaptic()
    // Le ultime azioni restano disegnate mentre il fumetto si chiude.
    var last by remember { mutableStateOf<List<BubbleAction>>(emptyList()) }
    var lastHead by remember { mutableStateOf<BubbleHead?>(null) }
    val swipe = rememberSwipeAwayState()
    LaunchedEffect(actions) {
        if (actions == null) return@LaunchedEffect
        last = actions
        lastHead = ActionBubble.head
        swipe.reset()
    }
    BottomBubble(visible = actions != null, swipe = swipe, onDismiss = ActionBubble::dismiss) {
        val layout = remember(last) { bubbleLayout(last) }
        var armed by remember(last) { mutableStateOf<BubbleAction?>(null) }
        LaunchedEffect(armed) { if (armed != null) { kotlinx.coroutines.delay(4_000); armed = null } }
        fun run(a: BubbleAction) {
            val confirm = a.confirm
            if (confirm != null && armed != a) {
                haptic(Haptic.THRESHOLD)
                armed = a
                return
            }
            haptic(Haptic.CONFIRM)
            ActionBubble.dismiss()
            a.onClick()
        }
        lastHead?.let { BubbleHeadLine(it) }
        val parts = layout.chips.size + layout.rows.size
        var index = 0
        // Le voci salgono una dopo l'altra, dal basso, invece di comparire tutte insieme.
        @Composable
        fun Appear(i: Int, content: @Composable () -> Unit) {
            val appear = remember(last) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(last) {
                kotlinx.coroutines.delay((parts - i).coerceAtLeast(0) * 30L)
                appear.animateTo(1f, Motion.lively())
            }
            Box(Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 12.dp.toPx() }) { content() }
        }
        layout.chips.forEach { a ->
            val i = index++
            if (i > 0 || lastHead != null) RowDivider()
            Appear(i) {
                BubbleChips(a.title, a.chips) { chip ->
                    haptic(Haptic.CONFIRM)
                    ActionBubble.dismiss()
                    chip.onClick()
                }
            }
        }
        layout.rows.forEach { a ->
            val i = index++
            if (i > 0 || lastHead != null) RowDivider()
            Appear(i) {
                BubbleRow(
                    a.icon, a.title, a.detail, a.danger,
                    onLongClick = a.onLongClick?.let { long -> { haptic(Haptic.LONG_PRESS); long() } },
                ) { run(a) }
            }
        }
        if (layout.bar.isNotEmpty()) {
            if (index > 0 || lastHead != null) RowDivider()
            Appear(index) { BubbleBar(layout.bar, armed) { run(it) } }
            armed?.confirm?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** La riga in cima: "NOTA · 💡 Spesa". */
@Composable
private fun BubbleHeadLine(head: BubbleHead) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            head.kind.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (head.name.isNotBlank()) {
            Text(
                " · " + head.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La barra dei tasti: solo quelli che servono, larghi a riempire la fila, sempre nello stesso ordine. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BubbleBar(bar: List<BubbleAction>, armed: BubbleAction?, onTap: (BubbleAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        bar.forEach { a ->
            val away = a.slot == BubbleSlot.AWAY
            val hot = armed == a
            val tint = when {
                hot -> MaterialTheme.colorScheme.onError
                away -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            val fill = when {
                hot -> MaterialTheme.colorScheme.error
                away -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fill)
                    .bouncyPress(0.96f)
                    .combinedClickable(onClick = { onTap(a) })
                    .padding(horizontal = 2.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(a.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(
                    if (hot) "Ancora" else a.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Una fila di chip del fumetto: un titolo piccolo e le scelte in fila, che vanno a capo. Sta dentro lo
 * stesso blocco di vetro delle altre righe, senza un riquadro suo (Antonio: "una bolla sopra l'altra").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BubbleChips(title: String, chips: List<BubbleChip>, onPick: (BubbleChip) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { c -> CanvasChip(selected = c.selected, onClick = { onPick(c) }, label = c.label) }
        }
    }
}
