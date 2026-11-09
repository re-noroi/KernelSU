package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.navigation3.Navigator

@Suppress("UNUSED_PARAMETER")
@Composable
fun HomePagerMinimal(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    navigator: Navigator,
    isCurrentPage: Boolean = true,
) {
    val scrollState = rememberScrollState()
    RegisterTabReselect(0) {
        scrollState.animateScrollTo(0)
    }

    // Scaffold background is now transparent so the global wallpaper shows through
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = state.systemInfo.deviceModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MinimalStatusCard(state = state, actions = actions)
            MinimalQuickGrid(state = state, actions = actions)
            MinimalSpecsCard(state = state)
            MinimalFooter(actions = actions)

            Spacer(
                Modifier.height(
                    bottomInnerPadding + if (!Natives.isFullFeatured()) {
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    } else {
                        0.dp
                    }
                )
            )
        }
    }
}

@Composable
private fun MinimalStatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val haptic = LocalHapticFeedback.current
    val statusText = when {
        state.ksuVersion == null -> stringResource(R.string.home_not_installed)
        state.isLateLoadMode -> stringResource(R.string.jailbreak_mode)
        !state.isRootAvailable -> stringResource(R.string.grant_root_failed)
        else -> stringResource(R.string.home_working)
    }
    val statusColor = when {
        state.ksuVersion == null -> MaterialTheme.colorScheme.error
        state.isLateLoadMode -> MaterialTheme.colorScheme.tertiary
        !state.isRootAvailable -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val versionText = state.ksuVersion?.let {
        stringResource(R.string.home_working_version, "KernelSU $it")
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        // Color.Black.copy(alpha = 0.75f) is inherited from the theme, but hardcoded here is fine
        color = Color.Black.copy(alpha = 0.75f), 
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.ksuVersion == null) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onInstallClick()
            },
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                if (state.hasUpdate && state.checkUpdateEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (state.ksuVersion == null) {
                        stringResource(R.string.home_click_to_install)
                    } else {
                        state.appName
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (versionText != null) {
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                    )
                }
                
                // ADDED: Manager version is now explicitly shown below the KernelSU version
                Text(
                    text = stringResource(R.string.home_manager_version) + " " + state.systemInfo.managerVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                )
            }

            if (state.showRootWarning) {
                MinimalNoticeRow(
                    icon = Icons.Rounded.Warning,
                    text = stringResource(R.string.grant_root_failed),
                    tint = MaterialTheme.colorScheme.error,
                )
            } else if (state.pendingReboot.isNotEmpty()) {
                MinimalNoticeRow(
                    icon = Icons.Rounded.Refresh,
                    text = stringResource(R.string.pending_reboot_card_title, state.pendingReboot.size),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else if (state.hasCrashLog) {
                MinimalNoticeRow(
                    icon = Icons.Rounded.BugReport,
                    text = stringResource(R.string.bento_crash_detected),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MinimalNoticeRow(
    icon: ImageVector,
    text: String,
    tint: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MinimalQuickGrid(
    state: HomeUiState,
    actions: HomeActions,
) {
    val haptic = LocalHapticFeedback.current
    val currentTool = LocalKernelTool.current
    val hasCrash = state.hasCrashLog

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MinimalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Security,
                value = state.superuserCount.toString(),
                label = stringResource(R.string.superuser),
                caption = stringResource(R.string.bento_active_superusers),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onSuperuserClick()
                },
            )
            MinimalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Extension,
                value = state.moduleCount.toString(),
                label = stringResource(R.string.module),
                caption = stringResource(R.string.bento_active_modules),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onModuleClick()
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MinimalTile(
                modifier = Modifier.weight(1f),
                icon = currentTool.roundedIcon,
                value = null,
                label = stringResource(currentTool.label),
                caption = stringResource(R.string.bento_kernel_tool),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onKernelToolClick()
                },
            )
            MinimalTile(
                modifier = Modifier.weight(1f),
                icon = when {
                    hasCrash -> Icons.Rounded.BugReport
                    else -> Icons.Rounded.CheckCircle
                },
                value = null,
                label = if (hasCrash) {
                    stringResource(R.string.bento_crash_detected)
                } else {
                    stringResource(R.string.bento_system_healthy)
                },
                caption = if (hasCrash) {
                    stringResource(R.string.bento_view_panic)
                } else {
                    stringResource(R.string.bento_no_crashes)
                },
                accent = hasCrash,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onCrashLogClick()
                },
            )
        }
    }
}

@Composable
private fun MinimalTile(
    icon: ImageVector,
    value: String?,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val container = if (accent) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
    } else {
        Color.Black.copy(alpha = 0.75f) // 25% transparent
    }
    val content = if (accent) MaterialTheme.colorScheme.error else Color.White

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = container,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                if (value != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accent) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    } else {
                        Color.LightGray
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MinimalSpecsCard(state: HomeUiState) {
    val info = state.systemInfo
    val modelText = if (info.socInfo.isNotEmpty()) {
        "${info.deviceModel} (${info.socInfo})"
    } else {
        info.deviceModel
    }
    val selinuxDisplay = when (info.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (info.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }
    val patch = if (info.securityPatch.isNotEmpty()) " | SPL ${info.securityPatch}" else ""

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.75f), // 25% transparent
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.bento_device_specs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            MinimalSpecRow(Icons.Rounded.Smartphone, stringResource(R.string.home_device_model), modelText)
            MinimalSpecRow(Icons.Rounded.DeveloperBoard, stringResource(R.string.home_kernel), info.kernelVersion)
            MinimalSpecRow(
                Icons.Outlined.Android,
                stringResource(R.string.home_android_version),
                "${info.androidVersion}$patch"
            )
            MinimalSpecRow(Icons.Outlined.Security, stringResource(R.string.home_selinux_status), selinuxDisplay)
            MinimalSpecRow(Icons.Rounded.CheckCircle, stringResource(R.string.home_seccomp_status), seccompDisplay)
        }
    }
}

@Composable
private fun MinimalSpecRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.LightGray,
            modifier = Modifier.size(24.dp), // Slightly larger icon matching Image 2
        )
        Spacer(Modifier.width(16.dp))
        // Using a Column fixes the vertical text bug and matches the Material theme layout
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun MinimalFooter(actions: HomeActions) {
    val learnUrl = stringResource(R.string.home_learn_kernelsu_url)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                actions.onOpenUrl(learnUrl)
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_learn_kernelsu),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}