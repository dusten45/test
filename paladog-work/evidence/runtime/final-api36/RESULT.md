# Final modern APK runtime result — API 36, 64-bit-only emulator

Status: PASS for title rendering, touch/summon, cross-architecture battle load, natural local save/exit, and relaunch/restoration on this API36 64-bit-only emulator.

The tested package is the final modern build, versionCode 26, versionName 2.2.0-offline, targetSdk 28. The installed `base.apk` was read back (40,227,841 bytes) and its actual SHA-256 matches the final artifact (`installed-apk-sha256.txt`): `44d1885f7a37bc38acd895a6a793924afc239f3cd56ad3f09f57e697ad55d5c2`.

## Test environment and scope

This is the Android 16 / API 36 AOSP x86_64 emulator. `ro.product.cpu.abilist` and `abilist64` are `x86_64`; `abilist32` is empty. The running game uses `/system/bin/app_process64` (`runtime-identity.txt`, `process-maps.txt`). SELinux remains Enforcing. This is an actual 64-bit-only Android runtime test, not an ARM64 Galaxy S26 hardware test.

The software-emulated guest required the runtime agent's per-boot hardware timeout multiplier of 10 to survive framework startup. The unchanged final APK was installed and then launched. Rendering/loading and file writes can take tens of seconds on this host. No original unpatched APK was run for these tests.

## Save preparation

The first fresh launch reached the ordinary title/menu (`01-fresh-title.png`), with What's New absent. Fresh API36 shared preferences were empty and were backed up (`pre-import-prefs`, `pre-import-listing.txt`).

To test meaningful saved combat without replaying the tutorial, this run imported only the synthetic test saves produced by our own API23 final-build test. These are the exact 11 XML files from `../final-api23/save-after-exit`; hashes were verified unchanged on import (`imported-sha256.txt`). No progress, unlock or inventory values were edited. Game battle serialization uses Java text/XML/base64 rather than native pointers, and the same patched APK uses the same empty save identifier on both platforms.

The app was stopped before import. The existing app UID/GID was 10150; the directory was set to 771 and its XML files to 600. After restorecon assigned a base context without the app's MCS categories to the new files, their context was corrected to the exact existing app-directory context `u:object_r:app_data_file:s0:c150,c256,c512,c768`. SELinux was never disabled.

## Observed gameplay and save results

1. Imported API23 battle state was recognized and displayed Resume Last Battle (`02-crossarch-load-04.png`). Selecting YES restored actual active survival combat (`02-crossarch-load-08.png`, `02-crossarch-load-09.png`). This demonstrates a saved battle crossing from a 32-bit runtime to a 64-bit runtime with this final APK.
2. The first pause attempt was inconclusive: the pause button disappeared, but no pause overlay was captured. Subsequent Android Back and time spent recording evidence coincided with a natural hero defeat. No touch-scaling bug was established. Code review found rendering and touch transforms to be inverses. This attempt is not counted as a successful pause test.
3. For a controlled repeat, the failed trial's preferences were preserved as `shared_prefs.trial1` on the guest, and the same original 11 test files were imported again without editing them. At the first restored battlefield frame (`06-repeat-resume-03.png`), tapping the first summon control at physical 70,355 and then Android Back opened the normal quit confirmation (`07-summon-and-back.png`). The food value changed from 40 to 34, and a mouse unit is visible behind the dialog. Both game touch input and the back-to-quit flow worked.
4. Selected YES in the quit dialog. The app process PID 2678 exited naturally after about 35 seconds (`exit-process-poll.txt`). No force-stop was used for this save. After process exit and an additional two seconds, all 11 preference files were pulled to `save-after-exit`; their SHA-256 values are in `save-after-exit-sha256.txt`. Ten files changed from the imported snapshot; local ranking remained unchanged.
5. Relaunched without reimporting or modifying the new save. Resume Last Battle appeared (`09-native64-relaunch-02.png`). Selecting YES restored actual live survival combat from that newly written 64-bit save (`10-native64-resume-04.png`), including the mouse unit and enemies. This is a complete natural save/exit/relaunch/battle-resume cycle on the 64-bit guest.
6. Immediately pressed Android Back and captured the ordinary quit confirmation (`11-final-quit-dialog.png`). The guest is left safely in this dialog for root-agent shutdown, rather than running combat while writing reports.

## Isolation and logs

Readback of both IPv4 and IPv6 rules (`firewall.txt`) confirms OUTPUT first enters PALADOG_OFFLINE and is dropped. The only IPv4 exception permits the existing root-owned ADB return connection from TCP source port 5555 to host 10.0.2.2; it does not permit the game UID. IPv6 has no exception. No firewall rule was changed during gameplay testing. `current-identity.txt` also confirms SELinux Enforcing and API36 with an empty 32-bit ABI list.

`final-process-exe.txt` and `final-process-maps.txt` identify the final restored game process. `package-info.txt` reports primaryCpuAbi=null and secondaryCpuAbi=null, consistent with this APK containing no native ABI payload. Full and app-process logcat were captured as `final-logcat.txt` and `final-app-logcat.txt`; crash/error scan findings are summarized in `log-error-scan.txt`. Neither log matched FATAL EXCEPTION, VerifyError, NoClassDefFoundError, UnsatisfiedLinkError, ActivityNotFoundException, SecurityException, an app ANR, or Loading Error during the captured interval.

## Limits

The quit dialog body is partly clipped at this very wide viewport, but its NO/YES controls work and the promised save/resume behavior was verified. This run does not validate ARM64 hardware drivers, every Galaxy S26 display/input behavior, every campaign stage, Darkdog progression, or every item/upgrade. The emulator's severe software-rendering delay means its loading time is not a useful phone-performance measurement.
