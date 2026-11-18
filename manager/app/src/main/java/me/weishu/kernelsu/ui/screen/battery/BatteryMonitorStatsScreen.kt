package me.weishu.kernelsu.ui.screen.battery

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.battery.BatteryStatsDump
import me.weishu.kernelsu.data.battery.BatteryStatsDumper
import me.weishu.kernelsu.data.battery.BatteryStatsStore
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.Locale

/**
 * Detailed battery telemetry, reached from the Battery screen when the
 * persistent monitor is enabled.
 *
 * The monitor's own counters are read from its preferences every two seconds
 * (cheap). `dumpsys batterystats`, which supplies the raw since-charge and
 * estimated-power sections, is run only when the screen opens or the user
 * explicitly refreshes — running it on a timer would be the expensive part.
 */
@Composable
fun BatteryMonitorStatsScreen() {
    val context = LocalContext.current
    var monitor by remember { mutableStateOf(BatteryStatsStore(context)) }
    var dump by remember { mutableStateOf(BatteryStatsDump()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        dump = BatteryStatsDumper.read()
        loading = false
    }

    // The service persists once a minute; re-reading prefs here is only a few
    // field reads and keeps the screen live without touching dumpsys.
    LaunchedEffect(Unit) {
        while (isActive) {
            monitor = BatteryStatsStore(context)
            delay(2_000L)
        }
    }

    val onRefresh: () -> Unit = { refreshKey++ }

    when (LocalUiMode.current) {
        UiMode.Miuix -> BatteryMonitorStatsMiuix(monitor, dump, loading, onRefresh)
        UiMode.Material, UiMode.Minimal ->
            BatteryMonitorStatsMaterial(monitor, dump, loading, onRefresh)
    }
}

// ---------------------------------------------------------------------------
// Material / Minimal
// ---------------------------------------------------------------------------

@Composable
private fun BatteryMonitorStatsMaterial(
    monitor: BatteryStatsStore,
    dump: BatteryStatsDump,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val colors = StatsColors(
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
                        stringResource(R.string.battery_monitor_stats_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.battery_monitor_stats_refresh),
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        StatsContent(
            monitor = monitor,
            dump = dump,
            loading = loading,
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
private fun BatteryMonitorStatsMiuix(
    monitor: BatteryStatsStore,
    dump: BatteryStatsDump,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val colors = StatsColors(
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
                    title = stringResource(R.string.battery_monitor_stats_title),
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
                    actions = {
                        MiuixIconButton(onClick = onRefresh) {
                            MiuixIcon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.battery_monitor_stats_refresh),
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
            StatsContent(
                monitor = monitor,
                dump = dump,
                loading = loading,
                isMiuix = true,
                colors = colors,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
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

private data class StatsColors(
    val label: Color,
    val value: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
private fun StatsContent(
    monitor: BatteryStatsStore,
    dump: BatteryStatsDump,
    loading: Boolean,
    isMiuix: Boolean,
    colors: StatsColors,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    val capacityMah = dump.valueOf("Last learned battery capacity")
        ?.let { Regex("[0-9]+(?:\\.[0-9]+)?").find(it)?.value?.toFloatOrNull() }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            StatsSection(
                title = stringResource(R.string.battery_monitor_stats_screen_on),
                rows = listOf(
                    stringResource(R.string.battery_monitor_stats_duration) to
                        formatSpan(monitor.screenOnMillis),
                    stringResource(R.string.battery_monitor_stats_battery_drained) to
                        formatDrained(monitor.screenOnPercent, capacityMah),
                    stringResource(R.string.battery_monitor_stats_active_drain) to
                        formatRate(monitor.activeDrainPerHour),
                ),
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        item {
            StatsSection(
                title = stringResource(R.string.battery_monitor_stats_screen_off),
                rows = listOf(
                    stringResource(R.string.battery_monitor_stats_duration) to
                        formatSpan(monitor.screenOffMillis),
                    stringResource(R.string.battery_monitor_stats_battery_drained) to
                        formatDrained(monitor.screenOffPercent, capacityMah),
                    stringResource(R.string.battery_monitor_stats_deep_sleep) to
                        formatShare(formatSpan(monitor.deepSleepMillis), monitor.deepSleepShare),
                    stringResource(R.string.battery_monitor_stats_awake) to
                        formatShare(formatSpan(monitor.awakeMillis), monitor.awakeShare),
                    stringResource(R.string.battery_monitor_stats_idle_drain) to
                        formatRate(monitor.idleDrainPerHour),
                ),
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        if (dump.powerUse.isNotEmpty()) {
            item {
                StatsSection(
                    title = stringResource(R.string.battery_monitor_stats_power_use),
                    rows = dump.powerUse.map { it.label to it.value },
                    isMiuix = isMiuix,
                    colors = colors,
                )
            }
        }
        item {
            StatsSection(
                title = stringResource(R.string.battery_monitor_stats_raw),
                rows = dump.sinceCharge.map { it.label to it.value },
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        item {
            val message = when {
                loading -> stringResource(R.string.battery_monitor_stats_loading)
                !dump.available -> stringResource(R.string.battery_monitor_stats_unavailable)
                else -> null
            }
            if (message != null) {
                Text(
                    text = message,
                    color = colors.label,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    rows: List<Pair<String, String>>,
    isMiuix: Boolean,
    colors: StatsColors,
) {
    val content: @Composable () -> Unit = {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(colors.accent, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = colors.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = label,
                            color = colors.label,
                            fontSize = 14.sp,
                            // Both columns must be constrained or the value's
                            // intrinsic width squeezes the label to one letter
                            // per line on long `dumpsys` strings.
                            modifier = Modifier.weight(0.42f),
                        )
                        Text(
                            text = value,
                            color = colors.value,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.58f),
                        )
                    }
                    if (index != rows.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.divider),
                        )
                    }
                }
            }
        }
    }

    if (isMiuix) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            content()
        }
    } else {
        TonalCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun formatDrained(percent: Float, capacityMah: Float?): String {
    val label = String.format(Locale.US, "%.0f%%", percent)
    return if (capacityMah != null && capacityMah > 0f) {
        val mah = String.format(Locale.US, "%.0f", capacityMah * percent / 100f)
        stringResource(R.string.battery_monitor_stats_drained, label, mah)
    } else {
        label
    }
}

@Composable
private fun formatRate(value: Float): String = stringResource(
    R.string.battery_monitor_stats_rate,
    String.format(Locale.US, "%.2f", value),
)

@Composable
private fun formatShare(span: String, share: Float): String = stringResource(
    R.string.battery_monitor_stats_share,
    span,
    String.format(Locale.US, "%.2f", share * 100f),
)

/** `3h 13m 37s` for short spans, `14 days 15h 57m 39s` for long ones. */
private fun formatSpan(millis: Long): String {
    val totalSeconds = millis / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        when {
            days > 1 -> append("$days days ")
            days == 1L -> append("1 day ")
        }
        if (days > 0 || hours > 0) append("${hours}h ")
        if (days > 0 || hours > 0 || minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}
