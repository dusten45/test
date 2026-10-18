package android.os;
public class HandlerThread extends Thread {
    public HandlerThread(String name) { super(name); setDaemon(true); }
    public Looper getLooper() { return Looper.getMainLooper(); }
    public void run() {}
    public boolean quit() { return true; }
}
