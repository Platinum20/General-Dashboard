package com.platinum20.dashboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.settings.AppTheme

// Palette classica (tema "Sistema" e riserva per Android < 12).
private val ClassicLight = lightColorScheme(
    primary = Color(0xFF465D91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF575E71),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    tertiaryContainer = Color(0xFFFBD7FC),
    onTertiaryContainer = Color(0xFF29132D),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

private val ClassicDark = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF14305F),
    primaryContainer = Color(0xFF2E4677),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBFC6DC),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDEBCDF),
    tertiaryContainer = Color(0xFF583E5B),
    onTertiaryContainer = Color(0xFFFBD7FC),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

/** Il tema in uso è scuro? Serve anche per il colore delle icone di sistema. */
@Composable
fun isAppInDarkTheme(theme: AppTheme): Boolean =
    theme == AppTheme.AMOLED || isSystemInDarkTheme()

@Composable
fun GeneralDashboardTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = isAppInDarkTheme(theme)
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when (theme) {
        AppTheme.DYNAMIC ->
            if (dynamicAvailable) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) ClassicDark else ClassicLight
            }
        AppTheme.SYSTEM -> if (dark) ClassicDark else ClassicLight
        AppTheme.AMOLED -> {
            // Accento dai colori del telefono quando possibile, sfondi neri.
            val base = if (dynamicAvailable) dynamicDarkColorScheme(context) else ClassicDark
            base.toAmoled()
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceBright = Color(0xFF262626),
    surfaceVariant = Color(0xFF1A1A1A),
)
