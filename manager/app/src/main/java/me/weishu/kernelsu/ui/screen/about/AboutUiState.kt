package me.weishu.kernelsu.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutUiState(
    val title: String,
    val appName: String,
    val appIconRes: Int,
    val versionName: String,
    val authorText: String,
    val authorUrl: String,
    val creditsTitle: String,
    val links: List<LinkInfo>,
    val credits: List<LinkInfo>,
)

@Immutable
data class AboutScreenActions(
    val onBack: () -> Unit,
    val onOpenLink: (String) -> Unit,
)
