package lexicon.logic;

/**
 * Service interface for mobile (Android/Capacitor) auth token management.
 *
 * Modelled on {@link RememberMeManagerService}: hashed at rest, rotated on use,
 * long-lived. Distinct from {@link SsoTokenService}, which is intentionally
 * single-use / 60-second and exists only for the Lexicon → Voice handoff.
 *
 * Unlike remember-me, this token is presented on *every* request (the native
 * WebView cannot rely on the session cookie), so rotating on each use would
 * race whenever the app fires concurrent requests. Rotation is therefore
 * sliding: the token is renewed only once it passes the renewal threshold.
 */
public interface MobileTokenService {

    /**
     * Create a new mobile token for the user.
     * Returns the raw (unhashed) token for the client to store.
     */
    String createToken(int userId);

    /**
     * Validate a raw bearer token.
     * Returns null if the token is unknown or expired.
     * If the token is valid but past its renewal threshold it is rotated
     * (old deleted, new stored) and the fresh raw token is returned in
     * {@link MobileTokenResult#newRawToken()}; otherwise that field is null.
     */
    MobileTokenResult validateAndRotate(String rawToken);

    /**
     * Delete all mobile tokens for a user (logout).
     */
    void clearTokens(int userId);

    /**
     * Delete all expired tokens (housekeeping).
     */
    void cleanupExpiredTokens();

    /**
     * Result of a validate-and-rotate operation.
     * {@code newRawToken} is null when no rotation was needed on this request.
     */
    record MobileTokenResult(int userId, String newRawToken) {}
}
