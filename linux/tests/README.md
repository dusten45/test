# Linux adapter tests

Run the complete suite after `linux/build.sh` has finished:

```sh
PALADOG_CACHE=/path/to/paladog-linux-cache ./linux/test.sh
```

Run it from a Linux graphical desktop with `DISPLAY` or `WAYLAND_DISPLAY` set. Java 21, Python 3, the already cached ECJ compiler/dependencies, extracted game assets, and the reference APK are required. `JAVA` can select a Java executable; `PALADOG_APK` can select a different location of the same reference APK. The script never downloads dependencies, rebuilds the game, or writes to `build/classes`.

For the managed cloud environment's existing Xvfb display:

```sh
PALADOG_CACHE=/workspace/paladog-linux-cache \
PALADOG_TEST_ENV=/workspace/paladog-linux-cache/test-env \
./linux/tools/test-display.sh exec ./linux/test.sh
```

If a display is not running, use `tools/test-display.sh start` with the same `PALADOG_TEST_ENV` first. Select an unused `PALADOG_TEST_DISPLAY` when needed. Do not stop or replace a display used by another game session.

The suite compiles only test classes and runs these checks:

- Storage: typed Android XML roundtrips, atomic replacement during reads, failed writes, valid backups, damaged-save preservation, and blocked external entities.
- Resources: actual game resource IDs, asset bytes, audio descriptors, filenames, and local file/preference access.
- Plists: nested game data and every packaged plist, with HTTP connections forbidden.
- Audio: all 145 packaged MP3 files, OpenAL loopback PCM, volume, pause/resume, lifecycle, and the game's resource-based media calls.
- Rendering: bitmap alpha, Korean font coverage, actual OpenGL draw/readback, and graphics cleanup thread ownership.
- Input: mouse/keyboard mapping, simultaneous controls, and release behavior; original game callbacks for visible unit order, Destiny stack order/compaction, Tower unit/lane sequences and cancellation. The callback tests replace final unit creation with a recording sink, so food deduction and actual spawning are checked separately in live play.

For isolated live gameplay checks, see [the synthetic fixture utility](gameplay/README.md). It is optional and must never be pointed at personal saves.

Storage tests create and remove temporary save directories. They never import or use player saves. Audio checks use OpenAL loopback and do not require a physical sound device. Graphics checks open their own small test window; they do not send events to a running game. The suite can run alongside a game session, but do not rebuild adapter classes during testing.

Results and test-only logs are saved to ignored `linux/build/tests/`. `summary.txt` reports each group, and class-file digests verify that the tested build remained unchanged. A nonzero exit status means at least one group failed. These adapter checks supplement the real menu, battle, progression, save/relaunch, and user-device audio tests; they do not establish complete gameplay compatibility.
