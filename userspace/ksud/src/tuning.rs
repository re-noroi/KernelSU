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

/// Same profile table that the N0Kontzzz kernel manager exposes.
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

/// Restore the persisted tuning settings. Called from the boot service stage.
///
/// Individual node failures are logged and skipped so a single missing vendor
/// node never breaks the rest of the boot flow.
pub fn apply_persisted() {
    let config = load_config();
    if config.is_empty() {
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
}
