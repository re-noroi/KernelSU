package me.weishu.kernelsu

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.execKsudCommand

class BypassChargingTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = qsTile?.state ?: Tile.STATE_INACTIVE

        // Optimistically update the UI to feel snappy
        val newState = if (currentState == Tile.STATE_ACTIVE) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }
        qsTile?.state = newState
        qsTile?.updateTile()

        scope.launch {
            val bypassValue = if (newState == Tile.STATE_ACTIVE) "1" else "0"
            
            // Call the same ksud command used by the app
            val result = execKsudCommand("tuning bypass $bypassValue")
            
            // If it failed (e.g. no root), revert the tile visual state
            if (!result.isSuccess) {
                withContext(Dispatchers.Main) {
                    qsTile?.state = currentState
                    qsTile?.updateTile()
                }
            }
        }
    }

    private fun updateTileState() {
        scope.launch {
            // Read the current state from ksud status
            val result = execKsudCommand("tuning status")
            if (result.isSuccess) {
                // Parse `bypass_current=1` or `bypass_current=0` from the output
                val isBypassActive = result.out.lineSequence()
                    .firstOrNull { it.startsWith("bypass_current=") }
                    ?.substringAfter("=")
                    ?.toIntOrNull() == 1

                withContext(Dispatchers.Main) {
                    qsTile?.state = if (isBypassActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile?.label = "Bypass Charging"
                    qsTile?.updateTile()
                }
            }
        }
    }
}