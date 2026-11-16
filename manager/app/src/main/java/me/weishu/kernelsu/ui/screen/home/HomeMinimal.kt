package me.weishu.kernelsu.ui.screen.home

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.MainActivity
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalKernelTool
import me.weishu.kernelsu.ui.component.bottombar.RegisterTabReselect
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.theme.LocalAppIconMode

/**
 * Flat Minimal dashboard.
 *
 * Layout rules that keep it lined up:
 *  - every block is a full-width card inside one 16dp gutter,
 *  - the quick grid is a real 2-column grid with equal cell heights,
 *  - inside a card, rows share one 16dp leading icon column and one text column,
 *    so labels, values and chevrons align across the whole screen,
 *  - card padding is 16dp everywhere and vertical rhythm is 12dp between cards.
 */
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

    Scaffold(
        containerColor = Color.Transparent,
        // The Minimal home ground is the wallpaper backdrop painted by the theme, so
        // the Scaffold itself is transparent. A transparent container resolves to no
        // content colour, so name the theme ink explicitly.
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            MinimalTopBar(
                appName = state.appName,
                isCurrentPage = isCurrentPage,
                onOpenMiniGames = { navigator.push(Route.MiniGames) },
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
            if (state.checkUpdateEnabled && state.hasUpdate) {
                MinimalUpdateCard(state = state, actions = actions)
            }
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
private fun MinimalTopBar(
    appName: String,
    isCurrentPage: Boolean,
    onOpenMiniGames: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val appIconMode = LocalAppIconMode.current
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) return@LaunchedEffect
        val elapsed = if (MainActivity.splashStartedAt > 0L) {
            SystemClock.uptimeMillis() - MainActivity.splashStartedAt
        } else 0L
        val delayMs = (1000L - elapsed).coerceAtLeast(150L)
        if (delayMs > 0L) {
            delay(delayMs)
        }
        wobbleHomeIcon(scale, rotation)
    }

    TopAppBar(
        title = {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    val now = SystemClock.uptimeMillis()
                    tapCount = if (now - lastTapAt > 1200L) 1 else tapCount + 1
                    lastTapAt = now

                    if (tapCount >= 4) {
                        // Easter egg: a full spin, then the mini-game hub.
                        tapCount = 0
                        coroutineScope.launch {
                            scale.animateTo(0.82f, tween(90))
                            scale.animateTo(1f, tween(180))
                        }
                        coroutineScope.launch {
                            rotation.animateTo(0f, tween(1))
                            rotation.animateTo(1080f, tween(700, easing = LinearEasing))
                            onOpenMiniGames()
                            rotation.snapTo(0f)
                        }
                    } else {
                        coroutineScope.launch { wobbleHomeIcon(scale, rotation) }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(
                        when (appIconMode) {
                            1 -> R.drawable.ic_launcher_kowsu
                            2 -> R.drawable.ic_launcher_monochrome
                            else -> R.drawable.ic_launcher_noroi
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .wrapContentSize(unbounded = true)
                        .requiredSize(48.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            rotationZ = rotation.value
                        },
                )
            }
        },
        actions = {
            RebootListPopup()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        windowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    )
}

/** Flat card used for every block on the dashboard. Its opacity follows the Theme slider. */
@Composable
private fun MinimalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    ) {
        content()
    }
}

