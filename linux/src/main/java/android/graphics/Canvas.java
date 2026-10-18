package android.graphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class Canvas {
    private Bitmap bitmap;
    public Canvas() {}
    public Canvas(Bitmap bitmap) { this.bitmap=bitmap; }
    public void setBitmap(Bitmap bitmap) { this.bitmap=bitmap; }
    public void drawBitmap(Bitmap source, float left, float top, Paint paint) {
        Graphics2D g = bitmap.image().createGraphics();
        if (paint != null && paint.getAlpha() != 255) g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, paint.getAlpha()/255f));
        g.drawImage(source.image(), java.awt.geom.AffineTransform.getTranslateInstance(left, top), null);
        g.dispose();
    }
    public void drawText(String text, float x, float y, Paint paint) {
        Graphics2D g = bitmap.image().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, paint.isAntiAlias() ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(paint.font());
        g.setColor(new java.awt.Color(paint.getColor(), true));
        g.drawString(text, x, y);
        g.dispose();
    }
    public void drawColor(int color) { bitmap.eraseColor(color); }
}
