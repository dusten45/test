package android.content;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real disk persistence and interrupted/corrupt save handling, no user saves. */
public final class StorageTest {
    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("paladog-storage-test-");
        try {
            Path file = directory.resolve("PDWXMLInfo.xml");
            FileSharedPreferences preferences = new FileSharedPreferences(file);
            check(preferences.getInt("absent", 41) == 41, "typed defaults");
            check(preferences.getString("absent", null) == null, "null string default");
            String text = "한글 <tag> & \"quotes\"\nline";
            check(preferences.edit().putInt("int", Integer.MIN_VALUE).putLong("long", Long.MAX_VALUE)
                    .putFloat("float", -0.0f).putBoolean("boolean", true).putString("string", text)
                    .putStringSet("set", Set.of("a", "가&나")).commit(), "first commit");
            FileSharedPreferences reopened = new FileSharedPreferences(file);
            check(reopened.getInt("int", 0) == Integer.MIN_VALUE, "integer disk roundtrip");
            check(reopened.getLong("long", 0) == Long.MAX_VALUE, "long disk roundtrip");
            check(Float.floatToRawIntBits(reopened.getFloat("float", 1)) == Float.floatToRawIntBits(-0.0f), "float exact roundtrip");
            check(reopened.getBoolean("boolean", false), "boolean disk roundtrip");
            check(text.equals(reopened.getString("string", "")), "unicode XML escaping roundtrip");
            check(reopened.getStringSet("set", null).equals(Set.of("a", "가&나")), "set roundtrip");
            reopened.getStringSet("set", null).clear();
            check(reopened.getStringSet("set", null).size() == 2, "defensive set copy");
            try { reopened.getLong("int", 0); throw new AssertionError("type mismatch must throw"); }
            catch (ClassCastException expected) { }
            check(reopened.edit().remove("int").putString("string", null).commit(), "remove commit");
            check(!reopened.contains("int") && !reopened.contains("string"), "null removes a key");
            check(reopened.edit().clear().putInt("revision", 0).commit(), "clear before updates");
            check(reopened.getAll().size() == 1, "clear semantics");

            // Readers must only observe complete XML while the writer replaces files.
            AtomicReference<Throwable> readerFailure = new AtomicReference<>();
            Thread reader = new Thread(() -> {
                try {
                    for (int i = 0; i < 150; i++) {
                        int revision = new FileSharedPreferences(file).getInt("revision", -1);
                        if (revision < 0 || revision > 40) { throw new AssertionError("incomplete atomic snapshot"); }
                    }
                } catch (Throwable error) { readerFailure.set(error); }
            });
            reader.start();
            for (int revision = 1; revision <= 40; revision++) {
                check(reopened.edit().putInt("revision", revision).commit(), "atomic commit");
            }
            reader.join();
            if (readerFailure.get() != null) { throw new AssertionError("concurrent reader", readerFailure.get()); }
            byte[] previous = Files.readAllBytes(file);
            Path backup = file.resolveSibling("PDWXMLInfo.xml.bak");
            check(new FileSharedPreferences(backup).getInt("revision", -1) == 39, "last good backup");

            // A failed backup update must not truncate the good destination or change memory.
            Files.delete(backup);
            Files.createDirectory(backup);
            Files.writeString(backup.resolve("blocker"), "keep");
            check(!reopened.edit().putInt("revision", 41).commit(), "disk failure reported");
            check(Arrays.equals(previous, Files.readAllBytes(file)), "disk failure preserves original bytes");
            check(reopened.getInt("revision", -1) == 40, "disk failure preserves committed memory");
            Files.delete(backup.resolve("blocker")); Files.delete(backup);
            check(reopened.edit().putInt("revision", 41).commit(), "commit after failure");

            byte[] corrupt = "<map><int name=\"broken\" value=\"".getBytes(StandardCharsets.UTF_8);
            Files.write(file, corrupt);
            FileSharedPreferences recovered = new FileSharedPreferences(file);
            check(recovered.getInt("revision", -1) == 40, "recover previous complete snapshot");
            check(Arrays.equals(corrupt, Files.readAllBytes(file)), "recovery does not erase corrupt input");
            try (var items = Files.list(directory)) {
                check(items.anyMatch(item -> item.getFileName().toString().startsWith("PDWXMLInfo.xml.corrupt-")), "corrupt evidence copy");
            }
            check(recovered.edit().putInt("revision", 42).commit(), "save after recovery");
            check(new FileSharedPreferences(backup).getInt("revision", -1) == 40, "recovery retains valid backup");
            Path noBackup = directory.resolve("broken.xml");
            Files.write(noBackup, corrupt);
            try { new FileSharedPreferences(noBackup); throw new AssertionError("unrecoverable save must stop"); }
            catch (IllegalStateException expected) { }
            check(Arrays.equals(corrupt, Files.readAllBytes(noBackup)), "unrecoverable bytes untouched");

            Path entity = directory.resolve("entity.xml");
            Files.writeString(entity, "<!DOCTYPE map [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><map><string name='x'>&x;</string></map>");
            try { new FileSharedPreferences(entity); throw new AssertionError("external entity must fail"); }
            catch (IllegalStateException expected) { }
            System.out.println("PASS: typed XML, atomic readers, backup, failed commit, corrupt preservation, external entities");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
        }
    }
}
