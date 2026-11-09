package com.platinum20.dashboard.ui.screens

import android.graphics.BitmapFactory
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.platinum20.dashboard.calendar.EventDetail
import com.platinum20.dashboard.tickets.TicketQrStore

/**
 * I codici del biglietto dell'evento, presi dalla mail (salvati o cercati la prima volta).
 * Vuoto finché non ci sono, o se la mail non ha codici, o Gmail non è collegato.
 */
@Composable
fun rememberTicketCodes(event: EventDetail?): List<ImageBitmap> {
    val context = LocalContext.current
    val id = event?.id
    val saved = remember(id) { if (event == null) emptyList() else TicketQrStore.cached(context, event.id) }
    val files by produceState(saved, id) {
        if (event != null) value = TicketQrStore.find(context, event)
    }
    return remember(files) {
        files.mapNotNull { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
    }
}

/**
 * Il biglietto aperto dal tasto in basso della pagina dell'evento (Antonio: non sempre aperto sotto il titolo).
 * Bianco e grande, con lo schermo alla luminosità massima finché resta aperto (i lettori ai tornelli lo vogliono);
 * si chiude toccando ovunque o con Indietro.
 */
@Composable
fun TicketSheet(codes: List<ImageBitmap>, onClose: () -> Unit) {
    val activityWindow = LocalContext.current.findActivity()?.window
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow, activityWindow) {
            val windows = listOfNotNull(dialogWindow, activityWindow)
            val before = windows.map { it.attributes.screenBrightness }
            windows.forEach { it.brightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL) }
            onDispose { windows.zip(before).forEach { (w, b) -> w.brightness(b) } }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                codes.forEach { img ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            img,
                            contentDescription = "Codice del biglietto",
                            contentScale = ContentScale.Fit,
                            // Pixel netti: un codice sfumato i lettori non lo prendono.
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(img.width.toFloat() / img.height),
                        )
                    }
                }
                Text(
                    "Tocca per chiudere",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun Window.brightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
