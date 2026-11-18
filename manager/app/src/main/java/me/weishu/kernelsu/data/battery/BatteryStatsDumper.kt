package me.weishu.kernelsu.data.battery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

/** One `label: value` pair as reported by the system. */
data class BatteryStatRow(val label: String, val value: String)

/**
 * The "Statistics since last charge" and "Estimated power use" blocks of
 * `dumpsys batterystats`. These are the values the reference battery monitor
 * screens show; they are read on demand only, never polled in the background.
 */
data class BatteryStatsDump(
    val available: Boolean = false,
    val sinceCharge: List<BatteryStatRow> = emptyList(),
    val powerUse: List<BatteryStatRow> = emptyList(),
) {
    fun valueOf(label: String): String? =
        sinceCharge.firstOrNull { it.label.equals(label, ignoreCase = true) }?.value
}

object BatteryStatsDumper {

    /** First numeric token of a power value: the mAh estimate. */
    private val LEADING_NUMBER = Regex("^[-+]?\\d+(?:\\.\\d+)?")

    /**
     * Duration reported by newer builds. Vendors append it either before or
     * after the redundant `apps:` breakdown and may wrap it in parentheses.
     */
    private val DURATION = Regex(
        "duration:\\s*(.+?)(?=\\s*\\)|\\s+apps:|\$)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * In display order, mirroring the reference screens. Labels must match the
     * beginning of a `dumpsys` line; anything the kernel does not report is
     * simply skipped.
     */
    private val SINCE_CHARGE_LABELS = listOf(
        "Last learned battery capacity",
        "Time on battery",
        "Time on battery screen off",
        "Time on battery screen doze",
        "Total run time",
        "Battery time remaining",
        "Discharge",
        "Screen off discharge",
        "Screen doze discharge",
        "Screen on discharge",
        "Device light doze discharge",
        "Device deep doze discharge",
        "Start clock time",
        "Screen on",
        "Device light idling",
        "Idle mode light time",
        "Device full idling",
        "Idle mode full time",
        "Connectivity changes",
        "Total full wakelock time",
        "Total partial wakelock time",
        "Total WiFi Multicast wakelock Count",
        "Total WiFi Multicast wakelock time",
    )

    /**
     * One shell call, on the caller's IO context. `dumpsys` can take a second
     * or two and produce a large buffer, which is why this is only invoked
     * when the user opens the stats screen or taps refresh.
     */
    suspend fun read(): BatteryStatsDump = withContext(Dispatchers.IO) {
        parse(SysfsNodeCache.execRootCommand("dumpsys batterystats 2>/dev/null"))
    }

    fun parse(raw: String): BatteryStatsDump {
        val lines = raw.lineSequence().map { it.trim() }.toList()
        val start = lines.indexOfFirst { it.startsWith("Statistics since last charge", true) }
        if (start < 0) return BatteryStatsDump()

        val relativeEnd = lines.subList(start + 1, lines.size).indexOfFirst { isSectionEnd(it) }
        val blockEnd = if (relativeEnd < 0) lines.size else start + 1 + relativeEnd
        val block = lines.subList(start + 1, blockEnd)

        val sinceCharge = SINCE_CHARGE_LABELS.mapNotNull { label ->
            val line = block.firstOrNull { it.startsWith("$label:", ignoreCase = true) }
                ?: return@mapNotNull null
            BatteryStatRow(label, line.substringAfter(':').trim())
        }

        val powerUse = parsePowerUse(lines)

        return BatteryStatsDump(
            available = sinceCharge.isNotEmpty() || powerUse.isNotEmpty(),
            sinceCharge = sinceCharge,
            powerUse = powerUse,
        )
    }

    private fun parsePowerUse(lines: List<String>): List<BatteryStatRow> {
        val start = lines.indexOfFirst { it.startsWith("Estimated power use", true) }
        if (start < 0) return emptyList()
        val relativeEnd = lines.subList(start + 1, lines.size)
            .indexOfFirst { it.isEmpty() || isSectionEnd(it) }
        val end = if (relativeEnd < 0) lines.size else start + 1 + relativeEnd
        val block = lines.subList(start + 1, end)
        return block.mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val key = line.substring(0, colon).trim()
            // Skip totals and per-UID entries: the reference only shows the
            // component breakdown (Screen, Idle, Wifi, ...), and UID rows carry
            // huge multi-part values that overflow a phone-width card.
            if (key.isEmpty() || key.any { it.isDigit() } ||
                key.contains("capacity", true) || key.contains("computed", true)
            ) {
                return@mapNotNull null
            }
            // Keep the mAh estimate and, when present, its duration. The
            // `apps:` breakdown repeats the mAh figure and is dropped.
            val rawValue = line.substring(colon + 1).trim()
            val mAh = LEADING_NUMBER.find(rawValue)?.value ?: return@mapNotNull null
            val duration = DURATION.find(rawValue)?.groupValues?.get(1)?.trim()?.take(16)
            val value = if (duration.isNullOrEmpty()) "$mAh mAh" else "$mAh mAh · $duration"
            BatteryStatRow(key, value)
        }.take(10)
    }

    private fun isSectionEnd(line: String): Boolean =
        line.startsWith("Per UID", true) ||
            line.startsWith("Estimated power use", true) ||
            line.startsWith("HISTORY", true) ||
            line.startsWith("Battery History", true) ||
            line.startsWith("u0a", true)
}
