//! Thermal profile and bypass-charging control.
//!
//! These knobs are vendor/kernel specific sysfs nodes. noroiSU only writes
//! them when they actually exist, and persists the user's choice in
//! `/data/adb/ksu/tuning.conf` so it can be restored during boot.

use anyhow::{Context, Result, bail};
use log::{info, warn};
use std::collections::HashMap;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::thread;
use std::time::Duration;

use crate::defs;

pub const THERMAL_PROFILES: &[(i32, &str)] = &[
    (10, "Dynamic"),
    (11, "Class 0"),
    (15, "AR VR"),
    (8, "In-calls"),
    (9, "Game"),
    (16, "Game 2"),
    (2, "Extreme"),
    (12, "Pubg"),
    (13, "Camera"),
    (14, "Youtube"),
    (0, "Disabled"),
    (-1, "Not Set"),
];

const THERMAL_NODES: &[&str] = &["/sys/class/thermal/thermal_message/sconfig"];
const THERMAL_CONTEXT: &str = "u:object_r:sysfs_thermal:s0";

const CHARGE_CURRENT: &str = "/sys/class/power_supply/battery/constant_charge_current";
const CHARGE_CURRENT_MAX: &str = "/sys/class/power_supply/battery/constant_charge_current_max";
const CHARGE_CURRENT_MIN_UA: u64 = 1_000_000; // 1.0 A floor
const BYPASS_NODES: &[&str] = &[
    "/sys/class/power_supply/battery/input_suspend",
    "/sys/class/power_supply/bms/input_suspend",
];

// Scheduler sysfs nodes (FakeDreamers)
const SCHED_SKIP_HEADROOM: &str = "/proc/sys/kernel/sched_skip_headroom";
const SCHED_HR_SCALING: &str = "/proc/sys/kernel/sched_hr_scaling";
const SCHED_BOOST_HR_LP: &str = "/proc/sys/kernel/sched_boost_hr_lp";
const SCHED_BOOST_HR_BIG: &str = "/proc/sys/kernel/sched_boost_hr_big";
const SCHED_BOOST_HR_PRIME: &str = "/proc/sys/kernel/sched_boost_hr_prime";

// CGroup tunables
const CGROUP_NAMES: &[&str] = &["background", "top-app", "foreground"];
const CGROUP_BASE: &str = "/dev/cpuctl";

// Networking tunables
const TCP_AVAILABLE: &str = "/proc/sys/net/ipv4/tcp_available_congestion_control";
const TCP_CURRENT: &str = "/proc/sys/net/ipv4/tcp_congestion_control";
const QDISC_CURRENT: &str = "/proc/sys/net/core/default_qdisc";

// Memory tunables
const SWAPPINESS: &str = "/proc/sys/vm/swappiness";
const VFS_CACHE_PRESSURE: &str = "/proc/sys/vm/vfs_cache_pressure";
const KCOMPRESSD: &str = "/proc/sys/vm/kcompressd";
const MGLRU_ENABLED: &str = "/sys/kernel/mm/lru_gen/enabled";
const MGLRU_MIN_TTL: &str = "/sys/kernel/mm/lru_gen/min_ttl_ms";

// zRAM
const ZRAM_DEV: &str = "/dev/block/zram0";
const ZRAM_SYSFS: &str = "/sys/block/zram0";
const ZRAM_SIZE_OPTIONS_GB: &[u64] = &[2, 3, 4, 6, 8];

fn config_path() -> PathBuf {
    PathBuf::from(defs::TUNING_CONFIG_PATH)
}

fn profile_name(index: i32) -> Option<&'static str> {
    THERMAL_PROFILES
        .iter()
        .find_map(|(value, name)| (*value == index).then_some(*name))
}

fn valid_sysfs_path(path: &str) -> bool {
    path.starts_with("/sys/") && !path.contains("..")
}

fn find_node(candidates: &[&str], override_path: Option<&str>) -> Option<PathBuf> {
    if let Some(path) = override_path.filter(|path| valid_sysfs_path(path)) {
        let node = PathBuf::from(path);
        if node.exists() {
            return Some(node);
        }
    }

    candidates
        .iter()
        .map(|path| PathBuf::from(*path))
        .find(|path| path.exists())
}

fn read_node(path: &Path) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string())
}

