package tech.vauldex.dekita.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sizes in sp so text grows with the system font scale.
private val base = Typography()

internal val DekitaTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Immutable
data class DekitaTextStyles(
    /** The order number shown to staff: large, bold, tabular digits. Use with [DekitaColors.orderNumber]. */
    val orderNumber: TextStyle,
)

internal val DefaultDekitaTextStyles = DekitaTextStyles(
    orderNumber = TextStyle(
        fontSize = 96.sp,
        lineHeight = 104.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    ),
)

internal val LocalDekitaTextStyles = staticCompositionLocalOf { DefaultDekitaTextStyles }
