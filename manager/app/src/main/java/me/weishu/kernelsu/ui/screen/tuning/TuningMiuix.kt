package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding

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
        RegisterTabReselect(2) {
            listState.animateScrollToItem(0)
        }
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
                val errorMessage = state.errorMessage
                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = colorScheme.error,
                            fontSize = 14.sp,
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val thermalName = state.profiles
                            .firstOrNull { it.index == state.thermalIndex }
                            ?.name
                        val summary = when {
                            !state.thermalSupported -> stringResource(R.string.tuning_unsupported)
                            state.isApplying -> stringResource(R.string.tuning_applying)
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
                                enabled = state.thermalSupported && !state.isApplying,
                                selectedIndex = state.profiles
                                    .indexOfFirst { it.index == state.thermalIndex }
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { position ->
                                    state.profiles.getOrNull(position)?.let { profile ->
                                        actions.onSelectThermal(profile.index)
                                    }
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
                            state.isApplying -> stringResource(R.string.tuning_applying)
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
                            enabled = state.bypassSupported && !state.isApplying,
                            checked = state.bypassEnabled,
                            onCheckedChange = actions.onSetBypass,
                        )
                    }
                }
            }
        }
    }
}
