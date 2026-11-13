package me.weishu.kernelsu.ui.navigation3

import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * Fade-only transition used by the Minimal UI.
 *
 * Entering pages fade in and leaving pages fade out, while the page underneath
 * stays exactly where it is (no slide or parallax). The covered layer keeps its
 * opacity, so the result is a plain cross-fade.
 */
val MinimalFadeTransition: NavTransition = navGraphicsTransition(opaqueDepth = 1f) { scope ->
    val depth = scope.relativeDepth
    if (depth <= 0f) {
        alpha = (1f + depth).coerceIn(0f, 1f)
    }
}
