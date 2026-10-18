# Isolated gameplay fixtures

`PrepareControlFixtures` prepares three **test saves** for ordinary stage 1,
Destiny stage 3 and five-lane Tower stage 9. Input must be an XML snapshot created
by a test run, never a personal save directory. Output must be a new directory.

After building the adapter:

```bash
PALADOG_CACHE=/path/to/cache bash linux/tests/gameplay/prepare-fixtures.sh \
  /path/to/synthetic-xml-snapshot /path/to/new-fixtures
```

Run with `--data-dir /path/to/new-fixtures/stage-3` (or `stage-1`, `stage-9`).
Choose Paladog, campaign, slot 1, then the corresponding unlocked stage. The
fixtures expose all nine level-one units and mark these tutorials complete.
They keep the original equipment, skills, gold and food/mana rules. Wait for
food and summon cooldowns to permit the action being checked. An optional
`--food-skills` argument grants food-regeneration skill level 3 and food-storage
skill level 2 in the test copies (120 food capacity). Food remains finite,
summons still deduct their original costs, and cooldowns remain unchanged.

The utility uses the original game's `XMLToData` and `DataToXML` to preserve its
encrypted format and checksums. It validates each result through the original
reader and checks that every source XML remains byte-for-byte unchanged. It
does not copy the old battle XML, so the game opens the stage selector rather
than resuming an unrelated battle. The game and production save paths are not
modified. `fixture-report.txt` records the resulting slot hashes.

The fixture tool is a development utility; it is not part of the runtime JAR
or the normal automated test suite. Do not distribute generated test saves as
user progress.
