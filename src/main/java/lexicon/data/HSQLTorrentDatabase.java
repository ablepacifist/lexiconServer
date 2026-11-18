package lexicon.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lexicon.object.TorrentDownload;
import lexicon.utils.DatabaseConfig;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class HSQLTorrentDatabase implements ITorrentDatabase {

    private final HikariDataSource dataSource;

    public HSQLTorrentDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DatabaseConfig.url());
        config.setUsername("SA");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);
        config.setPoolName("torrent-db");
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

    @PostConstruct
    private void initializeSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS torrent_downloads (" +
                "info_hash VARCHAR(40) PRIMARY KEY, " +
                "tag VARCHAR(100), " +
                "source VARCHAR(2000), " +
                "display_name VARCHAR(1000), " +
                "added_by INT NOT NULL, " +
                "media_type VARCHAR(50), " +
                "is_public BOOLEAN DEFAULT false, " +
                "status VARCHAR(20) NOT NULL, " +
                "error VARCHAR(1000), " +
                "added_at TIMESTAMP NOT NULL, " +
                "completed_at TIMESTAMP)");

            // No foreign key to media_files: a torrent row has to outlive its media rows
            // long enough for the delete hook to decide whether the torrent itself should go.
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS torrent_files (" +
                "info_hash VARCHAR(40) NOT NULL, " +
                "file_index INT NOT NULL, " +
                "media_file_id INT NOT NULL, " +
                "link_path VARCHAR(1000), " +
                "PRIMARY KEY (info_hash, file_index))");

            System.out.println("✓ Torrent database schema initialized successfully");
        } catch (SQLException e) {
            System.err.println("Error initializing torrent schema:");
            e.printStackTrace();
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_torrent_downloads_user ON torrent_downloads(added_by)"); } catch (SQLException ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_torrent_downloads_status ON torrent_downloads(status)"); } catch (SQLException ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_torrent_files_media ON torrent_files(media_file_id)"); } catch (SQLException ignored) {}
            System.out.println("✓ Torrent indexes verified");
        } catch (SQLException e) {
            System.err.println("Error creating torrent indexes: " + e.getMessage());
        }
    }

    @Override
    public void save(TorrentDownload download) {
        String sql = "INSERT INTO torrent_downloads (info_hash, tag, source, display_name, added_by, " +
                "media_type, is_public, status, error, added_at, completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, download.getInfoHash());
            stmt.setString(2, download.getTag());
            stmt.setString(3, download.getSource());
            stmt.setString(4, download.getDisplayName());
            stmt.setInt(5, download.getAddedBy());
            stmt.setString(6, download.getMediaType());
            stmt.setBoolean(7, download.isPublic());
            stmt.setString(8, download.getStatus().name());
            stmt.setString(9, download.getError());
            stmt.setTimestamp(10, Timestamp.valueOf(
                    download.getAddedAt() == null ? java.time.LocalDateTime.now() : download.getAddedAt()));
            stmt.setTimestamp(11, download.getCompletedAt() == null ? null
                    : Timestamp.valueOf(download.getCompletedAt()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving torrent " + download.getInfoHash() + ": " + e.getMessage());
        }
    }

    @Override
    public void updateStatus(String infoHash, TorrentDownload.Status status, String error) {
        String sql = "UPDATE torrent_downloads SET status = ?, error = ?, completed_at = ? WHERE info_hash = ?";
        boolean finished = status == TorrentDownload.Status.COMPLETED
                || status == TorrentDownload.Status.FAILED
                || status == TorrentDownload.Status.CANCELLED;
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, error);
            stmt.setTimestamp(3, finished ? Timestamp.valueOf(java.time.LocalDateTime.now()) : null);
            stmt.setString(4, infoHash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating torrent " + infoHash + ": " + e.getMessage());
        }
    }

    @Override
    public Optional<TorrentDownload> find(String infoHash) {
        String sql = "SELECT * FROM torrent_downloads WHERE info_hash = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, infoHash);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            System.err.println("Error reading torrent " + infoHash + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<TorrentDownload> findActive() {
        return query("SELECT * FROM torrent_downloads WHERE status IN ('PREPARING','DOWNLOADING','IMPORTING') "
                + "ORDER BY added_at", stmt -> { });
    }

    @Override
    public List<TorrentDownload> findRecentByUser(int userId, int limit) {
        return query("SELECT * FROM torrent_downloads WHERE added_by = ? ORDER BY added_at DESC LIMIT " + limit,
                stmt -> stmt.setInt(1, userId));
    }

    @Override
    public int countActiveForUser(int userId) {
        String sql = "SELECT COUNT(*) FROM torrent_downloads WHERE added_by = ? "
                + "AND status IN ('PREPARING','DOWNLOADING','IMPORTING')";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("Error counting torrents for user " + userId + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void delete(String infoHash) {
        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM torrent_files WHERE info_hash = ?")) {
                stmt.setString(1, infoHash);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM torrent_downloads WHERE info_hash = ?")) {
                stmt.setString(1, infoHash);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error deleting torrent " + infoHash + ": " + e.getMessage());
        }
    }

    // ── imported files ───────────────────────────────────────────────────────────────

    @Override
    public void recordImportedFile(String infoHash, int fileIndex, int mediaFileId, String linkPath) {
        String sql = "INSERT INTO torrent_files (info_hash, file_index, media_file_id, link_path) VALUES (?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, infoHash);
            stmt.setInt(2, fileIndex);
            stmt.setInt(3, mediaFileId);
            stmt.setString(4, linkPath);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error recording torrent file " + infoHash + "#" + fileIndex + ": " + e.getMessage());
        }
    }

    @Override
    public Optional<TorrentDownload.ImportedFile> findByMediaFileId(int mediaFileId) {
        String sql = "SELECT * FROM torrent_files WHERE media_file_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mediaFileId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(readFile(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            System.err.println("Error reading torrent file for media " + mediaFileId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<TorrentDownload.ImportedFile> findImportedFiles(String infoHash) {
        String sql = "SELECT * FROM torrent_files WHERE info_hash = ? ORDER BY file_index";
        List<TorrentDownload.ImportedFile> files = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, infoHash);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(readFile(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error listing torrent files for " + infoHash + ": " + e.getMessage());
        }
        return files;
    }

    @Override
    public void deleteImportedFile(String infoHash, int fileIndex) {
        String sql = "DELETE FROM torrent_files WHERE info_hash = ? AND file_index = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, infoHash);
            stmt.setInt(2, fileIndex);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting torrent file " + infoHash + "#" + fileIndex + ": " + e.getMessage());
        }
    }

    // ── mapping ──────────────────────────────────────────────────────────────────────

    private interface Binder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    private List<TorrentDownload> query(String sql, Binder binder) {
        List<TorrentDownload> downloads = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            binder.bind(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    downloads.add(read(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error querying torrents: " + e.getMessage());
        }
        return downloads;
    }

    private TorrentDownload read(ResultSet rs) throws SQLException {
        TorrentDownload download = new TorrentDownload();
        download.setInfoHash(rs.getString("info_hash"));
        download.setTag(rs.getString("tag"));
        download.setSource(rs.getString("source"));
        download.setDisplayName(rs.getString("display_name"));
        download.setAddedBy(rs.getInt("added_by"));
        download.setMediaType(rs.getString("media_type"));
        download.setPublic(rs.getBoolean("is_public"));
        download.setStatus(TorrentDownload.Status.valueOf(rs.getString("status")));
        download.setError(rs.getString("error"));
        Timestamp added = rs.getTimestamp("added_at");
        if (added != null) {
            download.setAddedAt(added.toLocalDateTime());
        }
        Timestamp completed = rs.getTimestamp("completed_at");
        if (completed != null) {
            download.setCompletedAt(completed.toLocalDateTime());
        }
        return download;
    }

    private TorrentDownload.ImportedFile readFile(ResultSet rs) throws SQLException {
        return new TorrentDownload.ImportedFile(
                rs.getString("info_hash"),
                rs.getInt("file_index"),
                rs.getInt("media_file_id"),
                rs.getString("link_path"));
    }
}
