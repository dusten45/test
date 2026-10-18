package android.util;

/** Legacy game logging is opt-in because some original calls contain save data. */
public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6, ASSERT = 7;
    private Log() { }
    public static boolean isLoggable(String tag, int priority) { return Boolean.getBoolean("paladog.legacyLog"); }
    public static int println(int priority, String tag, String message) {
        if (isLoggable(tag, priority)) { System.err.println("[legacy " + tag + "] " + message); }
        return 0;
    }
    public static int v(String tag, String message) { return println(VERBOSE, tag, message); }
    public static int d(String tag, String message) { return println(DEBUG, tag, message); }
    public static int i(String tag, String message) { return println(INFO, tag, message); }
    public static int w(String tag, String message) { return println(WARN, tag, message); }
    public static int e(String tag, String message) { return println(ERROR, tag, message); }
    public static int v(String tag, String message, Throwable error) { return v(tag, message); }
    public static int d(String tag, String message, Throwable error) { return d(tag, message); }
    public static int i(String tag, String message, Throwable error) { return i(tag, message); }
    public static int w(String tag, String message, Throwable error) { return w(tag, message); }
    public static int e(String tag, String message, Throwable error) { return e(tag, message); }
    public static int w(String tag, Throwable error) { return w(tag, error.getClass().getSimpleName()); }
    public static String getStackTraceString(Throwable error) {
        return error == null ? "" : error.getClass().getName();
    }
}
