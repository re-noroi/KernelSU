package me.weishu.kernelsu.ui.screen.gpumonitor

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import me.weishu.kernelsu.ui.viewmodel.GpuMonitorUiState
import me.weishu.kernelsu.ui.viewmodel.GpuMonitorViewModel
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

/**
 * Live GPU telemetry and a usage graph. Polls only while the screen is
 * resumed, one shell call per sample; the graph is a rolling in-memory list,
 * so there is no database and nothing runs when the tab is closed.
 */
@Composable
fun GpuMonitorScreen() {
    val viewModel = viewModel<GpuMonitorViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    val callbacks = GpuCallbacks(
        onClearHistory = viewModel::clearHistory,
        onLowerMin = viewModel::lowerMinFreq,
        onRaiseMin = viewModel::raiseMinFreq,
        onLowerMax = viewModel::lowerMaxFreq,
        onRaiseMax = viewModel::raiseMaxFreq,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> GpuMonitorMiuix(state, callbacks)
        UiMode.Material, UiMode.Minimal -> GpuMonitorMaterial(state, callbacks)
    }
}

private data class GpuCallbacks(
    val onClearHistory: () -> Unit,
    val onLowerMin: () -> Unit,
    val onRaiseMin: () -> Unit,
    val onLowerMax: () -> Unit,
    val onRaiseMax: () -> Unit,
)

// ---------------------------------------------------------------------------
// Material / Minimal
// ---------------------------------------------------------------------------

@Composable
private fun GpuMonitorMaterial(state: GpuMonitorUiState, callbacks: GpuCallbacks) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val colors = GpuColors(
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
                        stringResource(R.string.gpu_monitor_title),
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
        GpuContent(
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
private fun GpuMonitorMiuix(state: GpuMonitorUiState, callbacks: GpuCallbacks) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val colors = GpuColors(
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
                    title = stringResource(R.string.gpu_monitor_title),
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
            GpuContent(
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

private data class GpuColors(
    val label: Color,
    val value: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
private fun GpuContent(
    state: GpuMonitorUiState,
    callbacks: GpuCallbacks,
    isMiuix: Boolean,
    colors: GpuColors,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            LiveCard(state = state, isMiuix = isMiuix, colors = colors)
        }
        if (state.live.canLimit) {
            item {
                LimitCard(state = state, callbacks = callbacks, isMiuix = isMiuix, colors = colors)
            }
        }
        item {
            HistoryCard(state = state, callbacks = callbacks, isMiuix = isMiuix, colors = colors)
        }
    }
}

@Composable
private fun GpuCard(
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
    state: GpuMonitorUiState,
    isMiuix: Boolean,
    colors: GpuColors,
) {
    val live = state.live
    GpuCard(isMiuix = isMiuix, top = 12, bottom = 4) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.gpu_monitor_live_title),
                    color = colors.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))

            when {
                !live.available && !state.liveLoaded ->
                    StatusText(stringResource(R.string.gpu_monitor_reading), colors)

                !live.available ->
                    StatusText(stringResource(R.string.gpu_monitor_unavailable), colors)

                else -> {
                    live.busyPercent?.let {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_usage, it), colors)
                    }
                    live.curFreqKhz?.let {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_current_freq, formatFreq(it)), colors)
                    }
                    live.maxFreqKhz?.let {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_max_freq, formatFreq(it)), colors)
                    }
                    live.minFreqKhz?.let {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_min_freq, formatFreq(it)), colors)
                    }
                    if (live.governor.isNotBlank()) {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_governor, live.governor), colors)
                    }
                    if (live.vendor.isNotBlank()) {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_vendor, live.vendor), colors)
                    }
                    if (live.model.isNotBlank()) {
                        GpuInfoRow(stringResource(R.string.gpu_monitor_renderer, live.model), colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitCard(
    state: GpuMonitorUiState,
    callbacks: GpuCallbacks,
    isMiuix: Boolean,
    colors: GpuColors,
) {
    val live = state.live
    GpuCard(isMiuix = isMiuix, top = 4, bottom = 4) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.gpu_monitor_limits_title),
                color = colors.value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.gpu_monitor_limits_summary),
                color = colors.label,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(6.dp))
            LimitRow(
                label = stringResource(R.string.gpu_monitor_limits_min),
                value = live.minFreqKhz?.let(::formatFreq).orEmpty(),
                enabled = !state.limitsBusy,
                onLower = callbacks.onLowerMin,
                onRaise = callbacks.onRaiseMin,
                colors = colors,
            )
            LimitRow(
                label = stringResource(R.string.gpu_monitor_limits_max),
                value = live.maxFreqKhz?.let(::formatFreq).orEmpty(),
                enabled = !state.limitsBusy,
                onLower = callbacks.onLowerMax,
                onRaise = callbacks.onRaiseMax,
                colors = colors,
            )
        }
    }
}

