package com.platinum20.dashboard.ui.screens

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer

/**
 * La finestra di dialogo di Canvas: come AlertDialog, ma di vetro.
 * Da Android 12 la pagina dietro si sfoca e la finestra lascia intravedere la sfocatura;
 * se il telefono spegne la sfocatura (risparmio energetico, telefoni lenti) torna piena.
 */
@Composable
fun CanvasDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val shape = MaterialTheme.shapes.extraLarge
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.glassDialog(shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/**
 * Il fondo di vetro di una finestra di dialogo, da mettere sul suo contenitore (con fondo trasparente).
 * Non può usare il vetro liquido della barra: la finestra è un'altra e non vede le pagine,
 * quindi chiede al sistema di sfocare quello che c'è dietro. Niente ombra, come per il resto del vetro.
 */
fun Modifier.glassDialog(shape: Shape): Modifier = composed {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val radius = with(LocalDensity.current) { Glass.BLUR.roundToPx() }
    var blurred by remember { mutableStateOf(false) }
    // Il velo scuro di partenza, da rimettere se la sfocatura si spegne.
    val baseDim = remember(window) { window?.attributes?.dimAmount ?: 0.6f }
    if (window != null && Build.VERSION.SDK_INT >= 31) {
        DisposableEffect(window, dark) {
            val listener = Consumer<Boolean> { on ->
                blurred = on
                window.blurBehind(on, radius, if (on) Glass.scrim(dark) else baseDim)
            }
            val manager = window.windowManager
            // Nei test e su qualche telefono il servizio non risponde: resta la finestra piena.
            val registered = runCatching {
                listener.accept(manager.isCrossWindowBlurEnabled)
                manager.addCrossWindowBlurEnabledListener(listener)
            }.isSuccess
            onDispose { if (registered) runCatching { manager.removeCrossWindowBlurEnabledListener(listener) } }
        }
    }
    val card = MaterialTheme.colorScheme.card
    // Stesso velo e stesso bordo del vetro della barra e dei fumetti ([Glass]).
    val rim = Glass.rim(dark, MaterialTheme.colorScheme.outlineVariant)
    this
        .clip(shape)
        .background(card.copy(alpha = if (blurred) Glass.tint(dark) else 1f))
        .border(1.dp, rim, shape)
}

private fun Window.blurBehind(on: Boolean, radius: Int, dim: Float) {
    if (Build.VERSION.SDK_INT < 31) return
    if (on) addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) else clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    attributes = attributes.apply {
        blurBehindRadius = if (on) radius else 0
        dimAmount = dim
    }
}
