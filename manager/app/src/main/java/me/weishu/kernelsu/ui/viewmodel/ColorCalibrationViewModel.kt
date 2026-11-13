package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

@Immutable
data class ColorCalibrationUiState(
    val supported: Boolean = false,
    val enableSupported: Boolean = false,
    val enabled: Boolean = true,
    val red: Int = 256,
    val green: Int = 256,
    val blue: Int = 256,
    val saturation: Int = 255,
    val value: Int = 255,
    val contrast: Int = 255,
    val hue: Int = 0,
) {
    companion object {
        const val RGB_MIN = 0
        const val RGB_MAX = 256

        // msm_drm KCAL treats 255 as neutral for saturation/value/contrast and
        // allows roughly +/-128 around it.
        const val SATURATION_MIN = 128
        const val SATURATION_MAX = 383
        const val HUE_MIN = 0
        const val HUE_MAX = 1536

        const val DEFAULT_RED = 256
        const val DEFAULT_GREEN = 256
        const val DEFAULT_BLUE = 256
        const val DEFAULT_SATURATION = 255
        const val DEFAULT_VALUE = 255
        const val DEFAULT_CONTRAST = 255
        const val DEFAULT_HUE = 0
    }
}

/**
 * Drives the Color Calibration tab. Node discovery happens once in
 * [SysfsNodeCache.warmUpColor]; this view model only reads the cached nodes
 * and writes the values the user picks.
 */
class ColorCalibrationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        SysfsNodeCache.colorSnapshot()?.let(::parse) ?: ColorCalibrationUiState(),
    )
    val uiState: StateFlow<ColorCalibrationUiState> = _uiState.asStateFlow()

    /** Reads the calibration nodes once per screen open, reusing cached paths. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        var paths = SysfsNodeCache.colorNodePaths()
        if (paths == null) {
            SysfsNodeCache.warmUpColor()
            paths = SysfsNodeCache.colorNodePaths()
        }
        val cached = paths ?: return@withContext
        val raw = SysfsNodeCache.readColorNodes(cached)
        if (raw.isBlank()) return@withContext
        SysfsNodeCache.updateColorSnapshot(raw)
        _uiState.value = parse(raw)
    }

    fun setEnabled(enabled: Boolean) {
        val paths = SysfsNodeCache.colorNodePaths()
        if (paths?.enable == null) return
        _uiState.value = _uiState.value.copy(enabled = enabled)
        write { SysfsNodeCache.writeColorNode(paths.enable, if (enabled) "1" else "0") }
    }

    fun setRed(value: Int) = updateRgb { it.copy(red = value.coerceIn(ColorCalibrationUiState.RGB_MIN, ColorCalibrationUiState.RGB_MAX)) }
    fun setGreen(value: Int) = updateRgb { it.copy(green = value.coerceIn(ColorCalibrationUiState.RGB_MIN, ColorCalibrationUiState.RGB_MAX)) }
    fun setBlue(value: Int) = updateRgb { it.copy(blue = value.coerceIn(ColorCalibrationUiState.RGB_MIN, ColorCalibrationUiState.RGB_MAX)) }

    fun setSaturation(value: Int) {
        val next = _uiState.value.copy(
            saturation = value.coerceIn(ColorCalibrationUiState.SATURATION_MIN, ColorCalibrationUiState.SATURATION_MAX),
        )
        _uiState.value = next
        write { SysfsNodeCache.writeColorNode(it.saturation, next.saturation.toString()) }
    }

    fun setValue(value: Int) {
        val next = _uiState.value.copy(
            value = value.coerceIn(ColorCalibrationUiState.SATURATION_MIN, ColorCalibrationUiState.SATURATION_MAX),
        )
        _uiState.value = next
        write { SysfsNodeCache.writeColorNode(it.value, next.value.toString()) }
    }

    fun setContrast(value: Int) {
        val next = _uiState.value.copy(
            contrast = value.coerceIn(ColorCalibrationUiState.SATURATION_MIN, ColorCalibrationUiState.SATURATION_MAX),
        )
        _uiState.value = next
        write { SysfsNodeCache.writeColorNode(it.contrast, next.contrast.toString()) }
    }

    fun setHue(value: Int) {
        val next = _uiState.value.copy(
            hue = value.coerceIn(ColorCalibrationUiState.HUE_MIN, ColorCalibrationUiState.HUE_MAX),
        )
        _uiState.value = next
        write { SysfsNodeCache.writeColorNode(it.hue, next.hue.toString()) }
    }

    /** Restores every calibration slider to the driver defaults. */
    fun reset() {
        val defaults = _uiState.value.copy(
            enabled = true,
            red = ColorCalibrationUiState.DEFAULT_RED,
            green = ColorCalibrationUiState.DEFAULT_GREEN,
            blue = ColorCalibrationUiState.DEFAULT_BLUE,
            saturation = ColorCalibrationUiState.DEFAULT_SATURATION,
            value = ColorCalibrationUiState.DEFAULT_VALUE,
            contrast = ColorCalibrationUiState.DEFAULT_CONTRAST,
            hue = ColorCalibrationUiState.DEFAULT_HUE,
        )
        _uiState.value = defaults
        write { paths ->
            writeRgb(paths, defaults)
            SysfsNodeCache.writeColorNode(paths.enable, "1")
            SysfsNodeCache.writeColorNode(paths.saturation, defaults.saturation.toString())
            SysfsNodeCache.writeColorNode(paths.value, defaults.value.toString())
            SysfsNodeCache.writeColorNode(paths.contrast, defaults.contrast.toString())
            SysfsNodeCache.writeColorNode(paths.hue, defaults.hue.toString())
        }
    }

    private fun updateRgb(transform: (ColorCalibrationUiState) -> ColorCalibrationUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        write { paths -> writeRgb(paths, next) }
    }

    private fun writeRgb(paths: me.weishu.kernelsu.data.ColorNodePaths, state: ColorCalibrationUiState) {
        if (paths.hasSeparateRgb) {
            SysfsNodeCache.writeColorNode(paths.red, state.red.toString())
            SysfsNodeCache.writeColorNode(paths.green, state.green.toString())
            SysfsNodeCache.writeColorNode(paths.blue, state.blue.toString())
        } else {
            SysfsNodeCache.writeColorNode(paths.rgb, "${state.red} ${state.green} ${state.blue}")
        }
    }

    private fun write(block: (me.weishu.kernelsu.data.ColorNodePaths) -> Unit) {
        val paths = SysfsNodeCache.colorNodePaths() ?: return
        viewModelScope.launch(Dispatchers.IO) { block(paths) }
    }

    private fun parse(raw: String): ColorCalibrationUiState {
        fun node(key: String): String {
            val marker = "__${key}__"
            val start = raw.indexOf(marker)
            if (start < 0) return ""
            val valueStart = start + marker.length
            val next = raw.indexOf("__", valueStart)
            return raw.substring(valueStart, if (next < 0) raw.length else next).trim()
        }

        val redRaw = node("RED").toIntOrNull()
        val greenRaw = node("GREEN").toIntOrNull()
        val blueRaw = node("BLUE").toIntOrNull()
        val combined = node("RGB").split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
        val separateRgb = redRaw != null && greenRaw != null && blueRaw != null

        val enableRaw = node("ENABLE")
        val saturation = node("SATURATION").toIntOrNull()
        val value = node("VALUE").toIntOrNull()
        val contrast = node("CONTRAST").toIntOrNull()
        val hue = node("HUE").toIntOrNull()

        return ColorCalibrationUiState(
            supported = separateRgb || combined.size >= 3,
            enableSupported = enableRaw.isNotBlank(),
            enabled = enableRaw.toIntOrNull()?.let { it != 0 } ?: true,
            red = (redRaw ?: combined.getOrNull(0))?.coerceIn(ColorCalibrationUiState.RGB_MIN, ColorCalibrationUiState.RGB_MAX)
                ?: ColorCalibrationUiState.DEFAULT_RED,
            green = (greenRaw ?: combined.getOrNull(1))?.coerceIn(ColorCalibrationUiState.RGB_MIN, ColorCalibrationUiState.RGB_MAX)
                ?: ColorCalibrationUiState.DEFAULT_GREEN,
            blue = (blueRaw ?: combined.getOrNull(2))?.coerceIn(ColorCalibrationUiState.RGB_MIN, ColorCalibrationUiState.RGB_MAX)
                ?: ColorCalibrationUiState.DEFAULT_BLUE,
            saturation = saturation?.coerceIn(ColorCalibrationUiState.SATURATION_MIN, ColorCalibrationUiState.SATURATION_MAX)
                ?: ColorCalibrationUiState.DEFAULT_SATURATION,
            value = value?.coerceIn(ColorCalibrationUiState.SATURATION_MIN, ColorCalibrationUiState.SATURATION_MAX)
                ?: ColorCalibrationUiState.DEFAULT_VALUE,
            contrast = contrast?.coerceIn(ColorCalibrationUiState.SATURATION_MIN, ColorCalibrationUiState.SATURATION_MAX)
                ?: ColorCalibrationUiState.DEFAULT_CONTRAST,
            hue = hue?.coerceIn(ColorCalibrationUiState.HUE_MIN, ColorCalibrationUiState.HUE_MAX)
                ?: ColorCalibrationUiState.DEFAULT_HUE,
        )
    }
}
