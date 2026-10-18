package android.media;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import paladog.linux.audio.AudioEngine;
import paladog.linux.audio.Mp3Decoder;

/** The game's music API, implemented with desktop OpenAL and in-process MP3 decode. */
public class MediaPlayer {
    private static final Cleaner CLEANER = Cleaner.create();
    private AudioEngine.Buffer buffer;
    private AudioEngine.Voice voice;
    private Cleaner.Cleanable cleanup;
    private boolean released;
    private boolean looping;
    private boolean paused;
    private float leftVolume = 1f;
    private float rightVolume = 1f;

    public MediaPlayer() {}

    public static MediaPlayer create(Context context, int resourceId) {
        try (InputStream input = context.getResources().openRawResource(resourceId)) {
            MediaPlayer player = new MediaPlayer();
            player.buffer = AudioEngine.instance().upload(Mp3Decoder.decode(input));
            // The original game sometimes replaces an already-ended music player
            // without release(). Do not retain its native source forever.
            AudioEngine.Buffer ownedBuffer = player.buffer;
            player.cleanup = CLEANER.register(player, () -> {
                try { AudioEngine.instance().release(ownedBuffer); }
                catch (RuntimeException ignored) { /* Engine may already be shut down. */ }
            });
            return player;
        } catch (Exception error) {
            System.err.println("[audio] Cannot load music resource " + resourceId + ": " + error);
            return null;
        }
    }

    /** create(Context,int) already prepares; old Cocos2d calls prepare again. */
    public synchronized void prepare() throws IOException { requirePrepared(); }

    public synchronized void start() {
        requirePrepared();
        AudioEngine engine = AudioEngine.instance();
        if (voice == null) voice = engine.newVoice(buffer);
        if (paused) {
            engine.resume(voice);
        } else if (!engine.isPlaying(voice)) {
            engine.play(voice, looping ? -1 : 0, leftVolume, rightVolume, 1f);
        }
        paused = false;
    }

    public synchronized void pause() {
        requirePrepared();
        if (voice != null) AudioEngine.instance().pause(voice);
        paused = true;
    }

    public synchronized void stop() {
        requirePrepared();
        if (voice != null) AudioEngine.instance().stop(voice);
        paused = false;
    }

    public synchronized boolean isPlaying() {
        requirePrepared();
        return voice != null && AudioEngine.instance().isPlaying(voice);
    }

    public synchronized void setLooping(boolean value) {
        requireNotReleased();
        looping = value;
        if (voice != null) AudioEngine.instance().looping(voice, value ? -1 : 0);
    }

    public synchronized boolean isLooping() { requireNotReleased(); return looping; }

    public synchronized void setVolume(float left, float right) {
        requireNotReleased();
        leftVolume = Math.max(0f, Math.min(1f, left));
        rightVolume = Math.max(0f, Math.min(1f, right));
        if (voice != null) AudioEngine.instance().volume(voice, leftVolume, rightVolume);
    }

    public synchronized int getDuration() { requirePrepared(); return buffer.durationMillis; }

    public synchronized int getCurrentPosition() {
        requirePrepared();
        return voice == null ? 0 : AudioEngine.instance().positionMillis(voice);
    }

    public synchronized void seekTo(int milliseconds) {
        requirePrepared();
        if (voice == null) voice = AudioEngine.instance().newVoice(buffer);
        AudioEngine.instance().seek(voice, milliseconds);
    }

    public synchronized void reset() {
        requireNotReleased();
        freeBuffer();
        paused = false;
        looping = false;
    }

    public synchronized void release() {
        if (!released) {
            freeBuffer();
            released = true;
        }
    }

    private void freeBuffer() {
        if (cleanup != null) cleanup.clean();
        cleanup = null;
        buffer = null;
        voice = null;
    }

    private void requireNotReleased() {
        if (released) throw new IllegalStateException("MediaPlayer has been released");
    }

    private void requirePrepared() {
        requireNotReleased();
        if (buffer == null) throw new IllegalStateException("MediaPlayer has no sound loaded");
    }
}
