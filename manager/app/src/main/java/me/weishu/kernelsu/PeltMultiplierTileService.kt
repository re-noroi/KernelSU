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

    // The kernel keeps a fast-path cache for sched_pelt_multiplier, so we
    // ignore taps for 2s after a change (same guard as the KTune switch).
    private var busy = false

    override fun onTileAdded() {
        super.onTileAdded()
        requestListeningState(
            this,
            ComponentName(this, PeltMultiplierTileService::class.java)
        )
    }

    override fun onStartListening() {
        super.onStartListening()
        if (!busy) updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (busy) return

        val currentState = qsTile?.state ?: Tile.STATE_INACTIVE
        if (currentState == Tile.STATE_UNAVAILABLE) return

        // Optimistically update the UI to feel snappy
        val newState = if (currentState == Tile.STATE_ACTIVE) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }
        val enabling = newState == Tile.STATE_ACTIVE
        busy = true
        // Gray the tile out and ignore taps for 2s so the write cannot race
        // the kernel's fast-path cache (mirrors the KTune switch).
        qsTile?.state = Tile.STATE_UNAVAILABLE
        qsTile?.label = getString(
            if (enabling) R.string.tuning_sched_pelt_enabling
            else R.string.tuning_sched_pelt_disabling
        )
        qsTile?.updateTile()

        scope.launch {
            val value = if (enabling) "1" else "0"
            val result = execKsudCommand("tuning sched-pelt-multiplier $value")

            if (!result.isSuccess) {
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
            val result = execKsudCommand("tuning status")
            if (result.isSuccess) {
                val lines = result.out.lineSequence()
                val supported = lines
                    .firstOrNull { it.startsWith("sched_pelt_multiplier_supported=") }
                    ?.substringAfter("=")
                    ?.toIntOrNull() == 1
                val isActive = lines
                    .firstOrNull { it.startsWith("sched_pelt_multiplier=") }
                    ?.substringAfter("=")
                    ?.toIntOrNull() == 1

                withContext(Dispatchers.Main) {
                    qsTile?.state = when {
                        !supported -> Tile.STATE_UNAVAILABLE
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