fn read_i32(path: &Path) -> Option<i32> {
    read_node(path)?.parse().ok()
}

fn read_i32_str(path: &str) -> Option<i32> {
    fs::read_to_string(path).ok()?.trim().parse().ok()
}

fn write_i32_str(path: &str, value: i32) -> Result<()> {
    fs::write(path, value.to_string())
        .with_context(|| format!("failed to write {path}"))?;
    Ok(())
}

fn cgroup_path(name: &str, file: &str) -> PathBuf {
    PathBuf::from(format!("{CGROUP_BASE}/{name}/{file}"))
}

fn run_helper(program: &str, args: &[&std::ffi::OsStr]) {
    let _ = Command::new(program)
        .args(args)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
}

fn set_mode(node: &Path, mode: u32) {
    let _ = fs::set_permissions(node, fs::Permissions::from_mode(mode));
}

/// Write a value to a sysfs node, temporarily making it writable and then
/// locking it again so the vendor thermal HAL cannot immediately undo it.
fn write_node(node: &Path, value: &str, thermal: bool) -> Result<()> {
    if thermal {
        run_helper(
            "/system/bin/chcon",
            &[std::ffi::OsStr::new(THERMAL_CONTEXT), node.as_os_str()],
        );
    }

    set_mode(node, 0o644);
    fs::write(node, value).with_context(|| format!("failed to write {}", node.display()))?;
    set_mode(node, 0o444);

    Ok(())
}

fn load_config() -> HashMap<String, String> {
    let mut config = HashMap::new();
    let Ok(content) = fs::read_to_string(config_path()) else {
        return config;
    };

    for line in content.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            config.insert(key.trim().to_string(), value.trim().to_string());
        }
    }

    config
}

fn save_config(config: &HashMap<String, String>) -> Result<()> {
    let path = config_path();
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }

    let mut lines: Vec<String> = config
        .iter()
        .map(|(key, value)| format!("{key}={value}"))
        .collect();
    lines.sort();

    let content = lines.join("\n") + "\n";
    fs::write(&path, content).with_context(|| format!("failed to write {}", path.display()))
}

fn update_config(key: &str, value: &str) -> Result<()> {
    let mut config = load_config();
    config.insert(key.to_string(), value.to_string());
    save_config(&config)
}

fn thermal_node(config: &HashMap<String, String>) -> Option<PathBuf> {
    find_node(THERMAL_NODES, config.get("thermal_node").map(String::as_str))
}

fn bypass_node(config: &HashMap<String, String>) -> Option<PathBuf> {
    find_node(BYPASS_NODES, config.get("bypass_node").map(String::as_str))
}

fn wait_for_node(
    candidates: &[&str],
    override_path: Option<&str>,
    attempts: u32,
) -> Option<PathBuf> {
    for _ in 0..attempts {
        if let Some(node) = find_node(candidates, override_path) {
            return Some(node);
        }
        thread::sleep(Duration::from_millis(250));
    }
    find_node(candidates, override_path)
}

