package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache
import me.weishu.kernelsu.data.ThermalZonePath

/** Display order of the sections in the Thermal Monitor screen. */
enum class ThermalGroup {
    CPU,
    GPU,
    NPU,
    DSP,
    MODEM,
    CAMERA,
    VIDEO,
    MEMORY,
    CONNECTIVITY,
    POWER,
    BOARD,
    OTHER,
}

@Immutable
data class ThermalZoneInfo(
    val name: String,
    val type: String,
    val temperature: String,
    val group: ThermalGroup,
)

@Immutable
data class ThermalUiState(
    val zones: List<ThermalZoneInfo> = emptyList(),
    val hasLoaded: Boolean = false,
)

class ThermalViewModel : ViewModel() {
    // Seed from the cache warmed up when KTune was opened: the zone paths and
    // the last snapshot are already known, so the tab renders immediately
    // while the first live refresh runs.
    private val _uiState = MutableStateFlow(seedFromCache())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    /** Reads every thermal zone once. Callers decide when/whether to repeat it. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        var zones = SysfsNodeCache.thermalZonePaths().orEmpty()
        if (zones.isEmpty()) {
            // Cache was not warmed (screen opened directly); discover once.
            SysfsNodeCache.warmUpThermal()
            zones = SysfsNodeCache.thermalZonePaths().orEmpty()
        }

        if (zones.isEmpty()) {
            _uiState.value = ThermalUiState(hasLoaded = true)
            return@withContext
        }

        var raw = SysfsNodeCache.readThermalTemperatures(zones)
        var parsed = parseZones(zones, raw)
        if (parsed.isEmpty()) {
            // Stale paths (driver reloaded / zones renumbered): rediscover once.
            SysfsNodeCache.invalidateThermal()
            SysfsNodeCache.warmUpThermal()
            zones = SysfsNodeCache.thermalZonePaths().orEmpty()
            raw = SysfsNodeCache.thermalSnapshot().orEmpty()
            parsed = parseZones(zones, raw)
        }

        SysfsNodeCache.updateThermalSnapshot(raw)
        _uiState.value = ThermalUiState(zones = parsed, hasLoaded = true)
    }

    private fun seedFromCache(): ThermalUiState {
        val zones = SysfsNodeCache.thermalZonePaths().orEmpty()
        val raw = SysfsNodeCache.thermalSnapshot()
        if (zones.isEmpty() || raw == null) return ThermalUiState()
        return ThermalUiState(zones = parseZones(zones, raw), hasLoaded = true)
    }

    // Output of SysfsNodeCache.buildThermalCommand: "<temp path> <temp>".
    private fun parseZones(zones: List<ThermalZonePath>, raw: String): List<ThermalZoneInfo> {
        if (raw.isBlank()) return emptyList()
        val byPath = zones.associateBy { it.tempPath }
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(' ', limit = 2)
                if (parts.size < 2) return@mapNotNull null

                val path = byPath[parts[0]] ?: return@mapNotNull null
                val rawTemp = parts[1].trim().toLongOrNull() ?: return@mapNotNull null

                val celsius = toCelsius(rawTemp)
                if (celsius < MIN_CELSIUS || celsius > MAX_CELSIUS) return@mapNotNull null

                path.index to ThermalZoneInfo(
                    name = "tz${path.index}",
                    type = path.type,
                    temperature = String.format(Locale.US, "%.1f °C", celsius),
                    group = classify(path.type),
                )
            }
            .sortedBy { it.first }
            .map { it.second }
            .toList()
    }

    // thermal_zone temp is normally millidegrees; tolerate kernels that report
    // tenths of a degree or plain Celsius.
    private fun toCelsius(raw: Long): Double = when {
        abs(raw) >= 1_000L -> raw / 1000.0
        abs(raw) >= 100L -> raw / 10.0
        else -> raw.toDouble()
    }

    private fun classify(type: String): ThermalGroup {
        val t = type.lowercase()
        return when {
            t.contains("cpu") || t.startsWith("apc") -> ThermalGroup.CPU
            t.contains("gpu") -> ThermalGroup.GPU
            t.contains("npu") -> ThermalGroup.NPU
            t.contains("q6") || t.contains("hvx") || t.contains("dsp") -> ThermalGroup.DSP
            t.contains("modem") -> ThermalGroup.MODEM
            t.contains("camera") || t.contains("cmpss") -> ThermalGroup.CAMERA
            t.contains("video") || t.contains("venus") -> ThermalGroup.VIDEO
            t.contains("ddr") || t.contains("mem") -> ThermalGroup.MEMORY
            t.contains("wlan") || t.contains("wifi") || t.contains("conn") || t.contains("aoss") ->
                ThermalGroup.CONNECTIVITY
            t.startsWith("pm") || t.contains("charger") || t.startsWith("pa_") ||
                t == "bms" || t.contains("battery") -> ThermalGroup.POWER
            t.contains("quiet") || t.contains("board") -> ThermalGroup.BOARD
            else -> ThermalGroup.OTHER
        }
    }

    private companion object {
        const val MIN_CELSIUS = -40.0
        const val MAX_CELSIUS = 150.0
    }
}
