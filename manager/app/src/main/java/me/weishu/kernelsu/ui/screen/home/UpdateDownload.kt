package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.util.downloadManagerUpdate
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

/**
 * Starts an in-app manager update download and returns a launcher.
 *
 * The download runs in a foreground service, so it keeps going if the user leaves the screen
 * or backgrounds the app; progress and the Install action are surfaced by a notification
 * rather than by UI state.
 */
@Composable
fun rememberManagerUpdateDownloader(): (LatestVersionInfo) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { info ->
            scope.launch {
                // Cancelling this coroutine (e.g. leaving the screen) does not stop the
                // service; it only stops us waiting for the result here.
                downloadManagerUpdate(info)
            }
        }
    }
}
