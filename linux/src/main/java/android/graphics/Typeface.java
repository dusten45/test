package android.graphics;

import android.content.res.AssetManager;
import java.awt.Font;
import java.io.File;
import java.io.InputStream;

public final class Typeface {
    public static final int NORMAL=0, BOLD=1, ITALIC=2, BOLD_ITALIC=3;
    private static Font defaultFont;
    public static final Typeface DEFAULT = new Typeface(loadDefault());
    public static final Typeface SANS_SERIF = DEFAULT;
    public static final Typeface SERIF = new Typeface(new Font(Font.SERIF, Font.PLAIN, 12));
    public static final Typeface MONOSPACE = new Typeface(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    private final Font font;
    private Typeface(Font font) { this.font = font; }
    private static synchronized Font loadDefault() {
        if (defaultFont != null) return defaultFont;
        String path = System.getProperty("paladog.font");
        try {
            defaultFont = path == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : Font.createFont(Font.TRUETYPE_FONT, new File(path));
        } catch (Exception e) { throw new IllegalStateException("Cannot load configured game font: " + path, e); }
        return defaultFont;
    }
    public static Typeface create(String name, int style) {
        Font font = name == null || name.equals("DroidSans") || name.equals("sans-serif") ? loadDefault() : new Font(name, style, 12);
        return new Typeface(font.deriveFont(style));
    }
    public static Typeface create(Typeface family, int style) { return new Typeface((family == null ? loadDefault() : family.font).deriveFont(style)); }
    public static Typeface createFromAsset(AssetManager assets, String path) {
        try (InputStream stream = assets.open(path)) { return new Typeface(Font.createFont(Font.TRUETYPE_FONT, stream)); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot load font asset: " + path, e); }
    }
    Font font(float size) { return font.deriveFont(size); }
}
