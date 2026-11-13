package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolNavigationIconMaterial
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTitleDropdownMaterial
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedItemContainer
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.TuningUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box

@Composable
fun TuningScreenMaterial(
    state: TuningUiState,
    actions: TuningActions,
    isRootTab: Boolean = false,
    bottomInnerPadding: Dp = 0.dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val isMinimal = LocalUiMode.current == UiMode.Minimal

    if (isRootTab) {
        RegisterTabReselect(2) { listState.animateScrollToItem(0) }
    }

    ExpressiveScaffold(
        containerColor = if (isMinimal) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    if (isRootTab) KernelToolTitleDropdownMaterial(KernelTool.Tuning, navigator)
                    else Text(stringResource(R.string.tuning_title), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    if (isRootTab) KernelToolNavigationIconMaterial(KernelTool.Tuning)
                    else TopBarBackButton(onClick = actions.onBack)
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = maxOf(
                    bottomInnerPadding,
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ) + 24.dp,
            ),
        ) {
            state.errorMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            // ---- PERSISTENCE ----
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 13.dp),
                ) {
                    item {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Save,
                            title = stringResource(R.string.tuning_persist_title),
                            summary = stringResource(
                                if (state.persist) R.string.tuning_persist_summary_on
                                else R.string.tuning_persist_summary_off
                            ),
                            checked = state.persist,
                            enabled = true,
                            onCheckedChange = actions.onSetPersist,
                        )
                    }
                }
            }

            // ---- THERMALS ----
            item { SectionHeader(stringResource(R.string.tuning_section_thermals)) }
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                ) {
                    item {
                        val thermalName = state.profiles.firstOrNull { it.index == state.thermalIndex }?.name
                        val summary = when {
                            !state.thermalSupported -> stringResource(R.string.tuning_unsupported)
                            state.profiles.isEmpty() -> stringResource(R.string.tuning_profiles_unavailable)
                            thermalName == null -> stringResource(R.string.tuning_profiles_unavailable)
                            else -> stringResource(
                                R.string.tuning_thermal_current,
                                "${state.thermalIndex} ($thermalName)",
                            )
                        }
                        SegmentedDropdownItem(
                            icon = Icons.Filled.Thermostat,
                            title = stringResource(R.string.tuning_thermal_profile),
                            summary = summary,
                            items = state.profiles.map { it.name },
                            enabled = state.thermalSupported && state.profiles.isNotEmpty(),
                            selectedIndex = state.profiles
                                .indexOfFirst { it.index == state.thermalIndex }
                                .coerceAtLeast(0),
                            onItemSelected = { pos ->
                                state.profiles.getOrNull(pos)?.let { actions.onSelectThermal(it.index) }
                            },
                        )
                    }
                    if (state.chargeCurrentSupported) {
                        item {
                            val tenths = (state.chargeCurrent / 100_000L).toInt()
                            val currentLabel = "${tenths / 10}.${tenths % 10} A"
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Bolt,
                                title = "Charge Current Control",
                                summary = "Current: $currentLabel",
                                checked = state.chargeWritable,
                                enabled = true,
                                onCheckedChange = actions.onSetChargeWritable,
                            )
                        }
                        if (state.chargeWritable) {
                            item {
                                val minTenths = 10   // 1.0A
                                val maxTenths = 100  // 10.0A
                                val currentTenths = (state.chargeCurrent / 100_000L).toInt()
                                    .coerceIn(minTenths, maxTenths)

                                IntSliderItem(
                                    title = "Max Charging Current",
                                    value = currentTenths,
                                    range = minTenths..maxTenths,
                                    step = 5,
                                    formatter = { v -> "${v / 10}.${v % 10} A" },
                                    onValueChange = { v ->
                                        actions.onSetChargeCurrent(v * 100_000L)
                                    },
                                )
                            }
                        }
                    }
                    item {
                        val summary = when {
                            !state.bypassSupported -> stringResource(R.string.tuning_unsupported)
                            state.bypassEnabled -> stringResource(R.string.tuning_bypass_on)
                            else -> stringResource(R.string.tuning_bypass_off)
                        }
                        SegmentedSwitchItem(
                            icon = Icons.Filled.BatteryChargingFull,
                            title = stringResource(R.string.tuning_bypass_charging),
                            summary = summary,
                            checked = state.bypassEnabled,
                            enabled = state.bypassSupported,
                            onCheckedChange = actions.onSetBypass,
                        )
                    }
                    item {
                        SegmentedItemContainer {
                            BatteryEntry(onClick = actions.onOpenBattery)
                        }
                    }
                    item {
                        SegmentedItemContainer {
                            ThermalEntry(onClick = actions.onOpenThermalMonitor)
                        }
                    }
                }
            }

            // ---- MEMORY ----
            if (state.memory.supported) {
                item { SectionHeader("Memory Management") }
                item {
                    TonalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column {
                            // Swappiness — always exists, just might be RO
                            IntInputItem(
                                title = "Swappiness",
                                value = state.memory.swappiness,
                                range = 0..100,
                                enabled = !state.memory.swappinessRo,
                                onValueChange = actions.onSetSwappiness,
                            )
                            if (state.memory.swappinessRo) {
                                Text(
                                    "Read-only in this kernel.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }

                            // VFS Cache Pressure
                            if (state.memory.vfsSupported) {
                                IntInputItem(
                                    title = "VFS Cache Pressure",
                                    value = state.memory.vfsCache,
                                    range = 0..1000,
                                    onValueChange = actions.onSetVfsCache,
                                )
                            } else {
                                Text(
                                    "VFS Cache Pressure is not supported in this kernel.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }

                            // kcompressd
                            if (state.memory.kcompressdSupported) {
                                IntInputItem(
                                    title = "kcompressd",
                                    value = state.memory.kcompressd,
                                    range = 0..256,
                                    onValueChange = actions.onSetKcompressd,
                                )
                            } else {
                                Text(
                                    "kcompressd is not supported in this kernel.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }

                            // MGLRU
                            if (state.memory.mglruSupported) {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Memory,
                                    title = "MGLRU",
                                    summary = "Current: ${state.memory.mglruRaw}",
                                    checked = state.memory.mglruEnabled,
                                    onCheckedChange = actions.onSetMglru,
                                )
                                if (state.memory.mglruEnabled) {
                                    IntInputItem(
                                        title = "MGLRU min_ttl_ms",
                                        value = state.memory.mglruTtl,
                                        range = 0..10000,
                                        onValueChange = actions.onSetMglruTtl,
                                    )
                                }
                            } else {
                                Text(
                                    "MGLRU is not supported in this kernel.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }

                            MemoryMonitorEntry(onClick = actions.onOpenMemoryMonitor)
                        }
                    }
                }
            }

            // ---- ZRAM ----
            if (state.zram.supported) {
                item { SectionHeader("zRAM") }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("State", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (state.zram.swapActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.zram.swapActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                        )
                    }
                }
                
                item {
                    SegmentedColumn(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    ) {
                        // Size dropdown
                        item {
                            val currentGb = (state.zram.disksize / (1024L * 1024L * 1024L)).coerceAtLeast(0L)
                            val sizes = listOf(0L, 2L, 3L, 4L, 6L, 8L)
                            val labels = listOf("Auto (init default)", "2 GB", "3 GB", "4 GB", "6 GB", "8 GB")
                            val selectedIdx = sizes.indexOf(currentGb).takeIf { it >= 0 } ?: 0

                            SegmentedDropdownItem(
                                icon = Icons.Filled.Memory,
                                title = "Size",
                                summary = if (currentGb == 0L) "Current: Auto" else "Current: ${currentGb} GB",
                                items = labels,
                                enabled = true,
                                selectedIndex = selectedIdx,
                                onItemSelected = { pos ->
                                    sizes.getOrNull(pos)?.let { actions.onSetZramSize(it) }
                                },
                            )
                        }

                        // Compression algorithm dropdown
                        item {
                            val algos = state.zram.compAvailable
                            SegmentedDropdownItem(
                                icon = Icons.Filled.Compress,
                                title = "Compression Algorithm",
                                summary = "Current: ${state.zram.compCurrent.ifEmpty { "—" }}",
                                items = algos,
                                enabled = algos.isNotEmpty(),
                                selectedIndex = algos.indexOf(state.zram.compCurrent).coerceAtLeast(0),
                                onItemSelected = { pos ->
                                    algos.getOrNull(pos)?.let { actions.onSetZramAlgorithm(it) }
                                },
                            )
                        }
                    }
                }
            }

            // ---- SCHEDULERS ----
            if (state.scheduler.skipHeadroomSupported || state.scheduler.peltMultiplierSupported || state.cgroups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.tuning_section_schedulers)) }

                // FakeDreamers
                item { SubHeader(stringResource(R.string.tuning_section_fakedreamers)) }
                item {
                    SegmentedColumn(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    ) {
                        if (state.scheduler.peltMultiplierSupported) {
                            item {
                                PeltMultiplierSwitch(
                                    checked = state.scheduler.peltMultiplier != 0,
                                    onCheckedChange = actions.onSetPeltMultiplier,
                                )
                            }
                        } else if (state.scheduler.skipHeadroomSupported) {
                            item {
                                Text(
                                    stringResource(R.string.tuning_sched_pelt_unsupported),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        if (state.scheduler.skipHeadroomSupported) {
                            item {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Speed,
                                    title = stringResource(R.string.tuning_sched_skip_headroom),
                                    summary = stringResource(
                                        if (state.scheduler.skipHeadroom == 0) R.string.tuning_sched_skip_headroom_on
                                        else R.string.tuning_sched_skip_headroom_off
                                    ),
                                    checked = state.scheduler.skipHeadroom == 0,
                                    enabled = true,
                                    onCheckedChange = actions.onSetSkipHeadroom,
                                )
                            }
                            if (state.scheduler.skipHeadroom == 0) {
                                if (state.scheduler.hrScalingSupported) {
                                    item {
                                        SegmentedSwitchItem(
                                            icon = Icons.Filled.TrendingUp,
                                            title = stringResource(R.string.tuning_sched_hr_scaling),
                                            summary = if (state.scheduler.hrScaling != 0) "On" else "Off",
                                            checked = state.scheduler.hrScaling != 0,
                                            enabled = true,
                                            onCheckedChange = actions.onSetHrScaling,
                                        )
                                    }
                                    if (state.scheduler.hrScaling != 0) {
                                        if (state.scheduler.boostHrLpSupported) {
                                            item {
                                                IntSliderItem(
                                                    title = stringResource(R.string.tuning_sched_boost_lp),
                                                    value = state.scheduler.boostHrLp,
                                                    range = -50..100,
                                                    onValueChange = actions.onSetBoostHrLp,
                                                )
                                            }
                                        }
                                        if (state.scheduler.boostHrBigSupported) {
                                            item {
                                                IntSliderItem(
                                                    title = stringResource(R.string.tuning_sched_boost_big),
                                                    value = state.scheduler.boostHrBig,
                                                    range = -50..100,
                                                    onValueChange = actions.onSetBoostHrBig,
                                                )
                                            }
                                        }
                                        if (state.scheduler.boostHrPrimeSupported) {
                                            item {
                                                IntSliderItem(
                                                    title = stringResource(R.string.tuning_sched_boost_prime),
                                                    value = state.scheduler.boostHrPrime,
                                                    range = -50..30,
                                                    onValueChange = actions.onSetBoostHrPrime,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!state.scheduler.peltMultiplierSupported) {
                            item {
                                Text(
                                    "Not Supported, this is not a FakeDreamer Kernel.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }

                // CGroup
                if (state.cgroups.isNotEmpty()) {
                    item { SubHeader(stringResource(R.string.tuning_section_cgroup)) }
                    state.cgroups.forEach { cg ->
                        item {
                            TonalCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 13.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = cgroupLabel(cg.name),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                )

                                // UClamp Min & Max
                                if (cg.uclampMinSupported && cg.uclampMaxSupported) {
                                    IntInputItem(
                                        title = stringResource(R.string.tuning_sched_uclamp_min),
                                        value = cg.uclampMin,
                                        range = 0..100,
                                        onValueChange = { actions.onSetCgroupUclampMin(cg.name, it) },
                                    )
                                    IntInputItem(
                                        title = stringResource(R.string.tuning_sched_uclamp_max),
                                        value = cg.uclampMax,
                                        range = 0..100,
                                        onValueChange = { actions.onSetCgroupUclampMax(cg.name, it) },
                                    )
                                } else {
                                    Text(
                                        "UClamp is not supported in this kernel.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }

                                // CPU Shares
                                if (cg.sharesSupported) {
                                    IntSliderItem(
                                        title = stringResource(R.string.tuning_sched_shares),
                                        value = (cg.shares * 100 / 20480).coerceIn(5, 100),
                                        range = 5..100,
                                        step = 5,
                                        onValueChange = { pct -> actions.onSetCgroupShares(cg.name, pct * 20480 / 100) },
                                    )
                                } else {
                                    Text(
                                        "Fair group CPU scheduling is disabled in this kernel.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- NETWORKING ----
            if (state.network.supported) {
                item { SectionHeader("Networking") }
                item {
                    SegmentedColumn(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    ) {
                        item {
                            SegmentedDropdownItem(
                                icon = Icons.Filled.NetworkCheck,
                                title = "TCP Cong. Alg.",
                                summary = "Current: ${state.network.tcpCurrent}",
                                items = state.network.tcpAvailable,
                                enabled = state.network.tcpAvailable.isNotEmpty(),
                                selectedIndex = state.network.tcpAvailable.indexOf(state.network.tcpCurrent).coerceAtLeast(0),
                                onItemSelected = { pos -> 
                                    state.network.tcpAvailable.getOrNull(pos)?.let { actions.onSetTcpCongestion(it) }
                                },
                            )
                        }
                        item {
                            val qdiscs = listOf("fq_codel", "cake")
                            SegmentedDropdownItem(
                                icon = Icons.Filled.Router,
                                title = "Queueing Discipline",
                                summary = "Current: ${state.network.qdiscCurrent}",
                                items = qdiscs,
                                enabled = true,
                                selectedIndex = qdiscs.indexOf(state.network.qdiscCurrent).coerceAtLeast(0),
                                onItemSelected = { pos -> 
                                    qdiscs.getOrNull(pos)?.let { actions.onSetQdisc(it) }
                                },
                            )
                        }
                    }
                }
            }

            // ---- MISCELLANEOUS ----
            item { SectionHeader(stringResource(R.string.tuning_section_misc)) }
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                ) {
                    item {
                        SegmentedItemContainer {
                            ColorCalibrationEntry(onClick = actions.onOpenColorCalibration)
                        }
                    }
                }
            }
            item {
                TonalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        IntInputItem(
                            title = stringResource(R.string.tuning_pixel_density),
                            value = state.densityOverride ?: state.physicalDensity,
                            range = 240..1200,
                            enabled = state.physicalDensity > 0,
                            onValueChange = actions.onSetDensity,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.densityOverride?.let { override ->
                                    stringResource(
                                        R.string.tuning_pixel_density_override,
                                        override,
                                        state.physicalDensity,
                                    )
                                } ?: stringResource(
                                    R.string.tuning_pixel_density_physical,
                                    state.physicalDensity,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = actions.onResetDensity,
                                enabled = state.densityOverride != null,
                            ) {
                                Text(stringResource(R.string.tuning_reset))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun PeltMultiplierSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    var locked by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(locked) {
        if (locked) {
            delay(2000)
            locked = false
            pending = null
        }
    }

    val displayChecked = pending ?: checked
    val summary = when {
        locked && displayChecked -> stringResource(R.string.tuning_sched_pelt_enabling)
        locked -> stringResource(R.string.tuning_sched_pelt_disabling)
        displayChecked -> "On"
        else -> "Off"
    }

    SegmentedSwitchItem(
        icon = Icons.Filled.Bolt,
        title = stringResource(R.string.tuning_sched_pelt_multiplier),
        summary = summary,
        checked = displayChecked,
        enabled = !locked,
        onCheckedChange = { value ->
            pending = value
            locked = true
            onCheckedChange(value)
        },
    )
}

/**
 * Slider that tracks the finger in local state and commits a single write
 * when the user releases. The previous version wrote on every frame, which
 * spawned ~60 `su` processes per gesture and made the thumb stutter.
 */
@Composable
private fun IntSliderItem(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    enabled: Boolean = true,
    formatter: (Int) -> String = { it.toString() },
    onValueChange: (Int) -> Unit,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(formatter(dragValue.roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChange(dragValue.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step).coerceAtLeast(0) - 1,
            enabled = enabled,
        )
    }
}

@Composable
private fun IntInputItem(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    fun commit() {
        if (!enabled) return
        val parsed = text.toIntOrNull() ?: value
        val clamped = parsed.coerceIn(range.first, range.last)
        text = clamped.toString()
        if (clamped != value) onValueChange(clamped)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, 
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                "Current: $value  |  Range: ${range.first}–${range.last}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        
        BasicTextField(
            value = text,
            onValueChange = { new ->
                if (enabled && new.length <= 4 && new.all { it.isDigit() }) text = new
            },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .width(64.dp)
                .height(40.dp)
                .border(
                    width = 1.dp,
                    color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    innerTextField()
                }
            }
        )
        
        Button(
            onClick = { commit() },
            enabled = enabled,
            modifier = Modifier
                .width(56.dp)
                .height(40.dp),
            contentPadding = PaddingValues(0.dp)
        ) { 
            Text("Set") 
        }
    }
}

@Composable
private fun MemoryMonitorEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Memory,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.memory_monitor_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.memory_monitor_entry_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BatteryEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.BatteryChargingFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.battery_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.battery_entry_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorCalibrationEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tuning_color_calibration),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.tuning_color_calibration_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThermalEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Thermostat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.thermal_monitor_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.thermal_monitor_entry_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun cgroupLabel(name: String): String = when (name) {
    "background" -> stringResource(R.string.tuning_sched_cgroup_background)
    "top-app" -> stringResource(R.string.tuning_sched_cgroup_top_app)
    "foreground" -> stringResource(R.string.tuning_sched_cgroup_foreground)
    else -> name
}