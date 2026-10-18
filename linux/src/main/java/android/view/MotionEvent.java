package android.view;
/** Immutable pointer snapshots queued by the original Cocos touch dispatcher. */
public final class MotionEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;
    private final int action;
    private final int[] ids;
    private final float[] xs,ys;
    public MotionEvent(int action,int[] ids,float[] xs,float[] ys) { this.action=action; this.ids=ids.clone(); this.xs=xs.clone(); this.ys=ys.clone(); }
    public static MotionEvent obtain(MotionEvent e) { return new MotionEvent(e.action,e.ids,e.xs,e.ys); }
    public static MotionEvent obtain(long down,long now,int action,float x,float y,int meta) { return new MotionEvent(action,new int[]{0},new float[]{x},new float[]{y}); }
    public int getAction() { return action; }
    public int getActionMasked() { return action&255; }
    public int getActionIndex() { return action>>8; }
    public int getPointerCount() { return ids.length; }
    public int getPointerId(int index) { return ids[index]; }
    public float getX() { return xs[0]; }
    public float getY() { return ys[0]; }
    public float getX(int index) { return xs[index]; }
    public float getY(int index) { return ys[index]; }
    public void recycle() {}
}
