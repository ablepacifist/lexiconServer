package lexicon.object;

import java.time.LocalDateTime;

/**
 * Represents a long-lived mobile auth token stored in the database.
 * Used by the Android (Capacitor) app in place of the session cookie,
 * which cannot survive being sent from the WebView's local origin.
 */
public class MobileToken {

    private final int id;
    private final int userId;
    private final String tokenHash;
    private final LocalDateTime expiresAt;
    private final LocalDateTime createdAt;

    public MobileToken(int id, int userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
