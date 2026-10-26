package com.platinum20.dashboard.capture

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Una cosa appena salvata dal + (scritta o detta): il messaggio e come toglierla. */
internal data class CaptureNotice(
    val mode: CaptureMode,
    val message: String,
    val undo: (suspend () -> Unit)?,
    /** Per ciò che non è una nota (una canzone riconosciuta): l'icona e la scheda dove vola il pallino. */
    val icon: ImageVector = mode.icon,
    val target: String? = null,
    /** False per le conferme che non salvano niente (es. "Nota eliminata · Annulla"): il fumetto sparisce senza pallino. */
    val fly: Boolean = true,
)

/**
 * La conferma dopo un salvataggio, nello stile di Canvas: un fumetto di vetro sopra la barra
 * che poi diventa un pallino e vola dove la cosa è finita (la scheda Note, o la sua sezione).
 * Funziona solo con l'app in primo piano; altrimenti chi salva usa un avviso normale.
 */
internal object CaptureFeedback {
    private val _events = MutableSharedFlow<CaptureNotice>(extraBufferCapacity = 4)
    val events: SharedFlow<CaptureNotice> = _events.asSharedFlow()

    /** Vero mentre la schermata principale è visibile e sa mostrare il fumetto. */
    @Volatile
    var active = false

    /** False se la conferma va data in un altro modo (es. dalla finestra della condivisione). */
    fun show(notice: CaptureNotice): Boolean = active && _events.tryEmit(notice)
}

/**
 * Dove può atterrare il pallino: le schede in basso ("tab:notes", "tab:home") e, dentro le Note,
 * le sezioni di ogni tipo (chiave = nome del CaptureMode). Si registrano con [captureTarget].
 */
internal object CaptureTargets {
    val rects = mutableStateMapOf<String, Rect>()

    /** Quante volte è atterrato un pallino su ogni chiave: chi si registra può fare un piccolo sobbalzo. */
    val arrivals = mutableStateMapOf<String, Int>()

    const val NOTES_TAB = "tab:notes"
    const val HOME_TAB = "tab:home"
    const val MEDIA_TAB = "tab:media"

    fun arrived(key: String) {
        arrivals[key] = (arrivals[key] ?: 0) + 1
    }
}

/** Segna questo elemento come punto d'arrivo del pallino per [key]. */
internal fun Modifier.captureTarget(key: String): Modifier = composed {
    DisposableEffect(key) { onDispose { CaptureTargets.rects.remove(key) } }
    onGloballyPositioned { CaptureTargets.rects[key] = it.boundsInRoot() }
}

/** Per le sezioni delle Note: il pallino di un promemoria atterra sulla sezione dei promemoria, e così via. */
internal fun Modifier.captureTarget(mode: CaptureMode): Modifier = captureTarget(mode.name)
