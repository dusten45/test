import android.content.Context;
import android.content.FileSharedPreferences;
import android.content.SharedPreferences;
import com.common.AppDelegate;
import com.common.Library;
import com.my.Struct.ANDROIDSAVEINFO;
import com.my.Struct.GAMESAVESLOTINFO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Creates isolated keyboard-test fixtures from a synthetic save snapshot.
 * Never point this tool at personal saves. Existing destinations are refused.
 * Saves are encoded and checksummed by the original game's own API.
 */
public final class PrepareControlFixtures {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--food-skills"))) {
            throw new IllegalArgumentException(
                    "Usage: PrepareControlFixtures SYNTHETIC_XML_SNAPSHOT NEW_FIXTURE_ROOT [--food-skills]");
        }
        boolean foodSkills = args.length == 3;
        Locale.setDefault(Locale.US);
        Path source = Path.of(args[0]).toRealPath();
        Path target = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.exists(target) || target.startsWith(source) || source.startsWith(target)) {
            throw new IllegalArgumentException("Use a new output directory separate from the snapshot");
        }
        Map<String, byte[]> originals = new LinkedHashMap<>();
        try (var paths = Files.list(source)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("PDWXML(?:Info|Slot[0-7]|Battle)\\.xml")).sorted().toList()) {
                originals.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
        }
        if (!originals.containsKey("PDWXMLSlot0.xml") || !originals.containsKey("PDWXMLInfo.xml")) {
            throw new IllegalArgumentException("Expected a synthetic snapshot containing slot 0 and info XML");
        }
        Files.createDirectories(target);
        System.setProperty("paladog.data", target.resolve("unused-context").toString());
        AppDelegate app = AppDelegate.sharedAppDelegate();
        app.m_lib = new Library(new Context());
        app.g_GISavedAndroid = new ANDROIDSAVEINFO();
        app.g_GISavedAndroid.strUDID = ""; // Same local identity as the offline APK.
        StringBuilder report = new StringBuilder("Synthetic keyboard control fixtures\n");
        for (int stage : new int[] {1, 3, 9}) {
            Path save = target.resolve("stage-" + stage).resolve("shared_prefs");
            Files.createDirectories(save);
            for (var original : originals.entrySet()) {
                // Start at the stage selector, never resume the old stage-1 battle.
                if (!original.getKey().equals("PDWXMLBattle.xml")) {
                    Files.write(save.resolve(original.getKey()), original.getValue());
                }
            }
            SharedPreferences prefs = new FileSharedPreferences(save.resolve("PDWXMLSlot0.xml"));
            GAMESAVESLOTINFO slot = new GAMESAVESLOTINFO();
            slot.XMLToData(prefs);
            if (slot.bHacked || slot.iVersion != 107) throw new AssertionError("Source save failed game validation");
            // Expose only the stages needed for this fixture; unlocked, not won.
            Arrays.fill(slot.iStageStar, -1);
            slot.iStageStar[0] = 0;
            slot.iStageStar[stage - 1] = 0;
            // All nine normal units, at level one, allow visible-order key checks.
            Arrays.fill(slot.iUnitLevel, 0);
            Arrays.fill(slot.iUnitLevel, 0, 9, 1);
            slot.iTutorialState[1] = 13;
            slot.iTutorialState[2] = 7;
            slot.iTutorialState[4] = 9;
            // Optional finite food capacity/regeneration to test expensive units.
            // Original summon costs, resource deduction and cooldowns still apply.
            if (foodSkills) {
                slot.iSkillLevelCur[11] = 3;
                slot.iSkillLevelCur[12] = 2; // Base 40 + 2 * 40 = 120 capacity.
            }
            // Keep the original gold, equipment, other skills and mana rules.
            SharedPreferences.Editor edit = prefs.edit();
            slot.DataToXML(edit);
            if (!edit.commit()) throw new AssertionError("Fixture write failed");
            GAMESAVESLOTINFO checked = new GAMESAVESLOTINFO();
            checked.XMLToData(new FileSharedPreferences(save.resolve("PDWXMLSlot0.xml")));
            if (checked.bHacked || checked.iStageStar[stage - 1] != 0
                    || !Arrays.equals(slot.iUnitLevel, checked.iUnitLevel)) {
                throw new AssertionError("Fixture failed original-game readback");
            }
            report.append("stage=").append(stage).append(" mode=")
                    .append(stage == 1 ? "normal" : stage == 3 ? "destiny" : "tower")
                    .append(" units=").append(Arrays.toString(Arrays.copyOf(checked.iUnitLevel, 9)))
                    .append(" gold=").append(checked.iCurGold)
                    .append(" foodSkills=").append(checked.iSkillLevelCur[11]).append(',').append(checked.iSkillLevelCur[12])
                    .append(" slotSha256=").append(sha256(Files.readAllBytes(save.resolve("PDWXMLSlot0.xml"))))
                    .append(" checksumValidated=true\n");
        }
        for (var original : originals.entrySet()) {
            if (!Arrays.equals(original.getValue(), Files.readAllBytes(source.resolve(original.getKey())))) {
                throw new AssertionError("Original snapshot changed: " + original.getKey());
            }
            report.append("unchangedSource ").append(original.getKey()).append(' ')
                    .append(sha256(original.getValue())).append('\n');
        }
        Files.writeString(target.resolve("fixture-report.txt"), report);
        System.out.print(report);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
