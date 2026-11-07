package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.runtime.Immutable

@Immutable
data class TuningActions(
    val onBack: () -> Unit,
    val onSelectThermal: (Int) -> Unit,
    val onSetBypass: (Boolean) -> Unit,
)
