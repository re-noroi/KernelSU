package me.weishu.kernelsu.data.gpu

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

@Immutable
data class GpuLiveState(
    val model: String = "",
    val vendor: String = "",
    val busyPercent: Int? = null,
    val curFreqKhz: Long? = null,
    val maxFreqKhz: Long? = null,
    val minFreqKhz: Long? = null,
    val governor: String = "",
) {
    val available: Boolean
        get() = model.isNotBlank() || busyPercent != null || curFreqKhz != null
}

/**
 * Reads GPU telemetry from the two layouts Android devices actually use:
 * Qualcomm's `kgsl-3d0` (with its `devfreq` child) and, as a fallback, any
 * `devfreq` device whose name looks like a GPU. Everything is read in one
 * shell call, so a once-per-second live refresh stays cheap.
 */
object GpuMonitor {

    /**
     * kgsl keeps frequencies in Hz; Mali devfreq also uses Hz; a few vendor
     * nodes print MHz. The parser normalises all of them to kHz.
     */
    private const val LIVE_COMMAND =
        "B=/sys/class/kgsl/kgsl-3d0; D=\$B/devfreq; " +
            "M=\$(cat \$B/gpu_model 2>/dev/null); " +
            "[ -z \"\$M\" ] && M=\$(cat /sys/kernel/gpu/gpu_model 2>/dev/null); " +
            "U=\$(cat \$B/gpu_busy_percentage 2>/dev/null); " +
            "[ -z \"\$U\" ] && U=\$(cat /sys/kernel/gpu/gpu_busy 2>/dev/null); " +
            "C=\$(cat \$D/cur_freq 2>/dev/null); " +
            "[ -z \"\$C\" ] && C=\$(cat \$B/gpuclk 2>/dev/null); " +
            "[ -z \"\$C\" ] && C=\$(cat /sys/kernel/gpu/gpu_clock 2>/dev/null); " +
            "X=\$(cat \$D/max_freq 2>/dev/null); " +
            "[ -z \"\$X\" ] && X=\$(cat \$B/max_gpuclk 2>/dev/null); " +
            "N=\$(cat \$D/min_freq 2>/dev/null); " +
            "G=\$(cat \$D/governor 2>/dev/null); " +
            "F=\$(cat \$B/gpu_available_frequencies 2>/dev/null); " +
            "for d in /sys/class/devfreq/*; do n=\$(cat \"\$d/name\" 2>/dev/null); " +
            "case \"\$n\" in *gpu*|*mali*|*kgsl*|*3d*) " +
            "[ -z \"\$C\" ] && C=\$(cat \"\$d/cur_freq\" 2>/dev/null); " +
            "[ -z \"\$X\" ] && X=\$(cat \"\$d/max_freq\" 2>/dev/null); " +
            "[ -z \"\$N\" ] && N=\$(cat \"\$d/min_freq\" 2>/dev/null); " +
            "[ -z \"\$G\" ] && G=\$(cat \"\$d/governor\" 2>/dev/null); " +
            "[ -z \"\$U\" ] && U=\$(cat \"\$d/device/utilization\" 2>/dev/null); " +
            "[ -z \"\$U\" ] && U=\$(cat \"\$d/utilization\" 2>/dev/null); " +
            ";; esac; done; " +
            "echo \"M|\$M\"; echo \"U|\$U\"; echo \"C|\$C\"; echo \"X|\$X\"; " +
            "echo \"N|\$N\"; echo \"G|\$G\"; echo \"F|\$F\""

    suspend fun readLive(): GpuLiveState = withContext(Dispatchers.IO) {
        parseLive(SysfsNodeCache.execRootCommandOrNull(LIVE_COMMAND))
    }

    fun parseLive(raw: String?): GpuLiveState {
        if (raw.isNullOrBlank()) return GpuLiveState()

        val values = LinkedHashMap<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.length < 2 || trimmed[1] != '|') return@forEach
            values[trimmed.substring(0, 1)] = trimmed.substring(2).trim()
        }

        val available = values["F"].orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull(::normalizeFreq)
            .filter { it > 0L }

        val cur = parseFreq(values["C"])
        val max = parseFreq(values["X"])
            ?: available.maxOrNull()
        val min = parseFreq(values["N"])
            ?: available.minOrNull()

        val model = values["M"].orEmpty().trim()

        return GpuLiveState(
            model = model,
            vendor = vendorOf(model),
            busyPercent = parsePercent(values["U"]),
            curFreqKhz = cur,
            maxFreqKhz = max,
            minFreqKhz = min,
            governor = values["G"].orEmpty().trim(),
        )
    }

    /** Adreno, Mali and friends print their vendor in the model name. */
    private fun vendorOf(model: String): String = when {
        model.contains("adreno", true) -> "Qualcomm"
        model.contains("mali", true) -> "ARM"
        model.contains("powervr", true) -> "Imagination"
        model.contains("xclipse", true) -> "Samsung"
        else -> ""
    }

    private fun parseFreq(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return normalizeFreq(raw)
    }

    /** Normalises Hz, kHz and MHz to kHz. */
    private fun normalizeFreq(raw: String): Long? {
        val token = NUMBER.find(raw)?.value ?: return null
        val value = token.toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return when {
            value >= 1_000_000.0 -> (value / 1000.0).toLong()
            value >= 10_000.0 -> value.toLong()
            else -> (value * 1000.0).toLong()
        }
    }

    private fun parsePercent(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val token = NUMBER.find(raw)?.value ?: return null
        val value = token.toDoubleOrNull() ?: return null
        // Some exporters print a 0..1 fraction instead of 0..100.
        val percent = if (value in 0.0..1.0 && token.contains('.')) value * 100.0 else value
        return percent.roundToInt().coerceIn(0, 100)
    }

    private val NUMBER = Regex("[0-9]+(?:\\.[0-9]+)?")
}
