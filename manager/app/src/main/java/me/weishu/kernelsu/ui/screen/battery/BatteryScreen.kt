package me.weishu.kernelsu.ui.screen.battery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.battery.BatteryMonitorService
import me.weishu.kernelsu.data.battery.BatteryStatsStore
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.tuning.BatteryInfoRows
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.viewmodel.BatteryUiState
import me.weishu.kernelsu.ui.viewmodel.BatteryViewModel
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

@Composable
fun BatteryScreen() {
    val viewModel = viewModel<BatteryViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepositoryImpl() }
    var monitorEnabled by remember { mutableStateOf(settingsRepository.batteryMonitorEnabled) }
    var resetOnUnplug by remember {
        mutableStateOf(settingsRepository.batteryMonitorResetOnUnplug)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* The service still runs; the notification is just hidden without it. */ }

    // Poll only while this screen is composed and the app is resumed;
    // leaving the tab cancels the loop.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.refresh()
                delay(2000L)
            }
        }
    }

    val onMonitorToggle: (Boolean) -> Unit = { enabled ->
        monitorEnabled = enabled
        settingsRepository.batteryMonitorEnabled = enabled
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            BatteryMonitorService.start(context)
        } else {
            BatteryMonitorService.stop(context)
        }
    }

    val onResetMonitor: () -> Unit = {
        if (monitorEnabled) {
            // If the service is running, tell it to reset its in-memory state and update the notification
            BatteryMonitorService.reset(context)
        } else {
            // If the service is off, just clear the saved preferences directly
            BatteryStatsStore(context).reset()
        }
    }

    val onResetOnUnplugChange: (Boolean) -> Unit = { enabled ->
        resetOnUnplug = enabled
        settingsRepository.batteryMonitorResetOnUnplug = enabled
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> BatteryScreenMiuix(
            state = uiState,
            monitorEnabled = monitorEnabled,
            resetOnUnplug = resetOnUnplug,
            onMonitorToggle = onMonitorToggle,
            onResetMonitor = onResetMonitor,
            onResetOnUnplugChange = onResetOnUnplugChange,
        )

        UiMode.Material, UiMode.Minimal -> BatteryScreenMaterial(
            state = uiState,
            monitorEnabled = monitorEnabled,
            resetOnUnplug = resetOnUnplug,
            onMonitorToggle = onMonitorToggle,
            onResetMonitor = onResetMonitor,
            onResetOnUnplugChange = onResetOnUnplugChange,
        )
    }
}

// ---------------------------------------------------------------------------
// Material
// ---------------------------------------------------------------------------

@Composable
private fun BatteryScreenMaterial(
    state: BatteryUiState,
    monitorEnabled: Boolean,
    resetOnUnplug: Boolean,
    onMonitorToggle: (Boolean) -> Unit,
    onResetMonitor: () -> Unit,
    onResetOnUnplugChange: (Boolean) -> Unit,
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
                        stringResource(R.string.battery_title),
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
                BatteryMonitorCardMaterial(
                    enabled = monitorEnabled,
                    onToggle = onMonitorToggle,
                    onReset = onResetMonitor,
                )
            }
            if (monitorEnabled) {
                item {
                    BatteryResetOnUnplugCardMaterial(
                        enabled = resetOnUnplug,
                        onToggle = onResetOnUnplugChange,
                    )
                }
                item {
                    BatteryMonitorStatsCardMaterial(
                        onClick = { navigator.push(Route.BatteryMonitorStats) },
                    )
                }
            }
            item {
                TonalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 13.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    BatteryInfoRows(
                        state = state,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        valueColor = MaterialTheme.colorScheme.onSurface,
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
private fun BatteryScreenMiuix(
    state: BatteryUiState,
    monitorEnabled: Boolean,
    resetOnUnplug: Boolean,
    onMonitorToggle: (Boolean) -> Unit,
    onResetMonitor: () -> Unit,
    onResetOnUnplugChange: (Boolean) -> Unit,
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
                    title = stringResource(R.string.battery_title),
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
                    BatteryMonitorCardMiuix(
                        enabled = monitorEnabled,
                        onToggle = onMonitorToggle,
                        onReset = onResetMonitor,
                    )
                }
                if (monitorEnabled) {
                    item {
                        BatteryResetOnUnplugCardMiuix(
                            enabled = resetOnUnplug,
                            onToggle = onResetOnUnplugChange,
                        )
                    }
                    item {
                        BatteryMonitorStatsCardMiuix(
                            onClick = { navigator.push(Route.BatteryMonitorStats) },
                        )
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BatteryInfoRows(
                            state = state,
                            labelColor = colorScheme.onSurfaceVariantSummary,
                            valueColor = colorScheme.onBackground,
                            dividerColor = colorScheme.outline.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Battery monitor toggle
// ---------------------------------------------------------------------------

@Composable
private fun BatteryMonitorCardMaterial(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_monitor_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.battery_monitor_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            IconButton(
                onClick = onReset,
                enabled = enabled
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.battery_monitor_reset_stats))
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun BatteryMonitorCardMiuix(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_monitor_title),
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.battery_monitor_summary),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
            MiuixIconButton(
                onClick = onReset,
                enabled = enabled
            ) {
                MiuixIcon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.battery_monitor_reset_stats),
                )
            }
            MiuixSwitch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun BatteryResetOnUnplugCardMaterial(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_monitor_reset_on_unplug_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.battery_monitor_reset_on_unplug_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun BatteryResetOnUnplugCardMiuix(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_monitor_reset_on_unplug_title),
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.battery_monitor_reset_on_unplug_summary),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
            MiuixSwitch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun BatteryMonitorStatsCardMaterial(
    onClick: () -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_monitor_stats_open),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.battery_monitor_stats_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatteryMonitorStatsCardMiuix(
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.battery_monitor_stats_open),
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.battery_monitor_stats_summary),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
            MiuixIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
