package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.TuningViewModel

@Composable
fun TuningScreen() {
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
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> TuningScreenMiuix(uiState, actions)
        UiMode.Material -> TuningScreenMaterial(uiState, actions)
    }
}