/// Print machine-readable status for the manager app.
pub fn print_status() {
    let config = load_config();

    let persist = config
        .get("persist")
        .map(|v| v != "0")
        .unwrap_or(true);
    println!("persist={}", i32::from(persist));

    let thermal = thermal_node(&config);
    let thermal_current = thermal.as_deref().and_then(read_i32).unwrap_or(-1);
    let thermal_saved = config
        .get("thermal_profile")
        .and_then(|value| value.parse::<i32>().ok())
        .unwrap_or(-1);

    let charge_supported = Path::new(CHARGE_CURRENT).exists() && Path::new(CHARGE_CURRENT_MAX).exists();
    let charge_current = read_u64_str(CHARGE_CURRENT).unwrap_or(0);
    let charge_max = read_u64_str(CHARGE_CURRENT_MAX).unwrap_or(0);
    println!("charge_current_supported={}", i32::from(charge_supported));
    println!("charge_current={charge_current}");
    println!("charge_max={charge_max}");

    let bypass = bypass_node(&config);
    let bypass_current = bypass
        .as_deref()
        .and_then(read_i32)
        .is_some_and(|value| value != 0);
    let bypass_saved = config
        .get("bypass_charging")
        .and_then(|value| value.parse::<i32>().ok())
        .unwrap_or(-1);

    println!("thermal_supported={}", i32::from(thermal.is_some()));
    println!(
        "thermal_node={}",
        thermal
            .as_deref()
            .map_or("", |path| path.to_str().unwrap_or_default())
    );
    println!("thermal_current={thermal_current}");
    println!("thermal_saved={thermal_saved}");
    println!("bypass_supported={}", i32::from(bypass.is_some()));
    println!(
        "bypass_node={}",
        bypass
            .as_deref()
            .map_or("", |path| path.to_str().unwrap_or_default())
    );
    println!("bypass_current={}", i32::from(bypass_current));
    println!("bypass_saved={bypass_saved}");

    // --- Scheduler ---
    let skip = read_i32_str(SCHED_SKIP_HEADROOM);
    let hr = read_i32_str(SCHED_HR_SCALING);
    let lp = read_i32_str(SCHED_BOOST_HR_LP);
    let big = read_i32_str(SCHED_BOOST_HR_BIG);
    let prime = read_i32_str(SCHED_BOOST_HR_PRIME);

    println!("sched_supported={}", i32::from(skip.is_some()));
    println!("sched_skip_headroom={}", skip.unwrap_or(1));
    println!("sched_hr_scaling={}", hr.unwrap_or(0));
    println!("sched_boost_hr_lp={}", lp.unwrap_or(0));
    println!("sched_boost_hr_big={}", big.unwrap_or(0));
    println!("sched_boost_hr_prime={}", prime.unwrap_or(0));

    // --- CGroup ---
    for name in CGROUP_NAMES {
        let min = read_i32_str(&cgroup_path(name, "cpu.uclamp.min").to_string_lossy());
        let max = read_i32_str(&cgroup_path(name, "cpu.uclamp.max").to_string_lossy());
        let shares = read_i32_str(&cgroup_path(name, "cpu.shares").to_string_lossy());
        println!("cgroup_{name}_supported={}", i32::from(min.is_some()));
        println!("cgroup_{name}_uclamp_min={}", min.unwrap_or(0));
        println!("cgroup_{name}_uclamp_max={}", max.unwrap_or(100));
        println!("cgroup_{name}_shares={}", shares.unwrap_or(1024));
    }

    // --- Networking ---
    let tcp_avail = fs::read_to_string(TCP_AVAILABLE).unwrap_or_default();
    let tcp_cur = fs::read_to_string(TCP_CURRENT).unwrap_or_default().trim().to_string();
    let qdisc_cur = fs::read_to_string(QDISC_CURRENT).unwrap_or_default().trim().to_string();
    
    // Comma-separated so Kotlin can split it easily
    println!("tcp_available={}", tcp_avail.trim().replace(' ', ","));
    println!("tcp_current={tcp_cur}");
    println!("qdisc_current={qdisc_cur}");

    // --- Memory Management ---
    let swappiness_ro = fs::metadata(SWAPPINESS)
        .map(|m| m.permissions().readonly())
        .unwrap_or(true);
    let swappiness = read_i32_str(SWAPPINESS).unwrap_or(60);
    let vfs_cache = read_i32_str(VFS_CACHE_PRESSURE).unwrap_or(100);
    let kcompressd = read_i32_str(KCOMPRESSD).unwrap_or(24);

    let mglru_raw = fs::read_to_string(MGLRU_ENABLED).unwrap_or_default().trim().to_string();
    let mglru_enabled = mglru_raw != "0x0000" && !mglru_raw.is_empty();
    let mglru_ttl = read_i32_str(MGLRU_MIN_TTL).unwrap_or(1000);

    println!("mem_swappiness_ro={}", i32::from(swappiness_ro));
    println!("mem_swappiness={swappiness}");
    println!("mem_vfs_cache={vfs_cache}");
    println!("mem_kcompressd={kcompressd}");
    println!("mem_mglru_raw={mglru_raw}");
    println!("mem_mglru_enabled={}", i32::from(mglru_enabled));
    println!("mem_mglru_ttl={mglru_ttl}");

    // --- zRAM ---
    let zram_exists = Path::new(ZRAM_DEV).exists();
    let disksize = fs::read_to_string(format!("{ZRAM_SYSFS}/disksize"))
        .ok()
        .and_then(|s| s.trim().parse::<u64>().ok())
        .unwrap_or(0);
    let comp_raw = fs::read_to_string(format!("{ZRAM_SYSFS}/comp_algorithm"))
        .unwrap_or_default();
    // The active algorithm is wrapped in [brackets], e.g. "lzo [lz4] zstd"
    let comp_current = comp_raw
        .split_whitespace()
        .find(|s| s.starts_with('[') && s.ends_with(']'))
        .map(|s| s.trim_matches(|c| c == '[' || c == ']').to_string())
        .unwrap_or_default();
    let comp_available: Vec<String> = comp_raw
        .split_whitespace()
        .map(|s| s.trim_matches(|c| c == '[' || c == ']').to_string())
        .collect();
    // Swap state: look for "zram0" in /proc/swaps
    let swaps = fs::read_to_string("/proc/swaps").unwrap_or_default();
    let swap_active = swaps.lines().any(|l| l.contains("zram0"));

    println!("zram_supported={}", i32::from(zram_exists));
    println!("zram_disksize={disksize}");
    println!("zram_comp_current={comp_current}");
    println!("zram_comp_available={}", comp_available.join(","));
    println!("zram_swap_active={}", i32::from(swap_active));
}

