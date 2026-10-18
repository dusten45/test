package android.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** Android map XML, with durable atomic replacement and last-good-file recovery. */
public final class FileSharedPreferences implements SharedPreferences {
    private final Path file;
    private final Path backup;
    private Map<String, Object> values;
    private boolean recoveredFromBackup;
    private final Set<OnSharedPreferenceChangeListener> listeners = new LinkedHashSet<>();

    public FileSharedPreferences(Path file) {
        this.file = file.toAbsolutePath().normalize();
        this.backup = this.file.resolveSibling(this.file.getFileName() + ".bak");
        try {
            if (Files.exists(this.file)) {
                try {
                    values = read(this.file);
                    return;
                } catch (IOException invalid) {
                    if (!Files.isRegularFile(backup)) {
                        throw invalid;
                    }
                    values = read(backup);
                    // Preserve the damaged bytes before any future game save can replace them.
                    Files.copy(this.file, this.file.resolveSibling(this.file.getFileName()
                            + ".corrupt-" + UUID.randomUUID()));
                    recoveredFromBackup = true;
                    System.err.println("[save] Recovered a damaged preference file from its last-good backup.");
                    return;
                }
            }
            if (Files.isRegularFile(backup)) {
                values = read(backup);
                recoveredFromBackup = true;
            } else {
                values = new LinkedHashMap<>();
            }
        } catch (IOException invalid) {
            // Do not silently replace an unreadable save with an empty game.
            throw new IllegalStateException("Cannot read saved game preferences: " + this.file
                    + ". Original files were preserved; restore a valid backup before playing.", invalid);
        }
    }

