package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.update.WhatsNew
import kotlinx.coroutines.delay

/**
 * Al primo avvio dopo un aggiornamento sale il fumetto di vetro con le novità della versione,
 * una volta sola. Si chiude toccandolo, toccando fuori o portandolo di lato.
 */
@Composable
internal fun BoxScope.WhatsNewLayer() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<WhatsNew.Notes?>(null) }
    var visible by remember { mutableStateOf(false) }
    val swipe = rememberSwipeAwayState()
    LaunchedEffect(Unit) {
        notes = WhatsNew.pending(context) ?: return@LaunchedEffect
        // Prima si apre l'app, poi arriva il fumetto.
        delay(900)
        visible = true
    }
    val shown = notes ?: return
    BottomBubble(visible = visible, swipe = swipe, onDismiss = { visible = false }) {
        WhatsNewCard(shown) { visible = false }
    }
}

@Composable
private fun WhatsNewCard(notes: WhatsNew.Notes, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClose,
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth().glass(shape),
    ) {
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "NOVITÀ DELLA ${notes.version}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(notes.title, style = MaterialTheme.typography.titleMedium)
            notes.points.forEach { point ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(point, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
