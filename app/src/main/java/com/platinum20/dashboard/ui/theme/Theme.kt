package com.platinum20.dashboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
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

/**
 * Caratteri un filo più compatti di quelli standard di Material 3:
 * con i caratteri grandi del telefono le schermate restano leggibili senza diventare enormi.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
    )
}

/** Angoli arrotondati ma non "a bolla": card e gruppi usano large. */
private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
