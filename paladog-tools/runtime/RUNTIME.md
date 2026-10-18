# Paladog Android validation runtimes

The initial basic privacy candidate reached the Paladog 2.2.0 main menu in a
fresh Android 6.0 ARMv7 emulator. Its original startup records remain historical
evidence; they are not the final package's complete test record. The original
APK was never installed or executed.

The fully tested pre-text-fit APK SHA-256
`44d1885f7a37bc38acd895a6a793924afc239f3cd56ad3f09f57e697ad55d5c2` was subsequently
tested on this API23 guest. The installed APK byte stream matched that hash.
Offline survival entry, unit summoning, normal in-game exit, local preference
saves, and actual survival-combat restoration after restart passed. The restored
battle was paused at the end of that test; no save editing or unlock modification
was used. The API23 guest was later stopped to free CPU for API36 tests, so this
record does not imply a live paused guest. Recheck process and ADB readiness.
`/workspace/paladog-work/evidence/runtime/final-api23/RESULT.md` records the exact
sequence and limits. This is a 32-bit API23 runtime result, not a 64-bit-device
result, every-mode completion claim, or safety guarantee.

- SDK tools 25.2.5 provide the classic ARM engine. Current emulator 37.2.12 was
  tried first and rejected ARM32 before boot.
- Official API 23 ARMv7 system image revision 6, build 3079352; platform-tools
  37.0.1. Exact download URLs, published SHA-1 checksums, and validation status
  are in `runtime-status.json`. Repository XML snapshots and archives remain in
  `downloads/`; package notices remain alongside installed tools.
- No KVM is available. Software emulation uses one guest CPU and 1536MiB RAM.
  The classic engine ignores the two-core configuration setting.
- ADB serial: `emulator-5556`. ADB binary: `platform-tools/adb`.
- ADB requires `/home/agent/.android`; the initially absent path was created
  with approved filesystem escalation as a symlink to
  `/workspace/paladog-tools/runtime/user/.android`. No existing files were
  overwritten. The emulator console is disabled because its separate standard
  auth-token path is read-only; use ADB management.

Install with `python3 /workspace/paladog-tools/install-runtime.py`. It verifies
Google's published archive sizes and checksums over HTTPS before extraction.
Existing package members are compared byte-for-byte and retained unchanged;
changed installed files or mismatching cached archives stop installation.
A repeat run verified all 2349 existing files and wrote no package files.

Start with `bash /workspace/paladog-tools/start-runtime.sh`. Existing AVD
configuration and userdata are preserved. Only a deliberate new disposable
session should add `-wipe-data`, which destroys that AVD's local data. Do not
launch a second process for the same AVD while the first is running.

After **every boot**, before launching any APK, run
`bash /workspace/paladog-tools/lock-runtime-network.sh`. Android firewall state
does not survive reboot. The script safely refreshes only its own chains and
ensures exactly one first-position OUTPUT hook for each address family. IPv4
and IPv6 egress is dropped, except root-owned established TCP source-port 5555
replies to 10.0.2.2 needed for ADB. A temporary equivalent guard preserves the
block while refreshing rules. The script was successfully rerun on the live
guest; an ICMP probe returned EPERM and increased the DROP counter. No guest
credentials or host proxy credentials were installed.

The runtime is an old userdebug OS and obsolete emulator engine. The isolated
guest has no personal data, but this is not a hardened malware detonation VM.
Unprivileged host network namespaces were unavailable. Preserve the patched
APK's removal of INTERNET permission and the guest firewall; do not install
the original APK in this runtime.

Evidence: `logs/launch-candidate.txt`, `logs/candidate-logcat.txt`,
`logs/candidate-main-menu.png`, and `logs/firewall-validation-repeat.log`.
The initial package installation took about 70 seconds of ARM dex2oat work;
host-side short timeouts can end the observer while the guest installation
continues. Check `pm path com.Paladog.KorGG` before retrying an installation.

## Android16 / 64-bit-only validation runtime

