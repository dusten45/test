package android.content.res;

import java.util.Locale;

public class Configuration {
    public static final int ORIENTATION_UNDEFINED = 0, ORIENTATION_PORTRAIT = 1, ORIENTATION_LANDSCAPE = 2;
    public Locale locale = Locale.getDefault();
    public int orientation = ORIENTATION_LANDSCAPE;
    public float fontScale = 1;
    public int screenWidthDp = 960, screenHeightDp = 640;
    public Configuration() { }
    public Configuration(Configuration other) { setTo(other); }
    public void setTo(Configuration other) {
        locale = other.locale; orientation = other.orientation; fontScale = other.fontScale;
        screenWidthDp = other.screenWidthDp; screenHeightDp = other.screenHeightDp;
    }
    public void setLocale(Locale locale) { this.locale = locale; }
}
