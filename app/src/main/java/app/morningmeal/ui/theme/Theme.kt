package app.morningmeal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.morningmeal.data.ThemeMode

// Monochrome on purpose: ink on paper. The only "color" is how bright something is.
private val Paper = Color(0xFFFAFAF7)
private val Ink = Color(0xFF141414)
private val PaperDark = Color(0xFF0E0E0E)
private val InkDark = Color(0xFFEDEDEA)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Color(0xFF5C5C58),
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEFEA),
    onSurfaceVariant = Color(0xFF6B6B66),
    surfaceContainer = Color(0xFFF3F3EF),
    surfaceContainerHigh = Color(0xFFEDEDE8),
    surfaceContainerHighest = Color(0xFFE7E7E2),
    surfaceContainerLow = Color(0xFFF6F6F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFFCFCFC9),
    outlineVariant = Color(0xFFE2E2DC),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = PaperDark,
    secondary = Color(0xFFA3A39E),
    onSecondary = PaperDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF1C1C1B),
    onSurfaceVariant = Color(0xFF8F8F8A),
    surfaceContainer = Color(0xFF171716),
    surfaceContainerHigh = Color(0xFF1D1D1C),
    surfaceContainerHighest = Color(0xFF242423),
    surfaceContainerLow = Color(0xFF131312),
    surfaceContainerLowest = Color(0xFF090909),
    outline = Color(0xFF3A3A38),
    outlineVariant = Color(0xFF262625),
    error = Color(0xFFF2B8B5),
)

private val Base = Typography()

private val AppTypography = Base.copy(
    // The hero number on the calculator.
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Thin,
        fontSize = 112.sp,
        lineHeight = 116.sp,
        letterSpacing = (-4).sp,
    ),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Light),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Normal),
)

@Composable
fun MorningmealTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
