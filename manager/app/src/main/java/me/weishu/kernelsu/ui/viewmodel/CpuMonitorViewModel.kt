package me.weishu.kernelsu.ui.viewmodel

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.cpu.CpuLiveState
import me.weishu.kernelsu.data.cpu.CpuMonitor
import me.weishu.kernelsu.data.cpu.CpuStatTimes
import me.weishu.kernelsu.data.cpu.CpuStatsClusterStats
import me.weishu.kernelsu.data.cpu.CpuStatsStore
import me.weishu.kernelsu.data.cpu.CpuTimeSnapshot
import me.weishu.kernelsu.ksuApp

@Immutable
data class CpuMonitorUiState(
    val live: CpuLiveState = CpuLiveState(),
    val liveLoaded: Boolean = false,
    val statsEnabled: Boolean = false,
    val stats: List<CpuStatsClusterStats> = emptyList(),
    val statsWindowMs: Long = 0L,
    val selectedCluster: Int = 0,
    val statsBusy: Boolean = false,
    /** True when the device exposed no `time_in_state` nodes at all. */
    val statsUnavailable: Boolean = false,
)

/**
 * Holds the CPU tab state. The screen owns the polling cadence; this class only
 * performs one read per call, so nothing here runs unless the tab is visible.
 *
 * Statistics are a diff of the kernel's cumulative `time_in_state` counters
 * against a stored baseline. That means enabling the toggle costs one read,
 * and each 30 s refresh costs one more — there is no timer and no service.
 */
class CpuMonitorViewModel : ViewModel() {

    private val store = CpuStatsStore(ksuApp)

    private val _uiState = MutableStateFlow(
        CpuMonitorUiState(statsEnabled = store.enabled),
    )
    val uiState: StateFlow<CpuMonitorUiState> = _uiState.asStateFlow()

    private var liveRunning = false
    private var statsRunning = false

    /** Counter read paired with the previous live refresh, for the load delta. */
    private var lastStatTimes: CpuStatTimes? = null
    private var lastStatAtElapsedMs = 0L

    fun refreshLive() {
        if (liveRunning) return
        liveRunning = true
        viewModelScope.launch {
            try {
                val live = withContext(Dispatchers.IO) { CpuMonitor.readLive() }
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastStatAtElapsedMs
                val statTimes = live.statTimes
                val previous = lastStatTimes
                val load = when {
                    statTimes == null -> null
                    previous == null || elapsed > MAX_SAMPLE_GAP_MS -> null
                    else -> CpuMonitor.computeLoad(previous, statTimes)
                        // A very quick manual refresh may span no tick at all;
                        // keep the last value instead of flashing "reading".
                        ?: _uiState.value.live.loadPercent
                }
                lastStatTimes = statTimes
                lastStatAtElapsedMs = now
                _uiState.update {
                    it.copy(
                        live = live.copy(loadPercent = load),
                        liveLoaded = true,
                    )
                }
            } finally {
                liveRunning = false
            }
        }
    }

    fun refreshStats() {
        if (!_uiState.value.statsEnabled || statsRunning) return
        statsRunning = true
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val live = if (state.live.clusters.isEmpty()) {
                    withContext(Dispatchers.IO) { CpuMonitor.readLive() }.also { loaded ->
                        _uiState.update { it.copy(live = loaded, liveLoaded = true) }
                    }
                } else {
                    state.live
                }

                val current = withContext(Dispatchers.IO) { CpuMonitor.readTimeInState() }
                if (current == null) {
                    _uiState.update { it.copy(statsUnavailable = true, statsBusy = false) }
                    return@launch
                }

                val now = SystemClock.elapsedRealtime()
                val baseline = store.baseline()
                if (baseline == null || now < baseline.savedAtElapsedMs) {
                    // First run, or the device rebooted: start a new window.
                    store.saveBaseline(current, now)
                    _uiState.update {
                        it.copy(
                            stats = emptyList(),
                            statsWindowMs = 0L,
                            statsUnavailable = false,
                            selectedCluster = 0,
                        )
                    }
                    return@launch
                }

                val window = now - baseline.savedAtElapsedMs
                val stats = if (window >= MIN_WINDOW_MS) {
                    CpuMonitor.computeStats(live, current, baseline.snapshot, window)
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        stats = stats,
                        statsWindowMs = window,
                        statsUnavailable = false,
                        selectedCluster = it.selectedCluster
                            .coerceIn(0, (stats.size - 1).coerceAtLeast(0)),
                    )
                }
            } finally {
                statsRunning = false
            }
        }
    }

    fun setStatsEnabled(enabled: Boolean) {
        store.enabled = enabled
        _uiState.update {
            it.copy(
                statsEnabled = enabled,
                stats = emptyList(),
                statsWindowMs = 0L,
                selectedCluster = 0,
                statsUnavailable = false,
                statsBusy = enabled,
            )
        }
        if (!enabled) return

        viewModelScope.launch {
            try {
                captureFreshBaseline()
            } finally {
                _uiState.update { it.copy(statsBusy = false) }
            }
        }
    }

    /** Re-bases the counters so the table starts again from now. */
    fun resetStats() {
        if (!_uiState.value.statsEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(statsBusy = true) }
            try {
                captureFreshBaseline()
            } finally {
                _uiState.update { it.copy(statsBusy = false) }
            }
        }
    }

    fun selectCluster(index: Int) {
        _uiState.update {
            if (index in it.stats.indices) it.copy(selectedCluster = index) else it
        }
    }

    private suspend fun captureFreshBaseline() {
        val snapshot: CpuTimeSnapshot? = withContext(Dispatchers.IO) { CpuMonitor.readTimeInState() }
        if (snapshot == null) {
            _uiState.update {
                it.copy(stats = emptyList(), statsWindowMs = 0L, statsUnavailable = true)
            }
            return
        }
        store.saveBaseline(snapshot, SystemClock.elapsedRealtime())
        _uiState.update {
            it.copy(stats = emptyList(), statsWindowMs = 0L, statsUnavailable = false, selectedCluster = 0)
        }
    }

    private companion object {
        /** Avoids drawing a table from a few hundred milliseconds of data. */
        const val MIN_WINDOW_MS = 2_000L

        /** A gap larger than this means the tab was in the background. */
        const val MAX_SAMPLE_GAP_MS = 5_000L
    }
}
