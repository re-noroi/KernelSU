package me.weishu.kernelsu.data.cpu

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

/** One online CPU as reported by its cpufreq policy. */
@Immutable
data class CpuCoreLive(
    val index: Int,
    val curFreqKhz: Long,
    val maxFreqKhz: Long,
    val governor: String,
)

/** CPUs that share a maximum frequency, i.e. one hardware cluster. */
@Immutable
data class CpuClusterLive(
    val index: Int,
    val cores: List<CpuCoreLive>,
    val maxFreqKhz: Long,
)

/**
 * One read of the aggregate `cpu` line in `/proc/stat`, in clock ticks.
 *
 * The guest counters are deliberately not stored: the kernel already includes
 * guest time in `user`/`nice`, so adding them again would count it twice.
 */
@Immutable
data class CpuStatTimes(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long,
) {
    /** Everything except guest time, which is already part of user/nice. */
    val total: Long
        get() = user + nice + system + idle + iowait + irq + softirq + steal

    /** `iowait` counts as idle here, following Kernel Adiutor. */
    val idleTotal: Long
        get() = idle + iowait

    fun isMonotonicFrom(previous: CpuStatTimes): Boolean =
        user >= previous.user &&
            nice >= previous.nice &&
            system >= previous.system &&
            idle >= previous.idle &&
            iowait >= previous.iowait &&
            irq >= previous.irq &&
            softirq >= previous.softirq &&
            steal >= previous.steal
}

@Immutable
data class CpuLiveState(
    val cores: List<CpuCoreLive> = emptyList(),
    val clusters: List<CpuClusterLive> = emptyList(),
    /** Real utilisation from `/proc/stat`; null until two samples are available. */
    val loadPercent: Int? = null,
    /** The normalised 1-minute load average, kept for comparison. */
    val loadAvgPercent: Int = 0,
    val governor: String = "",
    /** Raw counters used to derive [loadPercent] on the next refresh. */
    val statTimes: CpuStatTimes? = null,
) {
    val available: Boolean get() = cores.isNotEmpty()
}

/** Cumulative time each CPU spent at each frequency, in milliseconds. */
@Immutable
data class CpuTimeSnapshot(
    val cores: Map<Int, Map<Long, Long>>,
) {
    val isEmpty: Boolean get() = cores.isEmpty()

    /**
     * Compact form stored in preferences. Frequencies are stable for the life
     * of a boot, so a plain `cpu:freq=ms` list is enough and needs no parser
     * dependency.
     */
    fun serialize(): String = cores.entries.joinToString(";") { (cpu, freqs) ->
        val values = freqs.entries.joinToString(",") { "${it.key}=${it.value}" }
        "$cpu:$values"
    }

    companion object {
        fun parse(raw: String?): CpuTimeSnapshot? {
            if (raw.isNullOrBlank()) return null
            val cores = LinkedHashMap<Int, Map<Long, Long>>()
            raw.split(';').forEach { block ->
                val parts = block.split(':', limit = 2)
                if (parts.size != 2) return@forEach
                val cpu = parts[0].toIntOrNull() ?: return@forEach
                val freqs = LinkedHashMap<Long, Long>()
                parts[1].split(',').forEach { pair ->
                    val kv = pair.split('=', limit = 2)
                    if (kv.size != 2) return@forEach
                    val freq = kv[0].toLongOrNull() ?: return@forEach
                    val time = kv[1].toLongOrNull() ?: return@forEach
                    freqs[freq] = time
                }
                if (freqs.isNotEmpty()) cores[cpu] = freqs
            }
            return CpuTimeSnapshot(cores).takeIf { !it.isEmpty }
        }
    }
}

/** One line of the residency table: a frequency, or `null` for deep sleep. */
@Immutable
data class CpuStatsRow(
    val freqKhz: Long?,
    val timeMs: Long,
    val fraction: Float,
)

@Immutable
data class CpuStatsClusterStats(
    val index: Int,
    val coreCount: Int,
    val rows: List<CpuStatsRow>,
    val windowMs: Long,
)

