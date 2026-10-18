package paladog.linux.audio;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** In-process MP3 decoding; no external player or transcoder is started. */
public final class Mp3Decoder {
    private Mp3Decoder() {}

    public static PcmSound decode(InputStream input) throws IOException {
        Bitstream stream = new Bitstream(input);
        Decoder decoder = new Decoder();
        short[] data = new short[65536];
        int count = 0;
        int channels = 0;
        int sampleRate = 0;
        try {
            Header header;
            while ((header = stream.readFrame()) != null) {
                SampleBuffer frame = (SampleBuffer) decoder.decodeFrame(header, stream);
                if (channels != 0 && (channels != frame.getChannelCount()
                        || sampleRate != frame.getSampleFrequency())) {
                    throw new IOException("MP3 format changes within a sound");
                }
                channels = frame.getChannelCount();
                sampleRate = frame.getSampleFrequency();
                int length = frame.getBufferLength();
                if (count + length > data.length) {
                    data = Arrays.copyOf(data, Math.max(data.length * 2, count + length));
                }
                System.arraycopy(frame.getBuffer(), 0, data, count, length);
                count += length;
                stream.closeFrame();
            }
            if (count == 0) {
                throw new IOException("MP3 has no decodable audio frames");
            }
            return new PcmSound(Arrays.copyOf(data, count), channels, sampleRate);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Cannot decode game MP3", error);
        } finally {
            try { stream.close(); } catch (Exception ignored) { }
        }
    }
}
