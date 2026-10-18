package android.graphics;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    private Typeface typeface = Typeface.DEFAULT;
    private float textSize = 12;
    private boolean antialias;
    private int color = 0xff000000;
    public Paint() {}
    public Paint(int flags) { antialias = (flags & ANTI_ALIAS_FLAG) != 0; }
    public Typeface setTypeface(Typeface typeface) { Typeface old=this.typeface; this.typeface=typeface == null ? Typeface.DEFAULT : typeface; return old; }
    public Typeface getTypeface() { return typeface; }
    public void setTextSize(float size) { if (!(size > 0)) throw new IllegalArgumentException("Nonpositive text size"); textSize=size; }
    public float getTextSize() { return textSize; }
    public void setAntiAlias(boolean enabled) { antialias=enabled; }
    public boolean isAntiAlias() { return antialias; }
    public void setColor(int color) { this.color=color; }
    public int getColor() { return color; }
    public void setAlpha(int alpha) { color=(color & 0xffffff) | ((alpha & 255) << 24); }
    public int getAlpha() { return color >>> 24; }
    Font font() { return typeface.font(textSize); }
    FontRenderContext context() { return new FontRenderContext(new AffineTransform(), antialias, true); }
    private LineMetrics metrics() { return font().getLineMetrics("Ag한", context()); }
    public float ascent() { return -metrics().getAscent(); }
    public float descent() { return metrics().getDescent(); }
    public float measureText(String text) { return (float) font().getStringBounds(text, context()).getWidth(); }
}
