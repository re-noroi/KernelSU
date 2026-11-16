package me.weishu.kernelsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import me.weishu.kernelsu.ui.webui.MonetColorsProvider

@Composable
fun MaterialKernelSUTheme(
    appSettings: AppSettings,
    minimal: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    // Monet is its own switch in the Minimal UI, independent of brightness, so a
    // pitch-black theme can still use a Monet accent and a light theme can opt out.
    val monet = if (minimal) appSettings.minimalMonet else appSettings.colorMode.isMonet
    val dynamicColor = appSettings.keyColor == 0

    val colorScheme = rememberKernelSUColorScheme(
        seedColor = if (dynamicColor) Color.Unspecified else Color(appSettings.keyColor),
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val animatedColorScheme = colorScheme.animateAsState()

    val finalColorScheme = if (minimal) {
        flatMinimalScheme(
            scheme = animatedColorScheme,
            dark = darkTheme,
            amoled = amoledMode,
            monet = monet,
            cardAlpha = appSettings.minimalCardAlpha,
        )
    } else {
        animatedColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = finalColorScheme,
        motionScheme = if (minimal) MotionScheme.standard() else MotionScheme.expressive(),
        typography = Typography,
        shapes = if (minimal) FlatShapes else Shapes(),
        content = {
            MonetColorsProvider.UpdateCss(finalColorScheme)
            content()
        }
    )
}
