package android.util;
public class DisplayMetrics {
    public static final int DENSITY_DEFAULT = 160;
    public int widthPixels = 960, heightPixels = 640, densityDpi = 160;
    public float density = 1, scaledDensity = 1, xdpi = 160, ydpi = 160;
    public void setTo(DisplayMetrics other) {
        widthPixels = other.widthPixels; heightPixels = other.heightPixels; densityDpi = other.densityDpi;
        density = other.density; scaledDensity = other.scaledDensity; xdpi = other.xdpi; ydpi = other.ydpi;
    }
    public void setToDefaults() { setTo(new DisplayMetrics()); }
}
