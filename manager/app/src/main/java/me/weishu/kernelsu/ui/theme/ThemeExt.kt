package me.weishu.kernelsu.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

/**
 * Pitch-black flattening for the AMOLED mode.
 *
 * Every container collapses to pure black. Kept for the Material and Miuix
 * skins, which have no flat surface ladder of their own.
 */
fun ColorScheme.amoledBackground(amoled: Boolean): ColorScheme =
    if (!amoled) this
    else copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    )

@Composable
fun rememberKernelSUColorScheme(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
): ColorScheme {
    val context = LocalContext.current
    val seed = if (seedColor == Color.Unspecified) {
        val baseScheme = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else expressiveLightColorScheme()
        }
        baseScheme.primary
    } else {
        seedColor
    }
    return rememberDynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        isAmoled = isAmoled,
        style = paletteStyle,
        specVersion = colorSpec.effectiveFor(paletteStyle),
    ).amoledBackground(isAmoled)
}

/**
 * Turns a generated Material scheme into the flat Minimal design.
 *
 * The Minimal UI is flat: solid grounds and a single radius family. Nothing is
 * translucent glass, so the result reads the same over a wallpaper, over pure
 * black, and in light mode.
 *
 * @param monet when false the accent falls back to plain ink for the current
 *   brightness instead of the wallpaper-derived palette.
 * @param cardAlpha opacity of the box containers. 1f keeps them solid; lower
 *   values let the wallpaper show through every box so the user can dial how
 *   much of the backdrop is visible. The ground and the outlines stay opaque.
 */
fun flatMinimalScheme(
    scheme: ColorScheme,
    dark: Boolean,
    amoled: Boolean,
    monet: Boolean,
    cardAlpha: Float = 1f,
): ColorScheme {
    val ground: Color = when {
        amoled -> Color.Black
        dark -> scheme.background
        // Light mode: sit the cards on a softly tinted ground, never on a
        // near-white one, otherwise white cards would have no edge at all.
        else -> lerp(scheme.background, Color.Black, 0.04f)
    }

    // A fixed ladder of surface steps keeps one flat system in every mode:
    // light cards are white on a tinted ground, dark cards step up from the
    // ground, and pitch black keeps a barely-there step so cards still read.
    val surfaces: ColorScheme = when {
        amoled -> scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF08080A),
            surfaceContainer = Color(0xFF0D0D10),
            surfaceContainerHigh = Color(0xFF141418),
            surfaceContainerHighest = Color(0xFF1B1B20),
            surfaceBright = Color(0xFF1F1F24),
            onSurface = Color(0xFFEDEDF0),
            onSurfaceVariant = Color(0xFFA8A8B0),
            onBackground = Color(0xFFEDEDF0),
            outline = Color(0xFF3A3A42),
            outlineVariant = Color(0xFF24242A),
        )

        dark -> scheme.copy(
            background = ground,
            surface = ground,
            surfaceContainerLowest = ground,
            surfaceContainerLow = lerp(ground, Color.White, 0.04f),
            surfaceContainer = lerp(ground, Color.White, 0.06f),
            surfaceContainerHigh = lerp(ground, Color.White, 0.09f),
            surfaceContainerHighest = lerp(ground, Color.White, 0.12f),
            surfaceBright = lerp(ground, Color.White, 0.10f),
            surfaceDim = ground,
            outline = lerp(ground, Color.White, 0.28f),
            outlineVariant = lerp(ground, Color.White, 0.14f),
        )

        else -> scheme.copy(
            background = ground,
            surface = ground,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = lerp(Color.White, ground, 0.45f),
            surfaceContainerHighest = lerp(Color.White, ground, 0.75f),
            surfaceBright = Color.White,
            surfaceDim = lerp(ground, Color.Black, 0.04f),
            outline = lerp(ground, Color.Black, 0.22f),
            outlineVariant = lerp(ground, Color.Black, 0.10f),
        )
    }

    // With Monet off the accent becomes plain ink, not white: a white accent
    // is invisible in light mode, which is what the old glass theme got away
    // with only because every surface was black.
    val accented: ColorScheme = if (monet) {
        surfaces
    } else if (dark || amoled) {
        surfaces.copy(
            primary = Color(0xFFE8E8EA),
            onPrimary = Color(0xFF141416),
            primaryContainer = if (amoled) Color(0xFF232326) else Color(0xFF2C2C30),
            onPrimaryContainer = Color(0xFFF5F5F7),
            inversePrimary = Color(0xFF2C2C30),
        )
    } else {
        surfaces.copy(
            primary = Color(0xFF1C1C1E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE4E4E8),
            onPrimaryContainer = Color(0xFF141416),
            inversePrimary = Color(0xFFE4E4E8),
        )
    }

    return accented.copy(
        onBackground = accented.onSurface,
    ).withCardAlpha(cardAlpha)
}

