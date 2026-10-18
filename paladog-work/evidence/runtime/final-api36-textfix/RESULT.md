# API 36 regression result — final text-fit build

Status: PASS for the corrected quit-dialog text, in-place update/save continuity, natural save/exit, and relaunch/restoration of actual combat.

Tested installed APK SHA-256: `c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`.

The installed base.apk was read back and matched the final 40,227,841-byte artifact (`installed-apk-sha256.txt`). Package metadata remains versionCode 26, versionName 2.2.0-offline, targetSdk 28, primaryCpuAbi=null and secondaryCpuAbi=null. Installation used `adb install -r` with the same signing key (`install-update.txt`).

## Scope

This is a focused regression on the existing Android 16/API 36 x86_64-only emulator. The build agent verified that CCTexture2D is the sole changed class and the other 1,175 classes are unchanged: WrapText now returns one unchanged line if Paint.measureText(text) fits the width; longer strings retain the original wrapping path. Game logic, save serialization, touch mapping and aspect settings are unchanged.

The previous full runtime reports remain at `../final-api36/RESULT.md` and `../final-api23/RESULT.md` with their original build hash 44d1885…. This report does not relabel those older tests as tests of the new binary.

## Observed results

1. Before updating, selected YES in the old build's existing quit dialog. PID 2972 exited naturally at 16:58:18 UTC. After two more seconds, backed up all 11 preferences (`pre-update-save`, `pre-update-save-sha256.txt`, `pre-update-natural-exit.txt`). No force-stop was used for that save.
2. Updated in place. The existing natural save was immediately recognized (`01-update-start-02.png`), and YES restored the battlefield. No save reimport, progress edits or unlock edits were performed during this regression.
3. Android Back opened the quit dialog with all three intended lines fully visible (`03-quit-text-fixed.png`), including the previously clipped text: “saved, and can be resumed later.)”. The NO/YES controls and game viewport remain unchanged.
4. Selected YES. The new-build process PID 3427 exited naturally at 17:02:57 UTC after 12 polls; no force-stop was used (`natural-exit-process-poll.txt`). After two more seconds, pulled all 11 new preference files to `save-after-exit` and recorded their hashes. This tested the new binary's natural save operation.
5. Relaunched without modifying or reimporting the save. Resume Last Battle appeared (`04-relaunch-02.png`). Selected YES; the corrected quit dialog was reachable again (`06-final-safe-quit-dialog.png`). The early `05-final-resume-03.png` frame still shows scene initialization values and is not used alone as proof of complete restoration.
6. Dismissed the dialog with NO for an additional live frame. `07-restored-live-combat.png` shows the actual resumed hero and enemy wave with 40/40 food and 100/100 mana. This confirms actual combat restoration after the new binary's natural save/exit/relaunch cycle. No additional summon was attempted on this already weakened hero; the earlier full-build test separately verifies summoning.
7. During that extra live capture the weakened hero died before Back reopened the dialog. `08-final-safe-quit-dialog.png`, despite its planned filename, is a combat/death-transition frame, not a quit dialog; `09-safe-exit-state.png` shows the normal Mission Failed result. This was ordinary game progression, not an app crash. After collecting logs, the app was closed with force-stop solely for the final quiet handoff (`final-closed-state.txt`); this post-test close is not counted as a successful save.

## Runtime identity and isolation

`isolation.txt` confirms API 36, abilist=x86_64, empty abilist32, abilist64=x86_64, and SELinux Enforcing. `final-process-exe.txt` and `final-process-maps.txt` capture the restored process using app_process64. The IPv4 and IPv6 OUTPUT chains still lead to PALADOG_OFFLINE DROP (`final-firewall.txt`); the only exception is the pre-existing root ADB return connection to the host, not game traffic. No isolation rule was changed for this regression.

Final app and full logs are preserved. Both contain zero targeted matches for fatal exceptions, verification/class/native-link/activity errors, SecurityException, app ANR or Loading Error (`log-error-scan.txt`). The guest remains running, and the game is closed; root owns subsequent guest shutdown.

## Limits

This is an x86_64-only Android 16 emulation test, not an ARM64/Galaxy S26 hardware test. It verifies the text-fit correction and save continuity without replaying every campaign, Darkdog progression or item/upgrade. The prior clipped quit body is corrected on this 1040×480 viewport; other dialog strings and other physical screen sizes were not exhaustively tested. Software emulation has substantial loading and save latency and does not predict phone performance.
