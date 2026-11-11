package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class BatteryUiState(
    val supported: Boolean = false,
    val level: String = "—",
    val health: String = "—",
    val status: String = "—",
    val powerSource: String = "—",
    val technology: String = "—",
    val temperature: String = "—",
    val voltage: String = "—",
    val currentLabel: String = "Current",
    val currentValue: String = "—",
    val chargedUpTo: String = "—",
    val designCapacity: String = "—",
    val kernelProfile: String = "—",
    val model: String = "—",
    val cycleCount: String = "—",
)

class BatteryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    /** Reads the battery nodes once. Callers decide when/whether to repeat it. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshInternal()
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

    private val readNodes = listOf(
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

    private val batteryCommand: String = buildString {
        readNodes.forEach { (key, path) ->
            append("echo __${key}__; cat $path 2>/dev/null; ")
        }
    }

    private fun refreshInternal() {
        val raw = execRootCommand(batteryCommand)

        fun node(key: String): String {
            val marker = "__${key}__"
            val start = raw.indexOf(marker)
            if (start < 0) return ""
            val valueStart = start + marker.length
            val next = raw.indexOf("__", valueStart)
            return raw.substring(valueStart, if (next < 0) raw.length else next).trim()
        }

        fun pick(primary: String, fallback: String): String {
            val value = node(primary)
            return if (value.isNotBlank()) value else node(fallback)
        }

        val levelRaw = pick("battery_capacity", "bms_capacity")
        val health = pick("battery_health", "bms_health")
        val status = pick("battery_status", "bms_status")
        val technology = pick("battery_technology", "bms_technology")
        val tempRaw = pick("battery_temp", "bms_temp").toLongOrNull()
        val voltageRaw = pick("battery_voltage_now", "bms_voltage_now").toLongOrNull()
        val currentRaw = pick("battery_current_now", "bms_current_now").toLongOrNull()
        val chargeCounterUah = pick("battery_charge_counter", "bms_charge_counter").toLongOrNull() ?: 0L
        val chargeFullUah = pick("battery_charge_full", "bms_charge_full").toLongOrNull() ?: 0L
        val chargeFullDesignUah = pick("battery_charge_full_design", "bms_charge_full_design").toLongOrNull() ?: 0L
        val bmsChargeFullUah = node("bms_charge_full").toLongOrNull() ?: 0L
        val cycleCount = pick("battery_cycle_count", "bms_cycle_count").toIntOrNull()
        val model = node("bms_battery_type")

        val powerSource = when {
            node("usb_online") == "1" -> "USB"
            node("ac_online") == "1" -> "AC"
            node("wireless_online") == "1" -> "Wireless"
            node("dc_online") == "1" -> "DC"
            else -> "Battery"
        }

        val supported = levelRaw.isNotBlank() || status.isNotBlank() || health.isNotBlank() ||
            technology.isNotBlank() || tempRaw != null || voltageRaw != null

        val level = levelRaw.toIntOrNull()?.let { "$it %" } ?: "—"

        // Temperature is usually tenths of a degree, but some kernels report millidegrees.
        val temperature = tempRaw?.let {
            val celsius = if (abs(it) > 1000L) it / 1000.0 else it / 10.0
            String.format(Locale.US, "%.1f °C", celsius)
        } ?: "—"

        // voltage_now is normally µV; tolerate kernels that already report mV.
        val voltageMv = voltageRaw?.let {
            if (abs(it) > 100_000L) it / 1000L else it
        }

        val currentUa = currentRaw?.let {
            // current_now is normally µA; tolerate values that are already mA.
            if (abs(it) < 10_000L && it != 0L) it * 1000L else it
        }

        val currentMa = currentUa?.let { abs(it) / 1000.0 }
        val powerMw = if (currentMa != null && voltageMv != null) {
            currentMa * voltageMv / 1000.0
        } else {
            null
        }

        val currentLabel = when {
            currentUa == null -> "Current"
            currentUa > 0 -> "Discharging speed"
            currentUa < 0 -> "Charging speed"
            else -> "Current"
        }

        val currentValue = if (currentMa != null) {
            if (powerMw != null) {
                String.format(Locale.US, "%.1f mA (%.0f mW)", currentMa, powerMw)
            } else {
                String.format(Locale.US, "%.1f mA", currentMa)
            }
        } else {
            "—"
        }

        fun formatMah(uah: Long): String = String.format(Locale.US, "%.0f mAh", uah / 1000.0)

        val chargedUpTo = when {
            chargeCounterUah > 0L && chargeFullUah > 0L ->
                "${chargeCounterUah / 1000L}/${chargeFullUah / 1000L} mAh"
            chargeFullUah > 0L && chargeFullDesignUah > 0L ->
                "${chargeFullUah / 1000L}/${chargeFullDesignUah / 1000L} mAh"
            chargeCounterUah > 0L -> formatMah(chargeCounterUah)
            chargeFullUah > 0L -> formatMah(chargeFullUah)
            else -> "—"
        }

        val designCapacity = if (chargeFullDesignUah > 0L) formatMah(chargeFullDesignUah) else "—"

        val kernelProfileUah = if (bmsChargeFullUah > 0L) bmsChargeFullUah else chargeFullUah
        val kernelProfile = if (kernelProfileUah > 0L) formatMah(kernelProfileUah) else "—"

        _uiState.update {
            BatteryUiState(
                supported = supported,
                level = level,
                health = health.ifBlank { "—" },
                status = status.ifBlank { "—" },
                powerSource = powerSource,
                technology = technology.ifBlank { "—" },
                temperature = temperature,
                voltage = voltageMv?.let { "$it mV" } ?: "—",
                currentLabel = currentLabel,
                currentValue = currentValue,
                chargedUpTo = chargedUpTo,
                designCapacity = designCapacity,
                kernelProfile = kernelProfile,
                model = model.ifBlank { "—" },
                cycleCount = cycleCount?.toString() ?: "—",
            )
        }
    }
}
