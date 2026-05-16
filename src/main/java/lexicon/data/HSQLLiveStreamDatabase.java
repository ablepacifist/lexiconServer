package lexicon.data;

import lexicon.object.LiveStreamQueue;
import lexicon.object.LiveStreamState;
import lexicon.object.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HSQL implementation of live stream database with channel support.
 * Each channel ("music" or "video") has its own state row and queue items.
 */
@Repository
public class HSQLLiveStreamDatabase implements ILiveStreamDatabase {
    
    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";
    private HikariDataSource dataSource;
    
    @Autowired
    private IMediaDatabase mediaDatabase;
    
    public HSQLLiveStreamDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DATABASE_URL);
        config.setUsername("SA");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("livestream-db");
        this.dataSource = new HikariDataSource(config);
    }
    
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @PreDestroy
    public void cleanup() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    /**
     * Initialize database tables with channel support.
     * Handles migration from old singleton schema.
     */
    @PostConstruct
    public void initializeTables() {
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            
            // Try to drop the old singleton CHECK constraint (may fail if already removed or table doesn't exist)
            try {
                stmt.execute("ALTER TABLE live_stream_state DROP CONSTRAINT single_row");
                System.out.println("Dropped old single_row constraint");
            } catch (SQLException e) {
                // Constraint doesn't exist or table doesn't exist - that's fine
            }
            
            // Create state table without CHECK constraint (IF NOT EXISTS)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS live_stream_state (
                    id INT PRIMARY KEY,
                    current_media_id INT,
                    current_start_time TIMESTAMP,
                    current_position_ms BIGINT,
                    required_skip_votes INT
                )
            """);
            
            // Add channel column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE live_stream_state ADD COLUMN channel VARCHAR(10) DEFAULT 'video'");
                System.out.println("Added channel column to live_stream_state");
            } catch (SQLException e) {
                // Column already exists
            }
            
            // Ensure existing row(s) have a channel value
            stmt.execute("UPDATE live_stream_state SET channel = 'video' WHERE channel IS NULL");
            
            // Create queue table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS live_stream_queue (
                    id INT PRIMARY KEY,
                    media_file_id INT NOT NULL,
                    added_by INT NOT NULL,
                    added_at TIMESTAMP NOT NULL,
                    position INT NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
            """);
            
            // Add channel column to queue if it doesn't exist
            try {
                stmt.execute("ALTER TABLE live_stream_queue ADD COLUMN channel VARCHAR(10) DEFAULT 'video'");
                System.out.println("Added channel column to live_stream_queue");
            } catch (SQLException e) {
                // Column already exists
            }
            
            // Ensure existing queue items have a channel value
            stmt.execute("UPDATE live_stream_queue SET channel = 'video' WHERE channel IS NULL");
            
            // Create skip votes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS live_stream_skip_votes (
                    id INT PRIMARY KEY IDENTITY,
                    queue_item_id INT NOT NULL,
                    user_id INT NOT NULL,
                    voted_at TIMESTAMP NOT NULL,
                    UNIQUE(queue_item_id, user_id)
                )
            """);
            
            // Initialize state rows for both channels
            // Video stays at id=1 (legacy), music gets id=2
            initChannelRow(stmt, 1, "video");
            initChannelRow(stmt, 2, "music");
            
            stmt.close();

            // Add performance indexes
            Statement idxStmt = conn.createStatement();
            try { idxStmt.execute("CREATE INDEX IF NOT EXISTS idx_lsq_channel_status ON live_stream_queue(channel, status)"); } catch (SQLException ignored) {}
            try { idxStmt.execute("CREATE INDEX IF NOT EXISTS idx_lsq_channel_position ON live_stream_queue(channel, position)"); } catch (SQLException ignored) {}
            try { idxStmt.execute("CREATE INDEX IF NOT EXISTS idx_lss_channel ON live_stream_state(channel)"); } catch (SQLException ignored) {}
            try { idxStmt.execute("CREATE INDEX IF NOT EXISTS idx_lssv_queue ON live_stream_skip_votes(queue_item_id)"); } catch (SQLException ignored) {}
            idxStmt.close();

            System.out.println("Live stream tables initialized with channel support");
            
        } catch (SQLException e) {
            System.err.println("Error initializing live stream tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initChannelRow(Statement stmt, int id, String channel) throws SQLException {
        ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM live_stream_state WHERE channel = '" + channel + "'");
        rs.next();
        int count = rs.getInt(1);
        rs.close();
        
        if (count == 0) {
            // For video channel, check if the old id=1 row exists and adopt it
            if (channel.equals("video")) {
                ResultSet existing = stmt.executeQuery("SELECT COUNT(*) FROM live_stream_state WHERE id = 1");
                existing.next();
                if (existing.getInt(1) > 0) {
                    stmt.execute("UPDATE live_stream_state SET channel = 'video' WHERE id = 1");
                    existing.close();
                    System.out.println("Adopted existing id=1 row for video channel");
                    return;
                }
                existing.close();
            }
            
            // Find a safe id that doesn't conflict
            ResultSet maxRs = stmt.executeQuery("SELECT MAX(id) FROM live_stream_state");
            int safeId = id;
            if (maxRs.next()) {
                int maxId = maxRs.getInt(1);
                if (safeId <= maxId) {
                    safeId = maxId + 1;
                }
            }
            maxRs.close();
            
            try {
                stmt.execute("INSERT INTO live_stream_state (id, channel, current_media_id, current_position_ms, required_skip_votes) " +
                    "VALUES (" + safeId + ", '" + channel + "', 0, 0, 1)");
                System.out.println("Initialized " + channel + " channel state row (id=" + safeId + ")");
            } catch (SQLException e) {
                System.err.println("Failed to insert " + channel + " channel row: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void initializeStreamState(String channel) {
        // Already handled in initializeTables
    }
    
    @Override
    public LiveStreamState getStreamState(String channel) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM live_stream_state WHERE channel = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channel);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        LiveStreamState state = new LiveStreamState();
                        state.setId(rs.getInt("id"));
                        state.setChannel(channel);
                        state.setCurrentMediaId(rs.getInt("current_media_id"));
                        
                        Timestamp startTime = rs.getTimestamp("current_start_time");
                        if (startTime != null) {
                            state.setCurrentStartTime(startTime.toLocalDateTime());
                        }
                        
                        state.setCurrentPositionMs(rs.getLong("current_position_ms"));
                        state.setRequiredSkipVotes(rs.getInt("required_skip_votes"));
                        
                        // Load current media if exists
                        if (state.getCurrentMediaId() > 0) {
                            MediaFile media = mediaDatabase.getMediaFile(state.getCurrentMediaId());
                            state.setCurrentMedia(media);

                            // Load currently playing item's skip vote count
                            try (PreparedStatement qStmt = conn.prepareStatement(
                                    "SELECT id FROM live_stream_queue WHERE channel = ? AND status = 'PLAYING' LIMIT 1")) {
                                qStmt.setString(1, channel);
                                try (ResultSet qRs = qStmt.executeQuery()) {
                                    if (qRs.next()) {
                                        int playingQueueId = qRs.getInt("id");
                                        state.setTotalSkipVotes(getSkipVoteCount(playingQueueId));
                                    }
                                }
                            }
                        }
                        
                        return state;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting stream state for channel " + channel + ": " + e.getMessage());
        }
        
        LiveStreamState empty = new LiveStreamState();
        empty.setChannel(channel);
        return empty;
    }
    
    @Override
    public void setCurrentMedia(String channel, int mediaId, long positionMs) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE live_stream_state SET current_media_id = ?, current_start_time = ?, current_position_ms = ? WHERE channel = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, mediaId);
                pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setLong(3, positionMs);
                pstmt.setString(4, channel);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error setting current media for channel " + channel + ": " + e.getMessage());
        }
    }
    
    @Override
    public int getNextQueueId() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT MAX(id) FROM live_stream_queue";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting next queue ID: " + e.getMessage());
        }
        return 1;
    }
    
    @Override
    public int addToQueue(String channel, int mediaFileId, int userId) {
        try (Connection conn = getConnection()) {
            int newId = getNextQueueId();
            
            // Find next position for this channel
            String positionSql = "SELECT COALESCE(MAX(position), -1) + 1 FROM live_stream_queue WHERE channel = ?";
            int position = 0;
            
            try (PreparedStatement pstmt = conn.prepareStatement(positionSql)) {
                pstmt.setString(1, channel);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        position = rs.getInt(1);
                    }
                }
            }
            
            String sql = "INSERT INTO live_stream_queue (id, channel, media_file_id, added_by, added_at, position, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newId);
                pstmt.setString(2, channel);
                pstmt.setInt(3, mediaFileId);
                pstmt.setInt(4, userId);
                pstmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setInt(6, position);
                pstmt.setString(7, LiveStreamQueue.QueueStatus.QUEUED.name());
                pstmt.executeUpdate();
            }
            
            return newId;
            
        } catch (SQLException e) {
            System.err.println("Error adding to queue: " + e.getMessage());
            return -1;
        }
    }
    
    @Override
    public boolean removeFromQueue(String channel, int queueId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM live_stream_queue WHERE id = ? AND channel = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                pstmt.setString(2, channel);
                int rows = pstmt.executeUpdate();
                
                if (rows > 0) {
                    reorderQueue(channel);
                    clearSkipVotesForItem(queueId);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error removing from queue: " + e.getMessage());
        }
        return false;
    }
    
    @Override
    public void updateQueueStatus(int queueId, LiveStreamQueue.QueueStatus status) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE live_stream_queue SET status = ? WHERE id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, status.name());
                pstmt.setInt(2, queueId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error updating queue status: " + e.getMessage());
        }
    }
    
    @Override
    public void reorderQueue(String channel) {
        try (Connection conn = getConnection()) {
            String selectSql = "SELECT id FROM live_stream_queue WHERE channel = ? ORDER BY position ASC";
            List<Integer> ids = new ArrayList<>();
            
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, channel);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getInt("id"));
                    }
                }
            }
            
            String updateSql = "UPDATE live_stream_queue SET position = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                for (int i = 0; i < ids.size(); i++) {
                    pstmt.setInt(1, i);
                    pstmt.setInt(2, ids.get(i));
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error reordering queue: " + e.getMessage());
        }
    }
    
    @Override
    public boolean addSkipVote(int queueId, int userId) {
        if (hasUserVotedSkip(queueId, userId)) {
            return false;
        }
        
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO live_stream_skip_votes (queue_item_id, user_id, voted_at) VALUES (?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                pstmt.setInt(2, userId);
                pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error adding skip vote: " + e.getMessage());
        }
        return false;
    }
    
    @Override
    public List<Integer> getSkipVotes(int queueId) {
        List<Integer> votes = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT user_id FROM live_stream_skip_votes WHERE queue_item_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        votes.add(rs.getInt("user_id"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting skip votes: " + e.getMessage());
        }
        return votes;
    }
    
    @Override
    public int getSkipVoteCount(int queueId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT COUNT(*) FROM live_stream_skip_votes WHERE queue_item_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting skip vote count: " + e.getMessage());
        }
        return 0;
    }
    
    @Override
    public void clearSkipVotesForItem(int queueId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM live_stream_skip_votes WHERE queue_item_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error clearing skip votes for item: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasUserVotedSkip(int queueId, int userId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT COUNT(*) FROM live_stream_skip_votes WHERE queue_item_id = ? AND user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                pstmt.setInt(2, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking skip vote: " + e.getMessage());
        }
        return false;
    }
    
    @Override
    public Integer getCurrentPlayingQueueId(String channel) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT id FROM live_stream_queue WHERE channel = ? AND status = 'PLAYING' LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channel);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting current playing queue ID: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public List<LiveStreamQueue> getQueueItemsLightweight(String channel) {
        List<LiveStreamQueue> items = new ArrayList<>();
        
        try (Connection conn = getConnection()) {
            String sql = "SELECT q.id, q.channel, q.media_file_id, q.added_by, q.added_at, q.position, q.status, " +
                        "m.title, m.original_filename " +
                        "FROM live_stream_queue q " +
                        "LEFT JOIN media_files m ON q.media_file_id = m.id " +
                        "WHERE q.channel = ? " +
                        "ORDER BY q.position ASC";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channel);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        LiveStreamQueue item = new LiveStreamQueue();
                        item.setId(rs.getInt("id"));
                        item.setChannel(rs.getString("channel"));
                        item.setMediaFileId(rs.getInt("media_file_id"));
                        item.setAddedBy(rs.getInt("added_by"));
                        item.setAddedAt(rs.getTimestamp("added_at").toLocalDateTime());
                        item.setPosition(rs.getInt("position"));
                        item.setStatus(LiveStreamQueue.QueueStatus.valueOf(rs.getString("status")));
                        
                        lexicon.object.MediaFile lightMedia = new lexicon.object.MediaFile();
                        lightMedia.setId(rs.getInt("media_file_id"));
                        lightMedia.setTitle(rs.getString("title"));
                        lightMedia.setOriginalFilename(rs.getString("original_filename"));
                        item.setMediaFile(lightMedia);
                        
                        items.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting lightweight queue items for channel " + channel + ": " + e.getMessage());
        }
        
        return items;
    }
}