@Composable
private fun LimitRow(
    label: String,
    value: String,
    enabled: Boolean,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    colors: GpuColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = colors.value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = onLower, enabled = enabled) {
            Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.gpu_monitor_limit_decrease),
                tint = colors.accent,
            )
        }
        IconButton(onClick = onRaise, enabled = enabled) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.gpu_monitor_limit_increase),
                tint = colors.accent,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    state: GpuMonitorUiState,
    callbacks: GpuCallbacks,
    isMiuix: Boolean,
    colors: GpuColors,
) {
    GpuCard(isMiuix = isMiuix, top = 4, bottom = 8) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.gpu_monitor_history_title),
                    color = colors.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.history.isEmpty()) {
                        stringResource(R.string.gpu_monitor_history_summary)
                    } else {
                        stringResource(R.string.gpu_monitor_history_count, state.history.size)
                    },
                    color = colors.label,
                    fontSize = 12.sp,
                )
            }
            if (state.history.isNotEmpty()) {
                IconButton(onClick = callbacks.onClearHistory) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.gpu_monitor_history_clear),
                        tint = colors.accent,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(GRAPH_HEIGHT),
        ) {
            UsageGraph(
                history = state.history,
                colors = colors,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.history.isEmpty()) {
                Text(
                    text = stringResource(R.string.gpu_monitor_history_empty),
                    color = colors.label,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun UsageGraph(
    history: List<Int>,
    colors: GpuColors,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Quarter lines make a value readable without an axis.
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            drawLine(
                color = colors.divider,
                start = Offset(0f, height * fraction),
                end = Offset(width, height * fraction),
                strokeWidth = 1f,
            )
        }

        if (history.isEmpty()) return@Canvas

        val step = if (history.size > 1) width / (history.size - 1) else width
        // Keep the stroke fully inside the canvas: at 0% the line would
        // otherwise sit on the bottom edge and be clipped away, which reads as
        // "no data" even though the GPU is simply idle.
        val inset = 1.dp.toPx().coerceAtMost(height / 2f)
        val usable = (height - inset * 2f).coerceAtLeast(1f)
        val points = history.mapIndexed { index, busy ->
            Offset(
                x = (index * step).coerceIn(0f, width),
                y = height - inset - (busy.coerceIn(0, 100) / 100f) * usable,
            )
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, height)
            lineTo(points.first().x, height)
            close()
        }

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(colors.accent.copy(alpha = 0.35f), colors.accent.copy(alpha = 0.02f)),
            ),
        )
        drawPath(
            path = line,
            color = colors.accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun GpuInfoRow(text: String, colors: GpuColors) {
    Text(
        text = text,
        color = colors.value,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 5.dp),
    )
}

@Composable
private fun StatusText(text: String, colors: GpuColors) {
    Text(
        text = text,
        color = colors.label,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

private fun formatFreq(khz: Long): String =
    String.format(Locale.US, "%d MHz", khz / 1000L)

private const val SAMPLE_INTERVAL_MS = 1_000L
private val GRAPH_HEIGHT = 170.dp
