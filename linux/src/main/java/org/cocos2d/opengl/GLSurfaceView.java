package org.cocos2d.opengl;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** The GLFW main thread owns the context and invokes this renderer. No Android GL thread. */
public class GLSurfaceView extends SurfaceView {
    public static final int RENDERMODE_CONTINUOUSLY=1, RENDERMODE_WHEN_DIRTY=0;
    public static final int DEBUG_CHECK_GL_ERROR=1, DEBUG_LOG_GL_CALLS=2;
    public interface Renderer {
        void onSurfaceCreated(GL10 gl, EGLConfig config);
        void onSurfaceChanged(GL10 gl, int width, int height);
        void onDrawFrame(GL10 gl);
    }
    private Renderer renderer;
    private int renderMode=RENDERMODE_CONTINUOUSLY, debugFlags;
    private volatile boolean paused, renderRequested=true;
    private final ConcurrentLinkedQueue<Runnable> events=new ConcurrentLinkedQueue<>();
    public GLSurfaceView(Context context) { super(context); }
    public GLSurfaceView(Context context, AttributeSet attributes) { super(context); }
    public void setRenderer(Renderer renderer) { if (this.renderer != null) throw new IllegalStateException("Renderer already set"); this.renderer=renderer; }
    public Renderer getRenderer() { return renderer; }
    public void setRenderMode(int mode) { if(mode!=0 && mode!=1) throw new IllegalArgumentException("Render mode");renderMode=mode; }
    public int getRenderMode() { return renderMode; }
    public void requestRender() { renderRequested=true; }
    public boolean consumeRenderRequest() { boolean requested=renderRequested;renderRequested=false;return requested; }
    public void queueEvent(Runnable event) { if(event==null) throw new IllegalArgumentException("Null GL event");events.add(event); }
    public void drainEvents() { Runnable next;while((next=events.poll())!=null)next.run(); }
    public void onPause() { paused=true; }
    public void onResume() { paused=false;renderRequested=true; }
    public boolean isPaused() { return paused; }
    public void setDebugFlags(int flags) { debugFlags=flags; }
    public int getDebugFlags() { return debugFlags; }
    public void setEGLConfigChooser(boolean depth) { }
    public void setEGLConfigChooser(int red,int green,int blue,int alpha,int depth,int stencil) { }
    public void setEGLContextClientVersion(int version) { if(version>1)throw new UnsupportedOperationException("This game uses the fixed pipeline"); }
    public void setSafeMode(boolean safe) { }
}