/// Print the supported thermal profiles as `index=name` lines.
pub fn print_profiles() {
    for (index, name) in THERMAL_PROFILES {
        println!("{index}={name}");
    }
}

/// Apply a thermal profile and remember it for the next boot.
///
/// # Errors
/// Returns an error when the profile index is unknown or the sysfs node is
/// missing or cannot be written.
pub fn set_thermal(index: i32) -> Result<()> {
    let Some(name) = profile_name(index) else {
        bail!("unknown thermal profile index {index}");
    };
    if index < 0 {
        bail!("cannot apply the '{name}' sentinel profile");
    }

    let config = load_config();
    let node = thermal_node(&config).context(
        "thermal node not found (kernel must expose /sys/class/thermal/thermal_message/sconfig)",
    )?;

    write_node(&node, &index.to_string(), true)?;
    update_config("thermal_profile", &index.to_string())?;

    info!(
        "thermal profile set to {name} ({index}) via {}",
        node.display()
    );
    println!("ok");
    Ok(())
}

pub fn set_charge_current(value_ua: u64) -> Result<()> {
    if !Path::new(CHARGE_CURRENT).exists() {
        bail!("constant_charge_current node not found");
    }
    // Clamp between 1A and the hardware max (if the max node exists)
    let max_ua = read_u64_str(CHARGE_CURRENT_MAX).unwrap_or(12_000_000);
    let clamped = value_ua.clamp(CHARGE_CURRENT_MIN_UA, max_ua);

    fs::write(CHARGE_CURRENT, clamped.to_string())
        .with_context(|| "failed to write constant_charge_current")?;
    update_config("charge_current_ua", &clamped.to_string())?;
    println!("ok");
    Ok(())
}

/// Enable or disable bypass charging and remember it for the next boot.
///
/// # Errors
/// Returns an error when the sysfs node is missing or cannot be written.
pub fn set_bypass(enabled: bool) -> Result<()> {
    let config = load_config();
    let node = bypass_node(&config)
        .context("bypass charging node not found (kernel must expose input_suspend)")?;

    write_node(&node, if enabled { "1" } else { "0" }, false)?;
    update_config("bypass_charging", if enabled { "1" } else { "0" })?;

    info!(
        "bypass charging {} via {}",
        if enabled { "enabled" } else { "disabled" },
        node.display()
    );
    println!("ok");
    Ok(())
}

