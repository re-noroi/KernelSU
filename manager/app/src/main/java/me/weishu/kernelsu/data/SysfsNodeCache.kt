package me.weishu.kernelsu.data

import androidx.compose.runtime.Immutable
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class ThermalZonePath(
    val index: Int,
    val type: String,
    val tempPath: String,
)

/**
 * Every power supply node the Battery screen may read, with the key it is
 * reported under. Only the paths that actually exist are cached at warm-up.
 */
val BatteryNodeCandidates: List<Pair<String, String>> = listOf(
    "battery_capacity" to "/sys/class/power_supply/battery/capacity",
    "battery_health" to "/sys/class/power_supply/battery/health",
    "battery_status" to "/sys/class/power_supply/battery/status",
    "battery_technology" to "/sys/class/power_supply/battery/technology",
    "battery_temp" to "/sys/class/power_supply/battery/temp",
    "battery_voltage_now" to "/sys/class/power_supply/battery/voltage_now",
    "battery_current_now" to "/sys/class/power_supply/battery/current_now",
    "battery_charge_counter" to "/sys/class/power_supply/battery/charge_counter",
    "battery_charge_full" to "/sys/class/power_supply/battery/charge_full",
    "battery_charge_full_design" to "/sys/class/power_supply/battery/charge_full_design",
    "battery_cycle_count" to "/sys/class/power_supply/battery/cycle_count",
    "bms_capacity" to "/sys/class/power_supply/bms/capacity",
    "bms_health" to "/sys/class/power_supply/bms/health",
    "bms_status" to "/sys/class/power_supply/bms/status",
    "bms_technology" to "/sys/class/power_supply/bms/technology",
    "bms_temp" to "/sys/class/power_supply/bms/temp",
    "bms_voltage_now" to "/sys/class/power_supply/bms/voltage_now",
    "bms_current_now" to "/sys/class/power_supply/bms/current_now",
    "bms_charge_counter" to "/sys/class/power_supply/bms/charge_counter",
    "bms_charge_full" to "/sys/class/power_supply/bms/charge_full",
    "bms_charge_full_design" to "/sys/class/power_supply/bms/charge_full_design",
    "bms_cycle_count" to "/sys/class/power_supply/bms/cycle_count",
    "bms_battery_type" to "/sys/class/power_supply/bms/battery_type",
    "usb_online" to "/sys/class/power_supply/usb/online",
    "ac_online" to "/sys/class/power_supply/ac/online",
    "wireless_online" to "/sys/class/power_supply/wireless/online",
    "dc_online" to "/sys/class/power_supply/dc/online",
)

/**
 * Process-wide cache of the sysfs nodes used by the KTune monitors.
 *
 * Warming up once (when KTune is opened) enumerates the thermal zone paths and
 * the power supply paths that exist, so opening the Thermal/Battery tabs only
 * has to read live values instead of discovering everything again. The last
 * raw snapshot is kept too, which lets those tabs render instantly while the
 * first refresh runs.
 */
object SysfsNodeCache {
    private val mutex = Mutex()

    @Volatile
    private var thermalZones: List<ThermalZonePath>? = null

    @Volatile
    private var batteryNodes: List<Pair<String, String>>? = null

    @Volatile
    private var thermalSnapshot: String? = null

    @Volatile
    private var batterySnapshot: String? = null

    suspend fun warmUp() = withContext(Dispatchers.IO) {
        warmUpBattery()
        warmUpThermal()
    }

    suspend fun warmUpThermal() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (thermalZones.isNullOrEmpty()) {
                thermalZones = discoverThermalZones()
            }
            val zones = thermalZones.orEmpty()
            if (zones.isNotEmpty() && thermalSnapshot == null) {
                thermalSnapshot = readThermalTemperatures(zones)
            }
        }
    }

    suspend fun warmUpBattery() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val nodes = batteryNodes ?: discoverBatteryNodes().also { batteryNodes = it }
            if (nodes.isNotEmpty() && batterySnapshot == null) {
                batterySnapshot = readBatteryNodes(nodes)
            }
        }
    }

    fun thermalZonePaths(): List<ThermalZonePath>? = thermalZones

    fun thermalSnapshot(): String? = thermalSnapshot

    fun updateThermalSnapshot(raw: String) {
        thermalSnapshot = raw
    }

    fun invalidateThermal() {
        thermalZones = null
        thermalSnapshot = null
    }

    fun batteryNodePaths(): List<Pair<String, String>>? = batteryNodes

    fun batterySnapshot(): String? = batterySnapshot

    fun updateBatterySnapshot(raw: String) {
        batterySnapshot = raw
    }

    fun buildThermalCommand(zones: List<ThermalZonePath>): String = buildString {
        append("for z in ")
        zones.forEach { append("\"${it.tempPath}\" ") }
        append("; do echo \"\$z \$(cat \"\$z\" 2>/dev/null)\"; done")
    }

    fun buildBatteryCommand(nodes: List<Pair<String, String>>): String = buildString {
        nodes.forEach { (key, path) -> append("echo __${key}__; cat $path 2>/dev/null; ") }
    }

    fun readThermalTemperatures(zones: List<ThermalZonePath>): String =
        execRootCommand(buildThermalCommand(zones))

    fun readBatteryNodes(nodes: List<Pair<String, String>>): String =
        execRootCommand(buildBatteryCommand(nodes))

    fun execRootCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    private fun discoverThermalZones(): List<ThermalZonePath> {
        val cmd = "for z in /sys/class/thermal/thermal_zone*; do " +
            "t=\$(cat \"\$z/type\" 2>/dev/null); " +
            "if [ -n \"\$t\" ]; then echo \"\$z|\$t\"; fi; " +
            "done"
        return execRootCommand(cmd).lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val path = parts[0].trim()
                val type = parts[1].trim()
                if (type.isBlank() || type.lowercase() in ignoredThermalTypes) return@mapNotNull null
                val index = path.substringAfterLast("thermal_zone").toIntOrNull() ?: return@mapNotNull null
                ThermalZonePath(index = index, type = type, tempPath = "$path/temp")
            }
            .sortedBy { it.index }
            .toList()
    }

    private fun discoverBatteryNodes(): List<Pair<String, String>> {
        val cmd = buildString {
            append("for p in ")
            BatteryNodeCandidates.forEach { append("\"${it.second}\" ") }
            append("; do if [ -e \"\$p\" ]; then echo \"\$p\"; fi; done")
        }
        val existing = execRootCommand(cmd).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val resolved = BatteryNodeCandidates.filter { it.second in existing }
        // If discovery failed (e.g. no root), keep the full list so the monitor
        // behaves exactly like before and simply reads whatever is there.
        return resolved.ifEmpty { BatteryNodeCandidates }
    }

    private val ignoredThermalTypes = setOf("unused", "invalid", "none")
}
