package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import me.weishu.kernelsu.ui.util.execKsudCommand
import me.weishu.kernelsu.ui.screen.tuning.NetworkState
import me.weishu.kernelsu.ui.screen.tuning.MemoryState

data class ThermalProfile(val index: Int, val name: String)

data class SchedulerState(
    val supported: Boolean = false,
    val skipHeadroom: Int = 1,
    val hrScaling: Int = 0,
    val boostHrLp: Int = 0,
    val boostHrBig: Int = 0,
    val boostHrPrime: Int = 0,
)

data class CgroupState(
    val name: String,
    val uclampMin: Int = 0,
    val uclampMax: Int = 100,
    val shares: Int = 1024,
)

data class TuningUiState(
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val thermalSupported: Boolean = false,
    val thermalCurrent: Int = -1,
    val thermalIndex: Int = -1,
    val bypassSupported: Boolean = false,
    val bypassCurrent: Boolean = false,
    val bypassEnabled: Boolean = false,
    val profiles: List<ThermalProfile> = emptyList(),
    val scheduler: SchedulerState = SchedulerState(),
    val cgroups: List<CgroupState> = emptyList(),
    val persist: Boolean = true,
    val network: NetworkState = NetworkState(),
    val memory: MemoryState = MemoryState(),
    val errorMessage: String? = null,
)

class TuningViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TuningUiState())
    val uiState: StateFlow<TuningUiState> = _uiState.asStateFlow()

    private val thermalProfiles = listOf(
        ThermalProfile(10, "Dynamic"),
        ThermalProfile(11, "Class 0"),
        ThermalProfile(15, "AR VR"),
        ThermalProfile(8, "In-calls"),
        ThermalProfile(9, "Game"),
        ThermalProfile(16, "Game 2"),
        ThermalProfile(2, "Extreme"),
        ThermalProfile(12, "Pubg"),
        ThermalProfile(13, "Camera"),
        ThermalProfile(14, "Youtube"),
        ThermalProfile(0, "Disabled"),
    )

    private val thermalNode = "/sys/class/thermal/thermal_message/sconfig"
    private val bypassNodes = listOf(
        "/sys/class/power_supply/battery/input_suspend",
        "/sys/class/power_supply/bms/input_suspend",
    )
    private val cgroupNames = listOf("background", "top-app", "foreground")
    private val configPath = "/data/adb/ksu/tuning.conf"

    private var activeBypassNode = ""

    private fun execRootCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshInternal() }
    }

    private fun refreshInternal() {
        val thermalOut = execRootCommand("cat $thermalNode")
        val thermalSupported = thermalOut.isNotEmpty()
        val thermalCurrent = thermalOut.toIntOrNull() ?: -1

        var bypassSupported = false
        var bypassCurrent = false
        activeBypassNode = ""
        for (node in bypassNodes) {
            val out = execRootCommand("cat $node")
            if (out.isNotEmpty()) {
                bypassSupported = true
                activeBypassNode = node
                bypassCurrent = out != "0"
                break
            }
        }

        val skipRaw = execRootCommand("cat /proc/sys/kernel/sched_skip_headroom")
        val hrRaw = execRootCommand("cat /proc/sys/kernel/sched_hr_scaling")
        val lpRaw = execRootCommand("cat /proc/sys/kernel/sched_boost_hr_lp")
        val bigRaw = execRootCommand("cat /proc/sys/kernel/sched_boost_hr_big")
        val primeRaw = execRootCommand("cat /proc/sys/kernel/sched_boost_hr_prime")

        val scheduler = SchedulerState(
            skipHeadroomSupported = skipRaw.isNotEmpty(),
            skipHeadroom = skipRaw.toIntOrNull() ?: 1,
            hrScalingSupported = hrRaw.isNotEmpty(),
            hrScaling = hrRaw.toIntOrNull() ?: 0,
            boostHrLpSupported = lpRaw.isNotEmpty(),
            boostHrLp = lpRaw.toIntOrNull() ?: 0,
            boostHrBigSupported = bigRaw.isNotEmpty(),
            boostHrBig = bigRaw.toIntOrNull() ?: 0,
            boostHrPrimeSupported = primeRaw.isNotEmpty(),
            boostHrPrime = primeRaw.toIntOrNull() ?: 0,
        )

        val cgroups = cgroupNames.mapNotNull { name ->
            val base = "/dev/cpuctl/$name"
            val minRaw = execRootCommand("cat $base/cpu.uclamp.min")
            val maxRaw = execRootCommand("cat $base/cpu.uclamp.max")
            val sharesRaw = execRootCommand("cat $base/cpu.shares")

            // Cgroup folder always exists on Android, but we check just in case
            if (minRaw.isEmpty() && maxRaw.isEmpty() && sharesRaw.isEmpty()) return@mapNotNull null

            CgroupState(
                name = name,
                uclampMinSupported = minRaw.isNotEmpty(),
                uclampMin = minRaw.toFloatOrNull()?.toInt() ?: 0,
                uclampMaxSupported = maxRaw.isNotEmpty(),
                uclampMax = maxRaw.toFloatOrNull()?.toInt() ?: 100,
                sharesSupported = sharesRaw.isNotEmpty(),
                shares = sharesRaw.toIntOrNull() ?: 1024,
            )
        }

        val tcpAvailStr = execRootCommand("cat /proc/sys/net/ipv4/tcp_available_congestion_control")
        val tcpCur = execRootCommand("cat /proc/sys/net/ipv4/tcp_congestion_control")
        val qdiscCur = execRootCommand("cat /proc/sys/net/core/default_qdisc")
        
        val network = NetworkState(
            supported = tcpAvailStr.isNotEmpty(),
            tcpAvailable = tcpAvailStr.split(" ").filter { it.isNotBlank() },
            tcpCurrent = tcpCur,
            qdiscCurrent = qdiscCur
        )

        val swappOut = execRootCommand("cat /proc/sys/vm/swappiness")
        val vfsOut = execRootCommand("cat /proc/sys/vm/vfs_cache_pressure")
        val kcompOut = execRootCommand("cat /proc/sys/vm/kcompressd")
        val mglruOut = execRootCommand("cat /sys/kernel/mm/lru_gen/enabled").trim()
        val mglruTtlOut = execRootCommand("cat /sys/kernel/mm/lru_gen/min_ttl_ms")

        // Swappiness always exists, we just check if it's writable
        val memSwappinessRo = execRootCommand("test -w /proc/sys/vm/swappiness && echo 1").isEmpty()

        val vfsSupported = vfsOut.isNotEmpty()
        val kcompSupported = kcompOut.isNotEmpty()
        val mglruSupported = mglruOut.isNotEmpty()

        val memory = MemoryState(
            supported = swappOut.isNotEmpty() || vfsSupported || kcompSupported || mglruSupported,
            swappinessRo = memSwappinessRo,
            swappiness = swappOut.toIntOrNull() ?: 60,
            vfsSupported = vfsSupported,
            vfsCache = vfsOut.toIntOrNull() ?: 100,
            kcompressdSupported = kcompSupported,
            kcompressd = kcompOut.toIntOrNull() ?: 24,
            mglruSupported = mglruSupported,
            mglruRaw = if (mglruOut.isEmpty()) "0x0000" else mglruOut,
            mglruEnabled = mglruSupported && mglruOut != "0x0000",
            mglruTtl = mglruTtlOut.toIntOrNull() ?: 1000
        )

        val persistStr = execRootCommand(
            "grep '^persist=' $configPath 2>/dev/null | cut -d= -f2"
        )
        val persist = persistStr.isEmpty() || persistStr != "0"

        _uiState.update {
            it.copy(
                isLoading = false,
                thermalSupported = thermalSupported,
                thermalCurrent = thermalCurrent,
                thermalIndex = if (thermalCurrent >= 0) thermalCurrent else -1,
                bypassSupported = bypassSupported,
                bypassCurrent = bypassCurrent,
                bypassEnabled = bypassCurrent,
                profiles = thermalProfiles,
                scheduler = scheduler,
                cgroups = cgroups,
                persist = persist,
                network = network,
                memory = memory,
                errorMessage = null,
            )
        }
    }

    fun selectThermal(index: Int) {
        if (index < 0) return
        _uiState.update { it.copy(thermalIndex = index) }
        apply("thermal", index.toString())
    }

    fun setBypass(enabled: Boolean) {
        _uiState.update { it.copy(bypassEnabled = enabled) }
        apply("bypass", if (enabled) "1" else "0")
    }

    fun setPersist(enabled: Boolean) {
        _uiState.update { it.copy(persist = enabled) }
        apply("persist", if (enabled) "1" else "0")
    }

    fun setSkipHeadroom(enabled: Boolean) {
        val v = if (enabled) 0 else 1
        _uiState.update { it.copy(scheduler = it.scheduler.copy(skipHeadroom = v)) }
        apply("sched_skip_headroom", v.toString())
    }

    fun setHrScaling(enabled: Boolean) {
        val v = if (enabled) 1 else 0
        _uiState.update { it.copy(scheduler = it.scheduler.copy(hrScaling = v)) }
        apply("sched_hr_scaling", v.toString())
    }

    fun setBoostHrLp(value: Int) {
        val v = value.coerceIn(-50, 100)
        _uiState.update { it.copy(scheduler = it.scheduler.copy(boostHrLp = v)) }
        apply("sched_boost_hr_lp", v.toString())
    }

    fun setBoostHrBig(value: Int) {
        val v = value.coerceIn(-50, 100)
        _uiState.update { it.copy(scheduler = it.scheduler.copy(boostHrBig = v)) }
        apply("sched_boost_hr_big", v.toString())
    }

    fun setBoostHrPrime(value: Int) {
        val v = value.coerceIn(-50, 30)
        _uiState.update { it.copy(scheduler = it.scheduler.copy(boostHrPrime = v)) }
        apply("sched_boost_hr_prime", v.toString())
    }

    fun setCgroupUclampMin(name: String, value: Int) {
        val v = value.coerceIn(0, 100)
        _uiState.update { it.copy(cgroups = it.cgroups.map { cg -> if (cg.name == name) cg.copy(uclampMin = v) else cg }) }
        apply("cgroup_${name}_uclamp_min", v.toString())
    }

    fun setCgroupUclampMax(name: String, value: Int) {
        val v = value.coerceIn(0, 100)
        _uiState.update { it.copy(cgroups = it.cgroups.map { cg -> if (cg.name == name) cg.copy(uclampMax = v) else cg }) }
        apply("cgroup_${name}_uclamp_max", v.toString())
    }

    fun setCgroupShares(name: String, value: Int) {
        val v = value.coerceIn(1024, 20480)
        _uiState.update { it.copy(cgroups = it.cgroups.map { cg -> if (cg.name == name) cg.copy(shares = v) else cg }) }
        apply("cgroup_${name}_shares", v.toString())
    }

    fun setTcpCongestion(value: String) {
        _uiState.update { it.copy(network = it.network.copy(tcpCurrent = value)) }
        apply("tcp_congestion", value)
    }

    fun setQdisc(value: String) {
        _uiState.update { it.copy(network = it.network.copy(qdiscCurrent = value)) }
        apply("qdisc", value)
    }

    fun setSwappiness(value: Int) {
        _uiState.update { it.copy(memory = it.memory.copy(swappiness = value)) }
        apply("mem_swappiness", value.toString())
    }
    fun setVfsCache(value: Int) {
        _uiState.update { it.copy(memory = it.memory.copy(vfsCache = value)) }
        apply("mem_vfs", value.toString())
    }
    fun setKcompressd(value: Int) {
        _uiState.update { it.copy(memory = it.memory.copy(kcompressd = value)) }
        apply("mem_kcompressd", value.toString())
    }
    fun setMglru(enabled: Boolean) {
        _uiState.update { it.copy(memory = it.memory.copy(mglruEnabled = enabled)) }
        apply("mem_mglru", if (enabled) "1" else "0")
    }
    fun setMglruTtl(value: Int) {
        _uiState.update { it.copy(memory = it.memory.copy(mglruTtl = value)) }
        apply("mem_mglru_ttl", value.toString())
    }

    private fun apply(type: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }

            // Route every write through ksud. It handles SELinux, sysfs formatting, 
            // and saving to tuning.conf automatically.
            val args = when {
                type == "thermal" -> "tuning thermal $value"
                type == "bypass" -> "tuning bypass $value"
                type == "persist" -> "tuning persist $value"
                type == "sched_skip_headroom" -> "tuning sched-skip-headroom $value"
                type == "sched_hr_scaling" -> "tuning sched-hr-scaling $value"
                type == "sched_boost_hr_lp" -> "tuning sched-boost-hr-lp $value"
                type == "sched_boost_hr_big" -> "tuning sched-boost-hr-big $value"
                type == "sched_boost_hr_prime" -> "tuning sched-boost-hr-prime $value"
                type.startsWith("cgroup_") -> {
                    val rest = type.removePrefix("cgroup_")
                    val sep = rest.indexOf('_')
                    if (sep == -1) null else {
                        val name = rest.substring(0, sep)
                        val key = rest.substring(sep + 1)
                        "tuning cgroup $name $key $value"
                    }
                }
                type == "tcp_congestion" -> "tuning tcp-congestion $value"
                type == "qdisc" -> "tuning qdisc $value"
                type == "mem_swappiness" -> "tuning swappiness $value"
                type == "mem_vfs" -> "tuning vfs-cache $value"
                type == "mem_kcompressd" -> "tuning kcompressd $value"
                type == "mem_mglru" -> "tuning mglru $value"
                type == "mem_mglru_ttl" -> "tuning mglru-ttl $value"
                else -> null
            }

            val success = args != null && execKsudCommand(args).isSuccess

            _uiState.update { it.copy(isApplying = false) }
            refreshInternal()

            if (!success) {
                _uiState.update { it.copy(errorMessage = "Failed: $type") }
            }
        }
    }

    private fun writeNodeAndVerify(path: String, value: String): Boolean {
        execRootCommand("echo $value > $path")
        return execRootCommand("cat $path") == value
    }
}