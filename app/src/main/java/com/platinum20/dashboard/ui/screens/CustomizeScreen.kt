package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Newspaper
import com.platinum20.dashboard.settings.HomeCard

/** Ordine e visibilità delle card della Dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val layout by AppSettings.homeLayout.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopLevelBar(
                title = "Personalizza Oggi",
                onBack = onBack,
                actions = {
                    IconButton(onClick = AppSettings::resetLayout) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "Ripristina")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Text(
                    "Le frecce cambiano l'ordine delle card in Oggi; l'interruttore le mostra o le nasconde.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
            itemsIndexed(layout.order, key = { _, card -> card.name }) { index, card ->
                val visible = card !in layout.hidden
                val haptic = rememberHaptic()
                val toggle = hapticToggle { AppSettings.setCardVisible(card, it) }
                // Come le righe delle Impostazioni: icona, testo, frecce per l'ordine e interruttore a destra.
                Row(
                    modifier = Modifier
                        .animateItem()
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.card)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                        .clickable { toggle(!visible) }
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.alpha(if (visible) 1f else 0.45f)) { IconTile(card.icon(), card.accent()) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).alpha(if (visible) 1f else 0.6f)) {
                        Text(card.label, style = MaterialTheme.typography.titleMedium)
                        Text(card.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        IconButton(onClick = { haptic(Haptic.TICK); AppSettings.moveCard(card, -1) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Sposta su")
                        }
                        IconButton(onClick = { haptic(Haptic.TICK); AppSettings.moveCard(card, 1) }, enabled = index < layout.order.lastIndex, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Sposta giù")
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = visible, onCheckedChange = toggle)
                }
            }
        }
    }
}

private fun HomeCard.icon(): ImageVector = when (this) {
    HomeCard.WEATHER -> Icons.Outlined.WbSunny
    HomeCard.DAY -> Icons.Outlined.Event
    HomeCard.RECAP -> Icons.Outlined.Newspaper
}

private fun HomeCard.accent(): TileAccent = when (this) {
    HomeCard.WEATHER -> TileAccent.BLUE
    HomeCard.DAY -> TileAccent.BLUE
    HomeCard.RECAP -> TileAccent.GREEN
}
