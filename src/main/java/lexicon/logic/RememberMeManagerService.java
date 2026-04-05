package lexicon.logic;

/**
 * Service interface for remember-me token management
 * Handles token creation, validation, rotation, and cleanup
 */
public interface RememberMeManagerService {

    /**
     * Create a new remember-me token for the user.
     * Returns the raw (unhashed) token to be set in a cookie.
     */
    String createToken(int userId);

    /**
     * Validate a raw token from a cookie.
     * If valid, rotates the token (deletes old, creates new) and returns the userId.
     * Returns null if the token is invalid or expired.
     * The caller receives the new raw token via the return value of the second element.
     */
    RememberMeResult validateAndRotate(String rawToken);

    /**
     * Delete all remember-me tokens for a user.
     */
    void clearTokens(int userId);

    /**
     * Delete all expired tokens (housekeeping).
     */
    void cleanupExpiredTokens();

    /**
     * Result of a validate-and-rotate operation
     */
    record RememberMeResult(int userId, String newRawToken) {}
}
