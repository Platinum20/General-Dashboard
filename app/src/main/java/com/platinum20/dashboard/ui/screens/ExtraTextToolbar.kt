package com.platinum20.dashboard.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Una voce in più nel menu che compare tenendo premuto il testo (1.606): "Salva negli Appunti" nel lettore delle
 * News, "Detta qui" nell'editor delle note. [run] riceve il testo selezionato (null se c'è solo il cursore).
 */
internal class TextToolbarExtra(
    val title: String,
    /** Solo con del testo selezionato (es. salvare la frase), non col cursore e basta. */
    val needsSelection: Boolean = false,
    val run: (selected: String?) -> Unit,
)

/**
 * Il menu di sistema (Copia, Taglia, Incolla, Seleziona tutto) con le voci di Canvas in fondo: lo stesso fumetto
 * flottante di Android, così sembra quello di sempre. Il testo selezionato si legge copiandolo e rimettendo subito
 * negli appunti del telefono quello che c'era prima.
 */
internal class ExtraTextToolbar(
    private val view: View,
    private val extras: () -> List<TextToolbarExtra>,
) : TextToolbar {
    private var mode: ActionMode? = null
    private var rect: Rect = Rect.Zero
    private var copy: (() -> Unit)? = null
    private var paste: (() -> Unit)? = null
    private var cut: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.rect = rect
        copy = onCopyRequested
        paste = onPasteRequested
        cut = onCutRequested
        selectAll = onSelectAllRequested
        status = TextToolbarStatus.Shown
        val current = mode
        if (current == null) {
            mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            current.invalidate()
            current.invalidateContentRect()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        mode?.finish()
        mode = null
    }

    /** Il testo selezionato, passando dagli appunti del telefono e rimettendo com'erano. */
    private fun selectedText(): String? {
        val doCopy = copy ?: return null
        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val before = runCatching { clipboard.primaryClip }.getOrNull()
        doCopy()
        val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(view.context)?.toString() }.getOrNull()
        runCatching {
            when {
                before != null -> clipboard.setPrimaryClip(before)
                Build.VERSION.SDK_INT >= 28 -> clipboard.clearPrimaryClip()
            }
        }
        return text?.takeIf { it.isNotBlank() }
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            fill(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            fill(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                ID_COPY -> copy?.invoke()
                ID_PASTE -> paste?.invoke()
                ID_CUT -> cut?.invoke()
                ID_SELECT_ALL -> selectAll?.invoke()
                else -> {
                    val extra = currentExtras.getOrNull(item.itemId - ID_EXTRA) ?: return false
                    val selected = if (copy != null) selectedText() else null
                    extra.run(selected)
                }
            }
            if (item.itemId != ID_SELECT_ALL) mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@ExtraTextToolbar.mode = null
            status = TextToolbarStatus.Hidden
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            outRect.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        }
    }

    private var currentExtras: List<TextToolbarExtra> = emptyList()

    private fun fill(menu: Menu) {
        var order = 0
        copy?.let { menu.add(0, ID_COPY, order++, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        cut?.let { menu.add(0, ID_CUT, order++, android.R.string.cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        paste?.let { menu.add(0, ID_PASTE, order++, android.R.string.paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        selectAll?.let { menu.add(0, ID_SELECT_ALL, order++, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        currentExtras = extras().filter { !it.needsSelection || copy != null }
        currentExtras.forEachIndexed { i, extra ->
            menu.add(0, ID_EXTRA + i, order++, extra.title).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
    }

    private companion object {
        const val ID_COPY = 1
        const val ID_PASTE = 2
        const val ID_CUT = 3
        const val ID_SELECT_ALL = 4
        const val ID_EXTRA = 100
    }
}

/** Il testo dentro ha il menu del tocco lungo con le voci [extras] in più. */
@Composable
internal fun WithTextExtras(extras: List<TextToolbarExtra>, content: @Composable () -> Unit) {
    val view = LocalView.current
    val latest = rememberUpdatedState(extras)
    val toolbar = remember(view) { ExtraTextToolbar(view) { latest.value } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar, content = content)
}
