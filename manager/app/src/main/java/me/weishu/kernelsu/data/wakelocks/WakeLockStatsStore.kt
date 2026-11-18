package me.weishu.kernelsu.data.wakelocks

import android.content.Context
import androidx.core.content.edit

/** Baseline snapshot plus the clock reading taken with it. */
data class WakeLockBaseline(
    val snapshot: WakeLockSnapshot,
    val savedAtElapsedMs: Long,
)

/**
 * Preferences for the opt-in wakelock table.
 *
 * Storing the enabled flag and one baseline is all it takes: the kernel does
 * the accounting, and the screen only reads the counters while it is open.
 * `elapsedRealtime` restarts at boot and so do the counters, so a clock that
 * moved backwards is treated as a reboot and the baseline is replaced.
 */
class WakeLockStatsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    fun baseline(): WakeLockBaseline? {
        val raw = prefs.getString(KEY_BASELINE, null) ?: return null
        // An empty snapshot serialises to an empty string; a missing baseline
        // is a null preference, which was already handled above.
        val snapshot = if (raw.isEmpty()) {
            WakeLockSnapshot(emptyMap())
        } else {
            WakeLockSnapshot.parse(raw) ?: return null
        }
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        if (savedAt <= 0L) return null
        return WakeLockBaseline(snapshot, savedAt)
    }

    fun saveBaseline(snapshot: WakeLockSnapshot, savedAtElapsedMs: Long) {
        prefs.edit {
            putString(KEY_BASELINE, snapshot.serialize())
            putLong(KEY_SAVED_AT, savedAtElapsedMs)
        }
    }

    private companion object {
        const val PREFS_NAME = "wakelock_stats"
        const val KEY_ENABLED = "enabled"
        const val KEY_BASELINE = "baseline"
        const val KEY_SAVED_AT = "saved_at"
    }
}
