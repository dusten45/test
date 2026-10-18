package android.view;
public class KeyEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,KEYCODE_BACK=4;
    private final int action,keyCode;
    public KeyEvent(int action,int keyCode) { this.action=action; this.keyCode=keyCode; }
    public KeyEvent(KeyEvent event) { this(event.action,event.keyCode); }
    public int getAction() { return action; }
    public int getKeyCode() { return keyCode; }
}
