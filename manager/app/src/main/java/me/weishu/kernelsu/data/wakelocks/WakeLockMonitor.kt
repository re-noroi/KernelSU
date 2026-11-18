package me.weishu.kernelsu.data.wakelocks

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

/** Cumulative time a kernel wake source has been held, in milliseconds. */
@Immutable
data class WakeLockSnapshot(
    val entries: Map<String, Long>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * One `name<TAB>milliseconds` pair per line. Kernel wake-source names never
     * contain tabs, so the compact form needs no escaping and no parser.
     */
    fun serialize(): String = entries.entries.joinToString("\n") { (name, time) ->
        "$name\t$time"
    }

    companion object {
        fun parse(raw: String?): WakeLockSnapshot? {
            if (raw.isNullOrBlank()) return null
            val entries = LinkedHashMap<String, Long>()
            raw.lineSequence().forEach { line ->
                if (line.isEmpty()) return@forEach
                val split = line.lastIndexOf('\t')
                if (split <= 0) return@forEach
                val name = line.substring(0, split)
                val time = line.substring(split + 1).toLongOrNull() ?: return@forEach
                if (name.isNotBlank()) entries[name] = time
            }
            return WakeLockSnapshot(entries)
        }
    }
}

/** One line of the wakelock table. */
@Immutable
data class WakeLockRow(
    val name: String,
    val timeMs: Long,
)

/**
 * Reads the kernel's cumulative wake-source counters.
 *
 * Both supported files are simple text tables that the kernel updates on its
 * own; reading one costs a single `cat`. `/proc/wakelocks` reports nanoseconds
 * while debugfs `wakeup_sources` reports milliseconds, so everything is
 * normalised to milliseconds before it reaches the UI.
 *
 * The counters are cumulative since boot, which is why the screen keeps a
 * baseline and only shows the growth since the user enabled it (or last reset).
 */
object WakeLockMonitor {

    private const val COMMAND =
        "if [ -r /proc/wakelocks ]; then echo 'S|proc'; cat /proc/wakelocks 2>/dev/null; " +
            "elif [ -r /sys/kernel/debug/wakeup_sources ]; then " +
            "echo 'S|debug'; cat /sys/kernel/debug/wakeup_sources 2>/dev/null; fi"

    /** Returns null when the kernel exposes neither file. */
    suspend fun read(): WakeLockSnapshot? = withContext(Dispatchers.IO) {
        parse(SysfsNodeCache.execRootCommandOrNull(COMMAND))
    }

    fun parse(raw: String?): WakeLockSnapshot? {
        if (raw.isNullOrBlank()) return null

        // Total time lives in the seventh column of both formats. `proc` uses
        // nanoseconds, debugfs already uses milliseconds.
        var nanosecondSource = false
        var sawSource = false
        val entries = LinkedHashMap<String, Long>()

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("S|")) {
                sawSource = true
                nanosecondSource = line.removePrefix("S|").trim() == "proc"
                return@forEach
            }

            val parts = line.split(Regex("\\s+"))
            if (parts.size < 7 || parts[0] == "name") return@forEach
            val total = parts[6].toLongOrNull() ?: return@forEach
            val millis = if (nanosecondSource) total / 1_000_000L else total
            entries[parts[0]] = millis
        }

        return if (sawSource) WakeLockSnapshot(entries) else null
    }

    /**
     * Shows only sources that grew since the baseline, newest growth first.
     * A source that is absent from the baseline is measured from zero because
     * it appeared during the window.
     */
    fun computeRows(
        current: WakeLockSnapshot,
        baseline: WakeLockSnapshot?,
    ): List<WakeLockRow> = current.entries
        .mapNotNull { (name, now) ->
            val then = baseline?.entries?.get(name) ?: 0L
            val grown = now - then
            if (grown > 0L) WakeLockRow(name, grown) else null
        }
        .sortedWith(compareByDescending<WakeLockRow> { it.timeMs }.thenBy { it.name })
        .take(MAX_ROWS)

    /** Kept small: the screen composes every row inside one card. */
    private const val MAX_ROWS = 60
}
