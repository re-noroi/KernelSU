package me.weishu.kernelsu.data.cpu

import android.content.Context
import androidx.core.content.edit

/** Baseline snapshot plus the clock reading taken with it. */
data class CpuStatsBaseline(
    val snapshot: CpuTimeSnapshot,
    val savedAtElapsedMs: Long,
)

/**
 * Preferences for the opt-in CPU residency table.
 *
 * Only two things are stored: whether the user enabled the table, and the
 * `time_in_state` snapshot it is measured against. The kernel counters do the
 * actual accounting, so nothing has to wake up in the background. Because
 * `elapsedRealtime` restarts at boot while the counters restart too, a clock
 * that went backwards is treated as a reboot and the baseline is replaced,
 * which is also what "reset on every reboot" asks for.
 */
class CpuStatsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    fun baseline(): CpuStatsBaseline? {
        val snapshot = CpuTimeSnapshot.parse(prefs.getString(KEY_BASELINE, null))
            ?: return null
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        if (savedAt <= 0L) return null
        return CpuStatsBaseline(snapshot, savedAt)
    }

    fun saveBaseline(snapshot: CpuTimeSnapshot, savedAtElapsedMs: Long) {
        prefs.edit {
            putString(KEY_BASELINE, snapshot.serialize())
            putLong(KEY_SAVED_AT, savedAtElapsedMs)
        }
    }

    private companion object {
        const val PREFS_NAME = "cpu_stats"
        const val KEY_ENABLED = "enabled"
        const val KEY_BASELINE = "baseline"
        const val KEY_SAVED_AT = "saved_at"
    }
}
