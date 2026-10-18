package lexicon.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lexicon.logic.VoiceRelayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Voice turns from a browser microphone.
 *
 * Exists because aragon's own microphone is not somewhere a person can always
 * stand. The browser records a clip, this forwards it to Lexi on localhost, and
 * the reply (transcript, answer text, and answer audio) comes back.
 *
 * Authentication is inherited: {@code /api/voice/**} matches no permitAll rule,
 * so it falls through to {@code /api/**} -> authenticated().
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceRelayService voiceRelay;

    public VoiceController(VoiceRelayService voiceRelay) {
        this.voiceRelay = voiceRelay;
    }

    /**
     * Whether the voice relay is usable, so the UI can explain itself before the
     * user records anything rather than failing after they have spoken.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
        if (unauthenticated(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("configured", voiceRelay.isConfigured());
        return ResponseEntity.ok(body);
    }

    /**
     * Relay one recorded clip. The body is the raw audio; whatever the browser's
     * MediaRecorder produced is fine, since Lexi sniffs the container.
     */
    @PostMapping(value = "/turn", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> turn(@RequestBody(required = false) byte[] clip,
                                       HttpServletRequest request) {
        if (unauthenticated(request)) {
            return json(HttpStatus.UNAUTHORIZED, "{\"error\":\"Not authenticated\"}");
        }
        try {
            VoiceRelayService.Relayed relayed = voiceRelay.relayTurn(clip);
            // Pass Lexi's status and body straight through: it already
            // distinguishes "no speech", "could not decode" and "brain down",
            // and re-wrapping those would only blur them.
            return json(HttpStatus.valueOf(relayed.status()), relayed.body());

        } catch (IllegalArgumentException e) {
            return json(HttpStatus.BAD_REQUEST, error(e.getMessage()));
        } catch (IllegalStateException e) {
            return json(HttpStatus.SERVICE_UNAVAILABLE, error(e.getMessage()));
        } catch (java.net.ConnectException e) {
            return json(HttpStatus.SERVICE_UNAVAILABLE,
                    error("Lexi is not running on this machine"));
        } catch (Exception e) {
            System.err.println("[Voice] Relay failed: " + e);
            return json(HttpStatus.INTERNAL_SERVER_ERROR,
                    error("Voice relay failed: " + e.getMessage()));
        }
    }

    private boolean unauthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null || session.getAttribute("userId") == null;
    }

    private ResponseEntity<String> json(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private String error(String message) {
        // Hand-built rather than a Map so the method can return Lexi's own JSON
        // string unchanged on the success path.
        return "{\"error\":\"" + String.valueOf(message).replace("\"", "'") + "\"}";
    }
}
