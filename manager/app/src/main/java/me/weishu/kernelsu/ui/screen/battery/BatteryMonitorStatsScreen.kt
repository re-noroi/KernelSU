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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.battery.BatteryStatsDump
import me.weishu.kernelsu.data.battery.BatteryStatsDumper
import me.weishu.kernelsu.data.battery.BatteryStatsFailure
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
 * The monitor's own counters are read from its preferences every few seconds
 * (cheap, and persisted on every screen/power transition by the service).
 * `dumpsys batterystats`, which supplies the raw since-charge and
 * estimated-power sections, is run only when the screen opens or the user
 * explicitly refreshes — running it on a timer would be the expensive part.
 */
@Composable
fun BatteryMonitorStatsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var monitor by remember { mutableStateOf(BatteryStatsStore(context)) }
    var dump by remember { mutableStateOf(BatteryStatsDump()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        // The first read may reuse a dump cached moments ago; an explicit
        // refresh always bypasses it.
        dump = BatteryStatsDumper.read(force = refreshKey > 0)
        loading = false
    }

    // Only while visible: the service persists on screen and power
    // transitions, so there is nothing worth waking up for in the background.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                monitor = BatteryStatsStore(context)
                delay(5_000L)
            }
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
        // Some builds group thousands ("4,000 mAh"); strip the separators
        // before taking the numeric part.
        ?.replace(",", "")
        ?.let { Regex("[0-9]+(?:\\.[0-9]+)?").find(it)?.value?.toFloatOrNull() }

    // Labels arrive from `dumpsys` in English; map the known ones and leave
    // anything newer as the device reported it.
    val sinceRows = mutableListOf<Pair<String, String>>()
    dump.sinceCharge.forEach { row ->
        sinceRows += localizeLabel(row.label) to row.value
    }
    val powerRows = mutableListOf<Pair<String, String>>()
    dump.powerUse.forEach { row ->
        powerRows += localizeLabel(row.label) to row.value
    }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        if (loading || !dump.available) {
            item {
                StatsStatus(loading = loading, failure = dump.failure, colors = colors)
            }
        }
        item {
            StatsSection(
                title = stringResource(R.string.battery_monitor_stats_screen_on),
                subtitle = stringResource(R.string.battery_monitor_stats_since_charge),
                rows = listOf(
                    stringResource(R.string.battery_monitor_stats_duration) to
                        formatSpan(monitor.screenOnMillis),
                    stringResource(R.string.battery_monitor_stats_battery_drained) to
                        formatDrained(monitor.screenOnPercent, capacityMah),
                    stringResource(R.string.battery_monitor_stats_active_drain) to
                        formatRate(monitor.activeDrainPerHour, monitor.hasActiveRate),
                ),
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        item {
            StatsSection(
                title = stringResource(R.string.battery_monitor_stats_screen_off),
                subtitle = stringResource(R.string.battery_monitor_stats_since_charge),
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
                        formatRate(monitor.idleDrainPerHour, monitor.hasIdleRate),
                ),
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        if (powerRows.isNotEmpty()) {
            item {
                StatsSection(
                    title = stringResource(R.string.battery_monitor_stats_power_use),
                    subtitle = stringResource(R.string.battery_monitor_stats_since_charge),
                    rows = powerRows,
                    isMiuix = isMiuix,
                    colors = colors,
                )
            }
        }
        if (sinceRows.isNotEmpty()) {
            item {
                StatsSection(
                    title = stringResource(R.string.battery_monitor_stats_raw),
                    rows = sinceRows,
                    isMiuix = isMiuix,
                    colors = colors,
                )
            }
        }
    }
}

/**
 * Loading spinner and failure reason, above the sections so it is visible
 * without scrolling past the whole raw dump.
 */
@Composable
private fun StatsStatus(
    loading: Boolean,
    failure: BatteryStatsFailure,
    colors: StatsColors,
) {
    val message = when {
        loading -> stringResource(R.string.battery_monitor_stats_loading)
        failure == BatteryStatsFailure.NO_ROOT ->
            stringResource(R.string.battery_monitor_stats_no_root)
        else -> stringResource(R.string.battery_monitor_stats_unavailable)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        }
        Text(text = message, color = colors.label, fontSize = 13.sp)
    }
}

@Composable
private fun StatsSection(
    title: String,
    rows: List<Pair<String, String>>,
    isMiuix: Boolean,
    colors: StatsColors,
    subtitle: String? = null,
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.label,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 31.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 9.dp),
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

