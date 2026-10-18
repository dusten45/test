package android.content;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.cocos2d.utils.PlistParser;

public final class OfflinePlistTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger connections = new AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol -> "http".equals(protocol) || "https".equals(protocol)
                ? new URLStreamHandler() {
                    @Override protected URLConnection openConnection(URL url) throws IOException {
                        connections.incrementAndGet();
                        throw new IOException("Test forbids any network access");
                    }
                } : null);
        String xml = "<?xml version='1.0'?><!DOCTYPE plist PUBLIC '-//Apple//DTD PLIST 1.0//EN' "
                + "'http://www.apple.com/DTDs/PropertyList-1.0.dtd'><plist version='1.0'><dict>"
                + "<key>frames</key><dict><key>sprite.png</key><dict><key>rotated</key><false/>"
                + "<key>frame</key><string>{{1,2},{3,4}}</string></dict></dict>"
                + "<key>number</key><integer>42</integer><key>real</key><real>1.25</real>"
                + "<key>array</key><array><string>한글</string><true/></array></dict></plist>";
        HashMap<String, Object> parsed = PlistParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        if (!Integer.valueOf(42).equals(parsed.get("number")) || !Double.valueOf(1.25).equals(parsed.get("real"))) {
            throw new AssertionError("Plist numeric semantics");
        }
        ArrayList<?> array = (ArrayList<?>) parsed.get("array");
        if (!array.equals(java.util.List.of("한글", Boolean.TRUE))) { throw new AssertionError("Unicode array"); }
        HashMap<?, ?> frame = (HashMap<?, ?>) ((HashMap<?, ?>) parsed.get("frames")).get("sprite.png");
        if (!"{{1,2},{3,4}}".equals(frame.get("frame")) || !Boolean.FALSE.equals(frame.get("rotated"))) {
            throw new AssertionError("Nested atlas dictionary semantics");
        }
        Path assets = Path.of(System.getProperty("paladog.assets"), "assets");
        int count = 0;
        try (var paths = Files.walk(assets)) {
            for (Path plist : paths.filter(path -> path.toString().endsWith(".plist")).toList()) {
                try (var input = Files.newInputStream(plist)) {
                    if (PlistParser.parse(input) == null) { throw new AssertionError("Empty packaged plist"); }
                    count++;
                }
            }
        }
        if (count == 0) { throw new AssertionError("No real game plist files tested"); }
        if (connections.get() != 0) { throw new AssertionError("Parser attempted external DTD access"); }
        System.out.println("PASS: nested typed plist, Apple DOCTYPE, " + count + " real game plists, zero HTTP connections");
    }
}
