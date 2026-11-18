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
import me.weishu.kernelsu.data.wakelocks.WakeLockMonitor
import me.weishu.kernelsu.data.wakelocks.WakeLockRow
import me.weishu.kernelsu.data.wakelocks.WakeLockSnapshot
import me.weishu.kernelsu.data.wakelocks.WakeLockStatsStore
import me.weishu.kernelsu.ksuApp

@Immutable
data class WakeLockMonitorUiState(
    val enabled: Boolean = false,
    val rows: List<WakeLockRow> = emptyList(),
    val windowMs: Long = 0L,
    val busy: Boolean = false,
    /** True when the kernel exposed neither wake-source file. */
    val unavailable: Boolean = false,
    /** True once at least one successful read has happened. */
    val loaded: Boolean = false,
)

/**
 * Holds the wakelock tab state. The screen owns the polling cadence and only
 * polls while the toggle is on and the tab is resumed; this class performs one
 * read per call and never starts a timer of its own.
 */
class WakeLockMonitorViewModel : ViewModel() {

    private val store = WakeLockStatsStore(ksuApp)

    private val _uiState = MutableStateFlow(
        WakeLockMonitorUiState(enabled = store.enabled),
    )
    val uiState: StateFlow<WakeLockMonitorUiState> = _uiState.asStateFlow()

    private var running = false

    fun refresh() {
        if (!_uiState.value.enabled || running) return
        running = true
        viewModelScope.launch {
            try {
                val current = withContext(Dispatchers.IO) { WakeLockMonitor.read() }
                if (current == null) {
                    _uiState.update {
                        it.copy(unavailable = true, busy = false, loaded = true)
                    }
                    return@launch
                }

                val now = SystemClock.elapsedRealtime()
                val baseline = store.baseline()
                if (baseline == null || now < baseline.savedAtElapsedMs) {
                    // First read, or the device rebooted: start a new window.
                    store.saveBaseline(current, now)
                    _uiState.update {
                        it.copy(
                            rows = emptyList(),
                            windowMs = 0L,
                            unavailable = false,
                            busy = false,
                            loaded = true,
                        )
                    }
                    return@launch
                }

                val window = now - baseline.savedAtElapsedMs
                val rows = if (window >= MIN_WINDOW_MS) {
                    WakeLockMonitor.computeRows(current, baseline.snapshot)
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        rows = rows,
                        windowMs = window,
                        unavailable = false,
                        busy = false,
                        loaded = true,
                    )
                }
            } finally {
                running = false
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        store.enabled = enabled
        _uiState.update {
            it.copy(
                enabled = enabled,
                rows = emptyList(),
                windowMs = 0L,
                unavailable = false,
                busy = enabled,
            )
        }
        if (!enabled) return

        viewModelScope.launch {
            try {
                captureFreshBaseline()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    /** Re-bases the counters so the table starts again from now. */
    fun reset() {
        if (!_uiState.value.enabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                captureFreshBaseline()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun captureFreshBaseline() {
        val snapshot: WakeLockSnapshot? = withContext(Dispatchers.IO) { WakeLockMonitor.read() }
        if (snapshot == null) {
            _uiState.update {
                it.copy(rows = emptyList(), windowMs = 0L, unavailable = true, loaded = true)
            }
            return
        }
        store.saveBaseline(snapshot, SystemClock.elapsedRealtime())
        _uiState.update {
            it.copy(rows = emptyList(), windowMs = 0L, unavailable = false, loaded = true)
        }
    }

    private companion object {
        /** Avoids drawing a table from a few hundred milliseconds of data. */
        const val MIN_WINDOW_MS = 2_000L
    }
}
