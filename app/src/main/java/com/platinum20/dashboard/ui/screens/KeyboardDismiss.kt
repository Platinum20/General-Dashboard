package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Trascinare una pagina chiude la tastiera (in tutta l'app, note comprese). Conta solo il dito che si è davvero mosso
 * (1.604): un tocco sul testo fa scorrere la pagina da sola per portare la riga sopra la tastiera, e quello scorrimento
 * arriva anche lui come "UserInput"; prima chiudeva la tastiera appena aperta. Il cursore resta: un tocco la riapre.
 */
internal fun shouldHideKeyboard(source: NestedScrollSource, available: Offset, imeOpen: Boolean, fingerDragging: Boolean): Boolean =
    imeOpen && fingerDragging && source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 0.5f

/** Lo scorrimento che chiude la tastiera e l'occhio sul dito che lo abilita: vanno messi tutti e due sulla radice. */
class HideKeyboardOnDrag internal constructor(private val connection: NestedScrollConnection, private val fingers: Modifier) {
    fun modifier(): Modifier = fingers.nestedScroll(connection)
}

/**
 * Da mettere sopra tutte le pagine (`Modifier.then(x.modifier())`): non consuma nulla, chiude solo la tastiera.
 * La tastiera si guarda al momento dello scorrimento (non in composizione: la radice non si ricompone a ogni apertura).
 */
@Composable
fun rememberHideKeyboardOnDrag(): HideKeyboardOnDrag {
    val view = LocalView.current
    val kb by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    return remember {
        // Vero solo mentre un dito è giù e si è mosso oltre la soglia del tocco.
        var dragging = false
        val fingers = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                dragging = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                    }
                } finally {
                    dragging = false
                }
            }
        }
        val connection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && dragging) {
                    val ime = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (shouldHideKeyboard(source, available, ime, dragging)) kb?.hide()
                }
                return Offset.Zero
            }
        }
        HideKeyboardOnDrag(connection, fingers)
    }
}
