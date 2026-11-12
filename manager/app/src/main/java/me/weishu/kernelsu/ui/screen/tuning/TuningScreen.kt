package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.data.SysfsNodeCache
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.TuningViewModel

@Composable
fun TuningScreen(
    isRootTab: Boolean = false,
    bottomInnerPadding: Dp = 0.dp,
) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<TuningViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Enumerate the thermal/battery sysfs paths once, so opening either monitor
    // only has to read live values.
    LaunchedEffect(Unit) {
        SysfsNodeCache.warmUp()
    }

    val actions = TuningActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenMemoryMonitor = dropUnlessResumed { navigator.push(Route.MemoryMonitor) },
        onOpenBattery = dropUnlessResumed { navigator.push(Route.Battery) },
        onOpenThermalMonitor = dropUnlessResumed { navigator.push(Route.ThermalMonitor) },
        onSelectThermal = viewModel::selectThermal,
        onSetChargeWritable = viewModel::setChargeWritable,
        onSetChargeCurrent = viewModel::setChargeCurrent,
        onSetBypass = viewModel::setBypass,
        // Persistence
        onSetPersist = viewModel::setPersist,
        // Scheduler — FakeDreamers
        onSetSkipHeadroom = viewModel::setSkipHeadroom,
        onSetPeltMultiplier = viewModel::setPeltMultiplier,
        onSetHrScaling = viewModel::setHrScaling,
        onSetBoostHrLp = viewModel::setBoostHrLp,
        onSetBoostHrBig = viewModel::setBoostHrBig,
        onSetBoostHrPrime = viewModel::setBoostHrPrime,
        // Scheduler — CGroup
        onSetCgroupUclampMin = viewModel::setCgroupUclampMin,
        onSetCgroupUclampMax = viewModel::setCgroupUclampMax,
        onSetCgroupShares = viewModel::setCgroupShares,
        // Networking
        onSetTcpCongestion = viewModel::setTcpCongestion,
        onSetQdisc = viewModel::setQdisc,
        // Memory
        onSetSwappiness = viewModel::setSwappiness,
        onSetVfsCache = viewModel::setVfsCache,
        onSetKcompressd = viewModel::setKcompressd,
        onSetMglru = viewModel::setMglru,
        onSetMglruTtl = viewModel::setMglruTtl,
        // zRAM
        onSetZramSize = viewModel::setZramSize,
        onSetZramAlgorithm = viewModel::setZramAlgorithm,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> TuningScreenMiuix(uiState, actions, isRootTab, bottomInnerPadding)
        UiMode.Material, UiMode.Minimal -> TuningScreenMaterial(uiState, actions, isRootTab, bottomInnerPadding)
    }
}
