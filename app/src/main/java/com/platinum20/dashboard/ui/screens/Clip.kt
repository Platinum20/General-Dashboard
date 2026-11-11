package com.platinum20.dashboard.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import com.platinum20.dashboard.capture.CaptureFeedback

/**
 * L'unico modo di copiare negli appunti. Da Android 13 il sistema mostra già il suo avviso
 * "Copiato" e nessuna app può toglierlo: lì il nostro fumetto sarebbe un doppione (Antonio, 2026-11-11).
 */
internal object Clip {
    /** Il fumetto di vetro serve solo dove il sistema non avvisa da sé. */
    fun ownNotice(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk < Build.VERSION_CODES.TIRAMISU

    fun copy(context: Context, label: String, text: String, notice: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        if (ownNotice()) CaptureFeedback.tell(context, notice, Icons.Outlined.ContentCopy)
    }
}
