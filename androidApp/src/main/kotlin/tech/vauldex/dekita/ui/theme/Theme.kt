package tech.vauldex.dekita.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

internal val DekitaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Dimens {
    /** Android's 48 dp minimum touch target. */
    val MinTouchTarget = 48.dp
    val ScreenPadding = 16.dp
}

/** Follows the system light/dark setting. No dynamic color, so the brand color stays fixed. */
@Composable
fun DekitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDekitaColors provides if (darkTheme) DarkDekitaColors else LightDekitaColors,
        LocalDekitaTextStyles provides DefaultDekitaTextStyles,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DekitaTypography,
            shapes = DekitaShapes,
            content = content,
        )
    }
}

object DekitaTheme {
    val colors: DekitaColors
        @Composable @ReadOnlyComposable get() = LocalDekitaColors.current

    val textStyles: DekitaTextStyles
        @Composable @ReadOnlyComposable get() = LocalDekitaTextStyles.current
}
