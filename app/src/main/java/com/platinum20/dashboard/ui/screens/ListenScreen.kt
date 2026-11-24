package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.radio.RadioStore

/**
 * La pagina Radio (1.572, stile delle pagine di Spotify e YouTube): niente barra del titolo, il blocco grande in cima
 * fa da titolo; sotto le tue radio, i generi e le più ascoltate in Italia. Cerca = trascinare giù.
 */
@Composable
fun ListenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    RadioStore.init(context)
    val radio by RadioStore.now.collectAsState()
    val popular by RadioStore.popular.collectAsState()
    val favorites by RadioStore.favorites.collectAsState()
    val recent by RadioStore.recent.collectAsState()
    LaunchedEffect(Unit) { RadioStore.loadPopular() }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Il blocco in cima va a tutta larghezza; le righe portano il loro margine, come nelle pagine di Spotify.
        contentPadding = PaddingValues(bottom = listBottom(96.dp)),
    ) {
        radioSections(popular, favorites, recent, radio, genre, onGenre = { genre = it }, onBack = onBack)
    }
}