/// Enable (0) / disable (1) DVFS headroom via sched_skip_headroom.
///
/// Note the *reverse* convention: 0 = enabled, 1 = disabled.
pub fn set_sched_skip_headroom(value: u8) -> Result<()> {
    let raw = if value != 0 { 0 } else { 1 };
    write_i32_str(SCHED_SKIP_HEADROOM, raw)?;
    update_config("sched_skip_headroom", &raw.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_sched_hr_scaling(value: u8) -> Result<()> {
    let raw = i32::from(value != 0);
    write_i32_str(SCHED_HR_SCALING, raw)?;
    update_config("sched_hr_scaling", &raw.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_sched_boost_hr_lp(value: i32) -> Result<()> {
    let v = value.clamp(-50, 100);
    write_i32_str(SCHED_BOOST_HR_LP, v)?;
    update_config("sched_boost_hr_lp", &v.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_sched_boost_hr_big(value: i32) -> Result<()> {
    let v = value.clamp(-50, 100);
    write_i32_str(SCHED_BOOST_HR_BIG, v)?;
    update_config("sched_boost_hr_big", &v.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_sched_boost_hr_prime(value: i32) -> Result<()> {
    let v = value.clamp(-50, 30);
    write_i32_str(SCHED_BOOST_HR_PRIME, v)?;
    update_config("sched_boost_hr_prime", &v.to_string())?;
    println!("ok");
    Ok(())
}

/// Set one cgroup tunable. `key` is one of `uclamp_min`, `uclamp_max`, `shares`.
pub fn set_cgroup_value(name: &str, key: &str, value: i32) -> Result<()> {
    if !CGROUP_NAMES.contains(&name) {
        bail!("unknown cgroup {name}");
    }
    let (file, clamped) = match key {
        "uclamp_min" => ("cpu.uclamp.min", value.clamp(0, 100)),
        "uclamp_max" => ("cpu.uclamp.max", value.clamp(0, 100)),
        "shares"      => ("cpu.shares",      value.clamp(1024, 20480)),
        _ => bail!("unknown cgroup key {key}"),
    };
    let path = cgroup_path(name, file);
    if !path.exists() {
        bail!("{} not found", path.display());
    }
    write_i32_str(path.to_str().unwrap_or_default(), clamped)?;
    update_config(&format!("cgroup_{name}_{key}"), &clamped.to_string())?;
    println!("ok");
    Ok(())
}

/// Enable or disable restoring tuning values on boot.
pub fn set_persist(enabled: bool) -> Result<()> {
    let value = if enabled { "1" } else { "0" };
    update_config("persist", value)?;
    info!("ktune persistence set to {value}");
    println!("ok");
    Ok(())
}

pub fn set_tcp_congestion(value: &str) -> Result<()> {
    fs::write(TCP_CURRENT, value).with_context(|| "failed to write tcp_congestion_control")?;
    update_config("tcp_congestion_control", value)?;
    println!("ok");
    Ok(())
}

pub fn set_qdisc(value: &str) -> Result<()> {
    fs::write(QDISC_CURRENT, value).with_context(|| "failed to write default_qdisc")?;
    update_config("default_qdisc", value)?;
    println!("ok");
    Ok(())
}

pub fn set_swappiness(value: i32) -> Result<()> {
    let v = value.clamp(0, 100);
    fs::write(SWAPPINESS, v.to_string()).with_context(|| "failed to write swappiness")?;
    update_config("vm_swappiness", &v.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_vfs_cache_pressure(value: i32) -> Result<()> {
    let v = value.clamp(0, 1000);
    fs::write(VFS_CACHE_PRESSURE, v.to_string()).with_context(|| "failed to write vfs_cache_pressure")?;
    update_config("vm_vfs_cache", &v.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_kcompressd(value: i32) -> Result<()> {
    // Kernel enforces its own max, we just avoid negative numbers
    let v = value.max(0);
    fs::write(KCOMPRESSD, v.to_string()).with_context(|| "failed to write kcompressd")?;
    update_config("vm_kcompressd", &v.to_string())?;
    println!("ok");
    Ok(())
}

pub fn set_mglru(enabled: bool) -> Result<()> {
    let val = if enabled { "y" } else { "n" };
    fs::write(MGLRU_ENABLED, val).with_context(|| "failed to write lru_gen/enabled")?;
    update_config("vm_mglru", if enabled { "1" } else { "0" })?;
    println!("ok");
    Ok(())
}

pub fn set_mglru_ttl(value: i32) -> Result<()> {
    let v = value.clamp(0, 10000);
    fs::write(MGLRU_MIN_TTL, v.to_string()).with_context(|| "failed to write lru_gen/min_ttl_ms")?;
    update_config("vm_mglru_ttl", &v.to_string())?;
    println!("ok");
    Ok(())
}

/// Resize zRAM. `size_gb` must be one of ZRAM_SIZE_OPTIONS_GB.
/// Returns `Err` if the size isn't in the allowed list.
pub fn set_zram_size(size_gb: u64) -> Result<()> {
    if size_gb == 0 {
        // "Auto" selected: remove the key so init's default is used on next boot
        let mut config = load_config();
        if config.remove("zram_size_gb").is_some() {
            save_config(&config)?;
        }
        println!("ok");
        return Ok(());
    }

    if !ZRAM_SIZE_OPTIONS_GB.contains(&size_gb) {
        bail!("unsupported zram size {size_gb}GB");
    }
    if !Path::new(ZRAM_DEV).exists() {
        bail!("zram device not found");
    }

    let bytes = size_gb * 1024 * 1024 * 1024;
    // Read the current algorithm first so we don't lose it on resize.
    let comp = fs::read_to_string(format!("{ZRAM_SYSFS}/comp_algorithm"))
        .unwrap_or_default()
        .split_whitespace()
        .find(|s| s.starts_with('[') && s.ends_with(']'))
        .map(|s| s.trim_matches(|c| c == '[' || c == ']').to_string())
        .unwrap_or_else(|| "lz4".to_string());

    let script = format!(
        "swapoff {dev} 2>/dev/null; \
         echo 1 > {sys}/reset; \
         echo {comp} > {sys}/comp_algorithm; \
         echo {bytes} > {sys}/disksize; \
         mkswap {dev} >/dev/null; \
         swapon {dev}",
        dev = ZRAM_DEV,
        sys = ZRAM_SYSFS,
        comp = comp,
        bytes = bytes,
    );

    let status = Command::new("/system/bin/sh")
        .args(["-c", &script])
        .status()
        .context("failed to spawn sh for zram resize")?;
    if !status.success() {
        bail!("zram resize script exited with {status}");
    }

    update_config("zram_size_gb", &size_gb.to_string())?;
    println!("ok");
    Ok(())
}

/// Change the zRAM compression algorithm.
pub fn set_zram_algorithm(algorithm: &str) -> Result<()> {
    if !Path::new(ZRAM_DEV).exists() {
        bail!("zram device not found");
    }
    // Validate: must be in the available list
    let available = fs::read_to_string(format!("{ZRAM_SYSFS}/comp_algorithm"))
        .unwrap_or_default();
    let ok = available
        .split_whitespace()
        .any(|s| s.trim_matches(|c| c == '[' || c == ']') == algorithm);
    if !ok {
        bail!("algorithm {algorithm} not available on this kernel");
    }

    // Changing the algorithm also requires reset. Same atomic-script pattern.
    let size = fs::read_to_string(format!("{ZRAM_SYSFS}/disksize"))
        .ok()
        .and_then(|s| s.trim().parse::<u64>().ok())
        .unwrap_or(0);
    if size == 0 {
        bail!("zram disksize is 0, cannot reapply");
    }

    let script = format!(
        "swapoff {dev} 2>/dev/null; \
         echo 1 > {sys}/reset; \
         echo {algo} > {sys}/comp_algorithm; \
         echo {size} > {sys}/disksize; \
         mkswap {dev} >/dev/null; \
         swapon {dev}",
        dev = ZRAM_DEV,
        sys = ZRAM_SYSFS,
        algo = algorithm,
        size = size,
    );

    let status = Command::new("/system/bin/sh")
        .args(["-c", &script])
        .status()
        .context("failed to spawn sh for zram algorithm change")?;
    if !status.success() {
        bail!("zram algorithm script exited with {status}");
    }

    update_config("zram_algorithm", algorithm)?;
    println!("ok");
    Ok(())
}

/// Restore the persisted tuning settings. Called from the boot service stage.
///
/// Individual node failures are logged and skipped so a single missing vendor
/// node never breaks the rest of the boot flow.
pub fn apply_persisted() {
    let config = load_config();
    if config.is_empty() {
        return;
    }

    // If persistence is disabled, do nothing — kernel defaults apply.
    let persist = config
        .get("persist")
        .map(|v| v != "0")
        .unwrap_or(true);
    if !persist {
        info!("ktune persistence disabled, using kernel defaults");
        return;
    }

    if let Some(saved) = config
        .get("thermal_profile")
        .and_then(|value| value.parse::<i32>().ok())
        .filter(|saved| *saved >= 0)
    {
        if let Some(node) = wait_for_node(
            THERMAL_NODES,
            config.get("thermal_node").map(String::as_str),
            20,
        ) {
            match read_i32(&node) {
                Some(current) if current == saved => {
                    info!("thermal profile {saved} already active");
                }
                Some(current) => {
                    info!("restoring thermal profile {saved} (was {current})");
                    if let Err(e) = write_node(&node, &saved.to_string(), true) {
                        warn!("failed to restore thermal profile: {e:#}");
                    }
                }
                None => warn!("thermal node {} is not readable", node.display()),
            }
        } else {
            warn!("thermal node not found, cannot restore profile {saved}");
        }
    }

    if let Some(v) = config.get("charge_current_ua") {
        if let Ok(ua) = v.parse::<u64>() {
            let _ = set_charge_current(ua);
        }
    }

    if let Some(saved) = config
        .get("bypass_charging")
        .and_then(|value| value.parse::<i32>().ok())
    {
        let saved = i32::from(saved != 0);
        if let Some(node) = wait_for_node(
            BYPASS_NODES,
            config.get("bypass_node").map(String::as_str),
            20,
        ) {
            match read_i32(&node) {
                Some(current) if i32::from(current != 0) == saved => {
                    info!("bypass charging already {saved}");
                }
                Some(current) => {
                    info!("restoring bypass charging {saved} (was {current})");
                    if let Err(e) = write_node(&node, &saved.to_string(), false) {
                        warn!("failed to restore bypass charging: {e:#}");
                    }
                }
                None => warn!("bypass node {} is not readable", node.display()),
            }
        } else {
            warn!("bypass charging node not found, cannot restore state");
        }
    }

    // --- Restore scheduler ---
    if let Some(v) = config.get("sched_skip_headroom") {
        let _ = write_i32_str(SCHED_SKIP_HEADROOM, v.parse().unwrap_or(1));
    }
    if let Some(v) = config.get("sched_hr_scaling") {
        let _ = write_i32_str(SCHED_HR_SCALING, v.parse().unwrap_or(0));
    }
    for (key, path) in [
        ("sched_boost_hr_lp", SCHED_BOOST_HR_LP),
        ("sched_boost_hr_big", SCHED_BOOST_HR_BIG),
        ("sched_boost_hr_prime", SCHED_BOOST_HR_PRIME),
    ] {
        if let Some(v) = config.get(key) {
            let _ = write_i32_str(path, v.parse().unwrap_or(0));
        }
    }

    // --- Restore cgroup tunables ---
    for name in CGROUP_NAMES {
        for (key, file) in [
            ("uclamp_min", "cpu.uclamp.min"),
            ("uclamp_max", "cpu.uclamp.max"),
            ("shares", "cpu.shares"),
        ] {
            if let Some(v) = config.get(&format!("cgroup_{name}_{key}")) {
                let path = cgroup_path(name, file);
                let _ = write_i32_str(path.to_string_lossy().as_ref(), v.parse().unwrap_or(0));
            }
        }
    }

    // --- Restore networking tunables ---
    if let Some(v) = config.get("tcp_congestion_control") {
        let _ = fs::write(TCP_CURRENT, v);
    }
    if let Some(v) = config.get("default_qdisc") {
        let _ = fs::write(QDISC_CURRENT, v);
    }

    // --- Restore memory tuning ---
    if let Some(v) = config.get("vm_swappiness") {
        let _ = fs::write(SWAPPINESS, v);
    }
    if let Some(v) = config.get("vm_vfs_cache") {
        let _ = fs::write(VFS_CACHE_PRESSURE, v);
    }
    if let Some(v) = config.get("vm_kcompressd") {
        let _ = fs::write(KCOMPRESSD, v);
    }
    if let Some(v) = config.get("vm_mglru") {
        let _ = fs::write(MGLRU_ENABLED, if v == "1" { "y" } else { "n" });
    }
    if let Some(v) = config.get("vm_mglru_ttl") {
        let _ = fs::write(MGLRU_MIN_TTL, v);
    }

    if let Some(v) = config.get("zram_size_gb") {
        if let Ok(gb) = v.parse::<u64>() {
            let _ = set_zram_size(gb);
        }
    }
    if let Some(v) = config.get("zram_algorithm") {
        let _ = set_zram_algorithm(v);
    }
}
