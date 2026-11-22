package me.weishu.kernelsu.data.battery

/**
 * Which bookkeeping action the next battery sample should take. Kept free of
 * Android types so the reboot/unplug precedence is testable in a plain JVM
 * unit test.
 */
internal data class SettleDecision(
    val rebase: Boolean,
    val newCycle: Boolean,
) {
    val accrue: Boolean get() = !rebase && !newCycle
}

/**
 * [rebase] when the monotonic clocks restarted or the boot identity changed;
 * [newCycle] when a real charger disconnect ends a measurement window.
 *
 * A persisted `lastCharging` alone is not enough for [newCycle]: it can be a
 * leftover from a previous boot (for example after flashing a new kernel), in
 * which case the first unplugged sample would wipe otherwise valid counters.
 * Charging must have been observed in the current boot first.
 */
internal fun decideSettle(
    charging: Boolean,
    lastCharging: Boolean,
    resetOnUnplug: Boolean,
    chargingSeenThisBoot: Boolean,
    lastElapsed: Long,
    nowElapsed: Long,
    lastUptime: Long,
    nowUptime: Long,
    lastBootSignature: String,
    bootSignature: String,
    lastBootCount: Int,
    bootCount: Int,
): SettleDecision {
    val clockRebased = lastElapsed == 0L ||
        nowElapsed < lastElapsed ||
        nowUptime < lastUptime
    val rebooted = (lastBootSignature.isNotEmpty() && bootSignature != lastBootSignature) ||
        (bootCount >= 0 && lastBootCount >= 0 && bootCount != lastBootCount)

    val rebase = clockRebased || rebooted
    val newCycle = !rebase && !charging && lastCharging &&
        chargingSeenThisBoot && resetOnUnplug
    return SettleDecision(rebase = rebase, newCycle = newCycle)
}
