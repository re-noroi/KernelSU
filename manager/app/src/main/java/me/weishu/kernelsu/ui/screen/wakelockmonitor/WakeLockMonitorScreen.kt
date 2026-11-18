package me.weishu.kernelsu.ui.screen.wakelockmonitor

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import me.weishu.kernelsu.data.wakelocks.WakeLockRow
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
import me.weishu.kernelsu.ui.viewmodel.WakeLockMonitorUiState
import me.weishu.kernelsu.ui.viewmodel.WakeLockMonitorViewModel
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
 * Opt-in table of kernel wake sources.
 *
 * Nothing is sampled while the toggle is off. When it is on, the screen reads
 * one text file every 30 s, and only while it is resumed. The counters are
 * cumulative since boot, so the values shown are the growth since the toggle
 * was enabled or the table was last reset — no background service, no timer.
 */
@Composable
fun WakeLockMonitorScreen() {
    val viewModel = viewModel<WakeLockMonitorViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, state.enabled) {
        if (!state.enabled) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    val callbacks = WakeLockCallbacks(
        onToggle = viewModel::setEnabled,
        onReset = viewModel::reset,
        onRefresh = viewModel::refresh,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> WakeLockMonitorMiuix(state, callbacks)
        UiMode.Material, UiMode.Minimal -> WakeLockMonitorMaterial(state, callbacks)
    }
}

private data class WakeLockCallbacks(
    val onToggle: (Boolean) -> Unit,
    val onReset: () -> Unit,
    val onRefresh: () -> Unit,
)

// ---------------------------------------------------------------------------
// Material / Minimal
// ---------------------------------------------------------------------------

@Composable
private fun WakeLockMonitorMaterial(state: WakeLockMonitorUiState, callbacks: WakeLockCallbacks) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val colors = WakeLockColors(
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
                        stringResource(R.string.wakelocks_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = callbacks.onRefresh, enabled = state.enabled) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.wakelocks_refresh),
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
        WakeLockContent(
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
private fun WakeLockMonitorMiuix(state: WakeLockMonitorUiState, callbacks: WakeLockCallbacks) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val colors = WakeLockColors(
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
                    title = stringResource(R.string.wakelocks_title),
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
                        MiuixIconButton(onClick = callbacks.onRefresh, enabled = state.enabled) {
                            MiuixIcon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.wakelocks_refresh),
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
            WakeLockContent(
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

private data class WakeLockColors(
    val label: Color,
    val value: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
private fun WakeLockContent(
    state: WakeLockMonitorUiState,
    callbacks: WakeLockCallbacks,
    isMiuix: Boolean,
    colors: WakeLockColors,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ToggleCard(
                enabled = state.enabled,
                onToggle = callbacks.onToggle,
                isMiuix = isMiuix,
                colors = colors,
            )
        }
        if (state.enabled) {
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
private fun WakeLockCard(
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
private fun ToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isMiuix: Boolean,
    colors: WakeLockColors,
) {
    WakeLockCard(isMiuix = isMiuix, top = 12, bottom = 4) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.wakelocks_toggle_title),
                    color = colors.value,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.wakelocks_toggle_summary),
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
    state: WakeLockMonitorUiState,
    callbacks: WakeLockCallbacks,
    isMiuix: Boolean,
    colors: WakeLockColors,
) {
    WakeLockCard(isMiuix = isMiuix, top = 4, bottom = 8) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wakelocks_stats_title),
                    color = colors.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.windowMs > 0L) {
                        stringResource(
                            R.string.wakelocks_window,
                            formatDuration(state.windowMs),
                        )
                    } else {
                        stringResource(R.string.wakelocks_since)
                    },
                    color = colors.label,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = callbacks.onReset, enabled = !state.busy && state.loaded) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.wakelocks_reset),
                    tint = colors.accent,
                )
            }
        }

        Text(
            text = stringResource(R.string.wakelocks_note),
            color = colors.label,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        )

        Spacer(Modifier.height(8.dp))
        WakeLockDivider(colors)

        when {
            state.busy && state.rows.isEmpty() ->
                StatusText(stringResource(R.string.wakelocks_resetting), colors)

            state.unavailable ->
                StatusText(stringResource(R.string.wakelocks_unavailable), colors)

            !state.loaded ->
                StatusText(stringResource(R.string.wakelocks_reading), colors)

            state.rows.isEmpty() ->
                StatusText(stringResource(R.string.wakelocks_empty), colors)

            else -> {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    state.rows.forEach { row ->
                        WakeLockRowItem(row = row, colors = colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun WakeLockRowItem(row: WakeLockRow, colors: WakeLockColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = row.name,
            color = colors.value,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDuration(row.timeMs),
            color = colors.label,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StatusText(text: String, colors: WakeLockColors) {
    Text(
        text = text,
        color = colors.label,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun WakeLockDivider(colors: WakeLockColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.divider),
    )
}

/** Compact form, like the reference: `8 m 22 s` rather than `0 h 8 m 22 s`. */
@Composable
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val parts = buildList {
        if (days > 0L) add(stringResource(R.string.wakelocks_unit_days, days))
        if (days > 0L || hours > 0L) add(stringResource(R.string.wakelocks_unit_hours, hours))
        if (days > 0L || hours > 0L || minutes > 0L) {
            add(stringResource(R.string.wakelocks_unit_minutes, minutes))
        }
        add(stringResource(R.string.wakelocks_unit_seconds, seconds))
    }
    return parts.joinToString(" ")
}

/** One file read every 30 s, only while the toggle is on and the tab visible. */
private const val REFRESH_INTERVAL_MS = 30_000L
