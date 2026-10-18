#!/usr/bin/env bash
# Run once after each modern guest boot, before any candidate launch/firewall.
# Official Android slow-hardware timeout multiplier, userdebug root only.
# Does not alter SELinux, userdata, APKs, purchase/game logic, or host settings.
set -euo pipefail
runtime_dir=/workspace/paladog-tools/runtime
adb_bin="$runtime_dir/platform-tools/adb"
serial=emulator-5558
timeout 30 "$adb_bin" -s "$serial" root
timeout 60 "$adb_bin" -s "$serial" wait-for-device
current=$(timeout 30 "$adb_bin" -s "$serial" shell getprop ro.hw_timeout_multiplier | tr -d '\r')
if [[ "$current" == 10 ]]; then
  printf 'Modern guest timeout multiplier already 10; no framework restart.\n'
  exit 0
fi
if [[ -n "$current" ]]; then
  printf 'Preserving existing timeout multiplier %s; review before changing.\n' "$current" >&2
  exit 1
fi
# A restart is safe only before the game is running. Never discard live progress.
app_pid=$(timeout 30 "$adb_bin" -s "$serial" shell 'pidof com.Paladog.KorGG || true' | tr -d '\r')
if [[ -n "$app_pid" ]]; then
  printf 'Game process is active; exit normally before preparing this guest.\n' >&2
  exit 1
fi
timeout 30 "$adb_bin" -s "$serial" shell setprop ro.hw_timeout_multiplier 10
actual=$(timeout 30 "$adb_bin" -s "$serial" shell getprop ro.hw_timeout_multiplier | tr -d '\r')
[[ "$actual" == 10 ]] || { printf 'Timeout property was not set; guest unchanged otherwise.\n' >&2; exit 1; }
# Read-only properties accept a first value here under the normal userdebug
# policy. Restart framework so Build's static initializer reads that value.
timeout 30 "$adb_bin" -s "$serial" shell stop
timeout 30 "$adb_bin" -s "$serial" shell setprop sys.boot_completed 0
timeout 30 "$adb_bin" -s "$serial" shell start
printf 'Timeout multiplier 10 set and framework restarted; wait for sys.boot_completed=1, then reapply the modern firewall before launching an APK.\n'
