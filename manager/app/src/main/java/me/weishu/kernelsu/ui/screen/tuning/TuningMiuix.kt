package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextFieldDefaults
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.KernelTool
import me.weishu.kernelsu.ui.component.bottombar.KernelToolTopAppBarMiuix
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.TuningUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@Composable
fun TuningScreenMiuix(
    state: TuningUiState,
    actions: TuningActions,
    isRootTab: Boolean = false,
    bottomInnerPadding: Dp = 0.dp,
) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()

    if (isRootTab) {
        RegisterTabReselect(2) { listState.animateScrollToItem(0) }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                KernelToolTopAppBarMiuix(
                    currentTool = KernelTool.Tuning,
                    isRootTab = isRootTab,
                    titleRes = R.string.tuning_title,
                    navigator = navigator,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() +
                        maxOf(
                            bottomInnerPadding,
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ) + 24.dp,
                ),
                overscrollEffect = null,
            ) {
                state.errorMessage?.let { msg ->
                    item {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = colorScheme.error,
                            fontSize = 14.sp,
                        )
                    }
                }

                // ---- PERSISTENCE ----
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(R.string.tuning_persist_title),
                            summary = stringResource(
                                if (state.persist) R.string.tuning_persist_summary_on
                                else R.string.tuning_persist_summary_off
                            ),
                            enabled = true,
                            checked = state.persist,
                            onCheckedChange = actions.onSetPersist,
                        )
                    }
                }

                // ---- THERMALS ----
                item { SectionHeaderMiuix(stringResource(R.string.tuning_section_thermals)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
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
                        if (state.profiles.isNotEmpty()) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.tuning_thermal_profile),
                                summary = summary,
                                items = state.profiles.map { it.name },
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Thermostat,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(R.string.tuning_thermal_profile),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                enabled = state.thermalSupported,
                                selectedIndex = state.profiles
                                    .indexOfFirst { it.index == state.thermalIndex }
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { pos ->
                                    state.profiles.getOrNull(pos)?.let { actions.onSelectThermal(it.index) }
                                },
                            )
                        } else {
                            Text(
                                text = summary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                            )
                        }
                        val bypassSummary = when {
                            !state.bypassSupported -> stringResource(R.string.tuning_unsupported)
                            state.bypassEnabled -> stringResource(R.string.tuning_bypass_on)
                            else -> stringResource(R.string.tuning_bypass_off)
                        }
                        SwitchPreference(
                            title = stringResource(R.string.tuning_bypass_charging),
                            summary = bypassSummary,
                            startAction = {
                                Icon(
                                    Icons.Rounded.BatteryChargingFull,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.tuning_bypass_charging),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            enabled = state.bypassSupported,
                            checked = state.bypassEnabled,
                            onCheckedChange = actions.onSetBypass,
                        )
                    }
                }

                // ---- MEMORY ----
                if (state.memory.supported) {
                    item { SectionHeaderMiuix("Memory Management") }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                IntSliderMiuix(
                                    title = "Swappiness",
                                    value = state.memory.swappiness,
                                    range = 0..100,
                                    enabled = !state.memory.swappinessRo,
                                    onValueChange = actions.onSetSwappiness,
                                )
                                if (state.memory.swappinessRo) {
                                    Text(
                                        "Read-only in kernel (CONFIG_RW_SWAPPINESS=n)",
                                        color = colorScheme.error,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                IntInputMiuix(
                                    title = "VFS Cache Pressure",
                                    value = state.memory.vfsCache,
                                    range = 0..1000,
                                    onValueChange = actions.onSetVfsCache,
                                )
                                IntInputMiuix(
                                    title = "kcompressd",
                                    value = state.memory.kcompressd,
                                    range = 0..256,
                                    onValueChange = actions.onSetKcompressd,
                                )
                                SwitchPreference(
                                    title = "MGLRU",
                                    summary = "Current: ${state.memory.mglruRaw}",
                                    enabled = true,
                                    checked = state.memory.mglruEnabled,
                                    onCheckedChange = actions.onSetMglru,
                                )
                                if (state.memory.mglruEnabled) {
                                    IntInputMiuix(
                                        title = "MGLRU min_ttl_ms",
                                        value = state.memory.mglruTtl,
                                        range = 0..10000,
                                        onValueChange = actions.onSetMglruTtl,
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- SCHEDULERS ----
                if (state.scheduler.supported) {
                    item { SectionHeaderMiuix(stringResource(R.string.tuning_section_schedulers)) }

                    item { SubHeaderMiuix(stringResource(R.string.tuning_section_fakedreamers)) }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = stringResource(R.string.tuning_sched_skip_headroom),
                                summary = stringResource(
                                    if (state.scheduler.skipHeadroom == 0)
                                        R.string.tuning_sched_skip_headroom_on
                                    else R.string.tuning_sched_skip_headroom_off
                                ),
                                enabled = true,
                                checked = state.scheduler.skipHeadroom == 0,
                                onCheckedChange = actions.onSetSkipHeadroom,
                            )
                            if (state.scheduler.skipHeadroom == 0) {
                                SwitchPreference(
                                    title = stringResource(R.string.tuning_sched_hr_scaling),
                                    summary = if (state.scheduler.hrScaling != 0) "On" else "Off",
                                    enabled = true,
                                    checked = state.scheduler.hrScaling != 0,
                                    onCheckedChange = actions.onSetHrScaling,
                                )
                                if (state.scheduler.hrScaling != 0) {
                                    IntSliderMiuix(
                                        title = stringResource(R.string.tuning_sched_boost_lp),
                                        value = state.scheduler.boostHrLp,
                                        range = -50..100,
                                        onValueChange = actions.onSetBoostHrLp,
                                    )
                                    IntSliderMiuix(
                                        title = stringResource(R.string.tuning_sched_boost_big),
                                        value = state.scheduler.boostHrBig,
                                        range = -50..100,
                                        onValueChange = actions.onSetBoostHrBig,
                                    )
                                    IntSliderMiuix(
                                        title = stringResource(R.string.tuning_sched_boost_prime),
                                        value = state.scheduler.boostHrPrime,
                                        range = -50..30,
                                        onValueChange = actions.onSetBoostHrPrime,
                                    )
                                }
                            }
                        }
                    }

                    if (state.cgroups.isNotEmpty()) {
                        item { SubHeaderMiuix(stringResource(R.string.tuning_section_cgroup)) }
                        state.cgroups.forEach { cg ->
                            item {
                                Card(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = cgroupLabelMiuix(cg.name),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontSize = 16.sp,
                                    )
                                    IntInputMiuix(
                                        title = stringResource(R.string.tuning_sched_uclamp_min),
                                        value = cg.uclampMin,
                                        range = 0..100,
                                        onValueChange = { actions.onSetCgroupUclampMin(cg.name, it) },
                                    )
                                    IntInputMiuix(
                                        title = stringResource(R.string.tuning_sched_uclamp_max),
                                        value = cg.uclampMax,
                                        range = 0..100,
                                        onValueChange = { actions.onSetCgroupUclampMax(cg.name, it) },
                                    )
                                    IntSliderMiuix(
                                        title = stringResource(R.string.tuning_sched_shares),
                                        value = (cg.shares * 100 / 20480).coerceIn(5, 100),
                                        range = 5..100,
                                        step = 5,
                                        onValueChange = { pct ->
                                            actions.onSetCgroupShares(cg.name, pct * 20480 / 100)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- NETWORKING ----
                if (state.network.supported) {
                    item { SectionHeaderMiuix("Networking") }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            OverlayDropdownPreference(
                                title = "TCP Cong. Alg.",
                                summary = "Current: ${state.network.tcpCurrent}",
                                items = state.network.tcpAvailable,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.NetworkCheck,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "TCP Congestion",
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                enabled = state.network.tcpAvailable.isNotEmpty(),
                                selectedIndex = state.network.tcpAvailable.indexOf(state.network.tcpCurrent).coerceAtLeast(0),
                                onSelectedIndexChange = { pos ->
                                    state.network.tcpAvailable.getOrNull(pos)?.let { actions.onSetTcpCongestion(it) }
                                },
                            )
                            
                            val qdiscs = listOf("fq_codel", "cake")
                            OverlayDropdownPreference(
                                title = "Queueing Discipline",
                                summary = "Current: ${state.network.qdiscCurrent}",
                                items = qdiscs,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Router,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "QDisc",
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                enabled = true,
                                selectedIndex = qdiscs.indexOf(state.network.qdiscCurrent).coerceAtLeast(0),
                                onSelectedIndexChange = { pos ->
                                    qdiscs.getOrNull(pos)?.let { actions.onSetQdisc(it) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderMiuix(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
        fontSize = 16.sp,
    )
}

@Composable
private fun SubHeaderMiuix(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
        color = colorScheme.onSurfaceVariantSummary,
        fontSize = 14.sp,
    )
}

@Composable
private fun IntSliderMiuix(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 15.sp)
            Text("${dragValue.roundToInt()}", fontSize = 15.sp)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChange(dragValue.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step).coerceAtLeast(0) - 1,
            // FORCE MIUIX COLORS
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                activeTickColor = colorScheme.onPrimary,
            ),
            modifier = Modifier.height(32.dp) // Make slider slightly more compact
        )
    }
}

@Composable
private fun IntInputMiuix(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean = true, // <-- ADD THIS
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
                fontSize = 15.sp,
                color = if (enabled) colorScheme.onBackground else colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                "Current: $value  |  Range: ${range.first}–${range.last}",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                if (enabled && new.length <= 4 && new.all { it.isDigit() }) text = new
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            textStyle = LocalTextStyle.current.copy(color = colorScheme.onBackground),
            colors = TextFieldDefaults.colors(
                focusedTextColor = colorScheme.onBackground,
                unfocusedTextColor = colorScheme.onBackground,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = colorScheme.primary,
                focusedIndicatorColor = colorScheme.primary
            ),
            modifier = Modifier
                .width(60.dp)
                .height(48.dp)
        )
        
        Button(
            onClick = { commit() },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ),
            modifier = Modifier
                .width(52.dp)
                .height(40.dp),
            contentPadding = PaddingValues(0.dp)
        ) { 
            Text("Set") 
        }
    }
}

@Composable
private fun cgroupLabelMiuix(name: String): String = when (name) {
    "background" -> stringResource(R.string.tuning_sched_cgroup_background)
    "top-app" -> stringResource(R.string.tuning_sched_cgroup_top_app)
    "foreground" -> stringResource(R.string.tuning_sched_cgroup_foreground)
    else -> name
}