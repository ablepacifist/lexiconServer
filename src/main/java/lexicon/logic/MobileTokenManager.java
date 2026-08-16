package lexicon.logic;

import lexicon.data.IMobileTokenDatabase;
import lexicon.object.MobileToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Implementation of MobileTokenService.
 * Handles token generation, hashing, validation, and sliding rotation.
 */
@Service
public class MobileTokenManager implements MobileTokenService {

    private static final int TOKEN_VALIDITY_DAYS = 90;

    /**
     * Rotate once the token has less than this long left before expiry.
     * Half the lifetime, so a regularly-used app rotates roughly every 45 days
     * rather than on every request (which would race concurrent requests).
     */
    private static final Duration RENEW_WHEN_REMAINING = Duration.ofDays(TOKEN_VALIDITY_DAYS / 2);

    private final IMobileTokenDatabase database;
    private final SecureRandom secureRandom = new SecureRandom();

    public MobileTokenManager(IMobileTokenDatabase database) {
        this.database = database;
    }

    @Override
    public String createToken(int userId) {
        String rawToken = generateRandomToken();
        String tokenHash = sha256(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);

        database.storeToken(userId, tokenHash, expiresAt);
        return rawToken;
    }

    @Override
    public MobileTokenResult validateAndRotate(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return null;
        }

        String tokenHash = sha256(rawToken);
        MobileToken token = database.findByTokenHash(tokenHash);

        if (token == null) {
            return null;
        }

        if (token.isExpired()) {
            // Expired tokens are useless — drop them on sight
            database.deleteById(token.getId());
            return null;
        }

        // Still comfortably valid — authenticate without touching the row
        LocalDateTime renewAfter = token.getExpiresAt().minus(RENEW_WHEN_REMAINING);
        if (LocalDateTime.now().isBefore(renewAfter)) {
            return new MobileTokenResult(token.getUserId(), null);
        }

        // Past the renewal threshold — issue a fresh token and retire the old one
        database.deleteById(token.getId());
        String newRawToken = generateRandomToken();
        String newTokenHash = sha256(newRawToken);
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);
        database.storeToken(token.getUserId(), newTokenHash, newExpiry);

        return new MobileTokenResult(token.getUserId(), newRawToken);
    }

    @Override
    public void clearTokens(int userId) {
        database.deleteByUserId(userId);
    }

    @Override
    public void cleanupExpiredTokens() {
        int deleted = database.deleteExpired();
        if (deleted > 0) {
            System.out.println("Cleaned up " + deleted + " expired mobile tokens");
        }
    }

    private String generateRandomToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
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
}
