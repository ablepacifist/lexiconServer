package lexicon.data;

import lexicon.object.PushSubscription;
import org.springframework.stereotype.Repository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HSQLPushSubscriptionDatabase implements IPushSubscriptionDatabase {

    private final String DATABASE_URL =
            System.getProperty("database.url",
                    System.getenv().getOrDefault("DATABASE_URL", "jdbc:hsqldb:hsql://localhost:9002/mydb"));
    private final HikariDataSource dataSource;

    public HSQLPushSubscriptionDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DATABASE_URL);
        config.setUsername("SA");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);
        config.setPoolName("push-sub-db");
        this.dataSource = new HikariDataSource(config);
    }

    @PreDestroy
    public void cleanup() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void saveSubscription(PushSubscription sub) {
        // MERGE: update if endpoint exists, insert if not
        String sql = "MERGE INTO push_subscriptions USING (VALUES(?, ?, ?, ?, ?)) " +
                "AS vals(user_id, endpoint, p256dh, auth, user_agent) " +
                "ON push_subscriptions.endpoint = vals.endpoint " +
                "WHEN MATCHED THEN UPDATE SET user_id = vals.user_id, p256dh = vals.p256dh, auth = vals.auth, user_agent = vals.user_agent " +
                "WHEN NOT MATCHED THEN INSERT (user_id, endpoint, p256dh, auth, user_agent) VALUES (vals.user_id, vals.endpoint, vals.p256dh, vals.auth, vals.user_agent)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, sub.getUserId());
            stmt.setString(2, sub.getEndpoint());
            stmt.setString(3, sub.getP256dh());
            stmt.setString(4, sub.getAuth());
            stmt.setString(5, sub.getUserAgent());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteByEndpoint(String endpoint) {
        String sql = "DELETE FROM push_subscriptions WHERE endpoint = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, endpoint);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteByUserId(int userId) {
        String sql = "DELETE FROM push_subscriptions WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<PushSubscription> getByUserId(int userId) {
        List<PushSubscription> results = new ArrayList<>();
        String sql = "SELECT id, user_id, endpoint, p256dh, auth, user_agent, created_at FROM push_subscriptions WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    @Override
    public List<PushSubscription> getAll() {
        List<PushSubscription> results = new ArrayList<>();
        String sql = "SELECT id, user_id, endpoint, p256dh, auth, user_agent, created_at FROM push_subscriptions";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    private PushSubscription mapRow(ResultSet rs) throws SQLException {
        PushSubscription sub = new PushSubscription();
        sub.setId(rs.getLong("id"));
        sub.setUserId(rs.getInt("user_id"));
        sub.setEndpoint(rs.getString("endpoint"));
        sub.setP256dh(rs.getString("p256dh"));
        sub.setAuth(rs.getString("auth"));
        sub.setUserAgent(rs.getString("user_agent"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) sub.setCreatedAt(ts.toLocalDateTime());
        return sub;
    }
}
