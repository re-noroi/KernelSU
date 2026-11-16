package me.weishu.kernelsu.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl

/**
 * Restarts the monitor after a reboot when the user has it enabled.
 *
 * `specialUse` foreground services are still permitted from BOOT_COMPLETED
 * (unlike `dataSync`), but some vendors are stricter, so a failure here must
 * not crash the boot broadcast.
 */
class BatteryMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsRepositoryImpl().batteryMonitorEnabled) return
        runCatching { BatteryMonitorService.start(context) }
    }
}
