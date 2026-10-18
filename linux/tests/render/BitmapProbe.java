import android.graphics.*;
import android.opengl.GLUtils;
import java.nio.*;
public class BitmapProbe {
  public static void main(String[] args) throws Exception {
    String fontPath=System.getProperty("paladog.font");
    if(fontPath==null)throw new AssertionError("Set paladog.font for reproducible font coverage");
    java.awt.Font bundled=java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,new java.io.File(fontPath));
    if(bundled.canDisplayUpTo("팔라독 test")!=-1)throw new AssertionError("Korean glyph coverage");
    Bitmap bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
    bitmap.setPixel(0,0,0x80804020);
    ByteBuffer rgba=GLUtils.pixels(bitmap,6408);
    int[] expected={64,32,16,128};
    for(int x:expected)if((rgba.get()&255)!=x)throw new AssertionError("Premultiply");
    java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();
    if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,encoded))throw new AssertionError("PNG encoding");
    Bitmap decoded=BitmapFactory.decodeStream(new java.io.ByteArrayInputStream(encoded.toByteArray()));
    ByteBuffer decodedRgba=GLUtils.pixels(decoded,6408);
    for(int x:expected)if((decodedRgba.get()&255)!=x)throw new AssertionError("PNG alpha decode");
    decoded.recycle();
    if(decoded.getWidth()!=1||decoded.getHeight()!=1||decoded.getConfig()!=Bitmap.Config.ARGB_8888||!decoded.isRecycled())throw new AssertionError("Recycled metadata");
    try { decoded.getPixel(0,0);throw new AssertionError("Recycled pixels accepted"); }
    catch(IllegalStateException expectedException) { }
    Bitmap alpha=Bitmap.createBitmap(160,50,Bitmap.Config.ALPHA_8);
    Paint p=new Paint();p.setTextSize(22);p.setAntiAlias(true);
    new Canvas(alpha).drawText("팔라독 test",0,-p.ascent(),p);
    if(p.measureText("팔라독")<=0||p.ascent()>=0||p.descent()<0)throw new AssertionError("Metrics");
    ByteBuffer mask=GLUtils.pixels(alpha,6406);
    int count=0,partial=0;while(mask.hasRemaining()){int a=mask.get()&255;if(a>0)count++;if(a>0&&a<255)partial++;}
    if(count<100||partial<10)throw new AssertionError("Font rasterization");
    System.out.println("PASS: exact premultiplied RGBA and Korean alpha-mask text ("+count+" pixels, "+partial+" antialiased)");
  }
}
