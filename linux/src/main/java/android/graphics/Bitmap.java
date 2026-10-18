package android.graphics;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import javax.imageio.ImageIO;

/** Android bitmap operations used by the preserved game, backed by Java2D. */
public final class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888 }
    public enum CompressFormat { JPEG, PNG, WEBP }
    private BufferedImage image;
    private final Config config;
    private final int width, height;

    Bitmap(BufferedImage image, Config config) {
        this.image = image;
        this.config = config;
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid bitmap dimensions: " + width + " x " + height);
        if (config == null) config = Config.ARGB_8888;
        int type = config == Config.RGB_565 ? BufferedImage.TYPE_USHORT_565_RGB : BufferedImage.TYPE_INT_ARGB_PRE;
        return new Bitmap(new BufferedImage(width, height, type), config);
    }

    public static Bitmap createBitmap(int[] colors, int width, int height, Config config) {
        Bitmap result = createBitmap(width, height, config);
        result.setPixels(colors, 0, width, 0, 0, width, height);
        return result;
    }

    public static Bitmap createScaledBitmap(Bitmap source, int width, int height, boolean filter) {
        Bitmap result = createBitmap(width, height, source.config);
        Graphics2D g = result.image.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, filter ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source.image(), 0, 0, width, height, null);
        g.dispose();
        return result;
    }

    public BufferedImage image() {
        if (image == null) throw new IllegalStateException("Bitmap has been recycled");
        return image;
    }
    // Android retains bitmap metadata after recycle; CCTexture2D reads it after immediate GL upload.
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Config getConfig() { return config; }
    public boolean hasAlpha() { return config != Config.RGB_565; }
    public boolean isRecycled() { return image == null; }
    public boolean isMutable() { return true; }
    public int getRowBytes() { return getWidth() * (config == Config.ALPHA_8 ? 1 : config == Config.RGB_565 ? 2 : 4); }
    public int getByteCount() { return getRowBytes() * getHeight(); }
    public void recycle() { image = null; }
    public int getPixel(int x, int y) { return image().getRGB(x, y); }
    public void setPixel(int x, int y, int color) { image().setRGB(x, y, color); }
    public void eraseColor(int color) {
        Graphics2D g = image().createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setColor(new java.awt.Color(color, true));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }
    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) image().getRGB(x, y + row, width, 1, pixels, offset + row * stride, stride);
    }
    public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) image().setRGB(x, y + row, width, 1, pixels, offset + row * stride, stride);
    }
    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        try {
            if (format == CompressFormat.WEBP) throw new UnsupportedOperationException("WebP encoding is not used by this game");
            return ImageIO.write(image(), format == CompressFormat.PNG ? "png" : "jpeg", stream);
        } catch (IOException e) { return false; }
    }

    public void copyPixelsToBuffer(Buffer destination) {
        for (int y = 0; y < getHeight(); y++) for (int x = 0; x < getWidth(); x++) {
            int argb = getPixel(x, y);
            if (config == Config.RGB_565) {
                short rgb = (short) (((argb >> 8) & 0xf800) | ((argb >> 5) & 0x07e0) | ((argb >> 3) & 31));
                if (destination instanceof ShortBuffer) ((ShortBuffer) destination).put(rgb);
                else ((ByteBuffer) destination).putShort(rgb);
            } else if (config == Config.ALPHA_8) ((ByteBuffer) destination).put((byte) (argb >>> 24));
            else if (destination instanceof IntBuffer) ((IntBuffer) destination).put(argb);
            else ((ByteBuffer) destination).putInt(argb);
        }
    }
    public void copyPixelsFromBuffer(Buffer source) {
        for (int y = 0; y < getHeight(); y++) for (int x = 0; x < getWidth(); x++) {
            int argb;
            if (config == Config.RGB_565) {
                int rgb = (source instanceof ShortBuffer ? ((ShortBuffer) source).get() : ((ByteBuffer) source).getShort()) & 65535;
                int r = (rgb >>> 11) & 31, g = (rgb >>> 5) & 63, b = rgb & 31;
                argb = 0xff000000 | ((r * 255 / 31) << 16) | ((g * 255 / 63) << 8) | (b * 255 / 31);
            } else if (config == Config.ALPHA_8) argb = (((ByteBuffer) source).get() & 255) << 24;
            else argb = source instanceof IntBuffer ? ((IntBuffer) source).get() : ((ByteBuffer) source).getInt();
            setPixel(x, y, argb);
        }
    }
}
