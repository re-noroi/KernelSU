package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One flat type scale used by every screen.
 *
 * The whole app runs on the platform font ([FontFamily.Default]), so an OEM or
 * system-wide font override applies here without the manager shipping its own
 * font files. Only the size, weight and line height ladder is pinned, which is
 * what keeps headings, body copy and labels consistent from screen to screen.
 *
 * Flat design notes:
 *  - letter spacing is 0 on every style (no tracked-out display type),
 *  - line heights are tight multiples of the size so rows line up on a grid,
 *  - weights use 400/500/600 only, never 700+, so text stays calm at small sizes.
 */
val Typography = Typography(
    displayLarge = flatStyle(57, 64, FontWeight.Normal),
    displayMedium = flatStyle(45, 52, FontWeight.Normal),
    displaySmall = flatStyle(36, 44, FontWeight.Normal),

    headlineLarge = flatStyle(32, 40, FontWeight.SemiBold),
    headlineMedium = flatStyle(28, 36, FontWeight.SemiBold),
    headlineSmall = flatStyle(24, 32, FontWeight.SemiBold),

    titleLarge = flatStyle(22, 28, FontWeight.SemiBold),
    titleMedium = flatStyle(16, 24, FontWeight.Medium),
    titleSmall = flatStyle(14, 20, FontWeight.Medium),

    bodyLarge = flatStyle(16, 24, FontWeight.Normal),
    bodyMedium = flatStyle(14, 20, FontWeight.Normal),
    bodySmall = flatStyle(12, 16, FontWeight.Normal),

    labelLarge = flatStyle(14, 20, FontWeight.Medium),
    labelMedium = flatStyle(12, 16, FontWeight.Medium),
    labelSmall = flatStyle(11, 16, FontWeight.Medium),
)

private fun flatStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)
