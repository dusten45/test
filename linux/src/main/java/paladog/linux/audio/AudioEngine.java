package paladog.linux.audio;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;
import static org.lwjgl.openal.ALC10.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.SOFTLoopback;

/** All OpenAL calls run on one thread, independent of the rendering context. */
public final class AudioEngine {
    private static final class Holder {
        static final AudioEngine INSTANCE = new AudioEngine();
    }

    public static AudioEngine instance() { return Holder.INSTANCE; }

    public static final class Buffer {
        private final int id;
        public final int durationMillis;
        private boolean released;
        private Buffer(int id, int durationMillis) {
            this.id = id;
            this.durationMillis = durationMillis;
        }
    }

    public static final class Voice {
        private final int id;
        private final Buffer buffer;
        private boolean released;
        private int repeats;
        private boolean started;
        private boolean paused;
        private Voice(int id, Buffer buffer) { this.id = id; this.buffer = buffer; }
    }

    private final ScheduledThreadPoolExecutor executor;
    private final Set<Voice> voices = new HashSet<>();
    private final Set<Buffer> buffers = new HashSet<>();
    private volatile Thread audioThread;
    private volatile boolean available;
    private volatile String status;
    private final boolean loopback;
    private long device;
    private long context;

    private AudioEngine() {
        String mode = System.getProperty("paladog.audio", "on");
        loopback = "loopback".equalsIgnoreCase(mode);
        executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "paladog-audio");
            thread.setDaemon(true);
            audioThread = thread;
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        if ("off".equalsIgnoreCase(mode)) {
            status = "disabled explicitly (-Dpaladog.audio=off)";
            System.err.println("[audio] " + status);
        } else {
            call(() -> { initialize(); return null; });
        }
        executor.scheduleAtFixedRate(() -> {
            try { poll(); } catch (RuntimeException error) {
                System.err.println("[audio] Playback update failed: " + error);
            }
        }, 10, 10, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "paladog-audio-shutdown"));
    }

    private void initialize() {
        try {
            if (loopback) {
                device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
            } else {
                device = alcOpenDevice((ByteBuffer) null);
            }
            if (device == 0) throw new IllegalStateException("no OpenAL playback device");
            ALCCapabilities capabilities = ALC.createCapabilities(device);
            if (loopback) {
                int[] attributes = {
                    ALC_FREQUENCY, 44100,
                    SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                    SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_SHORT_SOFT, 0
                };
                context = alcCreateContext(device, attributes);
            } else {
                context = alcCreateContext(device, (IntBuffer) null);
            }
            if (context == 0 || !alcMakeContextCurrent(context)) {
                throw new IllegalStateException("cannot create OpenAL context");
            }
            AL.createCapabilities(capabilities);
            alDistanceModel(AL_NONE);
            available = true;
            status = loopback ? "OpenAL loopback test output (not audible)" : "OpenAL playback device opened";
            System.err.println("[audio] " + status);
        } catch (Throwable error) {
            status = "unavailable: " + error.getClass().getSimpleName() + ": " + error.getMessage();
            System.err.println("[audio] " + status + "; continuing without audio");
            destroyDevice();
        }
    }

    public boolean isAvailable() { return available; }
    public String status() { return status; }

    public Buffer upload(PcmSound pcm) {
        return call(() -> {
            int id = 0;
            if (available) {
                id = alGenBuffers();
                ShortBuffer samples = ByteBuffer.allocateDirect(pcm.samples.length * 2)
                        .order(ByteOrder.nativeOrder()).asShortBuffer();
                samples.put(pcm.samples).flip();
                alBufferData(id, pcm.channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16,
                        samples, pcm.sampleRate);
                check("upload");
            }
            Buffer buffer = new Buffer(id, pcm.durationMillis());
            buffers.add(buffer);
            return buffer;
        });
    }

    public Voice newVoice(Buffer buffer) {
        return call(() -> {
            if (buffer.released) throw new IllegalStateException("Released audio buffer");
            int id = available ? alGenSources() : 0;
            if (available) {
                alSourcei(id, AL_BUFFER, buffer.id);
                alSourcei(id, AL_SOURCE_RELATIVE, AL_TRUE);
                alSource3f(id, AL_POSITION, 0, 0, 0);
                check("create source");
            }
            Voice voice = new Voice(id, buffer);
            voices.add(voice);
            return voice;
        });
    }

    public void play(Voice voice, int repeatCount, float left, float right, float rate) {
        call(() -> {
            requireVoice(voice);
            voice.repeats = repeatCount;
            voice.started = true;
            voice.paused = false;
            if (available) {
                alSourcei(voice.id, AL_LOOPING, repeatCount < 0 ? AL_TRUE : AL_FALSE);
                volumeImpl(voice, left, right);
                alSourcef(voice.id, AL_PITCH, Math.max(0.5f, Math.min(2.0f, rate)));
                alSourcePlay(voice.id);
                check("play");
            }
            return null;
        });
    }

    public void resume(Voice voice) {
        call(() -> {
            requireVoice(voice);
            voice.started = true;
            voice.paused = false;
            if (available) alSourcePlay(voice.id);
            return null;
        });
    }

    public void pause(Voice voice) {
        call(() -> {
            requireVoice(voice);
            voice.paused = true;
            if (available) alSourcePause(voice.id);
            return null;
        });
    }

    public void stop(Voice voice) {
        call(() -> {
            requireVoice(voice);
            voice.started = false;
            voice.paused = false;
            if (available) alSourceStop(voice.id);
            return null;
        });
    }

    public boolean isPlaying(Voice voice) {
        return call(() -> !voice.released && available && voice.started && !voice.paused
                && (alGetSourcei(voice.id, AL_SOURCE_STATE) == AL_PLAYING || voice.repeats > 0));
    }

    public void volume(Voice voice, float left, float right) {
        call(() -> { requireVoice(voice); if (available) volumeImpl(voice, left, right); return null; });
    }

    private void volumeImpl(Voice voice, float left, float right) {
        // The game sends equal left/right volumes. Keep stereo source imaging intact.
        alSourcef(voice.id, AL_GAIN, Math.max(0f, Math.min(1f, (left + right) * 0.5f)));
    }

    public void looping(Voice voice, int repeatCount) {
        call(() -> {
            requireVoice(voice);
            voice.repeats = repeatCount;
            if (available) alSourcei(voice.id, AL_LOOPING, repeatCount < 0 ? AL_TRUE : AL_FALSE);
            return null;
        });
    }

    public void rate(Voice voice, float rate) {
        call(() -> {
            requireVoice(voice);
            if (available) alSourcef(voice.id, AL_PITCH, Math.max(0.5f, Math.min(2f, rate)));
            return null;
        });
    }

    public void seek(Voice voice, int milliseconds) {
        call(() -> {
            requireVoice(voice);
            if (available) alSourcef(voice.id, AL_SEC_OFFSET,
                    Math.max(0, Math.min(milliseconds, voice.buffer.durationMillis - 1)) / 1000f);
            return null;
        });
    }

    public int positionMillis(Voice voice) {
        return call(() -> !voice.released && available
                ? (int) (alGetSourcef(voice.id, AL_SEC_OFFSET) * 1000) : 0);
    }

    public void release(Voice voice) {
        call(() -> { releaseVoice(voice); return null; });
    }

    private void releaseVoice(Voice voice) {
        if (!voice.released) {
            if (available) { alSourceStop(voice.id); alDeleteSources(voice.id); }
            voice.released = true;
            voices.remove(voice);
        }
    }

    public void release(Buffer buffer) {
        call(() -> {
            if (!buffer.released) {
                for (Voice voice : new HashSet<>(voices)) {
                    if (voice.buffer == buffer) releaseVoice(voice);
                }
                if (available) alDeleteBuffers(buffer.id);
                buffer.released = true;
                buffers.remove(buffer);
            }
            return null;
        });
    }

    private void poll() {
        if (!available) return;
        for (Voice voice : voices) {
            if (voice.started && !voice.paused && voice.repeats > 0
                    && alGetSourcei(voice.id, AL_SOURCE_STATE) == AL_STOPPED) {
                voice.repeats--;
                alSourcePlay(voice.id);
            }
        }
    }

    /** Test-only deterministic render; output is PCM, never a claimed audible check. */
    public short[] renderLoopback(int frames) {
        return call(() -> {
            if (!loopback || !available) throw new IllegalStateException("Loopback mode is not active");
            ShortBuffer rendered = ByteBuffer.allocateDirect(frames * 4)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            SOFTLoopback.alcRenderSamplesSOFT(device, rendered, frames);
            short[] result = new short[frames * 2];
            rendered.get(result);
            poll();
            return result;
        });
    }

    private static void requireVoice(Voice voice) {
        if (voice.released) throw new IllegalStateException("Released audio voice");
    }

    private static void check(String action) {
        int error = alGetError();
        if (error != AL_NO_ERROR) throw new IllegalStateException("OpenAL " + action + " error " + error);
    }

    private <T> T call(Callable<T> callable) {
        try {
            if (Thread.currentThread() == audioThread) return callable.call();
            return executor.submit(callable).get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Audio operation interrupted", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Audio operation failed", cause);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Audio operation failed", error);
        }
    }

    public void shutdown() {
        if (executor.isShutdown()) return;
        call(() -> {
            for (Voice voice : new HashSet<>(voices)) releaseVoice(voice);
            for (Buffer buffer : buffers) { if (available) alDeleteBuffers(buffer.id); }
            buffers.clear();
            destroyDevice();
            return null;
        });
        executor.shutdownNow();
    }

    private void destroyDevice() {
        available = false;
        if (context != 0) { alcMakeContextCurrent(0); alcDestroyContext(context); context = 0; }
        if (device != 0) { alcCloseDevice(device); device = 0; }
    }
}
