package com.platinum20.dashboard.ui.screens

import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.calendar.EventDetail
import com.platinum20.dashboard.tickets.TicketQrStore

/**
 * Il QR del biglietto nella pagina dell'evento, preso dalla mail. Bianco e grande, da mostrare al controllo;
 * toccandolo lo schermo va alla luminosità massima (i lettori ai tornelli lo vogliono), di nuovo per tornare.
 * Se la mail non ha codici, o Gmail non è collegato, non c'è niente.
 */
@Composable
fun TicketCodes(event: EventDetail) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val saved = remember(event.id) { TicketQrStore.cached(context, event.id) }
    val files by produceState(saved, event.id) {
        value = TicketQrStore.find(context, event)
    }
    val images = remember(files) {
        files.mapNotNull { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
    }
    if (images.isEmpty()) return
    var bright by remember { mutableStateOf(false) }
    val window = remember(context) { context.findActivity()?.window }
    DisposableEffect(bright, window) {
        val w = window
        if (bright && w != null) {
            val before = w.attributes.screenBrightness
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            onDispose { w.attributes = w.attributes.apply { screenBrightness = before } }
        } else onDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { img ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        bright = !bright
                        haptic(if (bright) Haptic.TOGGLE_ON else Haptic.TOGGLE_OFF)
                    }
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    img,
                    contentDescription = "Codice del biglietto",
                    contentScale = ContentScale.Fit,
                    // Pixel netti: un codice sfumato i lettori non lo prendono.
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .fillMaxWidth(if (img.width > img.height * 2) 1f else 0.78f)
                        .aspectRatio(img.width.toFloat() / img.height),
                )
            }
        }
        DetailNote(if (bright) "Luminosità al massimo: tocca il codice per tornare" else "Dalla mail del biglietto · tocca per la luminosità al massimo")
    }
}
