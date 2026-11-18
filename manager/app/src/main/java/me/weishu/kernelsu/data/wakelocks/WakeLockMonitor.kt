package me.weishu.kernelsu.data.wakelocks

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

/** Cumulative time a wake lock has been held, in milliseconds. */
@Immutable
data class WakeLockSnapshot(
    val entries: Map<String, Long>,
    /** Which file the counters came from, so a fallback switch re-bases. */
    val source: String = "",
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * A `source=` header, then one `name<TAB>milliseconds` pair per line. Wake
     * lock names never contain tabs, so the compact form needs no escaping.
     */
    fun serialize(): String = buildString {
        append(SOURCE_PREFIX).append(source).append('\n')
        entries.entries.forEach { (name, time) -> append(name).append('\t').append(time).append('\n') }
    }

    companion object {
        private const val SOURCE_PREFIX = "source="

        fun parse(raw: String?): WakeLockSnapshot? {
            if (raw.isNullOrBlank()) return null
            val entries = LinkedHashMap<String, Long>()
            var source = ""
            raw.lineSequence().forEach { line ->
                if (line.isEmpty()) return@forEach
                if (line.startsWith(SOURCE_PREFIX)) {
                    source = line.removePrefix(SOURCE_PREFIX).trim()
                    return@forEach
                }
                val split = line.lastIndexOf('\t')
                if (split <= 0) return@forEach
                val name = line.substring(0, split)
                val time = line.substring(split + 1).toLongOrNull() ?: return@forEach
                if (name.isNotBlank()) entries[name] = time
            }
            return WakeLockSnapshot(entries, source)
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
 * Reads the cumulative wake-lock counters the system keeps.
 *
 * `dumpsys batterystats --checkin` is the primary source: it is always
 * readable, and it is the only one that carries both the kernel wake locks
 * (`kwl`) and the framework ones (`wl`) such as the `*job*` and `*sync*` tags
 * that Kernel Managers list. The kernel tables remain as a fallback for
 * devices where `dumpsys` is restricted:
 *
 * 1. `dumpsys batterystats --checkin` — milliseconds.
 * 2. debugfs `wakeup_sources` — kernel suspend blockers, milliseconds.
 * 3. `/proc/wakelocks` — the pre-debugfs table, nanoseconds.
 *
 * Android 12+ user builds do not mount debugfs at boot, so the command tries
 * a `mount -t debugfs` before giving up on the kernel tables.
 *
 * The counters are cumulative since unplug or boot, which is why the screen
 * keeps a baseline and only shows the growth since the user enabled it.
 */
object WakeLockMonitor {

    private const val COMMAND =
        "B=\$(dumpsys batterystats --checkin 2>/dev/null " +
            "| grep -E '^[0-9]+,[0-9]+,(u|c|l),(kwl|wl),'); " +
            "if [ -n \"\$B\" ]; then echo 'S|battery'; echo \"\$B\"; else " +
            "D=/sys/kernel/debug; F=\$D/wakeup_sources; " +
            "[ -r \"\$F\" ] || mount -t debugfs debugfs \$D 2>/dev/null; " +
            "[ -r \"\$F\" ] || F=/d/wakeup_sources; " +
            "if [ -r \"\$F\" ] && [ \$(wc -l < \"\$F\") -gt 1 ]; then " +
            "echo 'S|debug'; cat \"\$F\" 2>/dev/null; " +
            "elif [ -r /proc/wakelocks ]; then echo 'S|proc'; cat /proc/wakelocks 2>/dev/null; " +
            "fi; fi"

    /** Returns null when neither the kernel nor batterystats exposed anything. */
    suspend fun read(): WakeLockSnapshot? = withContext(Dispatchers.IO) {
        parse(SysfsNodeCache.execRootCommandOrNull(COMMAND))
    }

    fun parse(raw: String?): WakeLockSnapshot? {
        if (raw.isNullOrBlank()) return null

        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val headerIndex = lines.indexOfFirst { it.startsWith("S|") }
        if (headerIndex < 0) return null

        val source = lines[headerIndex].removePrefix("S|").trim()
        val body = lines.drop(headerIndex + 1)
        return when (source) {
            "proc" -> parseTable(body, "proc", nanosecondSource = true)
            "debug" -> parseTable(body, "debug", nanosecondSource = false)
            "battery" -> parseCheckin(body)
            else -> null
        }
    }

    /**
     * Parses `wakeup_sources` and `/proc/wakelocks`. Both are whitespace
     * tables whose seventh column is the accumulated held time.
     */
    private fun parseTable(
        lines: List<String>,
        source: String,
        nanosecondSource: Boolean,
    ): WakeLockSnapshot {
        val entries = LinkedHashMap<String, Long>()
        lines.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 7 || parts[0] == "name") return@forEach
            val total = parts[6].toLongOrNull() ?: return@forEach
            entries[parts[0]] = if (nanosecondSource) total / 1_000_000L else total
        }
        return WakeLockSnapshot(entries, source)
    }

    /**
     * Parses the check-in dump, whose lines look like
     * `9,uid,category,type,values…`.
     *
     * `kwl` carries one kernel wake lock per line as
     * `timeMs,count,currentMs,maxMs`; `wl` packs up to four framework wake
     * locks per line as repeated `timeMs,kind,count,currentMs,maxMs,totalMs`
     * groups, where kind `p` is the partial wake lock that keeps the device
     * awake. The last/current/unplugged variants are merged separately and
     * the most useful one is kept so a mid-window unplug cannot mix values.
     */
    private fun parseCheckin(lines: List<String>): WakeLockSnapshot? {
        val byCategory = LinkedHashMap<String, LinkedHashMap<String, Long>>()

        fun put(map: MutableMap<String, Long>, name: String, timeMs: Long) {
            if (timeMs <= 0L || name.isBlank()) return
            map[name] = maxOf(map[name] ?: 0L, timeMs)
        }

        lines.forEach { line ->
            val fields = line.split(',')
            if (fields.size < 6) return@forEach
            val map = byCategory.getOrPut(fields[2]) { LinkedHashMap() }
            when (fields[3]) {
                "kwl" -> put(
                    map,
                    fields[4].trim('"'),
                    fields[5].toLongOrNull() ?: return@forEach,
                )

                "wl" -> {
                    val name = fields[4]
                    var index = 5
                    while (index + 5 < fields.size) {
                        val kind = fields[index + 1]
                        val time = fields[index].toLongOrNull() ?: 0L
                        if (kind == "p" && time > 0L) {
                            put(map, name, time)
                            break
                        }
                        index += 6
                    }
                }
            }
        }

        val chosen = CATEGORY_PREFERENCE
            .firstNotNullOfOrNull { byCategory[it] }
            ?: return null
        if (chosen.isEmpty()) return null
        return WakeLockSnapshot(chosen, "battery")
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

    /** `u` since unplugged, then `c` current, then `l` last full charge. */
    private val CATEGORY_PREFERENCE = listOf("u", "c", "l")

    /** Kept small: the screen composes every row inside one card. */
    private const val MAX_ROWS = 60
}