    @Override public synchronized Map<String, ?> getAll() { return copy(values); }
    @Override public synchronized String getString(String key, String defValue) {
        return values.containsKey(key) ? (String) values.get(key) : defValue;
    }
    @Override @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        Set<String> result = values.containsKey(key) ? (Set<String>) values.get(key) : defValues;
        return result == null ? null : new LinkedHashSet<>(result);
    }
    @Override public synchronized int getInt(String key, int defValue) {
        return values.containsKey(key) ? (Integer) values.get(key) : defValue;
    }
    @Override public synchronized long getLong(String key, long defValue) {
        return values.containsKey(key) ? (Long) values.get(key) : defValue;
    }
    @Override public synchronized float getFloat(String key, float defValue) {
        return values.containsKey(key) ? (Float) values.get(key) : defValue;
    }
    @Override public synchronized boolean getBoolean(String key, boolean defValue) {
        return values.containsKey(key) ? (Boolean) values.get(key) : defValue;
    }
    @Override public synchronized boolean contains(String key) { return values.containsKey(key); }
    @Override public Editor edit() { return new FileEditor(); }
    @Override public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }
    @Override public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key,
                value instanceof Set<?> ? new LinkedHashSet<>((Set<?>) value) : value));
        return result;
    }

    private final class FileEditor implements Editor {
        private final Map<String, Object> changes = new LinkedHashMap<>();
        private boolean clear;
        @Override public Editor putString(String key, String value) { return put(key, value); }
        @Override public Editor putStringSet(String key, Set<String> value) {
            return put(key, value == null ? null : new LinkedHashSet<>(value));
        }
        @Override public Editor putInt(String key, int value) { return put(key, value); }
        @Override public Editor putLong(String key, long value) { return put(key, value); }
        @Override public Editor putFloat(String key, float value) { return put(key, value); }
        @Override public Editor putBoolean(String key, boolean value) { return put(key, value); }
        private synchronized Editor put(String key, Object value) {
            changes.put(Objects.requireNonNull(key), value);
            return this;
        }
        @Override public Editor remove(String key) { return put(key, null); }
        @Override public synchronized Editor clear() { clear = true; return this; }
        @Override public boolean commit() {
            List<String> changed;
            List<OnSharedPreferenceChangeListener> notify;
            synchronized (FileSharedPreferences.this) {
                Map<String, Object> next = copy(values);
                synchronized (this) {
                    if (clear) { next.clear(); }
                    changes.forEach((key, value) -> {
                        if (value == null) { next.remove(key); }
                        else { next.put(key, value); }
                    });
                }
                changed = new ArrayList<>();
                Set<String> keys = new LinkedHashSet<>(values.keySet());
                keys.addAll(next.keySet());
                for (String key : keys) {
                    if (!Objects.equals(values.get(key), next.get(key))) { changed.add(key); }
                }
                try {
                    writeAtomically(next);
                } catch (IOException failure) {
                    System.err.println("[save] Cannot commit preferences; the previous save is preserved ("
                            + failure.getClass().getSimpleName() + ").");
                    return false;
                }
                values = copy(next);
                recoveredFromBackup = false;
                notify = new ArrayList<>(listeners);
            }
            for (String key : changed) {
                for (OnSharedPreferenceChangeListener listener : notify) {
                    listener.onSharedPreferenceChanged(FileSharedPreferences.this, key);
                }
            }
            return true;
        }
        @Override public void apply() {
            // This desktop adapter deliberately completes saves before returning/exit.
            if (!commit()) { throw new IllegalStateException("Saving preferences failed; previous save preserved."); }
        }
    }

    private void writeAtomically(Map<String, Object> next) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
        Path backupTemporary = null;
        try {
            write(temporary, next);
            forceFile(temporary);
            if (Files.isRegularFile(file) && !recoveredFromBackup) {
                backupTemporary = Files.createTempFile(file.getParent(), file.getFileName() + ".backup.", ".tmp");
                Files.copy(file, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                forceFile(backupTemporary);
                Files.move(backupTemporary, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                backupTemporary = null;
            }
            // Fail rather than truncate the destination on a filesystem without atomic moves.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            } catch (IOException unsupportedDirectorySync) {
                // The rename has completed. Some Linux filesystems do not permit directory fsync.
            }
        } finally {
            Files.deleteIfExists(temporary);
            if (backupTemporary != null) { Files.deleteIfExists(backupTemporary); }
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static Map<String, Object> read(Path file) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setXMLResolver((publicId, systemId, baseURI, namespace) -> {
            throw new javax.xml.stream.XMLStreamException("External entities are disabled");
        });
        try (InputStream input = Files.newInputStream(file)) {
            XMLStreamReader xml = factory.createXMLStreamReader(input, "UTF-8");
            Map<String, Object> result = new LinkedHashMap<>();
            if (xml.nextTag() != XMLStreamConstants.START_ELEMENT || !"map".equals(xml.getLocalName())) {
                throw new IOException("Expected preference map");
            }
            while (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
                String type = xml.getLocalName();
                String key = xml.getAttributeValue(null, "name");
                if (key == null || result.containsKey(key)) { throw new IOException("Invalid preference key"); }
                String raw = xml.getAttributeValue(null, "value");
                Object value;
                switch (type) {
                    case "string": value = xml.getElementText(); break;
                    case "set": {
                        Set<String> strings = new LinkedHashSet<>();
                        while (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
                            if (!"string".equals(xml.getLocalName())) { throw new IOException("Invalid string set"); }
                            strings.add(xml.getElementText());
                        }
                        value = strings;
                        break;
                    }
                    case "int": value = Integer.valueOf(raw); xml.getElementText(); break;
                    case "long": value = Long.valueOf(raw); xml.getElementText(); break;
                    case "float": value = Float.valueOf(raw); xml.getElementText(); break;
                    case "boolean":
                        if (!"true".equals(raw) && !"false".equals(raw)) { throw new IOException("Invalid boolean"); }
                        value = Boolean.valueOf(raw); xml.getElementText(); break;
                    default: throw new IOException("Unsupported preference value type");
                }
                result.put(key, value);
            }
            if (!"map".equals(xml.getLocalName())) { throw new IOException("Unclosed preference map"); }
            while (xml.hasNext()) {
                if (xml.next() == XMLStreamConstants.START_ELEMENT) { throw new IOException("Unexpected trailing XML"); }
            }
            xml.close();
            return result;
        } catch (Exception invalid) {
            if (invalid instanceof IOException) { throw (IOException) invalid; }
            // Parser exceptions can include raw save text. Keep them out of user logs/stack traces.
            throw new IOException("Invalid preference XML (" + invalid.getClass().getSimpleName() + ")");
        }
    }

    private static void write(Path file, Map<String, Object> values) throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("map");
            List<String> keys = new ArrayList<>(values.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                Object value = values.get(key);
                String type = value instanceof String ? "string" : value instanceof Integer ? "int"
                        : value instanceof Long ? "long" : value instanceof Float ? "float"
                        : value instanceof Boolean ? "boolean" : value instanceof Set<?> ? "set" : null;
                if (type == null) { throw new IOException("Unsupported preference value type"); }
                xml.writeStartElement(type);
                xml.writeAttribute("name", key);
                if (value instanceof String) { xml.writeCharacters((String) value); }
                else if (value instanceof Set<?>) {
                    for (Object item : (Set<?>) value) {
                        xml.writeStartElement("string");
                        xml.writeCharacters((String) item);
                        xml.writeEndElement();
                    }
                } else { xml.writeAttribute("value", value.toString()); }
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        } catch (Exception failure) {
            if (failure instanceof IOException) { throw (IOException) failure; }
            throw new IOException("Cannot encode preference XML (" + failure.getClass().getSimpleName() + ")");
        }
    }
}
