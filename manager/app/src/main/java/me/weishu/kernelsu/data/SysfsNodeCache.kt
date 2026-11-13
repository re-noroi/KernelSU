package me.weishu.kernelsu.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.getRootShell

@Immutable
data class ThermalZonePath(
    val index: Int,
    val type: String,
    val tempPath: String,
)

/** Paths read by the Memory Monitor, with the resolved zRAM device. */
@Immutable
data class MemoryNodePaths(
    val meminfo: String,
    val swaps: String,
    val zramMmStat: String,
    val zramDisksize: String,
    val zramCompAlgorithm: String,
)

/**
 * KCAL/display calibration nodes. All fields are absolute paths; the node set
 * is resolved once at warm-up so the Color Calibration tab does not have to
 * search sysfs again on every open.
 *
 * Two layouts are supported: kernels that expose per-channel nodes
 * (`kcal_red`, `kcal_green`, `kcal_blue`, as msm_drm does) and kernels that
 * expose a single space-separated `kcal` node. Missing optional nodes
 * (`kcal_enable`, or any of the sliders) are left as null.
 */
@Immutable
data class ColorNodePaths(
    val red: String?,
    val green: String?,
    val blue: String?,
    val rgb: String?,
    val enable: String?,
    val saturation: String?,
    val value: String?,
    val contrast: String?,
    val hue: String?,
) {
    val hasSeparateRgb: Boolean get() = red != null && green != null && blue != null

    val supported: Boolean get() = hasSeparateRgb || rgb != null
}

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
 * Warming up once (when KTune is opened) enumerates the thermal zone paths,
 * the power supply paths that exist, and resolves the zRAM device, so opening
 * the Memory/Battery/Thermal tabs only has to read live values instead of
 * discovering everything again. The last raw snapshot is kept too, which lets
 * those tabs render instantly while the first refresh runs.
 */
object SysfsNodeCache {
    private val mutex = Mutex()

    @Volatile
    private var thermalZones: List<ThermalZonePath>? = null

    @Volatile
    private var batteryNodes: List<Pair<String, String>>? = null

    @Volatile
    private var memoryPaths: MemoryNodePaths? = null

    @Volatile
    private var colorPaths: ColorNodePaths? = null

    @Volatile
    private var colorDiscovered = false

    @Volatile
    private var thermalSnapshot: String? = null

    @Volatile
    private var batterySnapshot: String? = null

    @Volatile
    private var memorySnapshot: String? = null

    @Volatile
    private var colorSnapshot: String? = null

