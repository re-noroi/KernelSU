package me.weishu.kernelsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
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
        animatedColorScheme.copy(
            background = Color.Transparent,
            surface = Color.Transparent,
            surfaceContainer = Color.Black.copy(alpha = 0.75f),
            surfaceContainerLow = Color.Black.copy(alpha = 0.75f),
            surfaceContainerHigh = Color.Black.copy(alpha = 0.75f),
            surfaceContainerHighest = Color.Black.copy(alpha = 0.75f),
            surfaceBright = Color.Black.copy(alpha = 0.75f),
            surfaceDim = Color.Black.copy(alpha = 0.75f),
            onSurface = Color.White,
            onSurfaceVariant = Color.LightGray,
            onBackground = Color.White,
        )
    } else {
        animatedColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = finalColorScheme,
        motionScheme = if (minimal) MotionScheme.standard() else MotionScheme.expressive(),
        typography = Typography,
        content = {
            MonetColorsProvider.UpdateCss(finalColorScheme)
            content()
        }
    )
}
