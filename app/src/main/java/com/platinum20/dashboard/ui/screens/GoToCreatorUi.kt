package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.GoToCreator
import com.platinum20.dashboard.media.ResumeItem

/** Titolo e icona di "Vai all'artista" / "Vai al canale" per [item], o null se non ha un autore da aprire. */
internal fun goToCreatorLabel(item: ResumeItem): Pair<String, ImageVector>? = when (GoToCreator.targetOf(item)) {
    GoToCreator.Target.ARTIST -> "Vai all'artista" to Icons.Rounded.Person
    GoToCreator.Target.CHANNEL -> "Vai al canale" to Icons.Rounded.AccountCircle
    null -> null
}

/** Apre la pagina dell'artista o del canale di [item]; se non si trova lo dice nel fumetto. */
internal fun goToCreator(context: android.content.Context, item: ResumeItem, openRoute: (String) -> Unit) {
    GoToCreator.open(item, openRoute) { CaptureFeedback.tell(context, it) }
}

/** La stessa azione per i fumetti di [ActionBubble] (salvati, coda). */
internal fun goToCreatorAction(context: android.content.Context, item: ResumeItem, openRoute: ((String) -> Unit)?): BubbleAction? {
    val open = openRoute ?: return null
    val (title, icon) = goToCreatorLabel(item) ?: return null
    return BubbleAction(icon, title, detail = item.subtitle) { goToCreator(context, item, open) }
}