/**
 * Applies the user's box transparency to the container ladder.
 *
 * Every box the Minimal UI paints comes from this ladder (cards, segmented
 * settings rows, the home dashboard blocks, the search field, the dock pill,
 * dialogs and sheets), so fading the ladder moves all boxes together, which is
 * what the Theme screen slider promises.
 *
 * The colours stay internally consistent: each component reads the same
 * translucent value from the scheme and passes it straight back to
 * `contentColorFor()`, so content colours keep resolving to `onSurface`/the
 * matching `on*` role. Only the ground (`surface`, `background`) and
 * `surfaceContainerLowest` stay opaque, so a transparent box never reveals the
 * window behind the app.
 */
private fun ColorScheme.withCardAlpha(alpha: Float): ColorScheme {
    if (alpha >= 0.999f) return this
    val a = alpha.coerceIn(0.2f, 1f)
    return copy(
        surfaceContainerLow = surfaceContainerLow.copy(alpha = a),
        surfaceContainer = surfaceContainer.copy(alpha = a),
        surfaceContainerHigh = surfaceContainerHigh.copy(alpha = a),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = a),
        surfaceBright = surfaceBright.copy(alpha = a),
    )
}

@Composable
fun ColorScheme.animateAsState(): ColorScheme {
    @Composable
    fun animateColor(color: Color): Color = animateColorAsState(
        targetValue = color,
        animationSpec = spring(),
        label = "theme_color_animation"
    ).value

    return ColorScheme(
        primary = animateColor(primary),
        onPrimary = animateColor(onPrimary),
        primaryContainer = animateColor(primaryContainer),
        onPrimaryContainer = animateColor(onPrimaryContainer),
        inversePrimary = animateColor(inversePrimary),
        secondary = animateColor(secondary),
        onSecondary = animateColor(onSecondary),
        secondaryContainer = animateColor(secondaryContainer),
        onSecondaryContainer = animateColor(onSecondaryContainer),
        tertiary = animateColor(tertiary),
        onTertiary = animateColor(onTertiary),
        tertiaryContainer = animateColor(tertiaryContainer),
        onTertiaryContainer = animateColor(onTertiaryContainer),
        background = animateColor(background),
        onBackground = animateColor(onBackground),
        surface = animateColor(surface),
        onSurface = animateColor(onSurface),
        surfaceVariant = animateColor(surfaceVariant),
        onSurfaceVariant = animateColor(onSurfaceVariant),
        surfaceTint = animateColor(surfaceTint),
        inverseSurface = animateColor(inverseSurface),
        inverseOnSurface = animateColor(inverseOnSurface),
        error = animateColor(error),
        onError = animateColor(onError),
        errorContainer = animateColor(errorContainer),
        onErrorContainer = animateColor(onErrorContainer),
        outline = animateColor(outline),
        outlineVariant = animateColor(outlineVariant),
        scrim = animateColor(scrim),
        surfaceBright = animateColor(surfaceBright),
        surfaceDim = animateColor(surfaceDim),
        surfaceContainer = animateColor(surfaceContainer),
        surfaceContainerHigh = animateColor(surfaceContainerHigh),
        surfaceContainerHighest = animateColor(surfaceContainerHighest),
        surfaceContainerLow = animateColor(surfaceContainerLow),
        surfaceContainerLowest = animateColor(surfaceContainerLowest),

        primaryFixed = animateColor(primaryFixed),
        primaryFixedDim = animateColor(primaryFixedDim),
        onPrimaryFixed = animateColor(onPrimaryFixed),
        onPrimaryFixedVariant = animateColor(onPrimaryFixedVariant),
        secondaryFixed = animateColor(secondaryFixed),
        secondaryFixedDim = animateColor(secondaryFixedDim),
        onSecondaryFixed = animateColor(onSecondaryFixed),
        onSecondaryFixedVariant = animateColor(onSecondaryFixedVariant),
        tertiaryFixed = animateColor(tertiaryFixed),
        tertiaryFixedDim = animateColor(tertiaryFixedDim),
        onTertiaryFixed = animateColor(onTertiaryFixed),
        onTertiaryFixedVariant = animateColor(onTertiaryFixedVariant)
    )
}
