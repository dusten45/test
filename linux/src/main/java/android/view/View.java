package android.view;
import android.content.Context;
import android.content.res.Resources;
public class View {
    protected final Context context;
    private int width=960,height=640,id;
    public View(Context context) { this.context=context; }
    public Context getContext() { return context; }
    public Resources getResources() { return context.getResources(); }
    public void setFocusable(boolean value) {}
    public void setFocusableInTouchMode(boolean value) {}
    public void setId(int id) { this.id=id; }
    public int getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void layout(int left,int top,int right,int bottom) { width=right-left; height=bottom-top; onLayout(true,left,top,right,bottom); }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) {}
    protected void onDetachedFromWindow() {}
    public void onWindowFocusChanged(boolean focus) {}
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public ViewParent getParent() { return null; }
    public boolean post(Runnable runnable) { return new android.os.Handler().post(runnable); }
    public void setKeepScreenOn(boolean value) {}
}
