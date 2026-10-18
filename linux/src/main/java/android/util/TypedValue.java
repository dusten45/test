package android.util;
public class TypedValue {
    public static final int COMPLEX_UNIT_PX = 0, COMPLEX_UNIT_DIP = 1, COMPLEX_UNIT_SP = 2;
    public int data, type, resourceId, density;
    public CharSequence string;
    public static float applyDimension(int unit, float value, DisplayMetrics metrics) {
        return unit == COMPLEX_UNIT_DIP ? value * metrics.density : unit == COMPLEX_UNIT_SP ? value * metrics.scaledDensity : value;
    }
}
