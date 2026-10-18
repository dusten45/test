package android.view;
import android.util.DisplayMetrics;
public class Display {
    public static int width=960,height=640;
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRotation() { return 0; }
    public void getMetrics(DisplayMetrics d) { d.widthPixels=width; d.heightPixels=height; d.density=1; d.scaledDensity=1; d.densityDpi=160; d.xdpi=160; d.ydpi=160; }
}
