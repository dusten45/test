package android.content.res;

import android.util.DisplayMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Resources {
    private final AssetManager assets;
    private final DisplayMetrics metrics;
    private final Configuration configuration;
    private final Map<Integer, String> names = new HashMap<>();
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<Integer, String> resolvedPaths = new HashMap<>();
    private static final Map<String, String> STRINGS = Map.of(
            "app_name", "Paladog", "hello", "Hello World, Paladog!",
            "story_title", "카카오 스토리", "story_upload", "올리기", "story_uploading", "올리는 중..",
            "story_failed_to_upload", "올리기 실패", "story_tap_to_write_a_post", "글을 쓰려면 터치하세요",
            "story_permission_public", "전체공개로 변경했습니다.",
            "story_permission_friends", "친구공개로 변경했습니다.\n나의 스토리 친구들에게만 공개됩니다.",
            "story_dont_have_permission", "해당 요청에 대한 권한이 없습니다");
    public Resources(AssetManager assets) { this(assets, new DisplayMetrics(), new Configuration()); }
    public Resources(AssetManager assets, DisplayMetrics metrics, Configuration configuration) {
        this.assets = assets; this.metrics = metrics; this.configuration = configuration;
        for (String type : new String[]{"raw", "string", "drawable", "layout", "id"}) {
            try {
                Class<?> table = Class.forName("com.Paladog.KorGG.R$" + type, false, Resources.class.getClassLoader());
                for (Field field : table.getFields()) {
                    if (field.getType() == int.class) {
                        int id = field.getInt(null);
                        names.put(id, type + "/" + field.getName());
                        ids.put(type + "/" + field.getName(), id);
                    }
                }
            } catch (ReflectiveOperationException absent) { /* resource classes are optional in unit tests */ }
        }
    }
    public AssetManager getAssets() { return assets; }
    public DisplayMetrics getDisplayMetrics() { return metrics; }
    public Configuration getConfiguration() { return configuration; }
    public void updateConfiguration(Configuration config, DisplayMetrics display) {
        if (config != null) { configuration.setTo(config); }
        if (display != null) { metrics.setTo(display); }
    }
    public InputStream openRawResource(int id) throws NotFoundException {
        try { return assets.openPackageFile(resourcePath(id)); }
        catch (IOException error) { throw new NotFoundException("Cannot open resource " + Integer.toHexString(id), error); }
    }
    public AssetFileDescriptor openRawResourceFd(int id) throws NotFoundException {
        try { return assets.openPackageFd(resourcePath(id)); }
        catch (IOException error) { throw new NotFoundException("Cannot open resource " + Integer.toHexString(id), error); }
    }
    public String getString(int id) {
        String value = STRINGS.get(getResourceEntryName(id));
        if (value == null) { throw new NotFoundException("Unknown string resource " + Integer.toHexString(id)); }
        return value;
    }
    public String getString(int id, Object... arguments) { return String.format(configuration.locale, getString(id), arguments); }
    public CharSequence getText(int id) { return getString(id); }
    public int getIdentifier(String name, String type, String packageName) {
        int colon = name.indexOf(':');
        if (colon >= 0) { name = name.substring(colon + 1); }
        return ids.getOrDefault(name.contains("/") ? name : type + "/" + name, 0);
    }
    public String getResourceName(int id) { return "com.Paladog.KorGG:" + name(id); }
    public String getResourceEntryName(int id) { String name = name(id); return name.substring(name.indexOf('/') + 1); }
    public String getResourceTypeName(int id) { String name = name(id); return name.substring(0, name.indexOf('/')); }
    private String name(int id) {
        String name = names.get(id);
        if (name == null) { throw new NotFoundException("Unknown resource " + Integer.toHexString(id)); }
        return name;
    }
    private synchronized String resourcePath(int id) throws IOException {
        String cached = resolvedPaths.get(id);
        if (cached != null) { return cached; }
        String name = name(id);
        String path = "res/" + name;
        String prefix = path.substring(path.lastIndexOf('/') + 1);
        if (assets.getRoot() != null) {
            Path directory = assets.resolve(path.substring(0, path.lastIndexOf('/')));
            try (Stream<Path> files = Files.list(directory)) {
                Path found = files.filter(file -> file.getFileName().toString().equals(prefix)
                        || file.getFileName().toString().startsWith(prefix + "."))
                        .findFirst().orElseThrow(() -> new IOException("Missing packaged resource " + name));
                path = assets.getRoot().relativize(found).toString().replace('\\', '/');
            }
        } else {
            String found = null;
            for (String extension : new String[]{"", ".mp3", ".ogg", ".wav", ".png", ".xml"}) {
                try (InputStream candidate = Resources.class.getClassLoader().getResourceAsStream(path + extension)) {
                    if (candidate != null) { found = path + extension; break; }
                }
            }
            if (found == null) { throw new IOException("Missing packaged resource " + name); }
            path = found;
        }
        resolvedPaths.put(id, path);
        return path;
    }
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
        public NotFoundException(String message, Throwable cause) { super(message, cause); }
    }
}
