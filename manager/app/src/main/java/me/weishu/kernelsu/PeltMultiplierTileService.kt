package me.weishu.kernelsu

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.execKsudCommand

class PeltMultiplierTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var busy = false
    private var isSupported = false

    override fun onTileAdded() {
        super.onTileAdded()
        requestListeningState(this, ComponentName(this, PeltMultiplierTileService::class.java))
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!busy) updateTileState()
    }

    override fun onClick() {
        super.onClick()
        // Don't do anything if we are already processing a click, or if the kernel doesn't support it
        if (busy || !isSupported) return

        val currentState = qsTile?.state ?: Tile.STATE_INACTIVE
        val newState = if (currentState == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        val enabling = newState == Tile.STATE_ACTIVE

        busy = true
        qsTile?.state = Tile.STATE_UNAVAILABLE
        qsTile?.label = getString(
            if (enabling) R.string.tuning_sched_pelt_enabling
            else R.string.tuning_sched_pelt_disabling
        )
        qsTile?.updateTile()

        scope.launch {
            val value = if (enabling) "1" else "0"
            // Wrap in try-catch to ensure busy is reset if ksud fails to launch
            val result = runCatching { execKsudCommand("tuning sched-pelt-multiplier $value") }.getOrNull()

            if (result == null || !result.isSuccess) {
                withContext(Dispatchers.Main) {
                    busy = false
                    qsTile?.state = currentState
                    qsTile?.label = getString(R.string.tuning_sched_pelt_multiplier)
                    qsTile?.updateTile()
                }
                return@launch
            }

            // Hold the lockout so rapid taps cannot race the kernel cache.
            delay(2000)
            withContext(Dispatchers.Main) {
                busy = false
                qsTile?.state = newState
                qsTile?.label = getString(R.string.tuning_sched_pelt_multiplier)
                qsTile?.updateTile()
            }
        }
    }

    private fun updateTileState() {
        scope.launch {
            val result = runCatching { execKsudCommand("tuning status") }.getOrNull()

            if (result != null && result.isSuccess) {
                val lines = result.out.lineSequence()
                isSupported = lines
                    .firstOrNull { it.startsWith("sched_pelt_multiplier_supported=") }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.toIntOrNull() == 1
                val isActive = lines
                    .firstOrNull { it.startsWith("sched_pelt_multiplier=") }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.toIntOrNull() == 1

                withContext(Dispatchers.Main) {
                    if (busy) return@withContext

                    qsTile?.state = when {
                        !isSupported -> Tile.STATE_UNAVAILABLE
                        isActive -> Tile.STATE_ACTIVE
                        else -> Tile.STATE_INACTIVE
                    }
                    qsTile?.label = getString(R.string.tuning_sched_pelt_multiplier)
                    qsTile?.updateTile()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
