package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class MemoryMonitorUiState(
    val isLoading: Boolean = true,
    val ramTotalBytes: Long = 0L,
    val ramUsedBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val zramSupported: Boolean = false,
    val zramOriginalBytes: Long = 0L,
    val zramCompressedBytes: Long = 0L,
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
    private val _uiState = MutableStateFlow(MemoryMonitorUiState())
    val uiState: StateFlow<MemoryMonitorUiState> = _uiState.asStateFlow()

    /** Reads the current values once. Callers decide when/whether to repeat it. */
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

    private fun refreshInternal() {
        val raw = execRootCommand(
            "cat /proc/meminfo 2>/dev/null;" +
                " echo __SWAPS__;" +
                " cat /proc/swaps 2>/dev/null;" +
                " echo __MMSTAT__;" +
                " cat /sys/block/zram0/mm_stat 2>/dev/null;" +
                " echo __DISKSIZE__;" +
                " cat /sys/block/zram0/disksize 2>/dev/null"
        )

        val parts = raw.split("__SWAPS__", "__MMSTAT__", "__DISKSIZE__")
        val meminfo = parts.getOrNull(0).orEmpty()
        val swaps = parts.getOrNull(1).orEmpty()
        val mmStat = parts.getOrNull(2).orEmpty()
        val diskSize = parts.getOrNull(3).orEmpty().trim().toLongOrNull() ?: 0L

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

        _uiState.update {
            it.copy(
                isLoading = false,
                ramTotalBytes = ramTotalBytes,
                ramUsedBytes = ramUsedBytes,
                swapTotalBytes = swapTotalKb * 1024L,
                swapUsedBytes = swapUsedKb * 1024L,
                zramSupported = zramSupported,
                zramOriginalBytes = originalBytes,
                zramCompressedBytes = compressedBytes,
            )
        }
    }
}
