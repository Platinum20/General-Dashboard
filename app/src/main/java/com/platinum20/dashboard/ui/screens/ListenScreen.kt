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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.radio.RadioStore

/**
 * La pagina Radio (1.529, scelta B/C): le tue radio in loghi tondi, in onda adesso, i generi e le più ascoltate
 * in Italia. Cerca = trascinare giù.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    RadioStore.init(context)
    val radio by RadioStore.now.collectAsState()
    val popular by RadioStore.popular.collectAsState()
    // Da quando la Radio è un tasto in Per te (1.488) le tue radio stanno qui, sopra le più ascoltate.
    val favorites by RadioStore.favorites.collectAsState()
    val recent by RadioStore.recent.collectAsState()
    LaunchedEffect(Unit) { RadioStore.loadPopular() }
    var genre by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = "Radio",
                scrollBehavior = barScroll,
                subtitle = "Le preferite in cima",
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            // Senza spazio fra gli elementi: le righe di una sezione formano una card sola (1.423); i titoli hanno il loro.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
        ) {
            radioSections(popular, favorites, recent, radio, genre, onGenre = { genre = it })
        }
    }
}
