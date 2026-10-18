package android.content;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Local desktop implementation of the small Context surface used by Paladog. */
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_APPEND = 32768;
    public static final int BIND_AUTO_CREATE = 1;
    public static final String SENSOR_SERVICE = "sensor";
    public static final String WINDOW_SERVICE = "window";
    public static final String INPUT_METHOD_SERVICE = "input_method";
    public static final String AUDIO_SERVICE = "audio";
    public static final String POWER_SERVICE = "power";
    private static final Map<Path, SharedPreferences> PREFERENCES = new ConcurrentHashMap<>();
    private static final Map<String, Object> SERVICES = new ConcurrentHashMap<>();
    private final Path dataDirectory;
    private final Resources resources;

    public Context() {
        dataDirectory = dataPath();
        resources = new Resources(new AssetManager());
    }

    public static Path dataPath() {
        String override = System.getProperty("paladog.data");
        if (override != null && !override.isBlank()) { return Path.of(override).toAbsolutePath().normalize(); }
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute()) {
            return Path.of(xdg).resolve("paladog").normalize();
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "paladog").toAbsolutePath().normalize();
    }

    public static void registerSystemService(String name, Object service) {
        if (service == null) { SERVICES.remove(name); }
        else { SERVICES.put(name, service); }
    }
    public Object getSystemService(String name) { return SERVICES.get(name); }
    public AssetManager getAssets() { return resources.getAssets(); }
    public Resources getResources() { return resources; }
    public String getPackageName() { return "com.Paladog.KorGG"; }
    public Context getApplicationContext() { return this; }
    public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
    public String getString(int resource) { return resources.getString(resource); }
    public String getString(int resource, Object... arguments) { return resources.getString(resource, arguments); }
    public CharSequence getText(int resource) { return resources.getText(resource); }
    public PackageManager getPackageManager() { return new PackageManager(); }
    public ApplicationInfo getApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = getPackageName();
        info.dataDir = dataDirectory.toString();
        return info;
    }
    public File getFilesDir() { return ensureDirectory(dataDirectory.resolve("files")).toFile(); }
    public File getCacheDir() { return ensureDirectory(dataDirectory.resolve("cache")).toFile(); }
    public File getDir(String name, int mode) { return ensureDirectory(dataDirectory.resolve("app_" + checkedName(name))).toFile(); }
    public File getFileStreamPath(String name) { return getFilesDir().toPath().resolve(checkedName(name)).toFile(); }
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return new FileInputStream(getFileStreamPath(name));
    }
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return new FileOutputStream(getFileStreamPath(name), (mode & MODE_APPEND) != 0);
    }
    public boolean deleteFile(String name) { return getFileStreamPath(name).delete(); }
    public String[] fileList() {
        String[] names = getFilesDir().list();
        return names == null ? new String[0] : names;
    }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        Path path = ensureDirectory(dataDirectory.resolve("shared_prefs")).resolve(checkedName(name) + ".xml");
        return PREFERENCES.computeIfAbsent(path, FileSharedPreferences::new);
    }

    public void startActivity(Intent intent) {
        throw new UnsupportedOperationException("Activity launch must be handled by the desktop activity host");
    }
    public boolean bindService(Intent intent, ServiceConnection connection, int flags) { return false; }
    public void unbindService(ServiceConnection connection) { }
    public ComponentName startService(Intent intent) { return null; }
    public boolean stopService(Intent intent) { return false; }
    public void sendBroadcast(Intent intent) { }
    public int checkCallingOrSelfPermission(String permission) { return PackageManager.PERMISSION_DENIED; }
    public int checkSelfPermission(String permission) { return PackageManager.PERMISSION_DENIED; }

    private static Path ensureDirectory(Path path) {
        try { return Files.createDirectories(path); }
        catch (IOException error) { throw new IllegalStateException("Cannot create game data directory: " + path, error); }
    }
    private static String checkedName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Expected a local filename");
        }
        return name;
    }
}
