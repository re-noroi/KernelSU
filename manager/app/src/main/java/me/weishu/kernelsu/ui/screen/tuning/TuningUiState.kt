package me.weishu.kernelsu.ui.screen.tuning

import androidx.compose.runtime.Immutable

@Immutable
data class SchedulerState(
    val skipHeadroomSupported: Boolean = false,
    val skipHeadroom: Int = 1,
    val hrScalingSupported: Boolean = false,
    val hrScaling: Int = 0,
    val boostHrLpSupported: Boolean = false,
    val boostHrLp: Int = 0,
    val boostHrBigSupported: Boolean = false,
    val boostHrBig: Int = 0,
    val boostHrPrimeSupported: Boolean = false,
    val boostHrPrime: Int = 0,
)

@Immutable
data class CgroupState(
    val name: String,
    val uclampMinSupported: Boolean = false,
    val uclampMin: Int = 0,
    val uclampMaxSupported: Boolean = false,
    val uclampMax: Int = 100,
    val sharesSupported: Boolean = false,
    val shares: Int = 1024,
)

@Immutable
data class NetworkState(
    val supported: Boolean = false,
    val tcpAvailable: List<String> = emptyList(),
    val tcpCurrent: String = "",
    val qdiscCurrent: String = ""
)

@Immutable
data class MemoryState(
    val supported: Boolean = false,
    // Swappiness — always exists, just track RO state
    val swappinessRo: Boolean = false,
    val swappiness: Int = 60,
    // VFS
    val vfsSupported: Boolean = false,
    val vfsCache: Int = 100,
    // kcompressd
    val kcompressdSupported: Boolean = false,
    val kcompressd: Int = 24,
    // MGLRU
    val mglruSupported: Boolean = false,
    val mglruRaw: String = "0x0000",
    val mglruEnabled: Boolean = false,
    val mglruTtl: Int = 1000,
)

@Immutable
data class ZramState(
    val supported: Boolean = false,
    val disksize: Long = 0L,
    val compCurrent: String = "",
    val compAvailable: List<String> = emptyList(),
    val swapActive: Boolean = false,
)

@Immutable
data class TuningActions(
    val onBack: () -> Unit,
    val onSelectThermal: (Int) -> Unit,
    val onSetChargeWritable: (Boolean) -> Unit,
    val onSetChargeCurrent: (Long) -> Unit,
    val onSetBypass: (Boolean) -> Unit,
    // Persistence
    val onSetPersist: (Boolean) -> Unit,
    // Scheduler — FakeDreamers
    val onSetSkipHeadroom: (Boolean) -> Unit,
    val onSetHrScaling: (Boolean) -> Unit,
    val onSetBoostHrLp: (Int) -> Unit,
    val onSetBoostHrBig: (Int) -> Unit,
    val onSetBoostHrPrime: (Int) -> Unit,
    // Scheduler — CGroup
    val onSetCgroupUclampMin: (String, Int) -> Unit,
    val onSetCgroupUclampMax: (String, Int) -> Unit,
    val onSetCgroupShares: (String, Int) -> Unit,
    // Networking
    val onSetTcpCongestion: (String) -> Unit,
    val onSetQdisc: (String) -> Unit,
    // Memory
    val onSetSwappiness: (Int) -> Unit,
    val onSetVfsCache: (Int) -> Unit,
    val onSetKcompressd: (Int) -> Unit,
    val onSetMglru: (Boolean) -> Unit,
    val onSetMglruTtl: (Int) -> Unit,
    // zRAM
    val onSetZramSize: (Long) -> Unit,
    val onSetZramAlgorithm: (String) -> Unit,
)