@Composable
private fun MinimalStatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary
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
        else -> accentColor
    }
    val versionText = state.ksuVersion?.let {
        stringResource(R.string.home_working_version, "KernelSU $it")
    }

    MinimalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!state.isLateLoadMode) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onInstallClick()
            }
        } else null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (state.ksuVersion == null) {
                        stringResource(R.string.home_click_to_install)
                    } else {
                        state.appName
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (versionText != null) {
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.home_manager_version) + " " + state.systemInfo.managerVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = accentColor,
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

/**
 * App update row. Tapping it opens the changelog and then downloads the APK in-app,
 * mirroring what Material and Miuix do from their smart pill, so the Minimal theme is
 * not limited to a decorative icon.
 */
@Composable
private fun MinimalUpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val haptic = LocalHapticFeedback.current
    val startUpdate = rememberManagerUpdateDownloader()
    val updateDialog = rememberConfirmDialog(
        onConfirm = { startUpdate(state.latestVersionInfo) }
    )
    val changelogTitle = stringResource(R.string.module_changelog)
    val updateText = stringResource(R.string.module_update)
    val accent = MaterialTheme.colorScheme.primary

    val title = stringResource(R.string.bento_smart_pill_update, state.latestVersionLabel)
    // Same call to action Material uses in its UpdateCard.
    val supporting = stringResource(R.string.new_version_available, state.latestVersionLabel)

    MinimalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            if (state.latestVersionInfo.changelog.isEmpty()) {
                startUpdate(state.latestVersionInfo)
            } else {
                updateDialog.showConfirm(
                    title = changelogTitle,
                    content = state.latestVersionInfo.changelog,
                    markdown = true,
                    confirm = updateText,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
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
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.12f),
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
                maxLines = 2,
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
                onClick = actions.onSuperuserClick,
            )
            MinimalTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Extension,
                value = state.moduleCount.toString(),
                label = stringResource(R.string.module),
                caption = stringResource(R.string.bento_active_modules),
                onClick = actions.onModuleClick,
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
                onClick = actions.onKernelToolClick,
            )
            MinimalTile(
                modifier = Modifier.weight(1f),
                icon = if (hasCrash) Icons.Rounded.BugReport else Icons.Rounded.CheckCircle,
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
                onClick = actions.onCrashLogClick,
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
    val haptic = LocalHapticFeedback.current
    val container = if (accent) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val content = if (accent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val supporting = if (accent) {
        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconTint = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                if (value != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = supporting,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MinimalSpecsCard(state: HomeUiState) {
    val context = LocalContext.current
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

    MinimalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.bento_device_specs),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            MinimalSpecRow(Icons.Rounded.Smartphone, stringResource(R.string.home_device_model), modelText)
            MinimalSpecRow(Icons.Rounded.DeveloperBoard, stringResource(R.string.home_kernel), info.kernelVersion)
            MinimalSpecRow(
                Icons.Outlined.Android,
                stringResource(R.string.home_android_version),
                "${info.androidVersion}$patch"
            )
            MinimalSpecRow(
                Icons.Rounded.Fingerprint,
                stringResource(R.string.home_fingerprint),
                info.fingerprint
            )
            if (state.showGpuVersion && info.gpuVersion.isNotEmpty()) {
                MinimalSpecRow(
                    Icons.Rounded.DeveloperBoard,
                    "GPU Version",
                    info.gpuVersion
                )
            }
            if (info.hookType.isNotEmpty() && info.hookType != "N/A" && info.hookType != "Unknown") {
                MinimalSpecRow(
                    Icons.Outlined.Link,
                    stringResource(R.string.home_hook_type),
                    getHookTypeDisplayName(info.hookType, context)
                )
            }
            MinimalSpecRow(Icons.Outlined.Security, stringResource(R.string.home_selinux_status), selinuxDisplay)
            MinimalSpecRow(Icons.Rounded.CheckCircle, stringResource(R.string.home_seccomp_status), seccompDisplay)
        }
    }
}

/** One row on the shared 24dp icon / 12dp gap / text grid. */
@Composable
private fun MinimalSpecRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MinimalFooter(actions: HomeActions) {
    val learnUrl = stringResource(R.string.home_learn_kernelsu_url)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MinimalFooterLink(
            text = stringResource(R.string.home_learn_kernelsu),
            onClick = { actions.onOpenUrl(learnUrl) },
        )
        MinimalFooterLink(
            text = stringResource(R.string.home_support_title),
            supportingText = stringResource(R.string.home_support_content),
            onClick = { actions.onOpenUrl("https://patreon.com/weishu") },
        )
    }
}

@Composable
private fun MinimalFooterLink(
    text: String,
    onClick: () -> Unit,
    supportingText: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                // Match the rest of the dashboard: full-opacity ink for the label and the
                // same variant colour the other blocks use for their supporting line. The
                // previous 0.75f alpha made these two rows read as faded/see-through.
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
