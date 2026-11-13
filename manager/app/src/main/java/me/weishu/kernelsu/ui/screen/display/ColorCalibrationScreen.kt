package me.weishu.kernelsu.ui.screen.display

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
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
import me.weishu.kernelsu.ui.viewmodel.ColorCalibrationUiState
import me.weishu.kernelsu.ui.viewmodel.ColorCalibrationViewModel
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

@Composable
fun ColorCalibrationScreen() {
    val viewModel = viewModel<ColorCalibrationViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Values are preloaded by SysfsNodeCache; this single read refreshes the
    // numbers without rediscovering sysfs every time the tab opens.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorCalibrationScreenMiuix(uiState, viewModel)
        UiMode.Material, UiMode.Minimal -> ColorCalibrationScreenMaterial(uiState, viewModel)
    }
}

// ---------------------------------------------------------------------------
// Material
// ---------------------------------------------------------------------------

@Composable
private fun ColorCalibrationScreenMaterial(
    state: ColorCalibrationUiState,
    viewModel: ColorCalibrationViewModel,
) {
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
                        stringResource(R.string.color_calibration_title),
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
                    ColorCalibrationBody(
                        state = state,
                        onEnabledChange = viewModel::setEnabled,
                        onRedChange = viewModel::setRed,
                        onGreenChange = viewModel::setGreen,
                        onBlueChange = viewModel::setBlue,
                        onSaturationChange = viewModel::setSaturation,
                        onValueChange = viewModel::setValue,
                        onContrastChange = viewModel::setContrast,
                        onHueChange = viewModel::setHue,
                        onReset = viewModel::reset,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
private fun ColorCalibrationScreenMiuix(
    state: ColorCalibrationUiState,
    viewModel: ColorCalibrationViewModel,
) {
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
                    title = stringResource(R.string.color_calibration_title),
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
                        ColorCalibrationBody(
                            state = state,
                            onEnabledChange = viewModel::setEnabled,
                            onRedChange = viewModel::setRed,
                            onGreenChange = viewModel::setGreen,
                            onBlueChange = viewModel::setBlue,
                            onSaturationChange = viewModel::setSaturation,
                            onValueChange = viewModel::setValue,
                            onContrastChange = viewModel::setContrast,
                            onHueChange = viewModel::setHue,
                            onReset = viewModel::reset,
                            labelColor = colorScheme.onBackground,
                            valueColor = colorScheme.onSurfaceVariantSummary,
                            dividerColor = colorScheme.outline.copy(alpha = 0.4f),
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
private fun ColorCalibrationBody(
    state: ColorCalibrationUiState,
    onEnabledChange: (Boolean) -> Unit,
    onRedChange: (Int) -> Unit,
    onGreenChange: (Int) -> Unit,
    onBlueChange: (Int) -> Unit,
    onSaturationChange: (Int) -> Unit,
    onValueChange: (Int) -> Unit,
    onContrastChange: (Int) -> Unit,
    onHueChange: (Int) -> Unit,
    onReset: () -> Unit,
    labelColor: Color,
    valueColor: Color,
    dividerColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (!state.supported) {
            Text(
                text = stringResource(R.string.color_calibration_unsupported),
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.color_calibration_rgb),
                    color = labelColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (!state.enableSupported) {
                    Text(
                        text = stringResource(R.string.color_calibration_always_on),
                        color = valueColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                enabled = state.enableSupported,
            )
        }

        val slidersEnabled = state.enabled
        CalibrationSlider(
            title = "R",
            value = state.red,
            range = ColorCalibrationUiState.RGB_MIN..ColorCalibrationUiState.RGB_MAX,
            accent = Color(0xFFE53935),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onRedChange,
        )
        CalibrationSlider(
            title = "G",
            value = state.green,
            range = ColorCalibrationUiState.RGB_MIN..ColorCalibrationUiState.RGB_MAX,
            accent = Color(0xFF43A047),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onGreenChange,
        )
        CalibrationSlider(
            title = "B",
            value = state.blue,
            range = ColorCalibrationUiState.RGB_MIN..ColorCalibrationUiState.RGB_MAX,
            accent = Color(0xFF1E88E5),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onBlueChange,
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = dividerColor,
        )

        CalibrationSlider(
            title = stringResource(R.string.color_calibration_saturation),
            value = state.saturation,
            range = ColorCalibrationUiState.SATURATION_MIN..ColorCalibrationUiState.SATURATION_MAX,
            accent = Color(0xFF00BFA5),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onSaturationChange,
        )
        CalibrationSlider(
            title = stringResource(R.string.color_calibration_value),
            value = state.value,
            range = ColorCalibrationUiState.SATURATION_MIN..ColorCalibrationUiState.SATURATION_MAX,
            accent = Color(0xFF00BFA5),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onValueChange,
        )
        CalibrationSlider(
            title = stringResource(R.string.color_calibration_contrast),
            value = state.contrast,
            range = ColorCalibrationUiState.SATURATION_MIN..ColorCalibrationUiState.SATURATION_MAX,
            accent = Color(0xFF00BFA5),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onContrastChange,
        )
        CalibrationSlider(
            title = stringResource(R.string.color_calibration_hue),
            value = state.hue,
            range = ColorCalibrationUiState.HUE_MIN..ColorCalibrationUiState.HUE_MAX,
            accent = Color(0xFF00BFA5),
            enabled = slidersEnabled,
            labelColor = labelColor,
            valueColor = valueColor,
            onValueChange = onHueChange,
        )

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.color_calibration_reset))
        }
    }
}

@Composable
private fun CalibrationSlider(
    title: String,
    value: Int,
    range: IntRange,
    accent: Color,
    enabled: Boolean,
    labelColor: Color,
    valueColor: Color,
    onValueChange: (Int) -> Unit,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, color = labelColor, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = dragValue.roundToInt().toString(),
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChange(dragValue.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = Modifier.height(32.dp),
        )
    }
}