/**
 * Reads CPU telemetry through one root shell call per refresh.
 *
 * The live card uses `scaling_cur_freq`, `cpuinfo_max_freq`,
 * `scaling_governor` and `/proc/loadavg` — a handful of tiny sysfs reads.
 * The residency table uses the kernel's own `time_in_state` counters, which
 * accumulate whether or not the app is running, so statistics need no
 * background sampler at all: opening the tab diffs the counters against a
 * stored baseline.
 */
object CpuMonitor {

    private const val LIVE_COMMAND =
        "for d in /sys/devices/system/cpu/cpu[0-9]*; do " +
            "f=\$(cat \"\$d/cpufreq/scaling_cur_freq\" 2>/dev/null); " +
            "m=\$(cat \"\$d/cpufreq/cpuinfo_max_freq\" 2>/dev/null); " +
            "g=\$(cat \"\$d/cpufreq/scaling_governor\" 2>/dev/null); " +
            "if [ -n \"\$m\" ]; then echo \"C|\${d##*cpu}|\$f|\$m|\$g\"; fi; " +
            "done; " +
            "echo \"L|\$(cat /proc/loadavg 2>/dev/null)\"; " +
            "echo \"S|\$(head -n 1 /proc/stat 2>/dev/null)\""

    private const val TIME_IN_STATE_COMMAND =
        "for f in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/stats/time_in_state; do " +
            "[ -e \"\$f\" ] || continue; " +
            "echo \"P|\${f#/sys/devices/system/cpu/cpu}\"; " +
            "cat \"\$f\" 2>/dev/null; " +
            "done"

    suspend fun readLive(): CpuLiveState = withContext(Dispatchers.IO) {
        parseLive(SysfsNodeCache.execRootCommandOrNull(LIVE_COMMAND))
    }

    /** Returns null when no CPU exposes `time_in_state`. */
    suspend fun readTimeInState(): CpuTimeSnapshot? = withContext(Dispatchers.IO) {
        parseTimeInState(SysfsNodeCache.execRootCommandOrNull(TIME_IN_STATE_COMMAND))
    }

