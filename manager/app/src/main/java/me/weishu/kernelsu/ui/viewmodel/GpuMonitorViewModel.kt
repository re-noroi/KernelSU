package me.weishu.kernelsu.ui.viewmodel

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.weishu.kernelsu.data.gpu.GpuLiveState
import me.weishu.kernelsu.data.gpu.GpuMonitor

@Immutable
data class GpuMonitorUiState(
    val live: GpuLiveState = GpuLiveState(),
    val liveLoaded: Boolean = false,
    /** Busy percentages, oldest first. Only filled while the tab is open. */
    val history: List<Int> = emptyList(),
)

/**
 * GPU tab state. The screen calls [refresh] only while it is resumed, so the
 * usage graph reflects the time the tab was actually being watched and the
 * app costs nothing in the background.
 */
class GpuMonitorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GpuMonitorUiState())
    val uiState: StateFlow<GpuMonitorUiState> = _uiState.asStateFlow()

    private var running = false
    private var lastHistoryElapsed = 0L

    fun refresh() {
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                val live = GpuMonitor.readLive()
                // Live values refresh every second, but the graph is sampled
                // every ten so its 90-point window stays 15 minutes wide.
                val busy = live.busyPercent
                val now = SystemClock.elapsedRealtime()
                val record = busy != null &&
                    (lastHistoryElapsed == 0L || now - lastHistoryElapsed >= HISTORY_INTERVAL_MS)
                if (record) lastHistoryElapsed = now
                _uiState.update { previous ->
                    val history = if (record) {
                        busy?.let { (previous.history + it).takeLast(MAX_SAMPLES) }
                            ?: previous.history
                    } else {
                        previous.history
                    }
                    previous.copy(live = live, liveLoaded = true, history = history)
                }
            } finally {
                running = false
            }
        }
    }

    fun clearHistory() {
        lastHistoryElapsed = 0L
        _uiState.update { it.copy(history = emptyList()) }
    }

    private companion object {
        /** 90 samples at one every 10 s is a 15 minute window. */
        const val MAX_SAMPLES = 90
        const val HISTORY_INTERVAL_MS = 10_000L
    }
}
