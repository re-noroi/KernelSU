package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.viewmodel.TuningUiState

@Composable
fun TuningScreenMaterial(
    state: TuningUiState,
    actions: TuningActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tuning_title)) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                ) {
                    item {
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
                        SegmentedDropdownItem(
                            icon = Icons.Filled.Thermostat,
                            title = stringResource(R.string.tuning_thermal_profile),
                            summary = summary,
                            items = state.profiles.map { it.name },
                            enabled = state.thermalSupported && !state.isApplying && state.profiles.isNotEmpty(),
                            selectedIndex = state.profiles
                                .indexOfFirst { it.index == state.thermalIndex }
                                .coerceAtLeast(0),
                            onItemSelected = { position ->
                                state.profiles.getOrNull(position)?.let { profile ->
                                    actions.onSelectThermal(profile.index)
                                }
                            },
                        )
                    }
                    item {
                        val summary = when {
                            !state.bypassSupported -> stringResource(R.string.tuning_unsupported)
                            state.isApplying -> stringResource(R.string.tuning_applying)
                            state.bypassEnabled -> stringResource(R.string.tuning_bypass_on)
                            else -> stringResource(R.string.tuning_bypass_off)
                        }
                        SegmentedSwitchItem(
                            icon = Icons.Filled.BatteryChargingFull,
                            title = stringResource(R.string.tuning_bypass_charging),
                            summary = summary,
                            checked = state.bypassEnabled,
                            enabled = state.bypassSupported && !state.isApplying,
                            onCheckedChange = actions.onSetBypass,
                        )
                    }
                }
            }
        }
    }
}
