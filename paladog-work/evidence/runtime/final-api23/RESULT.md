# Final modern APK runtime result — API 23 emulator

Tested final installed APK SHA-256:

`44d1885f7a37bc38acd895a6a793924afc239f3cd56ad3f09f57e697ad55d5c2`

Read back the installed `/data/app/com.Paladog.KorGG-2/base.apk` and hashed its byte stream. Package manager reports versionCode 26, versionName 2.2.0-offline, targetSdk 28, primaryCpuAbi=null and secondaryCpuAbi=null. This is the final modern build; the app was already installed as an update before this test.

## Passed

1. Finished the existing failed survival result with Next, which showed local ranking with wave 1, kills 0 and time 1:03. The disabled Retry control did not trigger anything; this was a gray disabled button, not an app hang.
2. Returned to the title/menu and confirmed the What's New control was absent (`03-rankclose-04.png`).
3. Entered Paladog → Survival. The game went directly to its existing offline-score notice (`06-survival-message.png`), with no Facebook/email login selection. Then entered the survival shop and hero equipment screens (`07-survival-entry-06.png`, `08-survival-hero.png`).
4. Started a fresh survival battle and summoned the mouse unit. `10-live-summon.png` shows the live unit and approaching enemies, with the game rendering normally. No save-file editing or unlock changes were used.
5. Pressed Android Back during combat. `11-exit-dialog.png` shows the game's quit confirmation and explicit promise to save the ongoing battle. Selected YES.
6. Polled process state until the app exited naturally; PID 3296 was present in three polls and absent in the next (`exit-process-poll.txt`). Waited two additional seconds and only then pulled the 11 preference files. No force-stop was used to end this save operation. Their hashes are in `save-after-exit-sha256.txt`.
7. Relaunched the app. `13-relaunch-03.png` shows “Resume Last Battle?”. Selected YES and recorded screenshots about every three seconds. The app showed its startup image while restoring, then resumed actual survival combat (`14-resume-06.png`), rather than a loading error or fresh main menu.
8. Paused the restored battle promptly using the in-game pause control. `15-restored-paused.png` shows the pause overlay above the restored battlefield. The emulator is left in this paused state.

## Isolation and logs

The pre-existing OUTPUT firewall chain remained active. Its PALADOG_OFFLINE chain drops output except an established root ADB return connection to the host (`firewall.txt`, `firewall-app-chain.txt`). No firewall rule was modified by this reviewer.

The captured `final-logcat.txt` contains no match for FATAL EXCEPTION, VerifyError, NoClassDefFoundError, UnsatisfiedLinkError, ActivityNotFoundException or a loading-error string during this test. The log does contain emulator platform graphics/memtrack noise; the displayed and interactive game flow succeeded.

## Limits

This validates the final modern package's core gameplay and local battle save/resume on this **API 23 emulator**, whose runtime is 32-bit. `primaryCpuAbi=null` proves the package has no selected native ABI on this device, not execution on an ARM64-only phone. The separate API 36/64-bit runtime test and actual Galaxy-device behavior are separate results. This test does not complete every campaign stage, Darkdog progression or every item/upgrade.

API23 UI ownership is released after this report; the restored battle remains paused.
