import android.graphics.Bitmap;
import android.opengl.GLUtils;
import java.nio.*;
import javax.microedition.khronos.opengles.GL10;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import paladog.linux.GLBridge;
import org.cocos2d.opengl.GLResourceHelper;
public class RenderProbe {
  private static void draw(GL10 gl,Bitmap bitmap,int src,float r,float g,float b) {
    gl.glClearColor(0,0,1,1);gl.glClear(16384);
    int[] tex=new int[1];gl.glGenTextures(1,tex,0);gl.glBindTexture(3553,tex[0]);
    gl.glTexParameterx(3553,10241,9728);gl.glTexParameterx(3553,10240,9728);
    GLUtils.texImage2D(3553,0,bitmap,0);gl.glEnable(3553);gl.glEnable(3042);gl.glBlendFunc(src,771);
    gl.glColor4f(r,g,b,1);gl.glEnableClientState(32884);gl.glEnableClientState(32888);
    FloatBuffer v=BufferUtils.createFloatBuffer(8).put(new float[]{0,0,32,0,0,32,32,32});v.flip();
    FloatBuffer uv=BufferUtils.createFloatBuffer(8).put(new float[]{0,1,1,1,0,0,1,0});uv.flip();
    gl.glVertexPointer(2,5126,0,v);gl.glTexCoordPointer(2,5126,0,uv);gl.glDrawArrays(5,0,4);
    gl.glFinish();
  }
  private static void pixel(GL10 gl,int r,int g,int b) {
    ByteBuffer out=BufferUtils.createByteBuffer(4);gl.glReadPixels(16,16,1,1,6408,5121,out);
    int[] got={out.get()&255,out.get()&255,out.get()&255};int[] want={r,g,b};
    for(int i=0;i<3;i++)if(Math.abs(got[i]-want[i])>1)throw new AssertionError(java.util.Arrays.toString(got));
    if(gl.glGetError()!=0)throw new AssertionError("GL error");
    System.out.println("PASS: GL rendered pixel "+java.util.Arrays.toString(got));
  }
  public static void main(String[] args) throws Exception {
    if(!GLFW.glfwInit())throw new AssertionError("GLFW");
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
    long w=GLFW.glfwCreateWindow(32,32,"render probe",0,0);if(w==0)throw new AssertionError("Window");
    GLFW.glfwMakeContextCurrent(w);GL.createCapabilities();
    GL10 gl=GLBridge.create();gl.glViewport(0,0,32,32);gl.glMatrixMode(5889);gl.glLoadIdentity();gl.glOrthof(0,32,0,32,-1,1);gl.glMatrixMode(5888);gl.glLoadIdentity();
    Bitmap rgba=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);rgba.setPixel(0,0,0x80ff0000);draw(gl,rgba,1,1,1,1);pixel(gl,128,0,127);
    Bitmap alpha=Bitmap.createBitmap(1,1,Bitmap.Config.ALPHA_8);alpha.setPixel(0,0,0x80000000);draw(gl,alpha,770,0,1,0);pixel(gl,0,128,127);
    int[] texture={0};gl.glGenTextures(1,texture,0);gl.glBindTexture(3553,texture[0]);
    if(!org.lwjgl.opengl.GL11.glIsTexture(texture[0]))throw new AssertionError("Texture creation");
    GLResourceHelper helper=GLResourceHelper.sharedHelper();helper.setInUpdate(true);
    java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
    Thread finalizer=new Thread(()->{try {gl.glDeleteTextures(1,texture,0);}catch(Throwable error){failure.set(error);}},"simulated-direct-finalizer");
    finalizer.start();finalizer.join();
    if(failure.get()!=null)throw new AssertionError("Finalizer cleanup failed",failure.get());
    if(!org.lwjgl.opengl.GL11.glIsTexture(texture[0]))throw new AssertionError("Texture deleted before render-thread drain");
    helper.update(gl);helper.setInUpdate(false);
    if(org.lwjgl.opengl.GL11.glIsTexture(texture[0]))throw new AssertionError("Texture cleanup was not drained");
    System.out.println("PASS: direct finalizer GL deletion runs on context owner");
    GLFW.glfwDestroyWindow(w);GLFW.glfwTerminate();
  }
}
