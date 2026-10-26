package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.AUTO_HINT
import com.platinum20.dashboard.capture.CaptureComposer
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureKindTag
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.CapturePreview
import com.platinum20.dashboard.capture.VoiceCapture
import kotlinx.coroutines.launch

/**
 * Il + toccato: lo stesso fumetto di vetro della dettatura, ma con dentro il campo per scrivere.
 * Sta sopra la barra e sale con la tastiera; inviato, diventa il pallino che vola alle Note.
 * Se manca qualcosa (il permesso del calendario, il giorno di un evento) si apre il dialogo del +.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.CaptureComposerLayer(onNeedsDialog: (String) -> Unit) {
    val request = CaptureComposer.request
    val open = request != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var text by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(request) {
        if (request != null) text = TextFieldValue(request, TextRange(request.length))
    }
    BackHandler(open) { CaptureComposer.close() }
    // Il primo "indietro" lo usa la tastiera per chiudersi: anche quello annulla la nota.
    val imeVisible = WindowInsets.isImeVisible
    var imeShown by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, open) {
        if (!open) imeShown = false
        else if (imeVisible) imeShown = true
        else if (imeShown) CaptureComposer.close()
    }

    fun send() {
        val value = text.text.trim()
        CaptureComposer.close()
        if (value.isEmpty()) return
        haptic(Haptic.CONFIRM)
        scope.launch {
            when (val outcome = VoiceCapture.save(context, value)) {
                VoiceCapture.Outcome.NeedsDialog -> onNeedsDialog(value)
                is VoiceCapture.Outcome.Saved ->
                    CaptureFeedback.show(CaptureNotice(outcome.mode, outcome.message, outcome.undo))
            }
        }
    }

    // Un velo leggero: toccando fuori dal fumetto si chiude.
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(tween(160)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(Unit) { detectTapGestures { CaptureComposer.close() } },
        )
    }
    // Senza tastiera sta sopra la barra, come il fumetto della voce; con la tastiera le sta sopra.
    val lift by animateDpAsState(if (imeVisible) 8.dp else 92.dp, label = "composerLift")
    AnimatedVisibility(
        open,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(bottom = lift),
    ) {
        val mode = remember(text.text) { CaptureGuess.guess(text.text) }
        val shape = RoundedCornerShape(22.dp)
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = cardBorder(),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(shape),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f).padding(top = 6.dp, bottom = 10.dp)) {
                        val style = MaterialTheme.typography.bodyLarge
                        if (text.text.isEmpty()) {
                            Text(AUTO_HINT, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 24.dp)
                                .autoFocus()
                                .semantics { contentDescription = "Scrivi" },
                        )
                    }
                    // La X annulla: la nota non si salva.
                    IconButton(onClick = { haptic(Haptic.TICK); CaptureComposer.close() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Annulla",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        onClick = ::send,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp).bouncyPress(0.88f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Inserisci", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                // Non una scelta ma una risposta: che cosa diventa e cosa ha capito Canvas.
                if (text.text.isNotBlank()) {
                    Column(Modifier.padding(bottom = 4.dp)) {
                        CaptureKindTag(mode)
                        CapturePreview(mode, text.text)
                    }
                }
            }
        }
    }
}
