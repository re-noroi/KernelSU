package me.weishu.kernelsu.data.battery

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import java.io.File
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

    /**
     * Stable identity of the current boot. `/proc/stat` reports the kernel boot
     * time as Unix seconds and is present on every Linux/Android kernel; unlike
     * Settings.Global.BOOT_COUNT it is always populated and cannot be reset by
     * an app clearing its own data. The other two variants are fallbacks for
     * kernels that hide /proc/stat.
     */
    private val bootSignature: String = runCatching {
        File("/proc/stat").useLines { lines ->
            lines.firstOrNull { it.startsWith("btime ") }
                ?.removePrefix("btime ")
                ?.trim()
                ?.takeIf { it.toLongOrNull() != null }
        }
    }.getOrNull()?.let { "btime:$it" }
        ?: runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1).let { count ->
            if (count >= 0) "boot_count:$count"
            else "wall:${(System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000}"
        }

    /**
     * Legacy boot counter, kept because older installs only persisted this.
     */
    private val bootCount: Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    private var lastBootSignature = prefs.getString(KEY_BOOT_SIGNATURE, "") ?: ""
    private var lastBootCount = prefs.getInt(KEY_BOOT_COUNT, -1)
    private var lastElapsed = prefs.getLong(KEY_ELAPSED, 0L)
    private var lastUptime = prefs.getLong(KEY_UPTIME, 0L)
    private var lastLevel = prefs.getInt(KEY_LEVEL, -1)
    private var lastScreenOn = prefs.getBoolean(KEY_LAST_SCREEN_ON, true)
    private var lastCharging = prefs.getBoolean(KEY_LAST_CHARGING, false)

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

    /**
     * Rates are only meaningful once a state has been observed long enough for
     * a single percentage step to stop reading as a wild extrapolation. Callers
     * show a placeholder while these are false instead of a bogus `0.00`.
     */
    val hasActiveRate: Boolean get() = screenOnMillis >= MIN_RATE_WINDOW

    val hasIdleRate: Boolean get() = screenOffMillis >= MIN_RATE_WINDOW

    val deepSleepShare: Float
       get() = if (screenOffMillis > 0L) deepSleepMillis.toFloat() / screenOffMillis else 0f

    val awakeShare: Float
        get() = if (screenOffMillis > 0L) awakeMillis.toFloat() / screenOffMillis else 0f

    /**
     * Folds the time since the previous settle into the totals, and attributes
     * any percentage drop to the state that was active. Safe to call as often
     * as the caller likes: it only reads clocks and does arithmetic.
     *
     * @param resetOnUnplug when true, disconnecting the charger starts a fresh
     *   measurement window (the default, which keeps the drained percentages
     *   inside 0..100 per charge cycle). When false, the counters survive the
     *   unplug and only [reset] clears them.
     * @return true when the device left the charger and every counter was
     *   reset, so callers can persist the zeros immediately.
     */
    fun settle(
        level: Int,
        screenOn: Boolean,
        charging: Boolean,
        resetOnUnplug: Boolean = true,
        nowElapsed: Long = SystemClock.elapsedRealtime(),
        nowUptime: Long = SystemClock.uptimeMillis(),
    ): Boolean {
        // A reboot restarts every monotonic clock, so the stored deltas are
        // meaningless: the gap across the reboot must not be booked to any
        // state. A changed boot signature is authoritative; a clock that went
        // backwards (or a freshly cleared store) is always a rebase too.
        val clockRebased = lastElapsed == 0L ||
            nowElapsed < lastElapsed ||
            nowUptime < lastUptime
        val rebooted = (lastBootSignature.isNotEmpty() && bootSignature != lastBootSignature) ||
            (bootCount >= 0 && lastBootCount >= 0 && bootCount != lastBootCount)
        if (clockRebased || rebooted) {
            // Keep the cumulative totals but rebase, otherwise the delta would
            // be bogus. Re-establishing `lastCharging` here is what stops the
            // first sample after a reboot from looking like an unplug.
            lastBootSignature = bootSignature
            lastBootCount = bootCount
            lastElapsed = nowElapsed
            lastUptime = nowUptime
            lastLevel = level
            lastCharging = charging
            lastScreenOn = screenOn
            // Persist the new boot identity right away. The service's throttled
            // writer may not run again for a minute, and the process can be
            // killed in between; without this the next start would still think
            // a reboot happened and keep rebasing forever.
            persist()
            return false
        }
        lastBootSignature = bootSignature
        lastBootCount = bootCount

        // Unplugging starts a fresh measurement window when the user asked for
        // one: everything collected so far belongs to the cycle that just
        // ended, and the raw "since last charge" section below restarts at the
        // same moment. Without this the drain percentages would accumulate
        // across charges. Durations reset with them so the two always agree.
        if (!charging && lastCharging && resetOnUnplug) {
            screenOnMillis = 0L
            screenOffMillis = 0L
            deepSleepMillis = 0L
            awakeMillis = 0L
            screenOnPercent = 0f
            screenOffPercent = 0f
            lastCharging = false
            lastScreenOn = screenOn
            lastElapsed = nowElapsed
            lastUptime = nowUptime
            lastLevel = level
            return true
        }
        lastCharging = charging

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
        return false
    }

    fun persist() {
        prefs.edit {
            putString(KEY_BOOT_SIGNATURE, lastBootSignature)
            putInt(KEY_BOOT_COUNT, lastBootCount)
            putLong(KEY_ELAPSED, lastElapsed)
            putLong(KEY_UPTIME, lastUptime)
            putInt(KEY_LEVEL, lastLevel)
            putBoolean(KEY_LAST_SCREEN_ON, lastScreenOn)
            putBoolean(KEY_LAST_CHARGING, lastCharging)
            putLong(KEY_SCREEN_ON, screenOnMillis)
            putLong(KEY_SCREEN_OFF, screenOffMillis)
            putLong(KEY_DEEP_SLEEP, deepSleepMillis)
            putLong(KEY_AWAKE, awakeMillis)
            putFloat(KEY_PCT_ON, screenOnPercent)
            putFloat(KEY_PCT_OFF, screenOffPercent)
        }
    }

    fun reset() {
        lastBootSignature = bootSignature
        lastBootCount = bootCount
        lastElapsed = 0L
        lastUptime = 0L
        lastLevel = -1
        lastScreenOn = true
        lastCharging = false
        screenOnMillis = 0L
        screenOffMillis = 0L
        deepSleepMillis = 0L
        awakeMillis = 0L
        screenOnPercent = 0f
        screenOffPercent = 0f
        // Write the zeroed counters together with the current boot identity.
        // Clearing everything used to lose the boot marker and made the next
        // sample look like a fresh install, which hid reboot detection.
        persist()
    }

    private companion object {
        const val PREFS = "battery_monitor"
        const val KEY_BOOT_SIGNATURE = "boot_signature"
        const val KEY_BOOT_COUNT = "boot_count"
        const val KEY_ELAPSED = "elapsed"
        const val KEY_UPTIME = "uptime"
        const val KEY_LEVEL = "level"
        const val KEY_LAST_SCREEN_ON = "last_screen_on"
        const val KEY_LAST_CHARGING = "last_charging"
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
