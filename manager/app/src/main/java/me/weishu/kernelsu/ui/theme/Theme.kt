package me.weishu.kernelsu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.util.LocalScrollAnimation
import me.weishu.kernelsu.ui.util.LocalShowSwitchIcon
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val appIconMode: Int,
    val classicUi: Boolean,
    val showSwitchIcon: Boolean,
    val scrollAnimation: Boolean,
    val modernBento: Boolean = false,
    val minimalMonet: Boolean = true,
    val showGpuVersion: Boolean = true,
)

val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

object ThemeController {
    fun getAppSettings(repo: SettingsRepository = SettingsRepositoryImpl()): AppSettings {
        val uiMode = repo.uiMode
        var colorModeValue = repo.themeMode

        val modeMonet = when (uiMode) {
            "miuix" -> repo.miuixMonet
            "minimal" -> repo.minimalMonet
            else -> null
        }
        if (modeMonet != null) {
            val colorMode = ColorMode.fromValue(colorModeValue)
            colorModeValue = if (!modeMonet && colorMode.isMonet) {
                colorMode.toNonMonetMode()
            } else if (modeMonet && !colorMode.isMonet) {
                colorMode.toMonetMode()
            } else {
                colorModeValue
            }
        }

        val colorMode = ColorMode.fromValue(colorModeValue)
        val keyColor = repo.keyColor
        val paletteStyleStr = repo.colorStyle
        val paletteStyle = try {
            PaletteStyle.valueOf(paletteStyleStr)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        val colorSpecStr = repo.colorSpec
        val colorSpec = try {
            ColorSpec.SpecVersion.valueOf(colorSpecStr)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.SPEC_2025
        }

        val appIconMode = repo.appIconMode
        val modernBento = repo.modernBento
        val classicUi = repo.classicUi
        val showSwitchIcon = repo.showSwitchIcon
        val scrollAnimation = repo.scrollAnimation
        val minimalMonet = repo.minimalMonet
        val showGpuVersion = repo.showGpuVersion

        return AppSettings(colorMode, keyColor, paletteStyle, colorSpec, appIconMode, classicUi, showSwitchIcon, scrollAnimation, modernBento, minimalMonet, showGpuVersion)
    }
}

@Composable
fun KernelSUTheme(
    appSettings: AppSettings = ThemeController.getAppSettings(),
    uiMode: UiMode = LocalUiMode.current,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColorMode provides appSettings.colorMode.value,
        LocalAppIconMode provides appSettings.appIconMode,
        LocalModernBento provides appSettings.modernBento,
        LocalClassicUi provides appSettings.classicUi,
        LocalShowSwitchIcon provides appSettings.showSwitchIcon,
        LocalScrollAnimation provides appSettings.scrollAnimation,
    ) {
        when (uiMode) {
            UiMode.Miuix -> MiuixKernelSUTheme(
                appSettings = appSettings,
                content = content
            )

            UiMode.Material -> MaterialKernelSUTheme(
                appSettings = appSettings,
                content = content
            )

            UiMode.Minimal -> {
                val wallpaper = LocalMinimalWallpaper.current
                val bitmap = rememberMinimalWallpaper(wallpaper)

                CompositionLocalProvider(LocalMinimalWallpaperBitmap provides bitmap) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Dim the wallpaper so text is readable
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = MINIMAL_MAIN_WALLPAPER_DIM))
                            )
                        }
                        MaterialKernelSUTheme(
                            appSettings = appSettings,
                            minimal = true,
                            content = content
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opaque wallpaper backdrop for pushed pages in the Minimal UI.
 *
 * The home screen lets the shared wallpaper show through at
 * [MINIMAL_MAIN_WALLPAPER_DIM]. A pushed page draws the same wallpaper itself
 * (so the page underneath can never show through while it fades) with an extra
 * ~20% dim, which keeps it slightly darker than the home screen and readable.
 */
@Composable
fun MinimalTabBackground(content: @Composable () -> Unit) {
    val bitmap = LocalMinimalWallpaperBitmap.current
    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = MINIMAL_PAGE_WALLPAPER_DIM))
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        content()
    }
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1, 4 -> false  // Force light mode
        2, 5, 6 -> true   // Force dark mode
        else -> isSystemInDarkTheme()  // Follow system (0 or default)
    }
}

val LocalColorMode = staticCompositionLocalOf { 0 }

val LocalAppIconMode = staticCompositionLocalOf { 0 }

val LocalModernBento = staticCompositionLocalOf { false }

val LocalMinimalWallpaper = staticCompositionLocalOf { "" }

/** Wallpaper bitmap shared by the Minimal home screen and every pushed page. */
val LocalMinimalWallpaperBitmap = staticCompositionLocalOf<ImageBitmap?> { null }

/** Dim applied to the wallpaper behind the Minimal home screen. */
private const val MINIMAL_MAIN_WALLPAPER_DIM = 0.4f

/** Pushed pages are ~20% dimmer than the home screen so their content stays readable. */
private const val MINIMAL_PAGE_WALLPAPER_DIM = 1f - (1f - MINIMAL_MAIN_WALLPAPER_DIM) * 0.8f

val LocalClassicUi = staticCompositionLocalOf { false }

val LocalEnableBlur = staticCompositionLocalOf { false }

val LocalEnableFloatingBottomBar = staticCompositionLocalOf { true }

val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

val LocalEnableModuleUpdateBadge = staticCompositionLocalOf { true }

val LocalModuleDescriptionMaxLines = staticCompositionLocalOf { 4 }

@Composable
private fun rememberMinimalWallpaper(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uriString) {
        value = if (uriString.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val longestEdge = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                        val sample = (longestEdge / 2048).coerceAtLeast(1)
                        decoder.setTargetSampleSize(sample)
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}
