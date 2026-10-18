package android.content;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Uses actual extracted game resources but writes only to a temporary save directory. */
public final class ResourcesTest {
    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
    public static void main(String[] args) throws Exception {
        if (System.getProperty("paladog.assets") == null) { throw new IllegalArgumentException("Set -Dpaladog.assets to the extracted APK root"); }
        Path directory = Files.createTempDirectory("paladog-resources-test-");
        System.setProperty("paladog.data", directory.toString());
        try {
            Context context = new Context();
            Resources resources = context.getResources();
            int title = resources.getIdentifier("bg_title", "raw", "com.Paladog.KorGG");
            check(title != 0, "R$raw integer lookup");
            byte[] audio;
            try (var input = resources.openRawResource(title)) { audio = input.readAllBytes(); }
            check(audio.length > 1000, "packaged audio bytes");
            try (AssetFileDescriptor descriptor = resources.openRawResourceFd(title);
                 var input = descriptor.createInputStream()) {
                check(descriptor.getStartOffset() == 0 && descriptor.getLength() == audio.length, "raw resource descriptor length");
                check(Arrays.equals(audio, input.readAllBytes()), "resource stream and descriptor match");
                check(descriptor.getFileDescriptor().valid(), "real resource file descriptor");
            }
            check("Paladog".equals(resources.getString(resources.getIdentifier("app_name", "string", context.getPackageName()))), "string resource mapping");
            String[] names = context.getAssets().list("");
            check(names.length > 0, "asset directory listing");
            try { context.getAssets().open("../../etc/passwd"); throw new AssertionError("path traversal rejected"); }
            catch (IOException expected) { }
            try { context.openFileOutput("../outside", 0); throw new AssertionError("save filename traversal rejected"); }
            catch (IllegalArgumentException expected) { }
            try (var output = context.openFileOutput("legacy.sav", 0)) { output.write(new byte[]{1, 2, 3}); }
            try (var input = context.openFileInput("legacy.sav")) { check(Arrays.equals(input.readAllBytes(), new byte[]{1, 2, 3}), "legacy file streams"); }
            SharedPreferences preferences = context.getSharedPreferences("PDWXMLInfo", 0);
            check(preferences.edit().putInt("revision", 10).commit(), "context preference commit");
            check(Files.isRegularFile(directory.resolve("shared_prefs/PDWXMLInfo.xml")), "Android-compatible XML filename");
            check(new Context().getSharedPreferences("PDWXMLInfo", 0) == preferences, "same-process cache shares context saves");
            System.out.println("PASS: actual APK raw ID/stream/descriptor/string/assets, local paths, legacy files, preference cache");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
        }
    }
}
