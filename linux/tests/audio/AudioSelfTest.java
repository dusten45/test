package paladog.linux.audio;

import android.media.SoundPool;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks decoded assets and actual OpenAL PCM output without a physical sound device. */
public final class AudioSelfTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the reference APK path");
        System.setProperty("paladog.audio", "loopback");
        int decoded = 0;
        long totalSamples = 0;
        PcmSound title = null;
        byte[] effectMp3 = null;
        try (ZipFile apk = new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("res/raw/") || !entry.getName().endsWith(".mp3")) continue;
                PcmSound sound;
                try (InputStream input = apk.getInputStream(entry)) { sound = Mp3Decoder.decode(input); }
                check(sound.durationMillis() > 0, "empty MP3: " + entry.getName());
                check(energy(sound.samples) > 0, "silent MP3: " + entry.getName());
                decoded++;
                totalSamples += sound.samples.length;
                if (entry.getName().equals("res/raw/bg_title.mp3")) title = sound;
                if (effectMp3 == null && !entry.getName().contains("bg_")) {
                    try (InputStream input = apk.getInputStream(entry)) { effectMp3 = input.readAllBytes(); }
                }
            }
        }
        check(decoded == 145, "expected 145 game MP3 files, got " + decoded);
        check(title != null && effectMp3 != null, "missing reference audio");
        AudioEngine engine = AudioEngine.instance();
        try {
            check(engine.isAvailable(), engine.status());
            AudioEngine.Buffer music = engine.upload(title);
            AudioEngine.Voice musicVoice = engine.newVoice(music);
            engine.play(musicVoice, -1, 1, 1, 1);
            check(energy(engine.renderLoopback(44100)) > 10000, "music MP3 produces no output");
            engine.release(music);
            engine.renderLoopback(1024);

            short[] tone = new short[4410];
            for (int i = 0; i < tone.length; i++) tone[i] = (short) (Math.sin(i * 2 * Math.PI * 440 / 44100) * 2000);
            AudioEngine.Buffer sound = engine.upload(new PcmSound(tone, 1, 44100));
            AudioEngine.Voice one = engine.newVoice(sound);
            engine.play(one, -1, 1, 1, 1);
            engine.renderLoopback(1024);
            double fullVolume = energy(engine.renderLoopback(4096));
            check(fullVolume > 10000, "source is silent");
            engine.volume(one, 0.5f, 0.5f);
            engine.renderLoopback(1024);
            double halfVolume = energy(engine.renderLoopback(4096));
            check(halfVolume < fullVolume * 0.35 && halfVolume > fullVolume * 0.18, "volume did not scale PCM energy");
            engine.pause(one);
            engine.renderLoopback(1024);
            check(energy(engine.renderLoopback(4096)) < 2, "pause did not silence source");
            engine.resume(one);
            engine.renderLoopback(1024);
            check(energy(engine.renderLoopback(4096)) > 1000, "resume stayed silent");
            check(engine.isPlaying(one), "infinite loop stopped at sound end");
            engine.stop(one);
            engine.renderLoopback(1024);

            // Finite loop means one additional repeat, then a stopped source.
            engine.play(one, 1, 1, 1, 1);
            engine.renderLoopback(4410);
            check(engine.isPlaying(one), "finite loop did not repeat");
            engine.renderLoopback(5000);
            check(!engine.isPlaying(one), "finite loop did not finish");
            engine.release(sound);

            Path mp3 = Files.createTempFile("paladog-audio-test-", ".mp3");
            try {
                Files.write(mp3, effectMp3);
                SoundPool pool = new SoundPool(2, 3, 0);
                int sample = pool.load(mp3.toString(), 1);
                check(sample != 0, "SoundPool failed to decode effect");
                int first = pool.play(sample, 0.25f, 0.25f, 2, -1, 1);
                int second = pool.play(sample, 0.25f, 0.25f, 2, -1, 1);
                check(first != 0 && second != 0 && first != second, "concurrent streams failed");
                check(pool.play(sample, 1, 1, 1, 0, 1) == 0, "lower-priority stream displaced a higher one");
                check(pool.play(sample, 0.25f, 0.25f, 2, -1, 1) != 0, "equal-priority stream should replace oldest");
                check(energy(engine.renderLoopback(44100)) > 100, "SoundPool effect output silent");
                pool.autoPause();
                engine.renderLoopback(1024);
                check(energy(engine.renderLoopback(4096)) < 2, "SoundPool autoPause failed");
                pool.autoResume();
                check(energy(engine.renderLoopback(44100)) > 100, "SoundPool autoResume failed");
                check(pool.unload(sample), "SoundPool unload failed");
                check(pool.play(sample, 1, 1, 1, 0, 1) == 0, "unloaded sample played");
                pool.release();
                engine.renderLoopback(1024);
                check(energy(engine.renderLoopback(4096)) < 2, "released voices still produce sound");
            } finally { Files.deleteIfExists(mp3); }

            System.out.println("PASS: decoded " + decoded + " game MP3 files (" + totalSamples + " PCM samples)");
            System.out.println("PASS: OpenAL loopback music/effects, volume, pause/resume, finite/infinite loops, concurrent priority-limited streams, unload/release");
            System.out.println("This verifies PCM output. It is not a physical speaker/listening test.");
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
