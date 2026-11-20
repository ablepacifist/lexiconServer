package lexicon.api;

import lexicon.logic.PermissionManagerService;
import lexicon.logic.SsoTokenService;
import lexicon.logic.SsoTokenService.SsoValidationResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {

    private final SsoTokenService ssoTokenService;
    private final PermissionManagerService permissionManagerService;

    public SsoController(SsoTokenService ssoTokenService, PermissionManagerService permissionManagerService) {
        this.ssoTokenService = ssoTokenService;
        this.permissionManagerService = permissionManagerService;
    }

    /**
     * Generate a single-use SSO token for the authenticated user.
     * Requires an active session (user must be logged in).
     */
    @PostMapping("/generate-token")
    public ResponseEntity<Map<String, Object>> generateToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        int userId = (int) session.getAttribute("userId");
        String token = ssoTokenService.generateToken(userId);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresInSeconds", 60
        ));
    }

    /**
     * Validate and consume an SSO token.
     * Called by the bridge server and by Alchemy to verify a user's identity and permissions.
     * Token is invalidated after this call (single-use).
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
        }

        SsoValidationResult result = ssoTokenService.validateToken(token);
        if (result == null) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "error", "Invalid or expired token"));
        }

        // Alchemy and the GTW bridge read role/permissions from here; they keep no lists of their own.
        Map<String, Object> out = new HashMap<>();
        out.put("valid", true);
        out.put("userId", result.userId());
        out.put("username", result.username());
        out.put("displayName", result.displayName());
        out.putAll(permissionManagerService.describe(result.userId()));
        return ResponseEntity.ok(out);
    }
}
