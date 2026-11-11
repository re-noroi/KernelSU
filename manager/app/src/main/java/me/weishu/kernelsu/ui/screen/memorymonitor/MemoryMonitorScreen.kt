package me.weishu.kernelsu.ui.screen.memorymonitor

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.roundToInt
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
import me.weishu.kernelsu.ui.viewmodel.MemoryMonitorUiState
import me.weishu.kernelsu.ui.viewmodel.MemoryMonitorViewModel
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
fun MemoryMonitorScreen() {
    val viewModel = viewModel<MemoryMonitorViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Poll only while this screen is composed; leaving the tab cancels the loop.
    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.refresh()
            delay(2000L)
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> MemoryMonitorMiuix(uiState)
        UiMode.Material, UiMode.Minimal -> MemoryMonitorMaterial(uiState)
    }
}

// ---------------------------------------------------------------------------
// Material
// ---------------------------------------------------------------------------

@Composable
private fun MemoryMonitorMaterial(state: MemoryMonitorUiState) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val layoutDirection = LocalLayoutDirection.current
    val isMinimal = LocalUiMode.current == UiMode.Minimal

    ExpressiveScaffold(
        containerColor = if (isMinimal) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.memory_monitor_title),
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
            item {
                TonalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 13.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    MemorySummaryContent(
                        state = state,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.tertiary,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.memory_monitor_zram_section)) }
            item {
                TonalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    ZramStatsContent(
                        state = state,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Miuix
// ---------------------------------------------------------------------------

@Composable
private fun MemoryMonitorMiuix(state: MemoryMonitorUiState) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                MiuixTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.memory_monitor_title),
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
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        MemorySummaryContent(
                            state = state,
                            trackColor = colorScheme.surfaceVariant,
                            primaryColor = colorScheme.primary,
                            secondaryColor = colorScheme.tertiaryContainer,
                            labelColor = colorScheme.onBackground,
                            mutedColor = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                item { SectionHeaderMiuix(stringResource(R.string.memory_monitor_zram_section)) }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ZramStatsContent(
                            state = state,
                            labelColor = colorScheme.onBackground,
                            mutedColor = colorScheme.onSurfaceVariantSummary,
                            dividerColor = colorScheme.outline.copy(alpha = 0.4f),
                            valueColor = colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared content
// ---------------------------------------------------------------------------

@Composable
private fun MemorySummaryContent(
    state: MemoryMonitorUiState,
    trackColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    labelColor: Color,
    mutedColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoryDonut(
            physicalFraction = state.ramPercent,
            virtualFraction = state.swapPercent,
            averagePercent = state.averagePercent,
            trackColor = trackColor,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            titleColor = mutedColor,
            valueColor = labelColor,
            modifier = Modifier.size(136.dp),
        )
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            MemoryMetric(
                label = stringResource(R.string.memory_monitor_physical),
                percent = state.ramPercent,
                totalBytes = state.ramTotalBytes,
                barColor = primaryColor,
                trackColor = trackColor,
                labelColor = labelColor,
                mutedColor = mutedColor,
            )
            Spacer(Modifier.height(18.dp))
            MemoryMetric(
                label = stringResource(R.string.memory_monitor_virtual),
                percent = state.swapPercent,
                totalBytes = state.swapTotalBytes,
                barColor = secondaryColor,
                trackColor = trackColor,
                labelColor = labelColor,
                mutedColor = mutedColor,
            )
        }
    }
}

@Composable
private fun MemoryDonut(
    physicalFraction: Float,
    virtualFraction: Float,
    averagePercent: Int,
    trackColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    titleColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerStroke = size.minDimension * 0.085f
            val ringGap = size.minDimension * 0.035f
            val innerStroke = size.minDimension * 0.06f

            val outerInset = outerStroke / 2f
            val outerSize = Size(size.width - outerStroke, size.height - outerStroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(outerInset, outerInset),
                size = outerSize,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * physicalFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(outerInset, outerInset),
                size = outerSize,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round),
            )

            val innerInset = outerStroke + ringGap + innerStroke / 2f
            val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                style = Stroke(width = innerStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = secondaryColor,
                startAngle = -90f,
                sweepAngle = 360f * virtualFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                style = Stroke(width = innerStroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$averagePercent%",
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
            )
            Text(
                text = stringResource(R.string.memory_monitor_average),
                color = titleColor,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MemoryMetric(
    label: String,
    percent: Float,
    totalBytes: Long,
    barColor: Color,
    trackColor: Color,
    labelColor: Color,
    mutedColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 15.sp,
            )
            Text(
                text = "${(percent * 100f).roundToInt()}%  (${formatBytes(totalBytes)})",
                color = mutedColor,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(trackColor, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(barColor, CircleShape),
            )
        }
    }
}

@Composable
private fun ZramStatsContent(
    state: MemoryMonitorUiState,
    labelColor: Color,
    mutedColor: Color,
    dividerColor: Color,
    valueColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (!state.zramSupported) {
            Text(
                text = stringResource(R.string.memory_monitor_zram_unsupported),
                color = mutedColor,
                fontSize = 14.sp,
            )
            return@Column
        }

        StatRow(
            label = stringResource(R.string.memory_monitor_original_size),
            value = formatBytes(state.zramOriginalBytes),
            labelColor = labelColor,
            valueColor = valueColor,
        )
        StatDivider(dividerColor)
        StatRow(
            label = stringResource(R.string.memory_monitor_compressed_size),
            value = formatBytes(state.zramCompressedBytes),
            labelColor = labelColor,
            valueColor = valueColor,
        )
        StatDivider(dividerColor)
        StatRow(
            label = stringResource(R.string.memory_monitor_compression_rate),
            value = String.format(Locale.US, "%.1f%%", state.compressionRate * 100f),
            labelColor = labelColor,
            valueColor = valueColor,
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = labelColor, fontSize = 15.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
