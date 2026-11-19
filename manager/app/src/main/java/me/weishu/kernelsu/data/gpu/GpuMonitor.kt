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
    /** Frequencies the GPU can be limited to, ascending, in kHz. */
    val frequenciesKhz: List<Long> = emptyList(),
    /** True when a devfreq `min_freq` node was found, so writes are possible. */
    val limitsWritable: Boolean = false,
) {
    val available: Boolean
        get() = model.isNotBlank() || busyPercent != null || curFreqKhz != null

    /** True when this kernel exposes both devfreq limits. */
    val canLimit: Boolean
        get() = limitsWritable && minFreqKhz != null && maxFreqKhz != null
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
            "[ -z \"\$U\" ] && U=\$(cat \$B/gpu_busy 2>/dev/null); " +
            "[ -z \"\$U\" ] && U=\$(cat /sys/kernel/gpu/gpu_busy 2>/dev/null); " +
            "C1=\$(cat \$D/cur_freq 2>/dev/null); " +
            "C2=\$(cat \$B/gpuclk 2>/dev/null); " +
            "C3=\$(cat /sys/kernel/gpu/gpu_clock 2>/dev/null); " +
            "X=\$(cat \$D/max_freq 2>/dev/null); " +
            "[ -z \"\$X\" ] && X=\$(cat \$B/max_gpuclk 2>/dev/null); " +
            "N=\$(cat \$D/min_freq 2>/dev/null); " +
            "G=\$(cat \$D/governor 2>/dev/null); " +
            "F=\$(cat \$D/available_frequencies 2>/dev/null); " +
            "[ -z \"\$F\" ] && F=\$(cat \$B/gpu_available_frequencies 2>/dev/null); " +
            "W=0; [ -w \"\$D/min_freq\" ] && [ -w \"\$D/max_freq\" ] && W=1; " +
            "for d in /sys/class/devfreq/*; do n=\$(cat \"\$d/name\" 2>/dev/null); " +
            "case \"\$n\" in *gpu*|*mali*|*kgsl*|*3d*) " +
            "[ -z \"\$C1\" ] && C1=\$(cat \"\$d/cur_freq\" 2>/dev/null); " +
            "[ -z \"\$X\" ] && X=\$(cat \"\$d/max_freq\" 2>/dev/null); " +
            "[ -z \"\$N\" ] && N=\$(cat \"\$d/min_freq\" 2>/dev/null); " +
            "[ -z \"\$G\" ] && G=\$(cat \"\$d/governor\" 2>/dev/null); " +
            "[ -z \"\$F\" ] && F=\$(cat \"\$d/available_frequencies\" 2>/dev/null); " +
            "[ -z \"\$U\" ] && U=\$(cat \"\$d/device/utilization\" 2>/dev/null); " +
            "[ -z \"\$U\" ] && U=\$(cat \"\$d/utilization\" 2>/dev/null); " +
            "[ \"\$W\" = 0 ] && [ -w \"\$d/min_freq\" ] && [ -w \"\$d/max_freq\" ] && W=1; " +
            ";; esac; done; " +
            "echo \"M|\$M\"; echo \"U|\$U\"; echo \"C1|\$C1\"; echo \"C2|\$C2\"; " +
            "echo \"C3|\$C3\"; echo \"X|\$X\"; echo \"N|\$N\"; echo \"G|\$G\"; " +
            "echo \"F|\$F\"; echo \"W|\$W\""

    suspend fun readLive(): GpuLiveState = withContext(Dispatchers.IO) {
        parseLive(SysfsNodeCache.execRootCommandOrNull(LIVE_COMMAND))
    }

    /**
     * Writes the devfreq min/max limits in kHz. The order is chosen so
     * `min_freq <= max_freq` stays true at every intermediate step, which is
     * what the devfreq core validates against.
     */
    suspend fun setFrequencyLimits(minKhz: Long, maxKhz: Long): Boolean =
        withContext(Dispatchers.IO) {
            val minHz = minKhz * 1000L
            val maxHz = maxKhz * 1000L
            val command = buildString {
                append("B=/sys/class/kgsl/kgsl-3d0; D=\$B/devfreq; P=\$D; ")
                append("if [ ! -w \"\$P/max_freq\" ]; then ")
                append("for d in /sys/class/devfreq/*; do n=\$(cat \"\$d/name\" 2>/dev/null); ")
                append("case \"\$n\" in *gpu*|*mali*|*kgsl*|*3d*) ")
                append("[ -w \"\$d/max_freq\" ] && { P=\"\$d\"; break; } ;; esac; done; fi; ")
                append("[ -w \"\$P/max_freq\" ] || exit 0; ")
                append("M0=\$(cat \"\$P/min_freq\" 2>/dev/null); ")
                append("if [ -n \"\$M0\" ] && [ $minHz -gt \"\$M0\" ]; then ")
                append("echo $maxHz > \"\$P/max_freq\" 2>/dev/null; ")
                append("echo $minHz > \"\$P/min_freq\" 2>/dev/null; else ")
                append("echo $minHz > \"\$P/min_freq\" 2>/dev/null; ")
                append("echo $maxHz > \"\$P/max_freq\" 2>/dev/null; fi; ")
                append("echo done")
            }
            SysfsNodeCache.execRootCommandOrNull(command)?.contains("done") == true
        }

    fun parseLive(raw: String?): GpuLiveState {
        if (raw.isNullOrBlank()) return GpuLiveState()

        val values = LinkedHashMap<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val bar = trimmed.indexOf('|')
            if (bar <= 0) return@forEach
            values[trimmed.substring(0, bar)] = trimmed.substring(bar + 1).trim()
        }

        val available = values["F"].orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull(::normalizeFreq)
            .filter { it > 0L }
            .distinct()
            .sorted()

        // Kernels disagree about what "current frequency" means. On Adreno the
        // devfreq node keeps reporting the last active clock while the GPU is
        // clock-gated, while `gpuclk` (and the vendor `gpu_clock` node) report
        // 0. Read every candidate and let a zero win, so an idle GPU shows
        // 0 MHz exactly like FKM/migu; otherwise the lowest report is the
        // actual clock.
        val candidates = listOf("C1", "C2", "C3").mapNotNull { key ->
            parseFreq(values[key], allowZero = true)
        }
        val cur = if (candidates.isEmpty()) {
            null
        } else if (candidates.any { it == 0L }) {
            0L
        } else {
            candidates.minOrNull()
        }
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
            frequenciesKhz = available,
            limitsWritable = values["W"].orEmpty().trim() == "1",
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

    private fun parseFreq(raw: String?, allowZero: Boolean = false): Long? {
        if (raw.isNullOrBlank()) return null
        return normalizeFreq(raw, allowZero)
    }

    /** Normalises Hz, kHz and MHz to kHz. */
    private fun normalizeFreq(raw: String, allowZero: Boolean = false): Long? {
        val token = NUMBER.find(raw)?.value ?: return null
        val value = token.toDoubleOrNull() ?: return null
        if (value < 0.0) return null
        if (value == 0.0) return if (allowZero) 0L else null
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
