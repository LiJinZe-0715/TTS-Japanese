package app.voicevox.tts.infrastructure.net;

import app.voicevox.tts.domain.voice.VoiceCatalog;
import app.voicevox.tts.domain.voice.VoicePack;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Where voice packs are downloaded from: the voicevox_vvm GitHub release the catalogue was
 * generated from.
 */
public final class VoicePackSource {
    private static final String BASE =
            "https://github.com/VOICEVOX/voicevox_vvm/releases/download/";

    private VoicePackSource() {
    }

    public static String url(VoicePack pack) {
        return BASE + VoiceCatalog.RELEASE + "/" + pack.file;
    }

    /** An open connection to the pack's release asset, past every redirect. */
    public static HttpURLConnection open(VoicePack pack) throws IOException {
        return open(url(pack));
    }

    /**
     * Resolves redirects by hand so each hop can be checked: the release asset is served from
     * another host (objects.githubusercontent.com), and only https is ever followed. Cleartext
     * is blocked on this targetSdk anyway; a redirect to any other scheme used to surface as a
     * ClassCastException from the cast below.
     */
    static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop < 6; hop++) {
            URL parsed = new URL(current);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())) {
                throw new IOException("refusing non-https URL: " + current);
            }
            HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "TTS-Japanese");
            int code = connection.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) {
                    throw new IOException("redirect without location: " + current);
                }
                current = new URL(parsed, location).toString();
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("HTTP " + code + " for " + current);
            }
            return connection;
        }
        throw new IOException("too many redirects: " + url);
    }
}
