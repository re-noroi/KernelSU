package me.weishu.kernelsu.ui.viewmodel

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

    fun refresh() {
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                val live = GpuMonitor.readLive()
                _uiState.update { previous ->
                    val history = live.busyPercent?.let { busy ->
                        (previous.history + busy).takeLast(MAX_SAMPLES)
                    } ?: previous.history
                    previous.copy(live = live, liveLoaded = true, history = history)
                }
            } finally {
                running = false
            }
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    private companion object {
        /** 90 samples at one every 10 s is a 15 minute window. */
        const val MAX_SAMPLES = 90
    }
}
