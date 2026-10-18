package android.os;
/** Desktop has no haptic device; game sound remains separate. */
public class Vibrator { public void vibrate(long milliseconds) {} public void cancel() {} public boolean hasVibrator() { return false; } }
