package android.opengl;

import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class GLUtils {
    private GLUtils() {}
    /** Android uploads premultiplied RGBA pixels; cocos2d selects ONE, ONE_MINUS_SRC_ALPHA. */
    public static void texImage2D(int target, int level, Bitmap bitmap, int border) {
        int format = bitmap.getConfig() == Bitmap.Config.ALPHA_8 ? GL11.GL_ALPHA : bitmap.hasAlpha() ? GL11.GL_RGBA : GL11.GL_RGB;
        ByteBuffer pixels = pixels(bitmap, format);
        int oldAlignment=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        try { GL11.glTexImage2D(target, level, format, bitmap.getWidth(), bitmap.getHeight(), border, format, GL11.GL_UNSIGNED_BYTE, pixels); }
        finally { GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, oldAlignment); }
    }
    public static void texImage2D(int target, int level, int internalformat, Bitmap bitmap, int border) { texImage2D(target, level, bitmap, border); }
    public static int getInternalFormat(Bitmap bitmap) { return bitmap.getConfig() == Bitmap.Config.ALPHA_8 ? GL11.GL_ALPHA : bitmap.hasAlpha() ? GL11.GL_RGBA : GL11.GL_RGB; }
    public static int getType(Bitmap bitmap) { return GL11.GL_UNSIGNED_BYTE; }
    public static ByteBuffer pixels(Bitmap bitmap, int format) {
        int channels = format == GL11.GL_ALPHA ? 1 : format == GL11.GL_RGB ? 3 : 4;
        ByteBuffer result = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(bitmap.getWidth(), bitmap.getHeight()), channels));
        int[] argbPixels=bitmap.image().getRGB(0,0,bitmap.getWidth(),bitmap.getHeight(),null,0,bitmap.getWidth());
        for (int argb : argbPixels) {
            int a=argb>>>24;
            if (format == GL11.GL_ALPHA) result.put((byte)a);
            else {
                result.put((byte)((((argb>>>16)&255)*a+127)/255));
                result.put((byte)((((argb>>>8)&255)*a+127)/255));
                result.put((byte)(((argb&255)*a+127)/255));
                if (channels==4) result.put((byte)a);
            }
        }
        return result.flip();
    }
}
