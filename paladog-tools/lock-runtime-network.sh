#!/usr/bin/env bash
# Run after a clean AVD has fully booted, before installing any candidate APK.
set -euo pipefail
runtime_dir=/workspace/paladog-tools/runtime
adb_bin="$runtime_dir/platform-tools/adb"
serial=emulator-5556
timeout 30 "$adb_bin" -s "$serial" root
timeout 60 "$adb_bin" -s "$serial" wait-for-device
# Replace only our private filter chain and its OUTPUT hook. A temporary
# equivalent guard keeps traffic blocked while the main chain is refreshed.
# Preserve existing Android chains. ADB is the sole egress exception: root UID,
# TCP source port 5555, established replies to the emulator host 10.0.2.2.
python3 - "$runtime_dir" <<'PY'
from pathlib import Path
import subprocess, sys
root = Path(sys.argv[1])
adb = [str(root / 'platform-tools/adb'), '-s', 'emulator-5556', 'shell']
reports = []
for family in ['iptables', 'ip6tables']:
    before = subprocess.check_output(adb + [family + ' -w -S'], timeout=30).decode().splitlines()
    hook = '-A OUTPUT -j PALADOG_OFFLINE'
    allowed = []
    if family == 'iptables':
        allowed = ['-d 10.0.2.2 -p tcp --sport 5555 -m owner --uid-owner 0 -m state --state ESTABLISHED -j ACCEPT']
    allowed += ['-j DROP']
    # Android 6 ships no iptables-restore. Never expose an empty live chain:
    # build a separate guard first, then replace only our named main chain.
    if '-N PALADOG_LOCK' in before:
        raise RuntimeError('A previous incomplete firewall refresh needs inspection')
    commands = [family + ' -w -N PALADOG_LOCK']
    commands += [family + ' -w -A PALADOG_LOCK ' + rule for rule in allowed]
    commands += [family + ' -w -I OUTPUT 1 -j PALADOG_LOCK']
    if '-N PALADOG_OFFLINE' not in before:
        commands += [family + ' -w -N PALADOG_OFFLINE']
    commands += [family + ' -w -F PALADOG_OFFLINE']
    commands += [family + ' -w -A PALADOG_OFFLINE ' + rule for rule in allowed]
    commands += [family + ' -w -D OUTPUT -j PALADOG_OFFLINE'] * before.count(hook)
    # Migrate the direct IPv6 DROP placed by the initial one-time setup.
    # It is only ours when no private chain has been created yet.
    if family == 'ip6tables' and '-N PALADOG_OFFLINE' not in before and '-A OUTPUT -j DROP' in before:
        commands += [family + ' -w -D OUTPUT -j DROP']
    commands += [family + ' -w -I OUTPUT 1 -j PALADOG_OFFLINE',
                 family + ' -w -D OUTPUT -j PALADOG_LOCK',
                 family + ' -w -F PALADOG_LOCK', family + ' -w -X PALADOG_LOCK',
                 'echo PALADOG_FIREWALL_OK']
    data = ' && '.join(commands)
    (root / 'logs' / (family + '-refresh.txt')).write_text(data + '\n')
    result = subprocess.run(adb + [data], capture_output=True, timeout=30)
    if result.returncode or result.stdout.decode().strip() != 'PALADOG_FIREWALL_OK' or result.stderr.strip():
        raise RuntimeError('Firewall guarded refresh failed: ' + (result.stdout + result.stderr).decode())
    lines = subprocess.check_output(adb + [family + ' -w -S'], timeout=30).decode().splitlines()
    output_rules = [x for x in lines if x.startswith('-A OUTPUT ')]
    assert output_rules[0] == hook and output_rules.count(hook) == 1, 'Firewall hook must be first and unique'
    own_rules = [x for x in lines if x.startswith('-A PALADOG_OFFLINE ')]
    assert own_rules[-1] == '-A PALADOG_OFFLINE -j DROP', 'Final DROP missing'
    assert len(own_rules) == (2 if family == 'iptables' else 1), 'Unexpected firewall exception'
    reports += [family] + lines
(root / 'logs/firewall-rules.txt').write_text('\n'.join(reports) + '\n')
print('Verified guest-wide IPv4 and IPv6 OUTPUT DROP rules')
PY
timeout 15 "$adb_bin" -s "$serial" shell 'ping -c 1 -W 2 8.8.8.8; iptables -w -nvxL PALADOG_OFFLINE; ip6tables -w -nvxL PALADOG_OFFLINE' > "$runtime_dir/logs/firewall-probe.txt"
cat "$runtime_dir/logs/firewall-rules.txt" "$runtime_dir/logs/firewall-probe.txt"
