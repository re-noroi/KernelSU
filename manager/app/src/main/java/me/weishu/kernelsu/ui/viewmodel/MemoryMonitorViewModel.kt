package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.SysfsNodeCache

data class MemInfoEntry(
    val key: String,
    val value: String,
)

data class MemoryMonitorUiState(
    val isLoading: Boolean = true,
    val ramTotalBytes: Long = 0L,
    val ramUsedBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val zramSupported: Boolean = false,
    val zramAlgorithm: String = "",
    val zramOriginalBytes: Long = 0L,
    val zramCompressedBytes: Long = 0L,
    val memInfo: List<MemInfoEntry> = emptyList(),
) {
    val ramPercent: Float
        get() = if (ramTotalBytes <= 0L) 0f
        else (ramUsedBytes.toFloat() / ramTotalBytes).coerceIn(0f, 1f)

    val swapPercent: Float
        get() = if (swapTotalBytes <= 0L) 0f
        else (swapUsedBytes.toFloat() / swapTotalBytes).coerceIn(0f, 1f)

    /** Average of the physical and virtual (zRAM swap) utilisation in percent. */
    val averagePercent: Int
        get() = ((ramPercent + swapPercent) / 2f * 100f).roundToInt()

    /** Compressed size / original size, expressed as a fraction. */
    val compressionRate: Float
        get() = if (zramOriginalBytes <= 0L) 0f
        else (zramCompressedBytes.toFloat() / zramOriginalBytes).coerceIn(0f, 1f)
}

class MemoryMonitorViewModel : ViewModel() {
    // Seed from the cache warmed up when KTune was opened so the tab renders
    // the last known values immediately instead of a loading state.
    private val _uiState = MutableStateFlow(
        SysfsNodeCache.memorySnapshot()?.let { parseMemory(it) } ?: MemoryMonitorUiState(),
    )
    val uiState: StateFlow<MemoryMonitorUiState> = _uiState.asStateFlow()

    /** Reads the current values once. Callers decide when/whether to repeat it. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        // Cheap no-op once warmed; otherwise resolves the zRAM device once.
        SysfsNodeCache.warmUpMemory()
        val raw = SysfsNodeCache.readMemoryNodes(SysfsNodeCache.memoryNodePaths())
        if (raw.isBlank()) return@withContext

        SysfsNodeCache.updateMemorySnapshot(raw)
        _uiState.value = parseMemory(raw)
    }

    private fun parseMemory(raw: String): MemoryMonitorUiState {
        val parts = raw.split("__SWAPS__", "__MMSTAT__", "__DISKSIZE__", "__COMPALGO__")
        val meminfo = parts.getOrNull(0).orEmpty()
        val swaps = parts.getOrNull(1).orEmpty()
        val mmStat = parts.getOrNull(2).orEmpty()
        val diskSize = parts.getOrNull(3).orEmpty().trim().toLongOrNull() ?: 0L
        val compAlgorithmRaw = parts.getOrNull(4).orEmpty().trim()

        // comp_algorithm is "lzo [lz4] zstd" — the active one is in brackets.
        val zramAlgorithm = compAlgorithmRaw
            .split(Regex("\\s+"))
            .firstOrNull { it.startsWith("[") && it.endsWith("]") }
            ?.trim('[', ']')
            ?: compAlgorithmRaw.split(Regex("\\s+")).firstOrNull().orEmpty()

        fun memValue(key: String): Long =
            Regex("^$key:\\s*(\\d+)", RegexOption.MULTILINE)
                .find(meminfo)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull() ?: 0L

        val totalKb = memValue("MemTotal")
        val availableKb = memValue("MemAvailable").takeIf { it > 0L } ?: memValue("MemFree")
        val ramTotalBytes = totalKb * 1024L
        val ramUsedBytes = (totalKb - availableKb).coerceAtLeast(0L) * 1024L

        var swapTotalKb = 0L
        var swapUsedKb = 0L
        swaps.lines().drop(1).forEach { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size >= 4 && cols[0].contains("zram")) {
                swapTotalKb = cols[2].toLongOrNull() ?: 0L
                swapUsedKb = cols[3].toLongOrNull() ?: 0L
            }
        }

        val mmValues = mmStat.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        val originalBytes = mmValues.getOrNull(0) ?: 0L
        val compressedBytes = mmValues.getOrNull(1) ?: 0L
        val zramSupported = mmStat.isNotBlank() || diskSize > 0L

        // Fall back to zram nodes when /proc/swaps has no zram entry.
        if (swapTotalKb == 0L && diskSize > 0L) {
            swapTotalKb = diskSize / 1024L
            swapUsedKb = originalBytes / 1024L
        } else if (swapUsedKb == 0L && originalBytes > 0L) {
            swapUsedKb = originalBytes / 1024L
        }

        val memInfoEntries = meminfo.lines().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key.isEmpty() || value.isEmpty()) null else MemInfoEntry(key, value)
        }

        return MemoryMonitorUiState(
            isLoading = false,
            ramTotalBytes = ramTotalBytes,
            ramUsedBytes = ramUsedBytes,
            swapTotalBytes = swapTotalKb * 1024L,
            swapUsedBytes = swapUsedKb * 1024L,
            zramSupported = zramSupported,
            zramAlgorithm = zramAlgorithm,
            zramOriginalBytes = originalBytes,
            zramCompressedBytes = compressedBytes,
            memInfo = memInfoEntries,
        )
    }
}
