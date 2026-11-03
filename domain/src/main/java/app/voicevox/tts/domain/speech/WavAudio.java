package app.voicevox.tts.domain.speech;

import java.nio.charset.StandardCharsets;

/**
 * Value object: locates the PCM payload inside the RIFF/WAVE container the core returns.
 *
 * <p>The framework only needs three things from the header — sample rate, channel count and
 * where the samples start — so this is all the parsing there is. Kept out of the TTS service
 * because byte-level twiddling is the one part of the streaming path that can be tested
 * without an Android process.
 */
public final class WavAudio {
    public final int offset;
    public final int length;
    public final int sampleRate;
    public final int channels;

    private WavAudio(int offset, int length, int sampleRate, int channels) {
        this.offset = offset;
        this.length = length;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /**
     * Null when the bytes are not a WAV this engine can stream: not RIFF/WAVE, no data chunk,
     * a chunk size that runs past the buffer, or a format other than 16-bit PCM — the only
     * encoding the service declares to the framework.
     */
    public static WavAudio parse(byte[] wav) {
        if (wav == null || wav.length < 12) return null;
        if (!"RIFF".equals(ascii(wav, 0)) || !"WAVE".equals(ascii(wav, 8))) return null;
        int position = 12;
        int sampleRate = 24000;
        int channels = 1;
        while (position + 8 <= wav.length) {
            String id = ascii(wav, position);
            int size = le32(wav, position + 4);
            int body = position + 8;
            if (size < 0 || body > wav.length) return null;
            if ("fmt ".equals(id) && body + 16 <= wav.length) {
                int format = le16(wav, body);
                int bits = le16(wav, body + 14);
                channels = le16(wav, body + 2);
                sampleRate = le32(wav, body + 4);
                if (format != 1 || bits != 16 || channels < 1 || sampleRate <= 0) return null;
            } else if ("data".equals(id)) {
                int length = Math.min(size, wav.length - body);
                return new WavAudio(body, length, sampleRate, channels);
            }
            // Chunks are word-aligned: an odd body is followed by one pad byte. In long
            // arithmetic: a size near Integer.MAX_VALUE used to wrap the position negative and
            // throw out of ascii() instead of returning null.
            long next = (long) body + size + (size & 1);
            if (next > wav.length) return null;
            position = (int) next;
        }
        return null;
    }

    private static String ascii(byte[] data, int offset) {
        if (offset + 4 > data.length) return "";
        return new String(data, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int le16(byte[] data, int offset) {
        if (offset + 2 > data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] data, int offset) {
        if (offset + 4 > data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }
}
