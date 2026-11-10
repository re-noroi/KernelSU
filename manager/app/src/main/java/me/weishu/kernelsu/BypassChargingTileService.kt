package me.weishu.kernelsu

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.TuningSignal
import me.weishu.kernelsu.ui.util.execKsudCommand
import me.weishu.kernelsu.ui.util.getRootShell

class BypassChargingTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var busy = false

    override fun onTileAdded() {
        super.onTileAdded()
        requestListeningState(this, ComponentName(this, BypassChargingTileService::class.java))
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
        // Optimistically update
        qsTile?.state = newState
        qsTile?.updateTile()

        scope.launch {
            val bypassValue = if (enabling) "1" else "0"
            val result = runCatching { execKsudCommand("tuning bypass $bypassValue") }.getOrNull()

            if (result == null || !result.isSuccess) {
                withContext(Dispatchers.Main) {
                    busy = false
                    qsTile?.state = currentState
                    qsTile?.updateTile()
                }
                return@launch
            }

            // Notify KTune that the state changed
            TuningSignal.notifyChanged()

            withContext(Dispatchers.Main) {
                busy = false
                qsTile?.state = newState
                qsTile?.updateTile()
            }
        }
    }

    private fun updateTileState() {
        scope.launch {
            // Read the file directly instead of calling 'ksud tuning status'
            val isActive = runCatching {
                val bat = ShellUtils.fastCmd(getRootShell(true), "cat /sys/class/power_supply/battery/input_suspend").trim()
                val bms = ShellUtils.fastCmd(getRootShell(true), "cat /sys/class/power_supply/bms/input_suspend").trim()
                val valStr = if (bat.isNotEmpty()) bat else bms
                valStr == "1"
            }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                if (busy) return@withContext
                qsTile?.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                qsTile?.updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}