    fun parseLive(raw: String?): CpuLiveState {
        if (raw.isNullOrBlank()) return CpuLiveState()

        val cores = mutableListOf<CpuCoreLive>()
        var loadAverage = 0f
        var statTimes: CpuStatTimes? = null

        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("C|") -> {
                    val parts = line.split('|')
                    if (parts.size >= 5) {
                        val index = parts[1].toIntOrNull() ?: return@forEach
                        val max = parts[3].toLongOrNull() ?: return@forEach
                        if (max <= 0L) return@forEach
                        cores += CpuCoreLive(
                            index = index,
                            curFreqKhz = parts[2].toLongOrNull() ?: 0L,
                            maxFreqKhz = max,
                            governor = parts[4].trim(),
                        )
                    }
                }

                line.startsWith("L|") -> {
                    val load = line.removePrefix("L|").trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?.toFloatOrNull() ?: 0f
                    loadAverage = load
                }

                line.startsWith("S|") ->
                    statTimes = parseStatTimes(line.removePrefix("S|"))
            }
        }

        if (cores.isEmpty()) return CpuLiveState()
        cores.sortBy { it.index }

        val clusters = cores
            .groupBy { it.maxFreqKhz }
            .toList()
            .sortedBy { (_, group) -> group.first().index }
            .mapIndexed { index, (max, group) ->
                CpuClusterLive(index = index, cores = group, maxFreqKhz = max)
            }

        val coreCount = cores.size.coerceAtLeast(1)
        val governor = cores
            .map { it.governor }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

        return CpuLiveState(
            cores = cores,
            clusters = clusters,
            loadAvgPercent = (loadAverage / coreCount * 100f).toInt().coerceAtLeast(0),
            governor = governor,
            statTimes = statTimes,
        )
    }

    /**
     * Parses the `cpu` summary line of `/proc/stat`:
     * `cpu user nice system idle iowait irq softirq steal guest guest_nice`.
     * Missing optional columns (older kernels) default to zero.
     */
    fun parseStatTimes(raw: String?): CpuStatTimes? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "cpu") return null

        fun at(index: Int): Long = parts.getOrNull(index + 1)?.toLongOrNull() ?: 0L

        return CpuStatTimes(
            user = at(0),
            nice = at(1),
            system = at(2),
            idle = at(3),
            iowait = at(4),
            irq = at(5),
            softirq = at(6),
            steal = at(7),
        )
    }

    /**
     * CPU utilisation between two `/proc/stat` reads, in percent.
     *
     * `irq`, `softirq` and `steal` count as work; `idle` and `iowait` count as
     * idle. Kernel Adiutor uses the same split. This is why the result differs
     * from the 1-minute load average: load average counts tasks waiting for
     * I/O, while this measures actual CPU time.
     *
     * Returns null when the counters did not advance or went backwards.
     */
    fun computeLoad(previous: CpuStatTimes, current: CpuStatTimes): Int? {
        if (!current.isMonotonicFrom(previous)) return null
        val totalDelta = current.total - previous.total
        val idleDelta = current.idleTotal - previous.idleTotal
        if (totalDelta <= 0L || idleDelta < 0L) return null
        val busyDelta = (totalDelta - idleDelta).coerceIn(0L, totalDelta)
        return ((busyDelta * 100L + totalDelta / 2L) / totalDelta).toInt().coerceIn(0, 100)
    }

    fun parseTimeInState(raw: String?): CpuTimeSnapshot? {
        if (raw.isNullOrBlank()) return null
        val cores = LinkedHashMap<Int, Map<Long, Long>>()
        var cpu: Int? = null
        var freqs = LinkedHashMap<Long, Long>()

        fun flush() {
            val index = cpu ?: return
            if (freqs.isNotEmpty()) cores[index] = freqs
        }

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("P|") -> {
                    flush()
                    freqs = LinkedHashMap()
                    // "P|0/cpufreq/stats/time_in_state"
                    cpu = line.removePrefix("P|").substringBefore('/').toIntOrNull()
                }

                line.isEmpty() -> Unit

                cpu != null -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val freq = parts[0].toLongOrNull() ?: return@forEach
                        val ticks = parts[1].toLongOrNull() ?: return@forEach
                        // time_in_state reports 10 ms units.
                        freqs[freq] = ticks * 10L
                    }
                }
            }
        }
        flush()
        return CpuTimeSnapshot(cores).takeIf { !it.isEmpty }
    }

    /**
     * Diffs two snapshots and returns one table per cluster. Time is averaged
     * across the cores of a cluster so a four-core cluster cannot report four
     * times the wall clock; whatever is missing from the window is shown as
     * deep sleep, exactly like the reference screen.
     */
    fun computeStats(
        live: CpuLiveState,
        current: CpuTimeSnapshot,
        baseline: CpuTimeSnapshot,
        windowMs: Long,
    ): List<CpuStatsClusterStats> {
        if (windowMs <= 0L || live.clusters.isEmpty()) return emptyList()

        return live.clusters.map { cluster ->
            val coreCount = cluster.cores.size.coerceAtLeast(1)
            val freqs = LinkedHashSet<Long>()
            cluster.cores.forEach { core ->
                current.cores[core.index]?.keys?.let(freqs::addAll)
            }

            val rows = freqs.sorted().mapNotNull { freq ->
                var total = 0L
                cluster.cores.forEach { core ->
                    val now = current.cores[core.index]?.get(freq) ?: return@forEach
                    val then = baseline.cores[core.index]?.get(freq) ?: 0L
                    total += (now - then).coerceAtLeast(0L)
                }
                val average = total / coreCount
                if (average <= 0L) return@mapNotNull null
                CpuStatsRow(
                    freqKhz = freq,
                    timeMs = average,
                    fraction = (average.toFloat() / windowMs).coerceIn(0f, 1f),
                )
            }

            val active = rows.sumOf { it.timeMs }
            val deepSleep = (windowMs - active).coerceAtLeast(0L)
            val allRows = buildList {
                if (deepSleep > 0L) {
                    add(
                        CpuStatsRow(
                            freqKhz = null,
                            timeMs = deepSleep,
                            fraction = (deepSleep.toFloat() / windowMs).coerceIn(0f, 1f),
                        ),
                    )
                }
                addAll(rows)
            }

            CpuStatsClusterStats(
                index = cluster.index,
                coreCount = coreCount,
                rows = allRows,
                windowMs = windowMs,
            )
        }
    }
}
