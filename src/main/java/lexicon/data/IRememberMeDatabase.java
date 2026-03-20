package lexicon.data;

import lexicon.object.RememberMeToken;

import java.time.LocalDateTime;

/**
 * Database interface for remember-me token persistence
 */
public interface IRememberMeDatabase {

    /**
     * Store a new remember-me token
     */
    void storeToken(int userId, String tokenHash, LocalDateTime expiresAt);

    /**
     * Find a token by its hash
     */
    RememberMeToken findByTokenHash(String tokenHash);

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
