package android.content.res;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Reads unchanged APK assets from an extracted root, or packaged classpath. */
public class AssetManager implements Closeable {
    public static final int ACCESS_UNKNOWN = 0, ACCESS_RANDOM = 1, ACCESS_STREAMING = 2, ACCESS_BUFFER = 3;
    private final Path root;
    public AssetManager() {
        String location = System.getProperty("paladog.assets");
        root = location == null || location.isBlank() ? null : Path.of(location).toAbsolutePath().normalize();
    }
    public AssetManager(Path extractedApkRoot) { root = extractedApkRoot.toAbsolutePath().normalize(); }
    public Path getRoot() { return root; }
    public InputStream open(String name) throws IOException { return open(name, ACCESS_STREAMING); }
    public InputStream open(String name, int accessMode) throws IOException { return openPackageFile("assets/" + checked(name)); }
    public InputStream openPackageFile(String name) throws IOException {
        String relative = checked(name);
        if (root != null) { return Files.newInputStream(resolve(relative)); }
        InputStream input = AssetManager.class.getClassLoader().getResourceAsStream(relative);
        if (input == null) { throw new FileNotFoundException("Missing game asset: " + relative); }
        return input;
    }
    public String[] list(String directory) throws IOException {
        if (root == null) { throw new IOException("Listing assets requires an extracted APK asset root"); }
        Path path = resolve("assets/" + checked(directory.isEmpty() ? "." : directory));
        if (!Files.isDirectory(path)) { return new String[0]; }
        try (Stream<Path> items = Files.list(path)) {
            return items.map(item -> item.getFileName().toString()).sorted().toArray(String[]::new);
        }
    }
    public AssetFileDescriptor openFd(String name) throws IOException { return openPackageFd("assets/" + checked(name)); }
    public AssetFileDescriptor openPackageFd(String name) throws IOException {
        if (root == null) { throw new IOException("File descriptors require an extracted APK asset root"); }
        return new AssetFileDescriptor(resolve(name));
    }
    public Path resolve(String name) throws IOException {
        if (root == null) { throw new IOException("No extracted APK asset root configured"); }
        Path path = root.resolve(checked(name)).normalize();
        if (!path.startsWith(root)) { throw new IOException("Asset path escapes the package"); }
        return path;
    }
    private static String checked(String name) throws IOException {
        if (name == null || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IOException("Invalid package asset path");
        }
        for (String component : name.split("/")) {
            if (component.equals("..")) { throw new IOException("Asset path escapes the package"); }
        }
        return name;
    }
    @Override public void close() { }
}