/** Known `dumpsys` labels, translated; unknown ones fall back to the raw text. */
private fun labelRes(label: String): Int? = when (label) {
    "Last learned battery capacity" -> R.string.battery_monitor_stats_label_capacity
    "Time on battery" -> R.string.battery_monitor_stats_label_time_on_battery
    "Time on battery screen off" -> R.string.battery_monitor_stats_label_time_on_battery_off
    "Time on battery screen doze" -> R.string.battery_monitor_stats_label_time_on_battery_doze
    "Total run time" -> R.string.battery_monitor_stats_label_total_run_time
    "Battery time remaining" -> R.string.battery_monitor_stats_label_time_remaining
    "Discharge" -> R.string.battery_monitor_stats_label_discharge
    "Screen off discharge" -> R.string.battery_monitor_stats_label_screen_off_discharge
    "Screen doze discharge" -> R.string.battery_monitor_stats_label_screen_doze_discharge
    "Screen on discharge" -> R.string.battery_monitor_stats_label_screen_on_discharge
    "Device light doze discharge" -> R.string.battery_monitor_stats_label_light_doze_discharge
    "Device deep doze discharge" -> R.string.battery_monitor_stats_label_deep_doze_discharge
    "Start clock time" -> R.string.battery_monitor_stats_label_start_clock
    "Screen on" -> R.string.battery_monitor_stats_label_screen_on
    "Device light idling" -> R.string.battery_monitor_stats_label_device_light_idling
    "Idle mode light time" -> R.string.battery_monitor_stats_label_idle_light_time
    "Device full idling" -> R.string.battery_monitor_stats_label_device_full_idling
    "Idle mode full time" -> R.string.battery_monitor_stats_label_idle_full_time
    "Connectivity changes" -> R.string.battery_monitor_stats_label_connectivity
    "Total full wakelock time" -> R.string.battery_monitor_stats_label_full_wakelock
    "Total partial wakelock time" -> R.string.battery_monitor_stats_label_partial_wakelock
    "Total WiFi Multicast wakelock Count" -> R.string.battery_monitor_stats_label_wifi_multicast_count
    "Total WiFi Multicast wakelock time" -> R.string.battery_monitor_stats_label_wifi_multicast_time

    "Screen" -> R.string.battery_monitor_stats_label_power_screen
    "CPU" -> R.string.battery_monitor_stats_label_power_cpu
    "Idle" -> R.string.battery_monitor_stats_label_power_idle
    "Wifi" -> R.string.battery_monitor_stats_label_power_wifi
    "Mobile network" -> R.string.battery_monitor_stats_label_power_mobile
    "Phone" -> R.string.battery_monitor_stats_label_power_phone
    "Bluetooth" -> R.string.battery_monitor_stats_label_power_bluetooth
    "Sensor" -> R.string.battery_monitor_stats_label_power_sensor
    "Camera" -> R.string.battery_monitor_stats_label_power_camera
    "Flashlight" -> R.string.battery_monitor_stats_label_power_flashlight
    "Audio" -> R.string.battery_monitor_stats_label_power_audio
    "Video" -> R.string.battery_monitor_stats_label_power_video
    "Radio" -> R.string.battery_monitor_stats_label_power_radio
    "Memory" -> R.string.battery_monitor_stats_label_power_memory
    "Ambient display" -> R.string.battery_monitor_stats_label_power_ambient
    else -> null
}

@Composable
private fun localizeLabel(label: String): String {
    val res = labelRes(label)
    return if (res != null) stringResource(res) else label
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
private fun formatRate(value: Float, available: Boolean): String = if (available) {
    stringResource(R.string.battery_monitor_stats_rate, String.format(Locale.US, "%.2f", value))
} else {
    // Rates are extrapolations from a single percentage step; showing 0.00 for
    // the first minute would look like a perfect idle device.
    stringResource(R.string.battery_monitor_stats_rate_pending)
}

@Composable
private fun formatShare(span: String, share: Float): String = stringResource(
    R.string.battery_monitor_stats_share,
    span,
    String.format(Locale.US, "%.2f", share * 100f),
)

/** `3h 13m 37s` for short spans, `14 days 15h 57m 39s` for long ones. */
@Composable
private fun formatSpan(millis: Long): String {
    val totalSeconds = millis / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    val builder = StringBuilder()
    if (days > 1) {
        builder.append(stringResource(R.string.battery_monitor_stats_span_days, days)).append(' ')
    } else if (days == 1L) {
        builder.append(stringResource(R.string.battery_monitor_stats_span_day)).append(' ')
    }
    if (days > 0 || hours > 0) {
        builder.append(stringResource(R.string.battery_monitor_stats_span_hours, hours)).append(' ')
    }
    if (days > 0 || hours > 0 || minutes > 0) {
        builder.append(stringResource(R.string.battery_monitor_stats_span_minutes, minutes)).append(' ')
    }
    builder.append(stringResource(R.string.battery_monitor_stats_span_seconds, seconds))
    return builder.toString()
}
