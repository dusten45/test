# Final APK: focused Android6/API23 text regression

PASS for the focused scope below. Tested APK:
`Paladog-2.2.0-modern-offline.apk`, 40,227,841 bytes, SHA-256
`c8211db68b9b093da2ae9a349365063241fd18da286738c563b081fc24c15bfe`.
The installed APK was independently streamed back and matched this hash
(`installed-apk-sha256.json`).

The existing ARMv7 API23 AVD was restarted without wiping userdata. It reached
boot completion with SELinux Enforcing. IPv4/IPv6 OUTPUT DROP rules were
verified after boot and again after the test. The classic guest's actual screen
was 320x480 at 160dpi, producing 480x320 landscape captures. An unrelated
Launcher3 crash dialog appeared after boot and was dismissed; no Paladog fatal
exception or ANR was found in the captured application test log.

Same-key `adb install -r` succeeded. All 11 pre-existing save XML files were
backed up, parsed, and remained byte-identical immediately after updating
(`update-preserved-saves.json`). The updated game recognized Resume Last Battle
and restored actual combat (`resume-prompt.png`, `battle-first.png`).

The full quit message is visible without clipping in
`trial2-quit-dialog.png`:

> Are you sure to quit?
> (The ongoing battle will be automatically
> saved, and can be resumed later.)

The final word `automatically`, both full lines, and closing `later.)` are
visible. Back opened the dialog and Yes performed a normal exit. A 500ms right
button input was also issued; the first comparison frame had not yet drawn the
hero, so this focused report does not independently quantify movement distance
or retest summoning/game modes. The unchanged gameplay/save code and previous
full-cycle tests are documented separately.

During the first observation attempt the hero died while screenshots were
being inspected. That synthetic state was preserved in `save-after-first-attempt`.
For the bounded second attempt, only our 11 backed-up synthetic pretest XMLs
were restored; UID, permissions, and SELinux context were preserved. An automated
first-battle-frame check then issued the short input and immediate Back. No
unlock flags, game code, or real-user saves were edited to obtain coverage.

After normal exit, `ps` showed no Paladog process
(`app-process-after-normal-exit.json`), and all 11 post-exit save XMLs were copied
and parsed (`save-after-normal-exit-hashes.json`). No additional full restart/save
cycle is claimed for this small text-only regression. The original APK was never
installed or executed. Shutdown status is recorded separately after guest sync.
