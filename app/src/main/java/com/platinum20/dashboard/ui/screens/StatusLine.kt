package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant

/**
 * Lo stato di un collegamento (WindTre, Creators Pro, Gmail), per la regola "lo stato dice la
 * verità" (revisione 1.402): un dato vecchio lo dice, un accesso scaduto lo dice, e l'azione è lì accanto.
 */
data class LinkStatus(val kind: Kind, val line: String?) {
    enum class Kind { OK, STALE, EXPIRED }

    /** Il dato sotto va mostrato sbiadito: c'è, ma non è di adesso. */
    val faded: Boolean get() = kind != Kind.OK

    companion object {
        /**
         * [readAt] = l'ultima lettura buona (0 = mai). Vecchio oltre [staleAfter]: di solito più volte il giro
         * di rilettura, così una lettura saltata non accende niente.
         */
        fun of(linked: Boolean, expired: Boolean, readAt: Long, now: Long, staleAfter: Long, ago: (Long) -> String = ::agoLabel): LinkStatus = when {
            !linked -> LinkStatus(Kind.OK, null)
            expired -> LinkStatus(Kind.EXPIRED, "Accesso scaduto" + (readAt.takeIf { it > 0 }?.let { " · dati di " + ago(it) } ?: ""))
            readAt > 0 && now - readAt > staleAfter -> LinkStatus(Kind.STALE, "Letto " + ago(readAt))
            else -> LinkStatus(Kind.OK, null)
        }

        private fun agoLabel(at: Long): String = relativeTime(Instant.ofEpochMilli(at))
    }
}

/**
 * La riga di stato dentro una card, sopra il dato: icona, la frase e l'azione ("Rientra", "Aggiorna").
 * Entra dall'alto una volta, con [Motion.settle] (tabella delle animazioni della revisione).
 */
@Composable
fun StatusLine(status: LinkStatus, action: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier, start: Dp = 16.dp) {
    val text = status.line
    val shown = remember(status.kind) { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = text != null }
    AnimatedVisibility(shown, enter = fadeIn(Motion.settle()) + expandVertically(Motion.settle(), expandFrom = Alignment.Top), modifier = modifier) {
        val warn = status.kind == LinkStatus.Kind.EXPIRED
        // Arancio = attenzione (regola dei colori): l'accesso scaduto; il dato solo vecchio resta grigio.
        val tint = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = start, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (warn) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (action != null && onAction != null) CanvasButton(action, onClick = onAction)
        }
    }
}

/** Quanto è sbiadito il dato vecchio sotto la riga di stato. */
const val FADED_ALPHA = 0.5f
