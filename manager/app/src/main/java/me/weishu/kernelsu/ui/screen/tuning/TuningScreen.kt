package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.TuningViewModel

@Composable
fun TuningScreen(isRootTab: Boolean = false, bottomInnerPadding: Dp = 0.dp) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<TuningViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val actions = TuningActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSelectThermal = viewModel::selectThermal,
        onSetBypass = viewModel::setBypass,
        // Persistence
        onSetPersist = viewModel::setPersist,
        // Scheduler — FakeDreamers
        onSetSkipHeadroom = viewModel::setSkipHeadroom,
        onSetHrScaling = viewModel::setHrScaling,
        onSetBoostHrLp = viewModel::setBoostHrLp,
        onSetBoostHrBig = viewModel::setBoostHrBig,
        onSetBoostHrPrime = viewModel::setBoostHrPrime,
        // Scheduler — CGroup
        onSetCgroupUclampMin = viewModel::setCgroupUclampMin,
        onSetCgroupUclampMax = viewModel::setCgroupUclampMax,
        onSetCgroupShares = viewModel::setCgroupShares,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> TuningScreenMiuix(uiState, actions, isRootTab, bottomInnerPadding)
        UiMode.Material -> TuningScreenMaterial(uiState, actions, isRootTab, bottomInnerPadding)
    }
}