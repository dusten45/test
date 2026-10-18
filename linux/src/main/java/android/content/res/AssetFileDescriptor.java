package android.content.res;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AssetFileDescriptor implements Closeable {
    public static final long UNKNOWN_LENGTH = -1;
    private final Path path;
    private final FileInputStream descriptorStream;
    public AssetFileDescriptor(Path path) throws IOException {
        this.path = path;
        descriptorStream = new FileInputStream(path.toFile());
    }
    public FileDescriptor getFileDescriptor() {
        try { return descriptorStream.getFD(); }
        catch (IOException error) { throw new IllegalStateException("Asset descriptor is closed", error); }
    }
    public long getStartOffset() { return 0; }
    public long getLength() {
        try { return Files.size(path); }
        catch (IOException error) { return UNKNOWN_LENGTH; }
    }
    public long getDeclaredLength() { return getLength(); }
    public FileInputStream createInputStream() throws IOException { return new FileInputStream(path.toFile()); }
    @Override public void close() throws IOException { descriptorStream.close(); }
}
