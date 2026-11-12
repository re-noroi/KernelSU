package me.weishu.kernelsu.ui.screen.thermal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.ThermalGroup
import me.weishu.kernelsu.ui.viewmodel.ThermalUiState
import me.weishu.kernelsu.ui.viewmodel.ThermalViewModel
import me.weishu.kernelsu.ui.viewmodel.ThermalZoneInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ThermalMonitorScreen() {
    val viewModel = viewModel<ThermalViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Poll only while this screen is open and the app is resumed; leaving the
    // tab cancels the loop, so nothing runs in the background.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(2000L)
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ThermalMonitorMiuix(uiState)
        UiMode.Material, UiMode.Minimal -> ThermalMonitorMaterial(uiState)
    }
}

// ---------------------------------------------------------------------------
// Material
// ---------------------------------------------------------------------------

@Composable
private fun ThermalMonitorMaterial(state: ThermalUiState) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val groups = remember(state.zones) { groupZones(state.zones) }

    ExpressiveScaffold(
        containerColor = if (isMinimal) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.thermal_monitor_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (state.hasLoaded && groups.isEmpty()) {
                item {
                    TonalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 13.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        ThermalZoneRows(
                            zones = emptyList(),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                groups.forEach { (group, zones) ->
                    item(key = "thermal_header_$group") {
                        SectionHeader(groupTitle(group))
                    }
                    item(key = "thermal_card_$group") {
                        TonalCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            ThermalZoneRows(
                                zones = zones,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Miuix
// ---------------------------------------------------------------------------

@Composable
private fun ThermalMonitorMiuix(state: ThermalUiState) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val groups = remember(state.zones) { groupZones(state.zones) }

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                MiuixTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.thermal_monitor_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        MiuixIconButton(onClick = { navigator.pop() }) {
                            MiuixIcon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
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
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
                overscrollEffect = null,
            ) {
                if (state.hasLoaded && groups.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ThermalZoneRows(
                                zones = emptyList(),
                                valueColor = colorScheme.onBackground,
                                mutedColor = colorScheme.onSurfaceVariantSummary,
                                dividerColor = colorScheme.outline.copy(alpha = 0.4f),
                            )
                        }
                    }
                } else {
                    groups.forEach { (group, zones) ->
                        item(key = "thermal_header_$group") {
                            SectionHeaderMiuix(groupTitle(group))
                        }
                        item(key = "thermal_card_$group") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                ThermalZoneRows(
                                    zones = zones,
                                    valueColor = colorScheme.onBackground,
                                    mutedColor = colorScheme.onSurfaceVariantSummary,
                                    dividerColor = colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared content
// ---------------------------------------------------------------------------

private fun groupZones(zones: List<ThermalZoneInfo>): List<Pair<ThermalGroup, List<ThermalZoneInfo>>> =
    ThermalGroup.entries.mapNotNull { group ->
        zones.filter { it.group == group }
            .takeIf { it.isNotEmpty() }
            ?.let { group to it }
    }

@Composable
private fun groupTitle(group: ThermalGroup): String = stringResource(
    when (group) {
        ThermalGroup.CPU -> R.string.thermal_group_cpu
        ThermalGroup.GPU -> R.string.thermal_group_gpu
        ThermalGroup.NPU -> R.string.thermal_group_npu
        ThermalGroup.DSP -> R.string.thermal_group_dsp
        ThermalGroup.MODEM -> R.string.thermal_group_modem
        ThermalGroup.CAMERA -> R.string.thermal_group_camera
        ThermalGroup.VIDEO -> R.string.thermal_group_video
        ThermalGroup.MEMORY -> R.string.thermal_group_memory
        ThermalGroup.CONNECTIVITY -> R.string.thermal_group_connectivity
        ThermalGroup.POWER -> R.string.thermal_group_power
        ThermalGroup.BOARD -> R.string.thermal_group_board
        ThermalGroup.OTHER -> R.string.thermal_group_other
    }
)

@Composable
private fun ThermalZoneRows(
    zones: List<ThermalZoneInfo>,
    valueColor: Color,
    mutedColor: Color,
    dividerColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (zones.isEmpty()) {
            Text(
                text = stringResource(R.string.thermal_monitor_empty),
                color = mutedColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@Column
        }

        zones.forEachIndexed { index, zone ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.name,
                        color = valueColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = zone.type,
                        color = mutedColor,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = zone.temperature,
                    color = valueColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (index != zones.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )
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
private fun SectionHeaderMiuix(text: String) {
    MiuixText(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
        fontSize = 16.sp,
    )
}
