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
const BYPASS_NODES: &[&str] = &[
    "/sys/class/power_supply/battery/input_suspend",
    "/sys/class/power_supply/bms/input_suspend",
];

const THERMAL_CONTEXT: &str = "u:object_r:sysfs_thermal:s0";

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
}
