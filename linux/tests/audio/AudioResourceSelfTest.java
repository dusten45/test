package paladog.linux.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.SoundPool;

/** Exercises the same resource and media entry points that original game bytecode calls. */
public final class AudioResourceSelfTest {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Supply extracted APK root path");
        System.setProperty("paladog.assets", args[0]);
        System.setProperty("paladog.audio", "loopback");
        Context context = new Context();
        int titleId = context.getResources().getIdentifier("bg_title", "raw", context.getPackageName());
        check(titleId != 0, "game R$raw resource IDs were not loaded");
        AudioEngine engine = AudioEngine.instance();
        try {
            MediaPlayer music = MediaPlayer.create(context, titleId);
            check(music != null && music.getDuration() > 1000, "resource music did not decode");
            music.setLooping(true);
            music.start();
            check(energy(engine.renderLoopback(44100)) > 100, "resource music is silent");
            check(music.isPlaying() && music.getCurrentPosition() >= 900, "music timeline did not advance");
            music.pause();
            engine.renderLoopback(1024);
            check(!music.isPlaying() && energy(engine.renderLoopback(4096)) < 2, "music pause failed");
            music.start();
            check(energy(engine.renderLoopback(44100)) > 100, "music resume failed");
            music.setVolume(0, 0);
            engine.renderLoopback(1024);
            check(energy(engine.renderLoopback(4096)) < 2, "music mute failed");
            music.setVolume(0.5f, 0.5f);
            check(energy(engine.renderLoopback(44100)) > 100, "music unmute failed");
            music.stop();
            check(!music.isPlaying(), "music stop failed");
            music.release();
            music.release();
            boolean rejected = false;
            try { music.start(); } catch (IllegalStateException expected) { rejected = true; }
            check(rejected, "released player accepted playback");

            SoundPool effects = new SoundPool(8, 3, 0);
            int effect = effects.load(context, titleId, 1);
            int stream = effects.play(effect, 0.5f, 0.5f, 1, -1, 1);
            check(effect != 0 && stream != 0, "resource SoundPool load/play failed");
            check(energy(engine.renderLoopback(44100)) > 100, "resource SoundPool is silent");
            effects.stop(stream);
            effects.release();
            System.out.println("PASS: actual Context/Resources -> MediaPlayer/SoundPool calls, timeline, pause/resume, mute/unmute, release");
            System.out.println("OpenAL loopback PCM only; physical Linux audio playback remains a user-device check.");
        } finally { engine.shutdown(); }
    }

    private static double energy(short[] samples) {
        double sum = 0;
        for (short sample : samples) sum += (double) sample * sample;
        return sum / samples.length;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
