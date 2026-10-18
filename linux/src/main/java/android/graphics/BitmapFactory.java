package android.graphics;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class BitmapFactory {
    public static final class Options {
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
        public boolean inScaled = true;
        public boolean inJustDecodeBounds;
        public int inSampleSize = 1;
        public int outWidth;
        public int outHeight;
    }
    private BitmapFactory() {}
    public static Bitmap decodeStream(InputStream input) { return decodeStream(input, null, null); }
    public static Bitmap decodeStream(InputStream input, Rect padding, Options options) {
        if (input == null) return null;
        try {
            BufferedImage decoded = ImageIO.read(input);
            if (decoded == null) return null;
            if (options != null) {
                options.outWidth = decoded.getWidth(); options.outHeight = decoded.getHeight();
                if (options.inJustDecodeBounds) return null;
            }
            Bitmap.Config config = options == null || options.inPreferredConfig == null ? Bitmap.Config.ARGB_8888 : options.inPreferredConfig;
            int sample = options == null ? 1 : Math.max(1, options.inSampleSize);
            Bitmap result = Bitmap.createBitmap(Math.max(1, decoded.getWidth() / sample), Math.max(1, decoded.getHeight() / sample), config);
            Graphics2D g = result.image().createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.drawImage(decoded, 0, 0, result.getWidth(), result.getHeight(), null);
            g.dispose();
            return result;
        } catch (IOException e) { throw new IllegalArgumentException("Unable to decode game bitmap", e); }
    }
    public static Bitmap decodeFile(String path) {
        try (InputStream input = new FileInputStream(path)) { return decodeStream(input); }
        catch (IOException e) { return null; }
    }
    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        return decodeStream(new ByteArrayInputStream(data, offset, length));
    }
}
