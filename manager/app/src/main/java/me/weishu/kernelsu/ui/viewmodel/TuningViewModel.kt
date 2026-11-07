package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class ThermalProfile(
    val index: Int,
    val name: String,
)

data class TuningUiState(
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val thermalSupported: Boolean = false,
    val thermalCurrent: Int = -1,
    val thermalIndex: Int = -1,
    val bypassSupported: Boolean = false,
    val bypassCurrent: Boolean = false,
    val bypassEnabled: Boolean = false,
    val profiles: List<ThermalProfile> = emptyList(),
    val errorMessage: String? = null,
)

class TuningViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TuningUiState())
    val uiState: StateFlow<TuningUiState> = _uiState.asStateFlow()

    private val thermalProfiles = listOf(
        ThermalProfile(10, "Dynamic"),
        ThermalProfile(11, "Class 0"),
        ThermalProfile(15, "AR VR"),
        ThermalProfile(8, "In-calls"),
        ThermalProfile(9, "Game"),
        ThermalProfile(16, "Game 2"),
        ThermalProfile(2, "Extreme"),
        ThermalProfile(12, "Pubg"),
        ThermalProfile(13, "Camera"),
        ThermalProfile(14, "Youtube"),
        ThermalProfile(0, "Disabled")
    )

    private val thermalNode = "/sys/class/thermal/thermal_message/sconfig"
    private val bypassNodes = listOf(
        "/sys/class/power_supply/battery/input_suspend",
        "/sys/class/power_supply/bms/input_suspend"
    )
    
    // Store the active bypass node here instead of in the UI state
    private var activeBypassNode = ""

    // Helper to run raw root commands reliably without libsu namespace bugs
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

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Read Thermal directly
            val thermalOut = execRootCommand("cat $thermalNode")
            val thermalSupported = thermalOut.isNotEmpty()
            val thermalCurrent = thermalOut.toIntOrNull() ?: -1

            // 2. Read Bypass directly
            var bypassSupported = false
            var bypassCurrent = false
            activeBypassNode = ""

            for (node in bypassNodes) {
                val bypassOut = execRootCommand("cat $node")
                if (bypassOut.isNotEmpty()) {
                    bypassSupported = true
                    activeBypassNode = node
                    bypassCurrent = bypassOut != "0"
                    break
                }
            }

            // 3. Update UI
            _uiState.update {
                it.copy(
                    isLoading = false,
                    thermalSupported = thermalSupported,
                    thermalCurrent = thermalCurrent,
                    thermalIndex = if (thermalCurrent >= 0) thermalCurrent else -1,
                    bypassSupported = bypassSupported,
                    bypassCurrent = bypassCurrent,
                    bypassEnabled = bypassCurrent,
                    profiles = thermalProfiles,
                    errorMessage = null
                )
            }
        }
    }

    fun selectThermal(index: Int) {
        if (index < 0) return
        apply("thermal", index.toString())
    }

    fun setBypass(enabled: Boolean) {
        apply("bypass", if (enabled) "1" else "0")
    }

    private fun apply(type: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }

            val success = if (type == "thermal") {
                // Force write and reset permissions
                val cmd = "chmod 644 $thermalNode; echo $value > $thermalNode; chmod 444 $thermalNode"
                execRootCommand(cmd).isEmpty() // No error output means success
            } else {
                // Use the private activeBypassNode variable
                if (activeBypassNode.isNotEmpty()) {
                    execRootCommand("echo $value > $activeBypassNode").isEmpty()
                } else false
            }

            _uiState.update {
                it.copy(
                    isApplying = false,
                    errorMessage = if (!success) "Failed to write to sysfs node" else null,
                )
            }
            if (success) refresh()
        }
    }
}