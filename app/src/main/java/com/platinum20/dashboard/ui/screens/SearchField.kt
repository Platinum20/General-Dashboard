package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Quanto è alta la pillola di ricerca: un dito pieno, come la barra in basso. */
private val FIELD_HEIGHT = 54.dp

/**
 * L'unico campo di ricerca di Canvas (Cerca, Località del meteo), rifatto nella 1.448: una pillola
 * piena, senza bordo, appena più scura dello sfondo, così si vede in tutti i temi (anche Puro bianco).
 * Toccandola si accende piano (fondo e lente diventano inchiostro, non blu: il blu resta per "adesso" e
 * per le azioni). La X per svuotare compare da sola appena c'è testo, con la molla viva.
 */
@Composable
fun CanvasSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** Prima della X: per esempio il pallino che dice "sto cercando". */
    trailing: (@Composable () -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        if (focused) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        Motion.settle(), label = "fondo ricerca",
    )
    val lens by animateColorAsState(
        if (focused || value.isNotEmpty()) colors.onSurface else colors.onSurfaceVariant,
        Motion.settle(), label = "lente ricerca",
    )
    val haptic = rememberHaptic()
    val text = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = text,
        cursorBrush = SolidColor(colors.onSurface),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        modifier = modifier.heightIn(min = FIELD_HEIGHT),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = FIELD_HEIGHT)
                    .clip(CircleShape)
                    .background(fill)
                    .padding(start = 18.dp, end = 8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = lens, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = text,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (trailing != null) trailing()
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = scaleIn(Motion.lively(), initialScale = 0.6f) + fadeIn(Motion.lively()),
                    exit = scaleOut(Motion.settle(), targetScale = 0.6f) + fadeOut(Motion.settle()),
                ) {
                    // Una X piccola su un tondo appena più scuro: si vede, ma non pesa più del testo.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.onSurface.copy(alpha = 0.08f))
                            .clickable { haptic(Haptic.TICK); onValueChange("") }
                            .semantics { contentDescription = "Cancella ricerca" },
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                    }
                }
                if (value.isEmpty() && trailing == null) Spacer(Modifier.width(10.dp))
            }
        },
    )
}
