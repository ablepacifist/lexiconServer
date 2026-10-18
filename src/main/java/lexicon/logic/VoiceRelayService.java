package lexicon.logic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Relays a recorded voice clip to Lexi and hands back Lexi's answer verbatim.
 *
 * Lexi runs on this machine bound to localhost and is deliberately not exposed.
 * The browser cannot reach it directly for two independent reasons: the page is
 * served over HTTPS, so a call to a plain-HTTP port is blocked as mixed content;
 * and putting Lexi on the LAN would mean shipping its shared secret to the
 * browser. Routing through here reuses the TLS termination and the session
 * cookie that already exist, and keeps the secret server-side.
 *
 * The body is raw audio bytes rather than multipart so every hop is a plain
 * byte forward — the container (WebM/Opus from Chrome, MP4/AAC from Safari) is
 * sniffed by Lexi's decoder rather than trusted from a filename.
 */
@Service
public class VoiceRelayService {

    /** Voice turns are slow by nature: transcription, then the LLM, then speech. */
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(180);
    private static final int MAX_CLIP_BYTES = 8 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final String lexiBaseUrl;
    private final String lexiToken;

    public VoiceRelayService(
            @Value("${lexi.base.url}") String lexiBaseUrl,
            @Value("${lexi.tool.token:}") String lexiToken) {
        this.lexiBaseUrl = lexiBaseUrl.replaceAll("/+$", "");
        this.lexiToken = lexiToken == null ? "" : lexiToken.trim();
    }

    /** Result of one relayed turn: Lexi's status code and its JSON body. */
    public record Relayed(int status, String body) {}

    public boolean isConfigured() {
        return !lexiToken.isEmpty();
    }

    /**
     * Forward a clip to Lexi's /turn endpoint.
     *
     * @throws IllegalArgumentException if the clip is empty or implausibly large
     * @throws IllegalStateException    if no Lexi token is configured
     */
    public Relayed relayTurn(byte[] clip) throws Exception {
        if (clip == null || clip.length == 0) {
            throw new IllegalArgumentException("No audio was uploaded");
        }
        if (clip.length > MAX_CLIP_BYTES) {
            throw new IllegalArgumentException("Clip is larger than 8MB");
        }
        if (!isConfigured()) {
            // Fail closed rather than calling Lexi unauthenticated, which would
            // only be rejected there anyway — but with a far more confusing error.
            throw new IllegalStateException("Lexi tool token is not configured on the server");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(lexiBaseUrl + "/turn"))
                .timeout(TURN_TIMEOUT)
                .header("Authorization", "Bearer " + lexiToken)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(clip))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Relayed(response.statusCode(), response.body());
    }
}
