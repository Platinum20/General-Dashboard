package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Il passaggio con elementi condivisi fra le pagine: il titolo di una riga del calendario vola dove sta
 * nella sua scheda, la card Calendario si allarga fino a diventare la pagina intera. Fuori dalla
 * navigazione (anteprime, test) questi due valori sono null e non succede niente.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** L'animazione della pagina in cui si trova chi chiede l'elemento condiviso. */
val LocalRouteScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Le chiavi che legano una riga alla sua scheda: le stesse delle righe del calendario ([DueRow.key]). */
object SharedKeys {
    const val CALENDAR = "calendar"
    /** Le card della Home che si allargano nella loro pagina (1.357). */
    const val WEATHER = "weather"
    const val SIM = "sim"
    const val BANK = "bank"
    fun document(id: Long) = "document-$id"

    fun item(kind: ItemKind, id: Long): String? = when (kind) {
        ItemKind.TASK -> "task-$id"
        ItemKind.PARCEL -> "parcel-$id"
        ItemKind.EXPENSE -> "expense-$id"
        ItemKind.HABIT, ItemKind.FIXED -> null
    }
}

/**
 * Il titolo di una riga vola solo fra la riga e la sua scheda, mai fra due liste (card Calendario ↔ pagina,
 * pagina ↔ spese del mese): lì la riga si staccava dalla card che si allarga e "saltava" per conto suo.
 * Ogni lista ha il suo [scope]; la scheda usa quello della lista da cui è stata aperta ([origin]).
 */
object SharedRows {
    const val PAGE = "page"
    const val HOME = "home"
    const val EXPENSES = "expenses"

    /** L'ultima lista da cui si è aperta una riga: la scheda lo legge una volta, all'apertura. */
    var origin: String = PAGE

    /** La chiave condivisa di una riga nella lista [scope]; null per le righe senza scheda (eventi, note). */
    fun key(scope: String, rowKey: String): String? = rowKey.takeIf { it.isSharedRow() }?.let { "$scope/$it" }
}

/**
 * Questo elemento è lo stesso in due pagine (stessa [key]): passando dall'una all'altra si sposta e
 * cambia misura invece di sparire e ricomparire. [rounded] = gli angoli durante il volo (le card).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedItem(key: String?, rounded: Boolean = false): Modifier {
    val shared = LocalSharedScope.current
    val route = LocalRouteScope.current
    if (key == null || shared == null || route == null) return this
    return with(shared) {
        val state = rememberSharedContentState("shared:$key")
        val resize = SharedTransitionScope.ResizeMode.ScaleToBounds()
        if (rounded) {
            this@sharedItem.sharedBounds(state, route, resizeMode = resize, clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(18.dp)))
        } else {
            this@sharedItem.sharedBounds(state, route, resizeMode = resize)
        }
    }
}
