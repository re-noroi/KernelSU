package me.weishu.kernelsu.data.perftop

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

/** One process as sampled from procfs. */
@Immutable
data class PerfTopProcess(
    val pid: Int,
    val name: String,
    val cpuPercent: Float,
    val resKb: Long,
)

/**
 * Samples the process table from `/proc`, the way Kernel Managers do it.
 *
 * `top` on Android can only report lifetime averages in batch mode on some
 * toybox builds, which is why its `%CPU` column reads far too high. Instead
 * two `/proc` snapshots are taken one second apart: the per-process tick
 * delta divided by the total CPU tick delta is the share of the whole device
 * the process used during that second, the same arithmetic `top` uses for its
 * interval table.
 *
 * Every process row comes from `/proc/<pid>/stat`, so no extra files are read
 * per process: field 14/15 are utime/stime and field 24 is the resident set.
 */
object PerfTopMonitor {

    /**
     * Prints `T <total cpu ticks>` plus one `/proc/<pid>/stat` line per
     * process. One `cat` covers every process, instead of a fork per pid.
     */
    private const val SNAPSHOT_COMMAND =
        "awk '/^cpu /{print \"T \" \$2+\$3+\$4+\$5+\$6+\$7+\$8+\$9+\$10+\$11; exit}' /proc/stat; " +
            "cat /proc/[0-9]*/stat 2>/dev/null"

    suspend fun read(): List<PerfTopProcess>? = withContext(Dispatchers.IO) {
        val first = SysfsNodeCache.execRootCommandOrNull(SNAPSHOT_COMMAND)
        if (first.isNullOrBlank()) return@withContext null
        // Long enough for the tick counters to move, short enough to stay
        // inside the screen's refresh cadence.
        delay(SAMPLE_WINDOW_MS)
        val second = SysfsNodeCache.execRootCommandOrNull(SNAPSHOT_COMMAND)
        if (second.isNullOrBlank()) return@withContext null

        val before = parseSample(first) ?: return@withContext null
        val after = parseSample(second) ?: return@withContext null
        compute(before, after)
    }

    private data class ProcSample(
        val name: String,
        val ticks: Long,
        val rssKb: Long,
    )

    private data class Sample(
        val totalTicks: Long,
        val processes: Map<Int, ProcSample>,
    )

    private fun parseSample(raw: String): Sample? {
        var total = -1L
        val processes = HashMap<Int, ProcSample>()

        raw.lineSequence().forEach { line ->
            when {
                line.startsWith("T ") -> {
                    total = line.substring(2).trim().toLongOrNull() ?: -1L
                }

                line.length > 1 && line[0].isDigit() -> {
                    val open = line.indexOf('(')
                    val close = line.lastIndexOf(')')
                    if (open <= 0 || close <= open) return@forEach
                    val pid = line.substring(0, open).trim().toIntOrNull() ?: return@forEach
                    val name = line.substring(open + 1, close)
                    val rest = line.substring(close + 1).trim().split(WHITESPACE)
                    // rest[0] is field 3 (state); utime/stime are fields 14/15,
                    // rss is field 24.
                    if (rest.size < 22) return@forEach
                    val utime = rest[11].toLongOrNull() ?: return@forEach
                    val stime = rest[12].toLongOrNull() ?: return@forEach
                    val rssPages = rest[21].toLongOrNull() ?: 0L
                    processes[pid] = ProcSample(name, utime + stime, rssPages * pageSizeKb)
                }
            }
        }

        if (total < 0L) return null
        return Sample(total, processes)
    }

    private fun compute(before: Sample, after: Sample): List<PerfTopProcess> {
        val totalDelta = after.totalTicks - before.totalTicks
        if (totalDelta <= 0L) return emptyList()

        return after.processes.mapNotNull { (pid, now) ->
            val previous = before.processes[pid] ?: return@mapNotNull null
            val delta = now.ticks - previous.ticks
            if (delta <= 0L) return@mapNotNull null
            PerfTopProcess(
                pid = pid,
                name = now.name,
                cpuPercent = delta * 100f / totalDelta,
                resKb = now.rssKb,
            )
        }
            .sortedWith(
                compareByDescending<PerfTopProcess> { it.cpuPercent }
                    .thenByDescending { it.resKb }
                    .thenBy { it.pid },
            )
            .take(MAX_ROWS)
    }

    private val WHITESPACE = Regex("\\s+")

    /** Some 16 KiB-page kernels exist, so the page size is not hard-coded. */
    private val pageSizeKb: Long by lazy {
        (android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) / 1024L).coerceAtLeast(1L)
    }

    private const val SAMPLE_WINDOW_MS = 1_000L

    /** Four table rows are already more than a phone screen shows. */
    private const val MAX_ROWS = 40
}
