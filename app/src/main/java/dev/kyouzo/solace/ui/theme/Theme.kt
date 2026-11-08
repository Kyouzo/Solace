package dev.kyouzo.solace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** "Twilight" dark scheme: deep violet surfaces stepped in small tonal increments so cards read without borders. */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCDBDFF), onPrimary = Color(0xFF2F1C6A),
    primaryContainer = Color(0xFF473482), onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFCBC2DC), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF2D2840), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFF2B8D2), onTertiary = Color(0xFF4A2539),
    tertiaryContainer = Color(0xFF633B50), onTertiaryContainer = Color(0xFFFFD8EB),
    background = Color(0xFF100E17), onBackground = Color(0xFFE7E1EC),
    surface = Color(0xFF14111B), onSurface = Color(0xFFE7E1EC),
    surfaceVariant = Color(0xFF2A2536), onSurfaceVariant = Color(0xFFC9C3D4),
    outline = Color(0xFF948E9F), outlineVariant = Color(0xFF3B3547),
    surfaceContainerLowest = Color(0xFF0C0A12), surfaceContainerLow = Color(0xFF181521),
    surfaceContainer = Color(0xFF1C1926), surfaceContainerHigh = Color(0xFF262231),
    surfaceContainerHighest = Color(0xFF312C3D),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5B44B8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF1A0C5C),
    secondary = Color(0xFF615B71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF8B4A6B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EB), onTertiaryContainer = Color(0xFF3A0B27),
    background = Color(0xFFFCF8FF), onBackground = Color(0xFF1C1A22),
    surface = Color(0xFFFCF8FF), onSurface = Color(0xFF1C1A22),
    surfaceVariant = Color(0xFFE6E0EC), onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A7582), outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F1FB),
    surfaceContainer = Color(0xFFF0EBF6), surfaceContainerHigh = Color(0xFFEAE5F0),
    surfaceContainerHighest = Color(0xFFE4DFEA)
)

private val SolaceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SolaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = SolaceShapes,
        content = content
    )
}
