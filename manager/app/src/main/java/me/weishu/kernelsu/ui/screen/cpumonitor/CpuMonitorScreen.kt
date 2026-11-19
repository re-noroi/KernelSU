package me.weishu.kernelsu.ui.screen.cpumonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.cpu.CpuCoreLive
import me.weishu.kernelsu.data.cpu.CpuStatsRow
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
import me.weishu.kernelsu.ui.viewmodel.CpuMonitorUiState
import me.weishu.kernelsu.ui.viewmodel.CpuMonitorViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Live CPU card plus an opt-in residency table.
 *
 * Both halves of the screen poll only while it is resumed: the live card every
 * second, the table every 30 s. The table itself is just a diff of the kernel's
 * cumulative `time_in_state` counters, so the values keep advancing while the
 * tab is closed without any background sampler.
 */
@Composable
fun CpuMonitorScreen() {
    val viewModel = viewModel<CpuMonitorViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refreshLive()
                delay(LIVE_INTERVAL_MS)
            }
        }
    }

    // Restarted whenever the toggle changes; disabling stops the loop because
    // there is nothing left to sample.
    LaunchedEffect(lifecycleOwner, state.statsEnabled) {
        if (!state.statsEnabled) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refreshStats()
                delay(30_000L)
            }
        }
    }

    val callbacks = CpuCallbacks(
        onToggleStats = viewModel::setStatsEnabled,
        onResetStats = viewModel::resetStats,
        onSelectCluster = viewModel::selectCluster,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> CpuMonitorMiuix(state, callbacks)
        UiMode.Material, UiMode.Minimal -> CpuMonitorMaterial(state, callbacks)
    }
}

private data class CpuCallbacks(
    val onToggleStats: (Boolean) -> Unit,
    val onResetStats: () -> Unit,
    val onSelectCluster: (Int) -> Unit,
)

// ---------------------------------------------------------------------------
// Material / Minimal
// ---------------------------------------------------------------------------

