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
        if (busy) return

        val currentState = qsTile?.state ?: Tile.STATE_INACTIVE
        val newState = if (currentState == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        val enabling = newState == Tile.STATE_ACTIVE

        busy = true
        // Visually show it's working (but don't set UNAVAILABLE, keep it active/inactive)
        qsTile?.label = getString(
            if (enabling) R.string.tuning_sched_pelt_enabling
            else R.string.tuning_sched_pelt_disabling
        )
        qsTile?.state = newState
        qsTile?.updateTile()

        scope.launch {
            val value = if (enabling) "1" else "0"
            val result = runCatching { execKsudCommand("tuning sched-pelt-multiplier $value") }.getOrNull()

            // If the command failed (e.g. old ksud binary), revert the tile
            if (result == null || !result.isSuccess) {
                withContext(Dispatchers.Main) {
                    busy = false
                    qsTile?.state = currentState
                    qsTile?.label = getString(R.string.tuning_sched_pelt_multiplier)
                    qsTile?.updateTile()
                }
                return@launch
            }

            // Hold the 2s lockout to prevent kernel cache issues
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

                // We only care if it's currently active, just like the Bypass tile
                val isActive = lines
                    .firstOrNull { it.startsWith("sched_pelt_multiplier=") }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.toIntOrNull() == 1

                withContext(Dispatchers.Main) {
                    if (busy) return@withContext

                    // Set state based on the boolean. No more UNAVAILABLE.
                    qsTile?.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
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
