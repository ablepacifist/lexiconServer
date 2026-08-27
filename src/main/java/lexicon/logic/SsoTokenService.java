package lexicon.logic;

import lexicon.data.ISsoTokenDatabase;
import lexicon.object.Player;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * SSO token service — generates short-lived, single-use tokens
 * for cross-service authentication (Lexicon → Voice bridge).
 */
@Service
public class SsoTokenService {

    private static final int TOKEN_VALIDITY_SECONDS = 60;

    private final ISsoTokenDatabase database;
    private final PlayerManagerService playerManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public SsoTokenService(ISsoTokenDatabase database, PlayerManagerService playerManager) {
        this.database = database;
        this.playerManager = playerManager;
    }

    /**
     * Generate a single-use SSO token for the given user.
     * Token expires in 60 seconds.
     */
    public String generateToken(int userId) {
        // Clean up any existing tokens for this user (only one active at a time)
        database.deleteByUserId(userId);

        String rawToken = generateRandomToken();
        String tokenHash = sha256(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(TOKEN_VALIDITY_SECONDS);

        database.storeToken(userId, tokenHash, expiresAt);
        return rawToken;
    }

    /**
     * Validate and consume an SSO token.
     * Returns the user info if valid, null otherwise.
     * The token is always deleted (single-use).
     */
    public SsoValidationResult validateToken(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return null;
        }

        String tokenHash = sha256(rawToken);
        int[] result = database.findUserIdByTokenHash(tokenHash);

        if (result == null) {
            return null;
        }

        int tokenId = result[0];
        int userId = result[1];
        boolean expired = result[2] == 1;

        // Always delete — single use
        database.deleteById(tokenId);

        if (expired) {
            return null;
        }

        Player player = playerManager.getPlayerById(userId);
        if (player == null) {
            return null;
        }

        return new SsoValidationResult(
                userId,
                player.getUsername(),
                player.getDisplayName() != null ? player.getDisplayName() : player.getUsername()
        );
    }

    public void cleanupExpired() {
        database.deleteExpired();
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record SsoValidationResult(int userId, String username, String displayName) {}
}