The fully tested pre-text-fit Java-only APK has SHA-256
`44d1885f7a37bc38acd895a6a793924afc239f3cd56ad3f09f57e697ad55d5c2`.
It installed successfully in the independent API36 x86_64 AOSP guest, serial
`emulator-5558`. The guest reports no 32-bit ABI and uses zygote64; the actual
Paladog process executable is `/system/bin/app_process64` with lib64 mappings.
The APK declares VIBRATE only. Android adds POST_NOTIFICATIONS for target
compatibility, but the observed grant is false; INTERNET remains absent.
Subsequent final-package tests also rendered the title/menu with What's New
absent and restored synthetic API23 survival saves into actual API36 combat.
Unit summoning and the Android Back quit dialog worked. A normal in-game exit
wrote 11 preference files on API36; relaunch without reimporting recognized
those new files and restored actual combat with the summoned mouse and enemies.
No save values or unlock flags were edited. The final Android Back dialog
provided a safe test handoff, not a promise that the process remains live.
See `/workspace/paladog-work/evidence/runtime/final-api36/RESULT.md` and its
screenshots for the exact sequence and remaining limits. Architecture, rendering
and this save round-trip are separate observations; neither proves every mode.
That build's long quit-dialog body was partly clipped at the emulated display
size, although its buttons and save flow worked. The current text-fit build,
SHA-256 `c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`, corrects
CCTexture2D.WrapText to preserve a whole line that already fits its measured
width. All three body lines now render in the API36 quit dialog
(`evidence/runtime/final-api36-textfix/03-quit-text-fixed.png`). The former
clipping is resolved in that observed UI. The current build then completed
normal in-game exit and new-save writing, recognized that save after relaunch,
and restored actual combat with the hero/enemies and saved food/mana values.
Full prior test records keep their
44d hash; current-build regressions are recorded separately in
`evidence/runtime/final-api23-textfix/` and `final-api36-textfix/`. Neither result
establishes every layout on physical Galaxy hardware.

The current c821 build also passed a focused API23 update check: the installed
APK hash matched, all 11 preference files survived the update unchanged, actual
combat restored, and Android Back/Yes produced a responsive quit dialog with
all three text lines visible and a normal exit. Brief directional input was
issued without measuring movement distance; no additional full save/relaunch
cycle is claimed for this focused API23 regression. This is the newer
focused regression, distinct from the earlier complete 44d test sequence.
The API36 guest was shut down after the final evidence was collected. Do not
assume either guest is live; verify actual process and ADB readiness before reuse.

Install modern packages with `install-modern-runtime.py`, which uses the same
preserving extractor as the legacy installer. Exact official archive metadata
is in `modern-runtime-status.json`: emulator 37.2.12 and API36 image revision 2.
A repeat run preserved 418 matching members and wrote no package files. Start
with `start-modern-runtime.sh`; it never wipes userdata, uses a separate AVD,
and additionally sets QEMU user-network and Wi-Fi `restrict=on`. Run
`lock-modern-runtime-network.sh` after every boot, before any app launch. This
firewall helper was repeated successfully; external and loopback ICMP were
both denied with EPERM and the private DROP counter increased.

Without KVM, the first modern software boot took approximately eight minutes.
The engine raised 2048MiB requested RAM to 2560MiB and the data partition to 6GiB.
The display was reduced from 720x1560@320dpi to 480x1040@213dpi for software
rendering while preserving phone aspect ratio. A fresh launch is needed after
test-display changes because the game caches metrics at startup. Initial
software-emulation slowness produced launcher/phone/SystemUI ANRs; distinguish
those system failures from application errors. The idle guest also slept,
leaving a stopped app behind an unresponsive swipe-only keyguard; power/window
and activity dumps record that state. The synthetic guest contains no PIN,
account, or personal data. For this guest we enabled stay-awake, disabled its
empty swipe-only keyguard, enabled airplane mode, disabled Bluetooth, and
set system animation scales to 0. The firewall remained enabled throughout.
Those are test-harness settings, not changes to the APK or game animations.

This AOSP x86_64 result cannot establish Samsung One UI behavior, physical
Galaxy S26 ARM64/GPU compatibility, device-specific install policies, or
real-device performance. No original APK has been run. Guest processes can be
stopped between tests; a recorded PID is not proof of readiness after
publication. Follow `start-instructions.md` for each-boot checks and data
preservation.

Android16 defines a slow-hardware timeout multiplier property
`ro.hw_timeout_multiplier` (default 1). Official source excerpts are under
`logs/modern/aosp-*-timeouts.txt`. The generic emulator `-prop` help appeared
to support setting it, but the actual modern boot rejected the value because
only qemu properties are accepted; SELinux correctly denied the derived vendor
property. The ineffective flag was removed from the start helper. No weakened
SELinux policy or modified system image was used.

Ordinary userdebug `adb root` followed by a first-write `setprop
ro.hw_timeout_multiplier 10` was subsequently verified: the property read back
as 10 and SELinux remained Enforcing. The framework was stopped and started so
its next static initialization reads the value. The helper
`prepare-modern-runtime.sh` implements that guarded preparation after ADB becomes
reachable and before any game launch. It preserves a different existing value,
refuses a restart while the game is live, and skips restart when the value is
already 10. Wait for boot completion afterward and reapply the modern firewall
before launching the candidate. This adjusts only test-platform timing. Successful
prepared framework boot, title/menu, touch controls and save/combat restoration
were observed afterward. This helper is not an APK or physical-phone requirement.
