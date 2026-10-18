package android.os;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
/** Dispatches callbacks on the game loop, preserving UI/GL thread confinement. */
public class Handler {
    private record Task(long time,long sequence,Handler owner,Runnable runnable) implements Comparable<Task> {
        public int compareTo(Task t) { int c=Long.compare(time,t.time); return c!=0?c:Long.compare(sequence,t.sequence); }
    }
    private static final PriorityQueue<Task> QUEUE=new PriorityQueue<>();
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private final Looper looper;
    public Handler() { this(Looper.getMainLooper()); }
    public Handler(Looper looper) { this.looper=looper; }
    public Looper getLooper() { return looper; }
    public boolean post(Runnable runnable) { return postDelayed(runnable,0); }
    public boolean postDelayed(Runnable runnable,long delay) { synchronized(QUEUE) { QUEUE.add(new Task(SystemClock.uptimeMillis()+Math.max(0,delay),SEQUENCE.incrementAndGet(),this,runnable)); } return true; }
    public void removeCallbacks(Runnable runnable) { synchronized(QUEUE) { QUEUE.removeIf(t->t.owner==this&&t.runnable==runnable); } }
    public static void pump() {
        for(int n=0;n<1000;n++) {
            Task t; synchronized(QUEUE) { t=QUEUE.peek(); if(t==null||t.time>SystemClock.uptimeMillis())return; QUEUE.remove(); }
            t.runnable.run();
        }
    }
}
