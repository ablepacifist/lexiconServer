package lexicon.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RememberMeService {

    private static final String COOKIE_NAME = "remember-me";
    private static final int TOKEN_VALIDITY_DAYS = 30;
    private static final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";

    private final SecureRandom secureRandom = new SecureRandom();

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }

    /**
     * Create a remember-me token for the user and set it as a cookie.
     */
    public void createToken(int userId, HttpServletResponse response) {
        // Generate a cryptographically secure random token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256(rawToken);

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);

        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO remember_me_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setString(2, tokenHash);
                stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Failed to store remember-me token: " + e.getMessage());
            return;
        }

        Cookie cookie = new Cookie(COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // false for HTTP tunnels; set true if all HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(TOKEN_VALIDITY_DAYS * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    /**
     * Validate a remember-me cookie and return the user ID if valid.
     * Rotates the token on successful validation to prevent replay attacks.
     * Returns null if invalid or expired.
     */
    public Integer validateAndRotate(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = getCookieValue(request);
        if (rawToken == null) {
            return null;
        }

        String tokenHash = sha256(rawToken);
        Integer userId = null;

        try (Connection conn = getConnection()) {
            // Look up the token
            String sql = "SELECT id, user_id, expires_at FROM remember_me_tokens WHERE token_hash = ?";
            int tokenId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tokenHash);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        tokenId = rs.getInt("id");
                        userId = rs.getInt("user_id");
                        Timestamp expiresAt = rs.getTimestamp("expires_at");
                        if (expiresAt.toLocalDateTime().isBefore(LocalDateTime.now())) {
                            // Token expired — delete it and clear cookie
                            deleteTokenById(conn, tokenId);
                            clearCookie(response);
                            return null;
                        }
                    } else {
                        // Token not found — possible theft, clear cookie
                        clearCookie(response);
                        return null;
                    }
                }
            }

            // Token is valid — rotate it (delete old, issue new)
            deleteTokenById(conn, tokenId);
        } catch (SQLException e) {
            System.err.println("Error validating remember-me token: " + e.getMessage());
            return null;
        }

        // Issue a fresh token
        if (userId != null) {
            createToken(userId, response);
        }

        return userId;
    }

    /**
     * Delete all remember-me tokens for a user and clear the cookie.
     */
    public void clearTokens(int userId, HttpServletResponse response) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM remember_me_tokens WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error clearing remember-me tokens: " + e.getMessage());
        }
        clearCookie(response);
    }

    /**
     * Delete expired tokens (housekeeping).
     */
    public void cleanupExpiredTokens() {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM remember_me_tokens WHERE expires_at < CURRENT_TIMESTAMP";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    System.out.println("Cleaned up " + deleted + " expired remember-me tokens");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up tokens: " + e.getMessage());
        }
    }

    private void deleteTokenById(Connection conn, int tokenId) throws SQLException {
        String sql = "DELETE FROM remember_me_tokens WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, tokenId);
            stmt.executeUpdate();
        }
    }

    private void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // delete
        response.addCookie(cookie);
    }

    private String getCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
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
