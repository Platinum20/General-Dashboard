package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Riordino trascinando, per qualunque LazyColumn (la coda, le card di Oggi...): si prende la riga
 * dalla maniglia ([reorderHandle]) e la si porta dove si vuole; le altre si spostano per farle posto.
 * Le voci sono indicate dalla loro chiave nella lista; [onMove] sposta [from] al posto di [to].
 */
class ReorderState internal constructor(private val list: LazyListState) {
    internal var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    internal var canMove: (Any) -> Boolean = { true }

    /** La riga che si sta trascinando (la sua chiave), null se nessuna. */
    var dragging by mutableStateOf<Any?>(null)
        private set

    /** Di quanto la riga trascinata è spostata dal suo posto nella lista, in pixel. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Dopo uno scambio si aspetta che la lista lo mostri, prima di valutarne un altro. */
    private var expectedIndex = -1

    fun isDragging(key: Any): Boolean = dragging == key

    internal fun start(key: Any) {
        dragging = key
        offset = 0f
        expectedIndex = -1
    }

    internal fun drag(delta: Float) {
        val key = dragging ?: return
        offset += delta
        val items = list.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        if (expectedIndex >= 0 && current.index != expectedIndex) return
        expectedIndex = -1
        val top = current.offset + offset
        val bottom = top + current.size
        val next = items.firstOrNull { it.index == current.index + 1 }
        val previous = items.firstOrNull { it.index == current.index - 1 }
        when {
            // Oltre metà della riga sotto (o sopra): si scambiano, e la riga resta sotto il dito.
            next != null && canMove(next.key) && bottom > next.offset + next.size / 2f -> {
                onMove(key, next.key)
                offset -= (next.offset + next.size) - (current.offset + current.size)
                expectedIndex = current.index + 1
            }
            previous != null && canMove(previous.key) && top < previous.offset + previous.size / 2f -> {
                onMove(key, previous.key)
                offset += current.offset - previous.offset
                expectedIndex = current.index - 1
            }
        }
    }

    internal fun end() {
        dragging = null
        offset = 0f
        expectedIndex = -1
    }
}

/** [onMove] sposta la voce [from] al posto della voce [to] (chiavi della lista). */
@Composable
fun rememberReorderState(
    list: LazyListState,
    canMove: (Any) -> Boolean = { true },
    onMove: (from: Any, to: Any) -> Unit,
): ReorderState {
    val state = remember(list) { ReorderState(list) }
    state.onMove = onMove
    state.canMove = canMove
    return state
}

/** La riga [key]: mentre la si trascina segue il dito, sta sopra le altre e si solleva un poco. */
fun Modifier.reorderItem(state: ReorderState, key: Any): Modifier {
    val lifted = state.isDragging(key)
    return zIndex(if (lifted) 1f else 0f).graphicsLayer {
        if (lifted) {
            translationY = state.offset
            scaleX = 1.02f
            scaleY = 1.02f
            shadowElevation = 24f
        }
    }
}

/** La maniglia della riga [key]: toccata e trascinata, sposta la riga. */
fun Modifier.reorderHandle(state: ReorderState, key: Any, onStart: () -> Unit = {}): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = {
                state.start(key)
                onStart()
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        ) { change, amount ->
            change.consume()
            state.drag(amount.y)
        }
    }