@Composable
private fun CpuMonitorMaterial(state: CpuMonitorUiState, callbacks: CpuCallbacks) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val colors = CpuColors(
        label = MaterialTheme.colorScheme.onSurfaceVariant,
        value = MaterialTheme.colorScheme.onSurface,
        accent = MaterialTheme.colorScheme.primary,
        divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    ExpressiveScaffold(
        containerColor = if (isMinimal) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.cpu_monitor_title),
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
        CpuContent(
            state = state,
            callbacks = callbacks,
            isMiuix = false,
            colors = colors,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Miuix
// ---------------------------------------------------------------------------

@Composable
private fun CpuMonitorMiuix(state: CpuMonitorUiState, callbacks: CpuCallbacks) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val colors = CpuColors(
        label = colorScheme.onSurfaceVariantSummary,
        value = colorScheme.onBackground,
        accent = colorScheme.primary,
        divider = colorScheme.outline.copy(alpha = 0.4f),
    )

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                MiuixTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.cpu_monitor_title),
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
            CpuContent(
                state = state,
                callbacks = callbacks,
                isMiuix = true,
                colors = colors,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared content
// ---------------------------------------------------------------------------

private data class CpuColors(
    val label: Color,
    val value: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
private fun CpuContent(
    state: CpuMonitorUiState,
    callbacks: CpuCallbacks,
    isMiuix: Boolean,
    colors: CpuColors,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            LiveCard(state = state, isMiuix = isMiuix, colors = colors)
        }
        item {
            StatsToggleCard(
                enabled = state.statsEnabled,
                onToggle = callbacks.onToggleStats,
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        if (state.statsEnabled) {
            item {
                StatsCard(
                    state = state,
                    callbacks = callbacks,
                    isMiuix = isMiuix,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun CpuCard(
    isMiuix: Boolean,
    top: Int = 12,
    bottom: Int = 4,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isMiuix) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = top.dp, bottom = bottom.dp),
        ) {
            Column(content = content)
        }
    } else {
        TonalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = top.dp, bottom = bottom.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun LiveCard(
    state: CpuMonitorUiState,
    isMiuix: Boolean,
    colors: CpuColors,
) {
    val live = state.live
    CpuCard(isMiuix = isMiuix, top = 12, bottom = 4) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.DeveloperBoard,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cpu_monitor_live_title),
                    color = colors.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                !live.available && !state.liveLoaded ->
                    StatusText(stringResource(R.string.cpu_monitor_reading), colors)

                !live.available ->
                    StatusText(stringResource(R.string.cpu_monitor_unavailable), colors)

                else -> {
                    live.cores.chunked(CORES_PER_ROW).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { core ->
                                CoreCell(core = core, colors = colors, modifier = Modifier.weight(1f))
                            }
                            repeat(CORES_PER_ROW - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(2.dp))
                    CpuDivider(colors)

                    InfoRow(
                        label = live.loadPercent?.let {
                            stringResource(R.string.cpu_monitor_load, it)
                        } ?: stringResource(R.string.cpu_monitor_load_reading),
                        value = null,
                        colors = colors,
                    )
                    InfoRow(
                        label = stringResource(R.string.cpu_monitor_load_average, live.loadAvgPercent),
                        value = null,
                        colors = colors,
                    )
                    live.clusters.forEach { cluster ->
                        InfoRow(
                            label = stringResource(
                                R.string.cpu_monitor_cluster_max_freq,
                                clusterLabel(cluster.index, live.clusters.size),
                                formatFreq(cluster.maxFreqKhz),
                            ),
                            value = null,
                            colors = colors,
                        )
                    }
                    if (live.governor.isNotBlank()) {
                        InfoRow(
                            label = stringResource(R.string.cpu_monitor_governor, live.governor),
                            value = null,
                            colors = colors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreCell(core: CpuCoreLive, colors: CpuColors, modifier: Modifier = Modifier) {
    // The four per-row cells are weighted, so the outer edges of the first and
    // last cell would otherwise look uneven: the text is centred and padded
    // symmetrically to keep both gutters the same width.
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (core.curFreqKhz > 0L) formatFreq(core.curFreqKhz)
            else stringResource(R.string.cpu_monitor_offline),
            color = colors.value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatFreq(core.maxFreqKhz),
            color = colors.label,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatsToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isMiuix: Boolean,
    colors: CpuColors,
) {
    CpuCard(isMiuix = isMiuix, top = 4, bottom = 4) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.cpu_monitor_stats_title),
                    color = colors.value,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.cpu_monitor_stats_summary),
                    color = colors.label,
                    fontSize = 12.sp,
                )
            }
            if (isMiuix) {
                MiuixSwitch(checked = enabled, onCheckedChange = onToggle)
            } else {
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun StatsCard(
    state: CpuMonitorUiState,
    callbacks: CpuCallbacks,
    isMiuix: Boolean,
    colors: CpuColors,
) {
    val clusterCount = state.stats.size
    val selected = state.selectedCluster.coerceIn(0, (clusterCount - 1).coerceAtLeast(0))
    val cluster = state.stats.getOrNull(selected)

    CpuCard(isMiuix = isMiuix, top = 4, bottom = 8) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cpu_monitor_stats_title),
                    color = colors.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.statsWindowMs > 0L) {
                        stringResource(
                            R.string.cpu_monitor_stats_window,
                            formatDuration(state.statsWindowMs),
                        )
                    } else {
                        stringResource(R.string.cpu_monitor_stats_since)
                    },
                    color = colors.label,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = callbacks.onResetStats, enabled = !state.statsBusy) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cpu_monitor_stats_reset),
                    tint = colors.accent,
                )
            }
        }

        if (clusterCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.stats.forEachIndexed { index, item ->
                    ClusterPill(
                        label = clusterLabel(item.index, clusterCount),
                        selected = index == selected,
                        onClick = { callbacks.onSelectCluster(index) },
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        CpuDivider(colors)

        when {
            state.statsBusy && cluster == null ->
                StatusText(stringResource(R.string.cpu_monitor_stats_resetting), colors)

            state.statsUnavailable ->
                StatusText(stringResource(R.string.cpu_monitor_stats_unavailable), colors)

            cluster == null || cluster.rows.isEmpty() ->
                StatusText(stringResource(R.string.cpu_monitor_stats_waiting), colors)

            else -> {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    cluster.rows.forEach { row ->
                        StatsRow(row = row, colors = colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: CpuColors,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.7f) else colors.divider,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.accent else colors.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun StatsRow(row: CpuStatsRow, colors: CpuColors) {
    val label = row.freqKhz?.let(::formatFreq)
        ?: stringResource(R.string.cpu_monitor_stats_deep_sleep)
    val percent = formatPercent(row.fraction)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.CenterStart,
    ) {
        // The proportional bar sits behind the text, like the reference table.
        Box(
            modifier = Modifier
                .fillMaxWidth(row.fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    color = colors.accent.copy(alpha = if (row.freqKhz == null) 0.22f else 0.14f),
                    shape = RoundedCornerShape(6.dp),
                ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colors.value,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(row.timeMs),
                color = colors.value,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(96.dp),
            )
            Text(
                text = percent,
                color = colors.value,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun InfoRow(label: String, value: String?, colors: CpuColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.value,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(text = value, color = colors.value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusText(text: String, colors: CpuColors) {
    Text(
        text = text,
        color = colors.label,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun CpuDivider(colors: CpuColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.divider),
    )
}

@Composable
private fun clusterLabel(index: Int, count: Int): String = when {
    count <= 1 -> stringResource(R.string.cpu_monitor_cluster_cpu)
    count == 2 && index == 0 -> stringResource(R.string.cpu_monitor_cluster_little)
    count == 2 && index == 1 -> stringResource(R.string.cpu_monitor_cluster_big)
    count == 3 && index == 0 -> stringResource(R.string.cpu_monitor_cluster_little)
    count == 3 && index == 1 -> stringResource(R.string.cpu_monitor_cluster_big)
    count == 3 && index == 2 -> stringResource(R.string.cpu_monitor_cluster_prime)
    else -> stringResource(R.string.cpu_monitor_cluster_number, index + 1)
}

private fun formatFreq(khz: Long): String =
    String.format(Locale.US, "%d MHz", khz / 1000L)

private fun formatPercent(fraction: Float): String {
    val percent = fraction.coerceIn(0f, 1f) * 100f
    return if (percent >= 10f || percent == 0f) {
        String.format(Locale.US, "%.0f%%", percent)
    } else {
        String.format(Locale.US, "%.1f%%", percent)
    }
}

@Composable
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val parts = mutableListOf<String>()
    if (days > 0L) parts += stringResource(R.string.cpu_monitor_unit_days, days)
    if (days > 0L || hours > 0L) parts += stringResource(R.string.cpu_monitor_unit_hours, hours)
    if (days > 0L || hours > 0L || minutes > 0L) {
        parts += stringResource(R.string.cpu_monitor_unit_minutes, minutes)
    }
    parts += stringResource(R.string.cpu_monitor_unit_seconds, seconds)
    return parts.joinToString(" ")
}

private const val CORES_PER_ROW = 4

/**
 * One second keeps the per-core frequencies close to live. The reads are a
 * handful of tiny sysfs files, and the loop only exists while the screen is
 * resumed, so this does not run in the background.
 */
private const val LIVE_INTERVAL_MS = 1_000L
