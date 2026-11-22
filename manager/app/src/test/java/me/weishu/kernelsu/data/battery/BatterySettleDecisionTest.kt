package me.weishu.kernelsu.data.battery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySettleDecisionTest {

    private fun decide(
        charging: Boolean = false,
        lastCharging: Boolean = false,
        resetOnUnplug: Boolean = true,
        chargingSeenThisBoot: Boolean = false,
        lastElapsed: Long = 10_000L,
        nowElapsed: Long = 20_000L,
        lastUptime: Long = 10_000L,
        nowUptime: Long = 20_000L,
        lastBootSignature: String = "btime:100",
        bootSignature: String = "btime:100",
        lastBootCount: Int = 3,
        bootCount: Int = 3,
    ) = decideSettle(
        charging = charging,
        lastCharging = lastCharging,
        resetOnUnplug = resetOnUnplug,
        chargingSeenThisBoot = chargingSeenThisBoot,
        lastElapsed = lastElapsed,
        nowElapsed = nowElapsed,
        lastUptime = lastUptime,
        nowUptime = nowUptime,
        lastBootSignature = lastBootSignature,
        bootSignature = bootSignature,
        lastBootCount = lastBootCount,
        bootCount = bootCount,
    )

    @Test
    fun staleChargingFromPreviousBootDoesNotResetCounters() {
        // Kernel flash: the boot guard missed the reboot, preferences still
        // say the device was charging, and the first sample is unplugged.
        val d = decide(lastCharging = true, charging = false)
        assertFalse(d.rebase)
        assertFalse(d.newCycle)
        assertTrue(d.accrue)
    }

    @Test
    fun realUnplugAfterChargingStartsNewCycle() {
        val d = decide(
            lastCharging = true,
            charging = false,
            chargingSeenThisBoot = true,
        )
        assertTrue(d.newCycle)
        assertFalse(d.rebase)
    }

    @Test
    fun rebootWinsOverApparentUnplug() {
        val d = decide(
            lastCharging = true,
            charging = false,
            chargingSeenThisBoot = true,
            bootSignature = "btime:200",
        )
        assertTrue(d.rebase)
        assertFalse(d.newCycle)
    }

    @Test
    fun clockRebaseWinsOverApparentUnplug() {
        val d = decide(lastCharging = true, charging = false, nowElapsed = 1_000L)
        assertTrue(d.rebase)
        assertFalse(d.newCycle)
    }

    @Test
    fun bootCountChangeDetectsReboot() {
        assertTrue(decide(lastBootCount = 3, bootCount = 4).rebase)
    }

    @Test
    fun firstSampleWithEmptyStoreRebases() {
        assertTrue(decide(lastElapsed = 0L).rebase)
    }

    @Test
    fun resetOnUnplugDisabledKeepsCounters() {
        val d = decide(
            lastCharging = true,
            charging = false,
            chargingSeenThisBoot = true,
            resetOnUnplug = false,
        )
        assertTrue(d.accrue)
    }
}
