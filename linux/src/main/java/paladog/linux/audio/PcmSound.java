package paladog.linux.audio;

/** Signed 16-bit interleaved PCM decoded from one packaged game sound. */
public final class PcmSound {
    public final short[] samples;
    public final int channels;
    public final int sampleRate;

    public PcmSound(short[] samples, int channels, int sampleRate) {
        if (samples.length == 0 || (channels != 1 && channels != 2) || sampleRate <= 0
                || samples.length % channels != 0) {
            throw new IllegalArgumentException("Invalid PCM sound");
        }
        this.samples = samples;
        this.channels = channels;
        this.sampleRate = sampleRate;
    }

    public int durationMillis() {
        return (int) ((long) samples.length * 1000 / channels / sampleRate);
    }
}
