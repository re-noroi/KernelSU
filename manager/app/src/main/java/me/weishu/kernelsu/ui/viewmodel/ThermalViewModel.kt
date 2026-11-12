package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    /** Reads every thermal zone once. Callers decide when/whether to repeat it. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val raw = execRootCommand(thermalCommand)
        _uiState.value = ThermalUiState(zones = parseZones(raw), hasLoaded = true)
    }

    private fun execRootCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    // One shell pass over /sys/class/thermal/thermal_zone*, emitting
    // "<index>|<type>|<temp>" per zone so a refresh spawns a single process.
    private val thermalCommand: String =
        "for z in /sys/class/thermal/thermal_zone*; do " +
            "t=\$(cat \"\$z/type\" 2>/dev/null); " +
            "v=\$(cat \"\$z/temp\" 2>/dev/null); " +
            "echo \"\${z##*thermal_zone}|\$t|\$v\"; " +
            "done"

    private fun parseZones(raw: String): List<ThermalZoneInfo> {
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size < 3) return@mapNotNull null

                val index = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
                val type = parts[1].trim()
                val rawTemp = parts[2].trim().toLongOrNull() ?: return@mapNotNull null

                // Zones without a type are unused placeholders; skip them so only
                // real sensors are listed.
                if (type.isBlank()) return@mapNotNull null
                if (type.lowercase() in ignoredTypes) return@mapNotNull null

                val celsius = toCelsius(rawTemp)
                if (celsius < MIN_CELSIUS || celsius > MAX_CELSIUS) return@mapNotNull null

                index to ThermalZoneInfo(
                    name = "tz$index",
                    type = type,
                    temperature = String.format(Locale.US, "%.1f °C", celsius),
                    group = classify(type),
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
        val ignoredTypes = setOf("unused", "invalid", "none")
        const val MIN_CELSIUS = -40.0
        const val MAX_CELSIUS = 150.0
    }
}
