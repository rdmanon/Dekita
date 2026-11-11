package tech.vauldex.dekita.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light values come from the Android artboards of the Passnize Mobile Design canvas.
// The design has no dark mode: dark values are M3 tones of the same brand hue and need designer review.

internal val LightColors = lightColorScheme(
    primary = Color(0xFFB3410E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF7A2E06),
    inversePrimary = Color(0xFFFFB596),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160C),
    tertiary = Color(0xFF685E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceTint = Color(0xFFB3410E),
    inverseSurface = Color(0xFF392E2A),
    inverseOnSurface = Color(0xFFFFEDE7),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF6E4DE),
    surfaceContainerHighest = Color(0xFFF1DFD8),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF581D00),
    primaryContainer = Color(0xFF7A2E06),
    onPrimaryContainer = Color(0xFFFFDBCC),
    inversePrimary = Color(0xFFB3410E),
    secondary = Color(0xFFE7BDAE),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD5C68D),
    onTertiary = Color(0xFF393005),
    tertiaryContainer = Color(0xFF50461A),
    onTertiaryContainer = Color(0xFFF1E2A7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A110E),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1A110E),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceTint = Color(0xFFFFB596),
    inverseSurface = Color(0xFFF1DFD8),
    inverseOnSurface = Color(0xFF392E2A),
    outline = Color(0xFFA08D86),
    outlineVariant = Color(0xFF53433D),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF140C09),
    surfaceContainerLow = Color(0xFF231A16),
    surfaceContainer = Color(0xFF271E1A),
    surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D322E),
)

@Immutable
data class BadgeColors(val container: Color, val content: Color)

/** Colors the M3 scheme has no role for. Every content/container pair is at least 4.5:1. */
@Immutable
data class DekitaColors(
    val open: BadgeColors,
    val liveDot: Color,
    val pickup: BadgeColors,
    val inStore: BadgeColors,
    val bothModes: BadgeColors,
    val paused: BadgeColors,
    val full: BadgeColors,
    /** The order number on the ticket: at least 7:1 so staff can read it from a distance. */
    val orderNumber: BadgeColors,
)

internal val LightDekitaColors = DekitaColors(
    open = BadgeColors(Color(0xFFD4EFD9), Color(0xFF1B5E2F)),
    liveDot = Color(0xFF2E7D32),
    pickup = BadgeColors(Color(0xFFDDE3FF), Color(0xFF1E3A8A)),
    inStore = BadgeColors(Color(0xFFF0DBFF), Color(0xFF5B1F8C)),
    bothModes = BadgeColors(Color(0xFFFFDBCC), Color(0xFF7A2E06)),
    paused = BadgeColors(Color(0xFFEDE0DB), Color(0xFF53433D)),
    full = BadgeColors(Color(0xFFFFDAD6), Color(0xFF93000A)),
    orderNumber = BadgeColors(Color(0xFFFFDBCC), Color(0xFF3A0B00)),
)

internal val DarkDekitaColors = DekitaColors(
    open = BadgeColors(Color(0xFF1B4D2A), Color(0xFFB6F0C3)),
    liveDot = Color(0xFF6FD08A),
    pickup = BadgeColors(Color(0xFF26367A), Color(0xFFDDE3FF)),
    inStore = BadgeColors(Color(0xFF4A1A72), Color(0xFFF0DBFF)),
    bothModes = BadgeColors(Color(0xFF7A2E06), Color(0xFFFFDBCC)),
    paused = BadgeColors(Color(0xFF3D322E), Color(0xFFD8C2BA)),
    full = BadgeColors(Color(0xFF93000A), Color(0xFFFFDAD6)),
    orderNumber = BadgeColors(Color(0xFF7A2E06), Color(0xFFFFEDE6)),
)

internal val LocalDekitaColors = staticCompositionLocalOf { LightDekitaColors }
