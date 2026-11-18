package me.weishu.kernelsu.ui.viewmodel

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
import me.weishu.kernelsu.data.perftop.PerfTopMonitor
import me.weishu.kernelsu.data.perftop.PerfTopProcess

@Immutable
data class PerfTopUiState(
    val processes: List<PerfTopProcess> = emptyList(),
    val loaded: Boolean = false,
    val unavailable: Boolean = false,
)

/**
 * Holds one `top` snapshot at a time. The screen owns the 3 s cadence and only
 * runs it while resumed, so nothing here samples in the background.
 */
class PerfTopViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PerfTopUiState())
    val uiState: StateFlow<PerfTopUiState> = _uiState.asStateFlow()

    private var running = false

    fun refresh() {
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                val processes = withContext(Dispatchers.IO) { PerfTopMonitor.read() }
                if (processes == null) {
                    _uiState.update {
                        it.copy(unavailable = true, loaded = true)
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        processes = processes,
                        unavailable = false,
                        loaded = true,
                    )
                }
            } finally {
                running = false
            }
        }
    }
}
