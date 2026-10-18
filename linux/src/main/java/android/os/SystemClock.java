package android.os;
public final class SystemClock {
    public static long uptimeMillis() { return System.nanoTime()/1000000L; }
    public static long elapsedRealtime() { return uptimeMillis(); }
    public static void sleep(long millis) { try { Thread.sleep(millis); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
}
