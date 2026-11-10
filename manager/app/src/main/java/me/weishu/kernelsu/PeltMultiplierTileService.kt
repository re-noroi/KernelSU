package me.weishu.kernelsu

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.execKsudCommand
import me.weishu.kernelsu.ui.util.getRootShell
import me.weishu.kernelsu.ui.util.TuningSignal

class PeltMultiplierTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var busy = false
    private var cachedSupport: Boolean? = null

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
        // Don't do anything if busy or if the node doesn't exist (UNAVAILABLE)
        if (busy || qsTile?.state == Tile.STATE_UNAVAILABLE) return

        val currentState = qsTile?.state ?: Tile.STATE_INACTIVE
        val newState = if (currentState == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        val enabling = newState == Tile.STATE_ACTIVE

        busy = true
        // Optimistically update the UI so it feels snappy
        qsTile?.state = newState
        qsTile?.label = getString(
            if (enabling) R.string.tuning_sched_pelt_enabling
            else R.string.tuning_sched_pelt_disabling
        )
        qsTile?.updateTile()

        scope.launch {
            val value = if (enabling) "1" else "0"
            val result = runCatching { execKsudCommand("tuning sched-pelt-multiplier $value") }.getOrNull()

            // If the write fails, revert the visual state
            if (result == null || !result.isSuccess) {
                withContext(Dispatchers.Main) {
                    busy = false
                    qsTile?.state = currentState
                    qsTile?.label = getString(R.string.tuning_sched_pelt_multiplier)
                    qsTile?.updateTile()
                }
                return@launch
            }

            TuningSignal.notifyChanged()

            // Hold the lockout to prevent kernel cache issues
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
            // If we already checked and it's not supported, don't even run the command
            if (cachedSupport == false) {
                withContext(Dispatchers.Main) {
                    if (busy) return@withContext
                    qsTile?.state = Tile.STATE_UNAVAILABLE
                    qsTile?.updateTile()
                }
                return@launch
            }

            // Read the file directly to check support and state
            val output = runCatching {
                ShellUtils.fastCmd(getRootShell(true), "cat /proc/sys/kernel/sched_pelt_multiplier").trim()
            }.getOrDefault("")

            val isSupported = output.isNotEmpty()
            cachedSupport = isSupported
            val isActive = output == "1"

            withContext(Dispatchers.Main) {
                // Don't overwrite the tile if a click is currently in progress
                if (busy) return@withContext

                qsTile?.state = when {
                    !isSupported -> Tile.STATE_UNAVAILABLE // Grayed out if file is missing
                    isActive -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
                qsTile?.label = getString(R.string.tuning_sched_pelt_multiplier)
                qsTile?.updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}