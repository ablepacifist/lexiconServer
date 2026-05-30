package lexicon.data;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;

@Repository
public class HSQLSsoTokenDatabase implements ISsoTokenDatabase {

    private final String DATABASE_URL =
            System.getProperty("database.url",
                    System.getenv().getOrDefault("DATABASE_URL", "jdbc:hsqldb:hsql://localhost:9002/mydb"));

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }

    @Override
    public void storeToken(int userId, String tokenHash, LocalDateTime expiresAt) {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO sso_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setString(2, tokenHash);
                stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store SSO token: " + e.getMessage(), e);
        }
    }

    @Override
    public int[] findUserIdByTokenHash(String tokenHash) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT id, user_id, CASE WHEN expires_at < CURRENT_TIMESTAMP THEN 1 ELSE 0 END AS expired FROM sso_tokens WHERE token_hash = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tokenHash);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new int[]{rs.getInt("id"), rs.getInt("user_id"), rs.getInt("expired")};
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find SSO token: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void deleteById(int tokenId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM sso_tokens WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, tokenId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete SSO token: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByUserId(int userId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM sso_tokens WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete SSO tokens for user: " + e.getMessage(), e);
        }
    }

    @Override
    public int deleteExpired() {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM sso_tokens WHERE expires_at < CURRENT_TIMESTAMP";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                return stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cleanup expired SSO tokens: " + e.getMessage(), e);
        }
    }
}
