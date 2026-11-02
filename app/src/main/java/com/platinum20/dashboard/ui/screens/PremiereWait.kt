package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.youtube.YtPremiere
import com.platinum20.dashboard.media.youtube.YtUpcomingException
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Attesa di un'anteprima di YouTube al posto del video: copertina scura, orario, conto alla rovescia
 * e "Avvisami". Passato l'orario si riprova da soli finché YouTube non dà la diretta.
 */
@Composable
fun PremiereWait(upcoming: YtUpcomingException, videoId: String, title: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val start = upcoming.startsAt
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Instant.now()
        }
    }
    val left = start?.let { YtPremiere.countdown(it, now) }
    // Orario passato (o sconosciuto): YouTube fa partire l'anteprima con qualche minuto di ritardo.
    val waitingStart = left == null
    LaunchedEffect(waitingStart) {
        if (!waitingStart) return@LaunchedEffect
        delay(if (start == null) 60_000 else 20_000)
        onRetry()
    }
    val reminded by remember(videoId) { YtPremiere.reminded(context, videoId) }.collectAsState(initial = false)

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)))
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ANTEPRIMA",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                left ?: "Sta per iniziare…",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            if (start != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Inizia " + YtPremiere.whenText(start, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
            if (start != null && left != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    if (reminded) YtPremiere.cancel(context, videoId) else YtPremiere.remind(context, videoId, title, start)
                }) {
                    Icon(
                        if (reminded) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(if (reminded) "Ti avviso quando inizia" else "Avvisami", color = Color.White)
                }
            }
        }
    }
}
