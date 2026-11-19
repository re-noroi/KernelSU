package me.weishu.kernelsu.ui.screen.perftop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.perftop.PerfTopProcess
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
import me.weishu.kernelsu.ui.viewmodel.PerfTopUiState
import me.weishu.kernelsu.ui.viewmodel.PerfTopViewModel
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
 * Process table built from one `top -b -n 1` call every 3 s.
 *
 * The loop exists only while the screen is resumed; closing the tab stops it
 * completely, which keeps the cost to one short-lived shell command per sample.
 */
@Composable
fun PerfTopScreen() {
    val viewModel = viewModel<PerfTopViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    val onRefresh = viewModel::refresh

    when (LocalUiMode.current) {
        UiMode.Miuix -> PerfTopMiuix(state, onRefresh)
        UiMode.Material, UiMode.Minimal -> PerfTopMaterial(state, onRefresh)
    }
}

// ---------------------------------------------------------------------------
// Material / Minimal
// ---------------------------------------------------------------------------

@Composable
private fun PerfTopMaterial(state: PerfTopUiState, onRefresh: () -> Unit) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val colors = PerfTopColors(
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
                        stringResource(R.string.perftop_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.perftop_refresh),
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
        PerfTopContent(
            state = state,
            onRefresh = onRefresh,
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
private fun PerfTopMiuix(state: PerfTopUiState, onRefresh: () -> Unit) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val colors = PerfTopColors(
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
                    title = stringResource(R.string.perftop_title),
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
                                contentDescription = stringResource(R.string.perftop_refresh),
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
            PerfTopContent(
                state = state,
                onRefresh = onRefresh,
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

private data class PerfTopColors(
    val label: Color,
    val value: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
private fun PerfTopContent(
    state: PerfTopUiState,
    onRefresh: () -> Unit,
    isMiuix: Boolean,
    colors: PerfTopColors,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ProcessCard(
                state = state,
                onRefresh = onRefresh,
                isMiuix = isMiuix,
                colors = colors,
            )
        }
    }
}

@Composable
private fun ProcessCard(
    state: PerfTopUiState,
    onRefresh: () -> Unit,
    isMiuix: Boolean,
    colors: PerfTopColors,
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(
            start = if (isMiuix) 0.dp else 16.dp,
            end = if (isMiuix) 0.dp else 16.dp,
            top = 12.dp,
            bottom = 8.dp,
        )
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.perftop_processes_title),
                    color = colors.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.perftop_summary),
                    color = colors.label,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.perftop_refresh),
                    tint = colors.accent,
                )
            }
        }

        TableHeader(colors = colors)
        PerfTopDivider(colors)

        when {
            state.unavailable ->
                StatusText(stringResource(R.string.perftop_unavailable), colors)

            !state.loaded ->
                StatusText(stringResource(R.string.perftop_reading), colors)

            state.processes.isEmpty() ->
                StatusText(stringResource(R.string.perftop_empty), colors)

            else -> {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                    state.processes.forEach { process ->
                        ProcessRow(process = process, colors = colors)
                    }
                }
            }
        }
    }

    if (isMiuix) {
        Card(modifier = cardModifier) {
            Column(content = cardContent)
        }
    } else {
        TonalCard(
            modifier = cardModifier,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(content = cardContent)
        }
    }
}

@Composable
private fun TableHeader(colors: PerfTopColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(stringResource(R.string.perftop_column_cpu), colors, Modifier.width(56.dp))
        HeaderCell(stringResource(R.string.perftop_column_ram), colors, Modifier.width(78.dp))
        HeaderCell(stringResource(R.string.perftop_column_pid), colors, Modifier.width(58.dp))
        HeaderCell(stringResource(R.string.perftop_column_name), colors, Modifier.weight(1f))
    }
}

@Composable
private fun HeaderCell(text: String, colors: PerfTopColors, modifier: Modifier) {
    Text(
        text = text,
        color = colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
private fun ProcessRow(process: PerfTopProcess, colors: PerfTopColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatCpu(process.cpuPercent),
            color = colors.value,
            fontSize = 13.sp,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = formatRam(process.resKb),
            color = colors.value,
            fontSize = 13.sp,
            modifier = Modifier.width(78.dp),
        )
        Text(
            text = process.pid.toString(),
            color = colors.value,
            fontSize = 13.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(58.dp),
        )
        Text(
            text = process.name,
            color = colors.value,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusText(text: String, colors: PerfTopColors) {
    Text(
        text = text,
        color = colors.label,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun PerfTopDivider(colors: PerfTopColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.divider),
    )
}

private fun formatCpu(percent: Float): String =
    String.format(Locale.US, "%.1f%%", percent.coerceAtLeast(0f))

private fun formatRam(resKb: Long): String {
    if (resKb <= 0L) return "0"
    val megabytes = (resKb / 1024.0).roundToInt().coerceAtLeast(1)
    return "$megabytes MB"
}

/** One procfs sample every 3 s, only while the tab is resumed. */
private const val REFRESH_INTERVAL_MS = 3_000L
