package lexicon.data;

import lexicon.object.MobileToken;

import java.time.LocalDateTime;

/**
 * Database interface for mobile auth token persistence
 */
public interface IMobileTokenDatabase {

    /**
     * Store a new mobile token
     */
    void storeToken(int userId, String tokenHash, LocalDateTime expiresAt);

    /**
     * Find a token by its hash
     */
    MobileToken findByTokenHash(String tokenHash);

    /**
     * Delete a specific token by ID
     */
    void deleteById(int tokenId);

    /**
     * Delete all tokens for a user
     */
    void deleteByUserId(int userId);

    /**
     * Delete all expired tokens
     */
    int deleteExpired();
}
