package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.settings.AppSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Newspaper
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.settings.HomeLayout
import androidx.compose.material3.TextButton

/** Ordine e visibilità delle card della Dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val layout by AppSettings.homeLayout.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopLevelBar(
                title = "Card della Home",
                onBack = onBack,
            )
        },
    ) { inner ->
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val reorderHaptic = rememberHaptic()
        val reorder = rememberReorderState(listState, canMove = { key -> key is String && HomeCard.entries.any { it.name == key && it !in AppSettings.homeLayout.value.hidden } }) { from, to ->
            val card = HomeCard.entries.firstOrNull { it.name == from }
            val target = HomeCard.entries.firstOrNull { it.name == to }
            if (card != null && target != null) {
                reorderHaptic(Haptic.TICK)
                AppSettings.moveCardTo(card, target)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp + miniPlayerRoom(),
            ),
        ) {
            // Due gruppi (punto 53): sopra esattamente la Home dall'alto in basso, sotto quelle nascoste.
            val shown = layout.order.filter { it !in layout.hidden }
            val hidden = layout.order.filter { it in layout.hidden }
            item(key = "h-shown") { SectionHeader("In Home", Modifier.padding(bottom = 8.dp)) }
            itemsIndexed(shown, key = { _, card -> card.name }) { i, card ->
                val haptic = rememberHaptic()
                CardRow(
                    card, first = i == 0, last = i == shown.lastIndex,
                    modifier = Modifier
                        .then(if (reorder.isDragging(card.name)) Modifier else Modifier.animateItem())
                        .reorderItem(reorder, card.name),
                    handle = {
                        Icon(
                            Icons.Outlined.DragHandle,
                            contentDescription = "Trascina per spostare",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .reorderHandle(reorder, card.name) { haptic(Haptic.GRAB) }
                                .padding(8.dp)
                                .size(24.dp),
                        )
                    },
                    action = Icons.Outlined.RemoveCircleOutline,
                    actionLabel = "Togli dalla Home",
                ) { haptic(Haptic.TICK); AppSettings.setCardVisible(card, false) }
            }
            if (hidden.isNotEmpty()) {
                item(key = "h-hidden") { SectionHeader("Nascoste", Modifier.padding(top = 20.dp, bottom = 8.dp)) }
                itemsIndexed(hidden, key = { _, card -> card.name }) { i, card ->
                    val haptic = rememberHaptic()
                    CardRow(
                        card, first = i == 0, last = i == hidden.lastIndex,
                        modifier = Modifier.animateItem(),
                        dim = true,
                        action = Icons.Outlined.AddCircleOutline,
                        actionLabel = "Rimetti in Home",
                    ) { haptic(Haptic.CONFIRM); AppSettings.setCardVisible(card, true) }
                }
            }
            if (layout != HomeLayout.DEFAULT) item(key = "reset") {
                Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                    CanvasButton("Ripristina l'ordine iniziale", onClick = AppSettings::resetLayout)
                }
            }
        }
    }
}

/**
 * Una card della Home nella lista: solo il nome (lo dice già tutto), la maniglia se è in Home, a destra il
 * tocco per toglierla o rimetterla. Le righe di un gruppo stanno in un'unica card.
 */
@Composable
private fun CardRow(
    card: HomeCard,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    handle: (@Composable () -> Unit)? = null,
    action: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val corner = 16.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (first) corner else 0.dp, topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp, bottomEnd = if (last) corner else 0.dp,
    )
    Column(modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.card)) {
        if (!first) RowDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.alpha(if (dim) 0.5f else 1f)) { IconTile(card.icon(), TileAccent.NEUTRAL) }
            Spacer(Modifier.width(14.dp))
            Text(
                card.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            handle?.invoke()
            IconButton(onClick = onAction) {
                Icon(action, contentDescription = actionLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun HomeCard.icon(): ImageVector = when (this) {
    HomeCard.WEATHER -> Icons.Outlined.WbSunny
    HomeCard.DAY -> Icons.Outlined.Event
    HomeCard.RESUME -> Icons.Outlined.PlayCircle
    HomeCard.SIM -> Icons.Outlined.SimCard
    HomeCard.BANK -> Icons.Outlined.AccountBalanceWallet
    HomeCard.DOCS -> Icons.Outlined.Badge
    HomeCard.MAIL -> Icons.Outlined.Mail
    HomeCard.RECAP -> Icons.Outlined.Newspaper
}

private fun HomeCard.accent(): TileAccent = when (this) {
    HomeCard.WEATHER -> TileAccent.BLUE
    HomeCard.DAY -> TileAccent.BLUE
    HomeCard.RESUME -> TileAccent.PURPLE
    HomeCard.SIM -> TileAccent.BLUE
    HomeCard.BANK -> TileAccent.BLUE
    HomeCard.DOCS -> TileAccent.BLUE
    HomeCard.MAIL -> TileAccent.ORANGE
    HomeCard.RECAP -> TileAccent.GREEN
}
