package com.platinum20.dashboard.ui.screens

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

/** Ordine e visibilità delle card della Dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val layout by AppSettings.homeLayout.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalizza Oggi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
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
                    "Usa le frecce per spostare le card e l'interruttore per mostrarle o nasconderle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
            itemsIndexed(layout.order, key = { _, card -> card.name }) { index, card ->
                val visible = card !in layout.hidden
                ListItem(
                    modifier = Modifier
                        .animateItem()
                        .padding(vertical = 2.dp)
                        .clip(MaterialTheme.shapes.large),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    headlineContent = { Text(card.label) },
                    supportingContent = { Text(card.description) },
                    leadingContent = {
                        Switch(checked = visible, onCheckedChange = { AppSettings.setCardVisible(card, it) })
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { AppSettings.moveCard(card, -1) }, enabled = index > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Sposta su")
                            }
                            IconButton(onClick = { AppSettings.moveCard(card, 1) }, enabled = index < layout.order.lastIndex) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Sposta giù")
                            }
                        }
                    },
                )
            }
        }
    }
}
