package app.voicevox.tts.infrastructure.storage;

import app.voicevox.tts.application.port.VoicePackInstaller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * The one copy loop behind unzipping the dictionary, copying the bundled pack and downloading
 * a pack. The three differ only in whether anyone is watching and whether a digest is known.
 */
final class VerifiedCopy {

    /** 256 KB: large enough that a 60 MB download is not syscall-bound. */
    private static final int BUFFER = 256 * 1024;

    private VerifiedCopy() {
    }

    /**
     * Copies until EOF, reporting bytes written when {@code progress} is given and checking
     * the SHA-256 when {@code sha256} is.
     */
    static void copy(InputStream in, OutputStream out, VoicePackInstaller.Progress progress,
                     String stage, long total, String sha256) throws IOException {
        MessageDigest digest = null;
        if (sha256 != null) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
        byte[] buffer = new byte[BUFFER];
        long done = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (digest != null) digest.update(buffer, 0, read);
            done += read;
            if (progress != null) progress.onProgress(stage, done, total);
        }
        if (digest != null) {
            String actual = hex(digest.digest());
            if (!actual.equals(sha256.toLowerCase(Locale.ROOT))) {
                throw new IOException("校验失败（SHA-256 不符），已丢弃：" + actual.substring(0, 12) + "…");
            }
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }
}
