package me.weishu.kernelsu.data.battery

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlin.math.max

/**
 * Rolling battery telemetry for the monitor notification: how long the device
 * spent screen-on, screen-off, awake and suspended, plus the share of the
 * battery each state consumed.
 *
 * The accounting is deliberately clock-delta based rather than sample based.
 * [SystemClock.elapsedRealtime] keeps running through deep sleep while
 * [SystemClock.uptimeMillis] does not, so suspend time is inferred for free
 * instead of being measured by keeping the CPU awake. Because every value is a
 * delta between two clock reads, a gap where the process was killed (or the
 * device suspended for hours) is still accounted for on the next settle.
 */
class BatteryStatsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var lastElapsed = prefs.getLong(KEY_ELAPSED, 0L)
    private var lastUptime = prefs.getLong(KEY_UPTIME, 0L)
    private var lastLevel = prefs.getInt(KEY_LEVEL, -1)
    private var lastScreenOn = prefs.getBoolean(KEY_LAST_SCREEN_ON, true)

    var screenOnMillis: Long = prefs.getLong(KEY_SCREEN_ON, 0L)
        private set

    var screenOffMillis: Long = prefs.getLong(KEY_SCREEN_OFF, 0L)
        private set

    var deepSleepMillis: Long = prefs.getLong(KEY_DEEP_SLEEP, 0L)
        private set

    var awakeMillis: Long = prefs.getLong(KEY_AWAKE, 0L)
        private set

    var screenOnPercent: Float = prefs.getFloat(KEY_PCT_ON, 0f)
        private set

    var screenOffPercent: Float = prefs.getFloat(KEY_PCT_OFF, 0f)
        private set

    /** Wall clock covered by the stats, i.e. screen-on + screen-off. */
    val totalMillis: Long get() = screenOnMillis + screenOffMillis

    /**
     * Percent of battery burned per hour of screen-on time. Rates are hidden
     * for the first minute of a state, where a single 1% step would otherwise
     * read as a wild extrapolation.
     */
    val activeDrainPerHour: Float
        get() = if (screenOnMillis >= MIN_RATE_WINDOW) {
            screenOnPercent / (screenOnMillis / HOUR_MILLIS)
        } else {
            0f
        }

    val idleDrainPerHour: Float
        get() = if (screenOffMillis >= MIN_RATE_WINDOW) {
            screenOffPercent / (screenOffMillis / HOUR_MILLIS)
        } else {
            0f
        }

    val deepSleepShare: Float
       get() = if (screenOffMillis > 0L) deepSleepMillis.toFloat() / screenOffMillis else 0f

    val awakeShare: Float
        get() = if (screenOffMillis > 0L) awakeMillis.toFloat() / screenOffMillis else 0f

    /**
     * Folds the time since the previous settle into the totals, and attributes
     * any percentage drop to the state that was active. Safe to call as often
     * as the caller likes: it only reads clocks and does arithmetic.
     */
    fun settle(
        level: Int,
        screenOn: Boolean,
        charging: Boolean,
        nowElapsed: Long = SystemClock.elapsedRealtime(),
        nowUptime: Long = SystemClock.uptimeMillis(),
    ) {
        // First sample, or the device rebooted and both clocks restarted. Keep
        // the cumulative totals but rebase, otherwise the delta would be bogus.
        if (lastElapsed == 0L || nowElapsed < lastElapsed || nowUptime < lastUptime) {
            lastElapsed = nowElapsed
            lastUptime = nowUptime
            lastLevel = level
            return
        }

        val wall = nowElapsed - lastElapsed
        val awake = (nowUptime - lastUptime).coerceAtMost(wall)

        if (lastScreenOn) {
            screenOnMillis += wall
        } else {
            screenOffMillis += wall
            awakeMillis += awake
            deepSleepMillis += max(0L, wall - awake)
        }

        // A percentage point can only be attributed to one state, so it lands
        // on whichever state was active when the step was observed. Charging
        // only rebases the level: a rise is not drain, and after unplugging the
        // first drop should start from the current level.
        if (!charging && lastLevel in 0..100 && level in 0..100) {
            val drop = lastLevel - level
            if (drop > 0) {
                if (screenOn) screenOnPercent += drop else screenOffPercent += drop
            }
        }
        lastLevel = level
        lastScreenOn = screenOn
        lastElapsed = nowElapsed
        lastUptime = nowUptime
    }

    fun persist() {
        prefs.edit {
            putLong(KEY_ELAPSED, lastElapsed)
            putLong(KEY_UPTIME, lastUptime)
            putInt(KEY_LEVEL, lastLevel)
            putBoolean(KEY_LAST_SCREEN_ON, lastScreenOn)
            putLong(KEY_SCREEN_ON, screenOnMillis)
            putLong(KEY_SCREEN_OFF, screenOffMillis)
            putLong(KEY_DEEP_SLEEP, deepSleepMillis)
            putLong(KEY_AWAKE, awakeMillis)
            putFloat(KEY_PCT_ON, screenOnPercent)
            putFloat(KEY_PCT_OFF, screenOffPercent)
        }
    }

    fun reset() {
        lastElapsed = 0L
        lastUptime = 0L
        lastLevel = -1
        lastScreenOn = true
        screenOnMillis = 0L
        screenOffMillis = 0L
        deepSleepMillis = 0L
        awakeMillis = 0L
        screenOnPercent = 0f
        screenOffPercent = 0f
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS = "battery_monitor"
        const val KEY_ELAPSED = "elapsed"
        const val KEY_UPTIME = "uptime"
        const val KEY_LEVEL = "level"
        const val KEY_LAST_SCREEN_ON = "last_screen_on"
        const val KEY_SCREEN_ON = "screen_on_ms"
        const val KEY_SCREEN_OFF = "screen_off_ms"
        const val KEY_DEEP_SLEEP = "deep_sleep_ms"
        const val KEY_AWAKE = "awake_ms"
        const val KEY_PCT_ON = "pct_on"
        const val KEY_PCT_OFF = "pct_off"

        const val HOUR_MILLIS = 3_600_000f
        const val MIN_RATE_WINDOW = 60_000L
    }
}