    suspend fun warmUp() = withContext(Dispatchers.IO) {
        warmUpBattery()
        warmUpThermal()
        warmUpMemory()
        warmUpColor()
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

    suspend fun warmUpMemory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val paths = memoryPaths ?: discoverMemoryPaths().also { memoryPaths = it }
            if (memorySnapshot == null) {
                memorySnapshot = readMemoryNodes(paths)
            }
        }
    }

    suspend fun warmUpColor() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!colorDiscovered) {
                colorPaths = discoverColorNodes()
                colorDiscovered = true
            }
            val paths = colorPaths
            if (paths != null && colorSnapshot == null) {
                colorSnapshot = readColorNodes(paths)
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

    fun memoryNodePaths(): MemoryNodePaths = memoryPaths ?: defaultMemoryPaths

    fun memorySnapshot(): String? = memorySnapshot

    fun updateMemorySnapshot(raw: String) {
        memorySnapshot = raw
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

    fun buildMemoryCommand(paths: MemoryNodePaths): String = buildString {
        append("cat ${paths.meminfo} 2>/dev/null; ")
        append("echo __SWAPS__; cat ${paths.swaps} 2>/dev/null; ")
        append("echo __MMSTAT__; cat ${paths.zramMmStat} 2>/dev/null; ")
        append("echo __DISKSIZE__; cat ${paths.zramDisksize} 2>/dev/null; ")
        append("echo __COMPALGO__; cat ${paths.zramCompAlgorithm} 2>/dev/null")
    }

    fun readMemoryNodes(paths: MemoryNodePaths): String =
        execRootCommand(buildMemoryCommand(paths))

    fun colorNodePaths(): ColorNodePaths? = colorPaths

    fun colorSnapshot(): String? = colorSnapshot

    fun updateColorSnapshot(raw: String) {
        colorSnapshot = raw
    }

    fun buildColorCommand(paths: ColorNodePaths): String = buildString {
        fun read(key: String, path: String?) {
            append("echo __${key}__; ")
            if (path != null) append("cat \"$path\" 2>/dev/null; ")
        }
        read("RED", paths.red)
        read("GREEN", paths.green)
        read("BLUE", paths.blue)
        read("RGB", paths.rgb)
        read("ENABLE", paths.enable)
        read("SATURATION", paths.saturation)
        read("VALUE", paths.value)
        read("CONTRAST", paths.contrast)
        read("HUE", paths.hue)
    }

    fun readColorNodes(paths: ColorNodePaths): String =
        execRootCommand(buildColorCommand(paths))

    /** Writes a calibration value; values are numeric only, paths are cached constants. */
    fun writeColorNode(path: String?, value: String) {
        if (path == null) return
        execRootCommand("echo $value > \"$path\" 2>/dev/null")
    }

    fun execRootCommand(cmd: String): String {
        return try {
            val out = mutableListOf<String>()
            getRootShell(true).newJob().add(cmd).to(out).exec()
            out.joinToString("\n").trim()
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

    private fun discoverMemoryPaths(): MemoryNodePaths {
        // Resolve the first zRAM device that exposes mm_stat; fall back to the
        // conventional zram0 so behaviour matches the pre-cache implementation.
        val base = execRootCommand(
            "for z in /sys/block/zram*; do " +
                "if [ -e \"\$z/mm_stat\" ]; then echo \"\$z\"; break; fi; " +
                "done",
        ).lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "/sys/block/zram0" }
        return MemoryNodePaths(
            meminfo = "/proc/meminfo",
            swaps = "/proc/swaps",
            zramMmStat = "$base/mm_stat",
            zramDisksize = "$base/disksize",
            zramCompAlgorithm = "$base/comp_algorithm",
        )
    }

    private val defaultMemoryPaths = MemoryNodePaths(
        meminfo = "/proc/meminfo",
        swaps = "/proc/swaps",
        zramMmStat = "/sys/block/zram0/mm_stat",
        zramDisksize = "/sys/block/zram0/disksize",
        zramCompAlgorithm = "/sys/block/zram0/comp_algorithm",
    )

    /**
     * Resolves the directory holding the KCAL nodes. The msm_drm module
     * parameter layout (per-channel `kcal_red`/`kcal_green`/`kcal_blue`) is
     * probed first, then the older combined `kcal` layout. If neither is found
     * in a known base, a bounded find handles vendor-specific layouts.
     */
    private fun discoverColorNodes(): ColorNodePaths? {
        val cmd = buildString {
            append("dir=''; ")
            append("for b in ")
            append("/sys/module/msm_drm/parameters ")
            append("/sys/devices/platform/kcal_ctrl.0 ")
            append("/sys/devices/platform/*/kcal_ctrl.0 ")
            append("/sys/class/display ")
            append("/sys/class/graphics/fb0 ")
            append("; do ")
            append("if [ -e \"\$b/kcal_red\" ] || [ -e \"\$b/kcal\" ]; then dir=\"\$b\"; break; fi; ")
            append("done; ")
            append("if [ -z \"\$dir\" ]; then ")
            append("f=\$(find /sys/module /sys/devices/platform -maxdepth 6 \\( -name kcal_red -o -name kcal \\) -print -quit 2>/dev/null); ")
            append("[ -n \"\$f\" ] && dir=\$(dirname \"\$f\"); ")
            append("fi; ")
            append("[ -z \"\$dir\" ] && exit 0; ")
            append("echo \"__DIR__\$dir\"; ")
            append("for n in kcal_red kcal_green kcal_blue kcal kcal_enable kcal_sat kcal_val kcal_cont kcal_contrast kcal_hue; do ")
            append("[ -e \"\$dir/\$n\" ] && echo \"\$n\"; done")
        }
        val lines = execRootCommand(cmd).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val base = lines.firstOrNull { it.startsWith("__DIR__") }
            ?.removePrefix("__DIR__")
            ?.trim()
            .orEmpty()
        if (base.isEmpty()) return null
        val names = lines.filterNot { it.startsWith("__DIR__") }.toSet()
        fun node(name: String): String? = if (name in names) "$base/$name" else null
        return ColorNodePaths(
            red = node("kcal_red"),
            green = node("kcal_green"),
            blue = node("kcal_blue"),
            rgb = node("kcal"),
            enable = node("kcal_enable"),
            saturation = node("kcal_sat"),
            value = node("kcal_val"),
            contrast = node("kcal_cont") ?: node("kcal_contrast"),
            hue = node("kcal_hue"),
        ).takeIf { it.supported }
    }

    private val ignoredThermalTypes = setOf("unused", "invalid", "none")
}
