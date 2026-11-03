package app.voicevox.tts.domain.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

/**
 * The RIFF walker behind streaming playback.
 *
 * <p>It is the only part of the audio path that is pure bytes, and it fails silently — a
 * misread offset plays noise instead of throwing. So each branch gets a test.
 */
public class WavAudioTest {

    @Test
    public void readsFormatAndPayloadOfACanonicalFile() {
        byte[] bytes = canonical(24000, 1, 480);

        WavAudio wav = WavAudio.parse(bytes);

        assertNotNull(wav);
        assertEquals(24000, wav.sampleRate);
        assertEquals(1, wav.channels);
        assertEquals(480, wav.length);
        assertEquals(bytes.length - 480, wav.offset);
    }

    @Test
    public void readsStereoAtFortyEightKilohertz() {
        WavAudio wav = WavAudio.parse(canonical(48000, 2, 960));

        assertNotNull(wav);
        assertEquals(48000, wav.sampleRate);
        assertEquals(2, wav.channels);
    }

    @Test
    public void skipsAnOddSizedChunkIncludingItsPadByte() {
        byte[] bytes = builder()
                .ascii("RIFF").le32(0).ascii("WAVE")
                .ascii("JUNK").le32(3).raw(new byte[]{1, 2, 3}).raw(new byte[]{0})
                .ascii("fmt ").le32(16).format(1, 1, 24000, 16)
                .ascii("data").le32(4).raw(new byte[]{9, 9, 9, 9})
                .done();

        WavAudio wav = WavAudio.parse(bytes);

        assertNotNull(wav);
        assertEquals(24000, wav.sampleRate);
        assertEquals(4, wav.length);
        assertEquals(9, bytes[wav.offset]);
    }

    @Test
    public void clampsAPayloadLongerThanTheBuffer() {
        byte[] bytes = canonical(24000, 1, 100);
        // Claim 10 000 bytes of samples in a file that only carries 100.
        le32(bytes, 40, 10_000);

        WavAudio wav = WavAudio.parse(bytes);

        assertNotNull(wav);
        assertEquals(100, wav.length);
    }

    @Test
    public void rejectsSomethingThatIsNotRiffWave() {
        assertNull(WavAudio.parse(null));
        assertNull(WavAudio.parse(new byte[0]));
        assertNull(WavAudio.parse("not a wav file at all".getBytes()));
        assertNull(WavAudio.parse(flipMagic()));
    }

    @Test
    public void rejectsATooShortHeader() {
        assertNull(WavAudio.parse(new byte[11]));
    }

    @Test
    public void rejectsAFileWithoutADataChunk() {
        byte[] bytes = builder()
                .ascii("RIFF").le32(0).ascii("WAVE")
                .ascii("fmt ").le32(16).format(1, 1, 24000, 16)
                .done();

        assertNull(WavAudio.parse(bytes));
    }

    @Test
    public void aChunkSizeThatWouldOverflowIsRejectedNotThrown() {
        // 0x7FFFFFF0 + the header offset used to wrap the read position negative, and the next
        // ascii() threw StringIndexOutOfBoundsException instead of returning null.
        byte[] bytes = builder()
                .ascii("RIFF").le32(0).ascii("WAVE")
                .ascii("LIST").le32(0x7FFFFFF0).raw(new byte[40])
                .done();

        assertNull(WavAudio.parse(bytes));
    }

    @Test
    public void rejectsAnythingButSixteenBitPcm() {
        // The service declares ENCODING_PCM_16BIT to the framework whatever the header says.
        byte[] floats = builder()
                .ascii("RIFF").le32(0).ascii("WAVE")
                .ascii("fmt ").le32(16).format(3, 1, 24000, 32)
                .ascii("data").le32(8).raw(new byte[8])
                .done();
        byte[] eightBit = builder()
                .ascii("RIFF").le32(0).ascii("WAVE")
                .ascii("fmt ").le32(16).format(1, 1, 24000, 8)
                .ascii("data").le32(8).raw(new byte[8])
                .done();

        assertNull(WavAudio.parse(floats));
        assertNull(WavAudio.parse(eightBit));
    }

    // ------------------------------------------------------------------ fixtures

    private static byte[] flipMagic() {
        byte[] bytes = canonical(24000, 1, 16);
        bytes[0] = 'X';
        return bytes;
    }

    /** A well-formed 16-bit PCM file with {@code dataBytes} of silence. */
    private static byte[] canonical(int sampleRate, int channels, int dataBytes) {
        return builder()
                .ascii("RIFF").le32(0).ascii("WAVE")
                .ascii("fmt ").le32(16).format(1, channels, sampleRate, 16)
                .ascii("data").le32(dataBytes).raw(new byte[dataBytes])
                .done();
    }

    private static Builder builder() {
        return new Builder();
    }

    /** Enough of a WAV writer to build the shapes the parser has to cope with. */
    private static final class Builder {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Builder ascii(String text) {
            out.write(text.getBytes(), 0, text.length());
            return this;
        }

        Builder raw(byte[] bytes) {
            out.write(bytes, 0, bytes.length);
            return this;
        }

        Builder le32(int value) {
            out.write(value & 0xFF);
            out.write((value >>> 8) & 0xFF);
            out.write((value >>> 16) & 0xFF);
            out.write((value >>> 24) & 0xFF);
            return this;
        }

        private void le16(int value) {
            out.write(value & 0xFF);
            out.write((value >>> 8) & 0xFF);
        }

        /** The 16-byte fmt body: PCM, {@code channels} channels, {@code bits} per sample. */
        Builder format(int encoding, int channels, int sampleRate, int bits) {
            le16(encoding);
            le16(channels);
            le32(sampleRate);
            le32(sampleRate * channels * bits / 8);
            le16(channels * bits / 8);
            le16(bits);
            return this;
        }

        byte[] done() {
            return out.toByteArray();
        }
    }

    private static void le32(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }
}
