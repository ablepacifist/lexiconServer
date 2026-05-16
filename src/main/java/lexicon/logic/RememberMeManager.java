package lexicon.logic;

import lexicon.data.IRememberMeDatabase;
import lexicon.object.RememberMeToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Implementation of RememberMeManagerService
 * Handles token generation, hashing, validation, and rotation
 */
@Service
public class RememberMeManager implements RememberMeManagerService {

    private static final int TOKEN_VALIDITY_DAYS = 30;

    private final IRememberMeDatabase database;
    private final SecureRandom secureRandom = new SecureRandom();

    public RememberMeManager(IRememberMeDatabase database) {
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
    public RememberMeResult validateAndRotate(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return null;
        }

        String tokenHash = sha256(rawToken);
        RememberMeToken token = database.findByTokenHash(tokenHash);

        if (token == null) {
            return null;
        }

        // Delete the old token regardless
        database.deleteById(token.getId());

        if (token.isExpired()) {
            return null;
        }

        // Token is valid — issue a fresh one
        String newRawToken = generateRandomToken();
        String newTokenHash = sha256(newRawToken);
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);
        database.storeToken(token.getUserId(), newTokenHash, newExpiry);

        return new RememberMeResult(token.getUserId(), newRawToken);
    }

    @Override
    public void clearTokens(int userId) {
        database.deleteByUserId(userId);
    }

    @Override
    public void cleanupExpiredTokens() {
        int deleted = database.deleteExpired();
        if (deleted > 0) {
            System.out.println("Cleaned up " + deleted + " expired remember-me tokens");
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
