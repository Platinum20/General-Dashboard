package com.platinum20.dashboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.platinum20.dashboard.R
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

// Palette "Canvas": carta calda, inchiostro quasi nero, un blu deciso come accento
// e un arancio per ciò che scade o è urgente. È anche la riserva per Android < 12.
private val CanvasLight = lightColorScheme(
    primary = Color(0xFF3550E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E8FF),
    onPrimaryContainer = Color(0xFF0E1A66),
    inversePrimary = Color(0xFF9AABFF),
    secondary = Color(0xFF5E5A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECE8DF),
    onSecondaryContainer = Color(0xFF1B1A17),
    tertiary = Color(0xFFD9572B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE5DA),
    onTertiaryContainer = Color(0xFF4A1606),
    error = Color(0xFFC8341F),
    errorContainer = Color(0xFFFDE2DC),
    onErrorContainer = Color(0xFF410C03),
    background = Color(0xFFF5F3EE),
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFF5F3EE),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFECE8DF),
    onSurfaceVariant = Color(0xFF6F6B62),
    surfaceTint = Color(0xFF3550E8),
    inverseSurface = Color(0xFF1B1A17),
    inverseOnSurface = Color(0xFFF5F3EE),
    outline = Color(0xFFA39F95),
    outlineVariant = Color(0xFFE5E1D8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E2DA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Color(0xFFF0EDE6),
    surfaceContainerHigh = Color(0xFFEAE6DE),
    surfaceContainerHighest = Color(0xFFE3DFD6),
)

private val CanvasDark = darkColorScheme(
    primary = Color(0xFF9AABFF),
    onPrimary = Color(0xFF0D1A5C),
    primaryContainer = Color(0xFF26326E),
    onPrimaryContainer = Color(0xFFDDE2FF),
    inversePrimary = Color(0xFF3550E8),
    secondary = Color(0xFFC9C4BA),
    onSecondary = Color(0xFF2E2C28),
    secondaryContainer = Color(0xFF2E2C28),
    onSecondaryContainer = Color(0xFFEDE9E1),
    tertiary = Color(0xFFFF8F66),
    onTertiary = Color(0xFF4A1606),
    tertiaryContainer = Color(0xFF4A2416),
    onTertiaryContainer = Color(0xFFFFDBCD),
    error = Color(0xFFFF8A73),
    errorContainer = Color(0xFF5C1A0E),
    onErrorContainer = Color(0xFFFFDAD2),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEFECE6),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFEFECE6),
    surfaceVariant = Color(0xFF2A2825),
    onSurfaceVariant = Color(0xFFA8A39A),
    surfaceTint = Color(0xFF9AABFF),
    inverseSurface = Color(0xFFEFECE6),
    inverseOnSurface = Color(0xFF1B1A17),
    outline = Color(0xFF6F6B62),
    outlineVariant = Color(0xFF2C2A27),
    surfaceBright = Color(0xFF2E2C29),
    surfaceDim = Color(0xFF121110),
    surfaceContainerLowest = Color(0xFF0D0C0B),
    surfaceContainerLow = Color(0xFF1C1B19),
    surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2A2825),
    surfaceContainerHighest = Color(0xFF34322F),
)

/** Il tema in uso è scuro? Serve anche per il colore delle icone di sistema. */
@Composable
fun isAppInDarkTheme(@Suppress("UNUSED_PARAMETER") theme: AppTheme): Boolean = isSystemInDarkTheme()

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
                if (dark) CanvasDark else CanvasLight
            }
        AppTheme.CANVAS -> if (dark) CanvasDark else CanvasLight
        // "Puro": gli stessi colori di Canvas su bianco pieno di giorno e su nero AMOLED di sera.
        AppTheme.AMOLED -> if (dark) CanvasDark.toAmoled() else CanvasLight.toDeepWhite()
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

/** Tutto bianco, senza la carta calda: le card si staccano solo con il bordo grigio chiaro. */
private fun ColorScheme.toDeepWhite(): ColorScheme = copy(
    background = Color.White,
    surface = Color.White,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF6F6F6),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFEAEAEA),
    surfaceDim = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFFF2F2F2),
    secondaryContainer = Color(0xFFF2F2F2),
    outlineVariant = Color(0xFFE6E6E6),
    inverseOnSurface = Color.White,
)

/** Inter per il testo, Inter Display (più stretto) per i titoli grandi: inclusi nell'app. */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
private val InterDisplay = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

/**
 * Caratteri compatti: titoli decisi e stretti, testo piccolo ma leggibile.
 * Con i caratteri grandi del telefono le schermate restano ordinate senza diventare enormi.
 */
private val AppTypography = Typography().let { base ->
    val text = { style: TextStyle -> style.copy(fontFamily = Inter) }
    val title = { style: TextStyle -> style.copy(fontFamily = InterDisplay) }
    base.copy(
        displayLarge = title(base.displayLarge).copy(fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
        displayMedium = title(base.displayMedium).copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        displaySmall = title(base.displaySmall).copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.8).sp),
        headlineLarge = title(base.headlineLarge).copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineMedium = title(base.headlineMedium).copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = title(base.headlineSmall).copy(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = title(base.titleLarge).copy(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = text(base.titleMedium).copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = text(base.titleSmall).copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = text(base.bodyLarge).copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
        bodyMedium = text(base.bodyMedium).copy(fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
        bodySmall = text(base.bodySmall).copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelLarge = text(base.labelLarge).copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = text(base.labelMedium).copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelSmall = text(base.labelSmall).copy(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
    )
}

/** Angoli morbidi ma sobri: card e gruppi usano large, le card grandi extraLarge. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)
