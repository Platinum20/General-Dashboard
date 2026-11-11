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
 * "Tutte le radio" (1.475, Antonio: la radio sta dentro Musica): l'elenco delle più ascoltate in Italia, per
 * sceglierne una nuova o metterla fra le preferite. Le preferite stanno nella fila Radio di Musica. Cerca = trascinare giù.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    RadioStore.init(context)
    val radio by RadioStore.now.collectAsState()
    val popular by RadioStore.popular.collectAsState()
    LaunchedEffect(Unit) { RadioStore.loadPopular() }
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = "Radio",
                scrollBehavior = barScroll,
                subtitle = "Le più ascoltate in Italia",
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            // Senza spazio fra gli elementi: le righe di una sezione formano una card sola (1.423); i titoli hanno il loro.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
        ) {
            radioSections(popular, emptyList(), emptyList(), radio)
        }
    }
}
