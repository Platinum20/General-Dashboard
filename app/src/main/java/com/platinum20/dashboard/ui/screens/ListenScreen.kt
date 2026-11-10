package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.radio.RadioStore

/**
 * Radio, una pagina che scorre senza pillole: preferite, ascoltate di recente, le più ascoltate in Italia.
 * Dalla 1.437 (Antonio) i podcast stanno in Musica; qui resta solo la radio. Cerca = trascinare giù.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    RadioStore.init(context)
    val radio by RadioStore.now.collectAsState()
    val popular by RadioStore.popular.collectAsState()
    val favorites by RadioStore.favorites.collectAsState()
    val recent by RadioStore.recent.collectAsState()
    LaunchedEffect(Unit) { RadioStore.loadPopular() }
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = "Radio",
                scrollBehavior = barScroll,
                subtitle = if (radio?.playing == true) "In diretta: ${radio?.station?.name}" else "Le tue radio e le più ascoltate",
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            // Senza spazio fra gli elementi: le righe di una sezione formano una card sola (1.423); i titoli hanno il loro.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
        ) {
            radioSections(popular, favorites, recent, radio)
        }
    }
}
