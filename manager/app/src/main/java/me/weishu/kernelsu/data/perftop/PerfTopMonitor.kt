package me.weishu.kernelsu.data.perftop

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

/** One process as printed by `top`. */
@Immutable
data class PerfTopProcess(
    val pid: Int,
    val name: String,
    val cpuPercent: Float,
    val resKb: Long,
)

/**
 * Reads the process table through a single batch `top` call.
 *
 * Android ships toybox `top`, so no procfs walk is needed on our side: one
 * command produces the whole snapshot. The header is parsed for column
 * positions because toybox glues the state and CPU columns together in the
 * header (`S[%CPU]`) while printing them as two values per row.
 *
 * Rows are sorted here rather than relying on `top`'s default order, and only
 * the most expensive processes are kept so the UI never composes hundreds of
 * rows.
 */
object PerfTopMonitor {

    private const val COMMAND = "top -b -n 1 2>/dev/null"

    suspend fun read(): List<PerfTopProcess>? = withContext(Dispatchers.IO) {
        parse(SysfsNodeCache.execRootCommandOrNull(COMMAND))
    }

    fun parse(raw: String?): List<PerfTopProcess>? {
        if (raw.isNullOrBlank()) return null

        var header: List<String>? = null
        var pidIndex = 0
        var resIndex = 5
        var cpuIndex = 8
        var argsIndex = 11
        val processes = mutableListOf<PerfTopProcess>()

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val tokens = line.split(Regex("\\s+"))
            if (tokens.isEmpty()) return@forEach

            if (tokens[0] == "PID" && tokens.any { it == "RES" || it == "RSS" }) {
                header = tokens
                val pid = tokens.indexOf("PID")
                val res = tokens.indexOfFirst { it == "RES" || it == "RSS" }
                val cpu = tokens.indexOfFirst { it.contains("%CPU") }
                val args = tokens.indexOfFirst { it == "ARGS" || it == "COMMAND" || it == "NAME" }
                if (res < 0 || cpu < 0 || args < 0) {
                    header = null
                    return@forEach
                }
                // `S[%CPU]` is one header cell covering the state and CPU
                // columns, so every later column shifts by one in data rows.
                val glued = tokens[cpu].startsWith("S") && tokens[cpu].contains('[')
                val shift = if (glued) 1 else 0
                pidIndex = pid.coerceAtLeast(0)
                resIndex = res
                cpuIndex = cpu + shift
                argsIndex = args + shift
                return@forEach
            }

            if (header == null) return@forEach
            if (tokens.size <= argsIndex) return@forEach

            val pid = tokens.getOrNull(pidIndex)?.toIntOrNull() ?: return@forEach
            val cpu = tokens.getOrNull(cpuIndex)?.toFloatOrNull() ?: return@forEach
            val res = tokens.getOrNull(resIndex)?.let(::parseSizeKb) ?: 0L
            val name = tokens.drop(argsIndex).joinToString(" ").trim()
            if (name.isEmpty()) return@forEach
            if (cpu <= 0f && res <= 0L) return@forEach

            processes += PerfTopProcess(
                pid = pid,
                name = name,
                cpuPercent = cpu,
                resKb = res,
            )
        }

        if (header == null) return null
        return processes
            .sortedWith(
                compareByDescending<PerfTopProcess> { it.cpuPercent }
                    .thenByDescending { it.resKb }
                    .thenBy { it.pid },
            )
            .take(MAX_ROWS)
    }

    /** `top` prints RES as KiB by default, or with a K/M/G suffix. */
    private fun parseSizeKb(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0L
        val unit = text.last()
        val number = when {
            unit.isDigit() -> text
            else -> text.dropLast(1)
        }.toDoubleOrNull() ?: return 0L
        return when (unit.uppercaseChar()) {
            'K' -> number.toLong()
            'M' -> (number * 1024L).toLong()
            'G' -> (number * 1024L * 1024L).toLong()
            'B' -> (number / 1024L).toLong()
            else -> number.toLong()
        }
    }

    /** Four table rows are already more than a phone screen shows. */
    private const val MAX_ROWS = 40
}
