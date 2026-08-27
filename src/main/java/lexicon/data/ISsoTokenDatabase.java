package lexicon.data;

import java.time.LocalDateTime;

/**
 * Database interface for SSO token persistence.
 * SSO tokens are single-use, short-lived tokens for cross-service authentication.
 */
public interface ISsoTokenDatabase {

    void storeToken(int userId, String tokenHash, LocalDateTime expiresAt);

    /**
     * Find a token by its hash. Returns null if not found.
     * Result: [id, userId, expired (1=yes, 0=no)]
     */
    int[] findUserIdByTokenHash(String tokenHash);

    void deleteById(int tokenId);

    void deleteByUserId(int userId);

    int deleteExpired();
}
