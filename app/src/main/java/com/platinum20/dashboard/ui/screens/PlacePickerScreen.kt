package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.weather.GeoPlace
import com.platinum20.dashboard.weather.WeatherStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Scelta della località del meteo: posizione del telefono o una città cercata a mano. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manual = remember { WeatherStore.manualPlace(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        error = null
        searching = false
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        runCatching { WeatherStore.search(query) }
            .onSuccess { results = it }
            .onFailure { if (it !is CancellationException) error = "Ricerca non riuscita. Controlla la connessione." }
        searching = false
    }

    fun choose(place: GeoPlace?) {
        WeatherStore.setManualPlace(context, place)
        onBack()
    }

    Scaffold(
        topBar = {
            TopLevelBar(
                title = "Località del meteo",
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cerca una città") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        when {
                            searching -> CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
                            query.isNotEmpty() -> IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancella ricerca")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // Cerca sulla tastiera sceglie il primo risultato, come toccarlo.
                    keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(::choose) }),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    modifier = Modifier.bouncyPress(0.98f).clickable { choose(null) },
                    leadingContent = { Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("La mia posizione") },
                    supportingContent = { Text("Segue il telefono ovunque tu sia") },
                    trailingContent = { if (manual == null) Icon(Icons.Outlined.Check, contentDescription = "Attiva") },
                )
            }
            if (manual != null) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(manual.name) },
                        supportingContent = { Text("Località scelta") },
                        trailingContent = { Icon(Icons.Outlined.Check, contentDescription = "Attiva") },
                    )
                }
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(results) { place ->
                ListItem(
                    modifier = Modifier.bouncyPress(0.98f).clickable { choose(place) },
                    leadingContent = { Icon(Icons.Outlined.LocationCity, contentDescription = null) },
                    headlineContent = { Text(place.name) },
                    supportingContent = { if (place.detail.isNotBlank()) Text(place.detail) },
                )
            }
            if (query.trim().length >= 2 && !searching && results.isEmpty() && error == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Nessuna città trovata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
