package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import me.weishu.kernelsu.ui.viewmodel.ThermalProfile
import me.weishu.kernelsu.ui.viewmodel.TuningViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun TuningThermalAppsScreen() {
    val viewModel = viewModel<TuningViewModel>()
    val tuningState by viewModel.uiState.collectAsStateWithLifecycle()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialogUid by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        val loaded = SuperUserViewModel.loadApps(useCache = true)
            .getOrNull()
            ?.first
            .orEmpty()
            .filter { it.uid >= 10_000 && it.packageInfo.applicationInfo != null }
            .distinctBy { it.uid }
            .sortedBy { it.label.lowercase() }
        apps = loaded
        loading = false
    }

    val filteredApps = remember(apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(needle, ignoreCase = true) ||
                    it.packageName.contains(needle, ignoreCase = true)
            }
        }
    }
    val mappings = remember(tuningState.thermalApps) {
        tuningState.thermalApps.associate { it.uid to it.profileIndex }
    }
    val dialogApp = remember(apps, dialogUid) { apps.firstOrNull { it.uid == dialogUid } }

    val state = ThermalAppsState(
        apps = filteredApps,
        loading = loading,
        query = query,
        profiles = tuningState.profiles,
        mappings = mappings,
        dialogApp = dialogApp,
        onQueryChange = { query = it },
        onOpenDialog = { dialogUid = it },
        onDismissDialog = { dialogUid = null },
        onSetProfile = { uid, index ->
            viewModel.setAppThermal(uid, index)
            dialogUid = null
        },
        onClearProfile = { uid ->
            viewModel.removeAppThermal(uid)
            dialogUid = null
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ThermalAppsScreenMiuix(state)
        UiMode.Material, UiMode.Minimal -> ThermalAppsScreenMaterial(state)
    }
}

private data class ThermalAppsState(
    val apps: List<AppInfo>,
    val loading: Boolean,
    val query: String,
    val profiles: List<ThermalProfile>,
    val mappings: Map<Int, Int>,
    val dialogApp: AppInfo?,
    val onQueryChange: (String) -> Unit,
    val onOpenDialog: (Int) -> Unit,
    val onDismissDialog: () -> Unit,
    val onSetProfile: (Int, Int) -> Unit,
    val onClearProfile: (Int) -> Unit,
)

private fun ThermalAppsState.profileLabel(uid: Int): String? {
    val index = mappings[uid] ?: return null
    return profiles.firstOrNull { it.index == index }?.name ?: index.toString()
}

private fun profileChoiceLabel(profile: ThermalProfile): String =
    "${profile.name} (${profile.index})"

@Composable
private fun ThermalAppsScreenMaterial(state: ThermalAppsState) {
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
                        stringResource(R.string.tuning_thermal_apps_screen_title),
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
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.tuning_thermal_apps_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = state.onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.tuning_thermal_apps_search)) },
                    )
                }
            }

            if (state.loading) {
                item {
                    Text(
                        text = stringResource(R.string.tuning_thermal_apps_loading),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.apps.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.tuning_thermal_apps_empty),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.apps, key = { it.uid }) { app ->
                ThermalAppRowMaterial(
                    app = app,
                    profileLabel = state.profileLabel(app.uid),
                    onClick = { state.onOpenDialog(app.uid) },
                )
            }
        }
    }

    state.dialogApp?.let { app ->
        AlertDialog(
            onDismissRequest = state.onDismissDialog,
            title = { Text(app.label) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = { state.onClearProfile(app.uid) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.tuning_thermal_apps_global))
                    }
                    state.profiles.forEach { profile ->
                        TextButton(
                            onClick = { state.onSetProfile(app.uid, profile.index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(profileChoiceLabel(profile))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = state.onDismissDialog) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ThermalAppRowMaterial(
    app: AppInfo,
    profileLabel: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            applicationInfo = app.packageInfo.applicationInfo!!,
            label = app.label,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = profileLabel ?: stringResource(R.string.tuning_thermal_apps_global),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThermalAppsScreenMiuix(state: ThermalAppsState) {
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                color = colorScheme.surface,
                title = stringResource(R.string.tuning_thermal_apps_screen_title),
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
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                MiuixText(
                    text = stringResource(R.string.tuning_thermal_apps_hint),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = state.onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tuning_thermal_apps_search)) },
                )
            }

            if (state.loading) {
                item {
                    MiuixText(
                        text = stringResource(R.string.tuning_thermal_apps_loading),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    )
                }
            } else if (state.apps.isEmpty()) {
                item {
                    MiuixText(
                        text = stringResource(R.string.tuning_thermal_apps_empty),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    )
                }
            }

            items(state.apps, key = { it.uid }) { app ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    BasicComponent(
                        title = app.label,
                        summary = app.packageName,
                        onClick = { state.onOpenDialog(app.uid) },
                        startAction = {
                            AppIconImage(
                                applicationInfo = app.packageInfo.applicationInfo!!,
                                label = app.label,
                                modifier = Modifier.padding(end = 10.dp).size(40.dp),
                            )
                        },
                        endActions = {
                            MiuixText(
                                text = state.profileLabel(app.uid)
                                    ?: stringResource(R.string.tuning_thermal_apps_global),
                                color = colorScheme.primary,
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }
        }
    }

    val dialogApp = state.dialogApp
    if (dialogApp != null) {
        WindowDialog(
            show = true,
            title = dialogApp.label,
            onDismissRequest = state.onDismissDialog,
            content = {
                Column {
                    MiuixTextButton(
                        text = stringResource(R.string.tuning_thermal_apps_global),
                        onClick = { state.onClearProfile(dialogApp.uid) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.profiles.forEach { profile ->
                        MiuixTextButton(
                            text = profileChoiceLabel(profile),
                            onClick = { state.onSetProfile(dialogApp.uid, profile.index) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    MiuixTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = state.onDismissDialog,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}
