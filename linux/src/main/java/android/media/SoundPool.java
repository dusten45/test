package android.media;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import paladog.linux.audio.AudioEngine;
import paladog.linux.audio.Mp3Decoder;

/** Concurrent sound effects with Android's stream limit and priority selection. */
public class SoundPool {
    public interface OnLoadCompleteListener {
        void onLoadComplete(SoundPool soundPool, int sampleId, int status);
    }

    private static final class Stream {
        final int sampleId;
        final AudioEngine.Voice voice;
        int priority;
        boolean paused;
        boolean autoPaused;
        Stream(int sampleId, AudioEngine.Voice voice, int priority) {
            this.sampleId = sampleId;
            this.voice = voice;
            this.priority = priority;
        }
    }

    private final int maxStreams;
    private final Map<Integer, AudioEngine.Buffer> samples = new HashMap<>();
    private final LinkedHashMap<Integer, Stream> streams = new LinkedHashMap<>();
    private int nextSampleId = 1;
    private int nextStreamId = 1;
    private boolean released;
    private OnLoadCompleteListener listener;

    public SoundPool(int maxStreams, int streamType, int sourceQuality) {
        if (maxStreams < 1) throw new IllegalArgumentException("maxStreams must be positive");
        this.maxStreams = maxStreams;
    }

    public synchronized int load(Context context, int resourceId, int priority) {
        requireOpen();
        try (InputStream input = context.getResources().openRawResource(resourceId)) {
            return loadStream(input);
        } catch (Exception error) {
            System.err.println("[audio] Cannot load effect resource " + resourceId + ": " + error);
            return 0;
        }
    }

    public synchronized int load(AssetFileDescriptor descriptor, int priority) {
        requireOpen();
        try (InputStream input = descriptor.createInputStream()) {
            return loadStream(input);
        } catch (Exception error) {
            System.err.println("[audio] Cannot load effect descriptor: " + error);
            return 0;
        }
    }

    public synchronized int load(String path, int priority) {
        requireOpen();
        try (InputStream input = new FileInputStream(path)) {
            return loadStream(input);
        } catch (Exception error) {
            System.err.println("[audio] Cannot load effect " + path + ": " + error);
            return 0;
        }
    }

    private int loadStream(InputStream input) throws Exception {
        AudioEngine.Buffer buffer = AudioEngine.instance().upload(Mp3Decoder.decode(input));
        int sampleId = nextSampleId++;
        samples.put(sampleId, buffer);
        if (listener != null) listener.onLoadComplete(this, sampleId, 0);
        return sampleId;
    }

    public synchronized int play(int soundId, float leftVolume, float rightVolume,
            int priority, int loop, float rate) {
        requireOpen();
        AudioEngine engine = AudioEngine.instance();
        AudioEngine.Buffer buffer = samples.get(soundId);
        if (buffer == null || !engine.isAvailable()) return 0;
        pruneCompleted();
        if (streams.size() >= maxStreams) {
            Integer oldestLowestPriority = null;
            int lowestPriority = Integer.MAX_VALUE;
            for (Map.Entry<Integer, Stream> entry : streams.entrySet()) {
                if (entry.getValue().priority < lowestPriority) {
                    lowestPriority = entry.getValue().priority;
                    oldestLowestPriority = entry.getKey();
                }
            }
            if (priority < lowestPriority) return 0;
            stop(oldestLowestPriority);
        }
        AudioEngine.Voice voice = engine.newVoice(buffer);
        engine.play(voice, loop, leftVolume, rightVolume, rate);
        int streamId = nextStreamId++;
        streams.put(streamId, new Stream(soundId, voice, priority));
        return streamId;
    }

    public synchronized void stop(int streamId) {
        Stream stream = streams.remove(streamId);
        if (stream != null) AudioEngine.instance().release(stream.voice);
    }

    public synchronized void pause(int streamId) {
        Stream stream = streams.get(streamId);
        if (stream != null) {
            AudioEngine.instance().pause(stream.voice);
            stream.paused = true;
        }
    }

    public synchronized void resume(int streamId) {
        Stream stream = streams.get(streamId);
        if (stream != null && stream.paused) {
            AudioEngine.instance().resume(stream.voice);
            stream.paused = false;
            stream.autoPaused = false;
        }
    }

    public synchronized void autoPause() {
        for (Stream stream : streams.values()) {
            if (!stream.paused && AudioEngine.instance().isPlaying(stream.voice)) {
                AudioEngine.instance().pause(stream.voice);
                stream.paused = true;
                stream.autoPaused = true;
            }
        }
    }

    public synchronized void autoResume() {
        for (Integer id : streams.keySet()) {
            if (streams.get(id).autoPaused) resume(id);
        }
    }

    public synchronized void setVolume(int streamId, float leftVolume, float rightVolume) {
        Stream stream = streams.get(streamId);
        if (stream != null) AudioEngine.instance().volume(stream.voice, leftVolume, rightVolume);
    }

    public synchronized void setLoop(int streamId, int loop) {
        Stream stream = streams.get(streamId);
        if (stream != null) AudioEngine.instance().looping(stream.voice, loop);
    }

    public synchronized void setRate(int streamId, float rate) {
        Stream stream = streams.get(streamId);
        if (stream != null) AudioEngine.instance().rate(stream.voice, rate);
    }

    public synchronized void setPriority(int streamId, int priority) {
        Stream stream = streams.get(streamId);
        if (stream != null) stream.priority = priority;
    }

    public synchronized boolean unload(int soundId) {
        AudioEngine.Buffer buffer = samples.remove(soundId);
        if (buffer == null) return false;
        Iterator<Stream> iterator = streams.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().sampleId == soundId) iterator.remove();
        }
        AudioEngine.instance().release(buffer);
        return true;
    }

    public synchronized void release() {
        if (released) return;
        for (AudioEngine.Buffer buffer : samples.values()) AudioEngine.instance().release(buffer);
        samples.clear();
        streams.clear();
        released = true;
    }

    public synchronized void setOnLoadCompleteListener(OnLoadCompleteListener listener) {
        this.listener = listener;
    }

    private void pruneCompleted() {
        Iterator<Stream> iterator = streams.values().iterator();
        while (iterator.hasNext()) {
            Stream stream = iterator.next();
            if (!stream.paused && !AudioEngine.instance().isPlaying(stream.voice)) {
                AudioEngine.instance().release(stream.voice);
                iterator.remove();
            }
        }
    }

    private void requireOpen() {
        if (released) throw new IllegalStateException("SoundPool has been released");
    }
}
