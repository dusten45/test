# Linux keyboard controls verification

2026-10-18. Linux x86-64, Debian 13, Java 21.0.12.1, Xvfb X11 and Mesa
llvmpipe 25.0.7. These checks exercise the standalone JVM/OpenGL game, without
Android. All saves are isolated synthetic fixtures.

## Live game checks

| Mode | Input | Observed result |
| --- | --- | --- |
| Ordinary stage 1 | `5` | Spawned the turtle, original character kind `16908294`, matching the fifth visible unit button. |
| Destiny stage 3 | `5` with three items present | None of the existing items was consumed. A fourth item arrived naturally between trace samples. |
| Destiny stage 3 | `1` | Consumed the oldest/bottom item: storage slot 7, order 0, value 3. A bear (`16908291`) appeared. |
| Destiny stage 3 | `2` after that consumption | Consumed the magic item now second in order: storage slot 0, value `16777220`. Remaining orders compacted correctly. |
| Tower stage 9 | `11`, `12`, `35` | Spawned mouse (`16908289`) in lane 1, mouse in lane 2, and bear (`16908291`) in lane 5, respectively. Lane 1 is the top lane. |

All three modes were checked on the final positive-pointer-2 input build.
The final Tower retest used a separate Xvfb display and injected each two-digit
sequence with a 5 ms inter-key delay. The trace and screenshot show both mice
and the bear in their requested lanes at the same time.

The original game still decides whether a summon is affordable and ready.
The turtle's original food cost is 50; food continues regenerating between
the two-second trace samples, so subtracting sampled food values does not
measure the exact cost. No unlimited food or cooldown bypass was used.

Evidence:

- [Ordinary mode observations](controls-v2-normal-key5-observations.json)
- [Destiny observations, including recycled storage slots](controls-v2-destiny-input-observations.json)
- [Final-build Tower observations](controls-v2-tower-observations.json)
- [Turtle spawned with key 5](controls-v2-normal-key5.png)
- [Mouse in the top Tower lane](controls-v2-tower-11.png)
- [Final build: mice in lanes 1 and 2, bear in lane 5](controls-v2-tower-final.png)
- [Destiny mode entry](controls-v2-destiny-start.png): establishes mode entry, not stack consumption.

Screenshots show a 960×640 game window within a larger test display. Black
space outside the window is unused display area. The later Destiny capture
shows a normal level-up panel and is not used as evidence of visible stacks.
Trace observations establish the stack changes; the screenshots alone do not.

## Automated checks

[The adapter suite](controls-v2-adapter-tests.txt) passed all groups with zero
failures, including storage, resources, 814 offline plists, 145 MP3 files,
OpenAL loopback, OpenGL rendering and thread cleanup. Test execution did not
modify the adapter class files. Audio results verify generated PCM, not
physical speaker playback.

The input tests exercise the actual original UI callbacks: visible unit order,
Destiny recycled slots and order compaction, rapid `11`/`12`/`35`, every lane,
rejected unit selection, and cancellation on pause, focus loss, mouse input or
mode changes. Existing movement aliases and simultaneous movement/actions
also pass. These checks supplement the live game tests rather than proving
that every campaign feature works.

The final `DesktopInput.java` build source SHA-256 at this check was
`afd2a9ba0487b82b16acfe079ba39de2c559ca03c8ddc661e7fc164741a191c1`.
The unchanged converted `game.jar` SHA-256 is
`30f946aac6d9c440f30c9f0150db0d45a8799475b29c7acae5cad7874f7ec5d6`.
[Evidence checksums](controls-v2-sha256.json) identify the copied observations
and screenshots.

## Fixtures and limits

The [fixture utility](../tests/gameplay/README.md) uses the game's original
encrypted XML read/write and checksum APIs. It unlocks only the tested stage
and stage 1, exposes nine level-one units and marks the relevant tutorials
complete. It omits the old battle snapshot. The ordinary-mode and final Tower fixtures use
finite food-storage level 2 (capacity 120) and regeneration level 3; the
original equipment, gold and summon rules remain. Original synthetic source
XML hashes were checked unchanged. No personal saves were used or published.

Inspection of the original ordinary summon path and `CharManager` found no
general fixed population cap; allies and enemies use dynamically growing
`ArrayList` storage. Food, cooldowns and mode-specific rules still restrict
spawning. This does not establish unlimited capacity: memory, CPU/GPU work
and untested edge cases remain practical limits. This change does not modify
population balance or introduce a new cap.

These are targeted input checks, not a full campaign, all-units, all-equipment,
long-duration stress, physical GPU or Fedora desktop certification. The user
separately reported approximately ten minutes of successful PC play and a
large-population experiment without a failure; those observations are not
measurements from this test environment.

## Headless level-up presentation observation

During an additional stage 3 check, the Xvfb root-window capture became black
after Escape on the skill-selection panel. A diagnostic read of `GL_BACK`
still showed the [complete, correctly rendered panel](controls-v2-levelup-backbuffer.png).
The running scene, layer visibility, card opacity, texture validity, matrices,
and relevant GL state were unchanged; the renderer reported no GL error.
Clicking the middle skill continued to select it and restored visible battle.

`GL_FRONT` read back black both during this symptom and during normal visible
battle, so that result alone does not establish a game-rendering failure in
this test environment. A subsequent panel remained visible without Escape
during idle waiting, consecutive captures, and Shift input. The specific
X11/GLX presentation or capture cause remains unresolved. No production
workaround was added, and this observation must not be described as a fixed
gameplay bug or proof of correct presentation on a physical desktop.

[Diagnostic observations](controls-v2-levelup-render-observations.json) retain
the relevant measurements. The diagnostic code ran outside the repository
and did not alter game logic, save fields, or production classes.
