package com.platinum20.dashboard.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Trascinare una pagina chiude la tastiera (in tutta l'app, note comprese). Conta solo il dito:
 * gli scorrimenti fatti dal codice (la nota che tiene la riga sopra la tastiera) non passano di qui,
 * e il lancio dopo il rilascio nemmeno. Il cursore resta: un tocco sul testo la riapre.
 */
internal fun shouldHideKeyboard(source: NestedScrollSource, available: Offset, imeOpen: Boolean): Boolean =
    imeOpen && source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 0.5f

/**
 * Da mettere con `Modifier.nestedScroll` sopra tutte le pagine: non consuma nulla, chiude solo la tastiera.
 * La tastiera si guarda al momento dello scorrimento (non in composizione: la radice non si ricompone a ogni apertura).
 */
@Composable
fun rememberHideKeyboardOnDrag(): NestedScrollConnection {
    val view = LocalView.current
    val kb by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val ime = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (shouldHideKeyboard(source, available, ime)) kb?.hide()
                }
                return Offset.Zero
            }
        }
    }
}
