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
 * HSQL implementation of live stream database
 * Uses HikariCP connection pool to prevent getConnection() blocking on scheduler threads
 */
@Repository
public class HSQLLiveStreamDatabase implements ILiveStreamDatabase {
    
    // Use same database as media files and playlists
    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";
    
    private HikariDataSource dataSource;
    
    @Autowired
    private IMediaDatabase mediaDatabase;
    
    public HSQLLiveStreamDatabase() {
        // Initialize connection pool
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DATABASE_URL);
        config.setUsername("SA");
        config.setPassword("");
        config.setMaximumPoolSize(10); // Small pool for livestream ops
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000); // 5 sec timeout when no conn available
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
     * Initialize database tables if they don't exist
     */
    @PostConstruct
    public void initializeTables() {
        try (Connection conn = getConnection()) {
            // Create live_stream_state table (singleton)
            String createStateTable = """
                CREATE TABLE IF NOT EXISTS live_stream_state (
                    id INT PRIMARY KEY,
                    current_media_id INT,
                    current_start_time TIMESTAMP,
                    current_position_ms BIGINT,
                    required_skip_votes INT,
                    CONSTRAINT single_row CHECK (id = 1)
                )
            """;
            
            // Create live_stream_queue table
            String createQueueTable = """
                CREATE TABLE IF NOT EXISTS live_stream_queue (
                    id INT PRIMARY KEY,
                    media_file_id INT NOT NULL,
                    added_by INT NOT NULL,
                    added_at TIMESTAMP NOT NULL,
                    position INT NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
            """;
            
            // Create skip votes table
            String createVotesTable = """
                CREATE TABLE IF NOT EXISTS live_stream_skip_votes (
                    id INT PRIMARY KEY IDENTITY,
                    queue_item_id INT NOT NULL,
                    user_id INT NOT NULL,
                    voted_at TIMESTAMP NOT NULL,
                    UNIQUE(queue_item_id, user_id)
                )
            """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createStateTable);
                stmt.execute(createQueueTable);
                stmt.execute(createVotesTable);
                
                // Initialize default state if not exists (HSQLDB compatible)
                // First check if record exists
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM live_stream_state WHERE id = 1");
                rs.next();
                if (rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO live_stream_state (id, current_position_ms, required_skip_votes) VALUES (1, 0, 1)");
                }
                rs.close();
            }
            
        } catch (SQLException e) {
            System.err.println("Error initializing live stream tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void initializeStreamState() {
        try (Connection conn = getConnection()) {
            // Check if state exists
            String checkSql = "SELECT COUNT(*) FROM live_stream_state WHERE id = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {
                
                if (rs.next() && rs.getInt(1) == 0) {
                    // Insert initial state with no media
                    String insertSql = """
                        INSERT INTO live_stream_state (id, current_media_id, current_start_time, current_position_ms, required_skip_votes)
                        VALUES (1, 0, NULL, 0, 1)
                    """;
                    try (Statement insertStmt = conn.createStatement()) {
                        insertStmt.execute(insertSql);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error initializing stream state: " + e.getMessage());
        }
    }
    
    @Override
    public LiveStreamState getStreamState() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM live_stream_state WHERE id = 1";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                if (rs.next()) {
                    LiveStreamState state = new LiveStreamState();
                    state.setId(rs.getInt("id"));
                    state.setCurrentMediaId(rs.getInt("current_media_id"));
                    
                    Timestamp startTime = rs.getTimestamp("current_start_time");
                    if (startTime != null) {
                        state.setCurrentStartTime(startTime.toLocalDateTime());
                    }
                    
                    state.setCurrentPositionMs(rs.getLong("current_position_ms"));
                    state.setRequiredSkipVotes(rs.getInt("required_skip_votes"));
                    
                    // Load current media if exists (lightweight - no full queue load)
                    if (state.getCurrentMediaId() > 0) {
                        MediaFile media = mediaDatabase.getMediaFile(state.getCurrentMediaId());
                        state.setCurrentMedia(media);

                        // Only load currently playing item's skip vote count, not entire queue
                        try (Statement queueStmt = conn.createStatement();
                             ResultSet queueRs = queueStmt.executeQuery("SELECT id FROM live_stream_queue WHERE status = 'PLAYING' LIMIT 1")) {
                            if (queueRs.next()) {
                                int playingQueueId = queueRs.getInt("id");
                                state.setTotalSkipVotes(getSkipVoteCount(playingQueueId));
                            }
                        }
                    }
                    
                    return state;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting stream state: " + e.getMessage());
        }
        
        return new LiveStreamState();
    }
    
    @Override
    public void setCurrentMedia(int mediaId, long positionMs) {
        try (Connection conn = getConnection()) {
            String sql = """
                UPDATE live_stream_state 
                SET current_media_id = ?, current_start_time = ?, current_position_ms = ?
                WHERE id = 1
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, mediaId);
                pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setLong(3, positionMs);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error setting current media: " + e.getMessage());
        }
    }
    
    @Override
    public void clearSkipVotes() {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM live_stream_skip_votes";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            System.err.println("Error clearing skip votes: " + e.getMessage());
        }
    }
    
    // NOTE: Old slow getQueueItems() method removed - use getQueueItemsLightweight() instead
    // The slow method was loading full MediaFile objects for each queue item, causing 35+ second delays
    
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
    public int addToQueue(int mediaFileId, int userId) {
        try (Connection conn = getConnection()) {
            int newId = getNextQueueId();
            
            // Find next position (max position + 1)
            String positionSql = "SELECT COALESCE(MAX(position), -1) + 1 FROM live_stream_queue";
            int position = 0;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(positionSql)) {
                if (rs.next()) {
                    position = rs.getInt(1);
                }
            }
            
            // Insert queue item
            String sql = """
                INSERT INTO live_stream_queue (id, media_file_id, added_by, added_at, position, status)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newId);
                pstmt.setInt(2, mediaFileId);
                pstmt.setInt(3, userId);
                pstmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setInt(5, position);
                pstmt.setString(6, LiveStreamQueue.QueueStatus.QUEUED.name());
                pstmt.executeUpdate();
            }
            
            return newId;
            
        } catch (SQLException e) {
            System.err.println("Error adding to queue: " + e.getMessage());
            return -1;
        }
    }
    
    @Override
    public boolean removeFromQueue(int queueId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM live_stream_queue WHERE id = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, queueId);
                int rows = pstmt.executeUpdate();
                
                if (rows > 0) {
                    reorderQueue();
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
    public void reorderQueue() {
        try (Connection conn = getConnection()) {
            // Get all items ordered by position
            String selectSql = "SELECT id FROM live_stream_queue ORDER BY position ASC";
            List<Integer> ids = new ArrayList<>();
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSql)) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
            }
            
            // Update positions sequentially
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
        // Check if already voted
        if (hasUserVotedSkip(queueId, userId)) {
            return false;
        }
        
        try (Connection conn = getConnection()) {
            String sql = """
                INSERT INTO live_stream_skip_votes (queue_item_id, user_id, voted_at)
                VALUES (?, ?, ?)
            """;
            
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
            votes = getSkipVotes(conn, queueId);
        } catch (SQLException e) {
            System.err.println("Error getting skip votes: " + e.getMessage());
        }
        
        return votes;
    }

    private List<Integer> getSkipVotes(Connection conn, int queueId) throws SQLException {
        List<Integer> votes = new ArrayList<>();

        String sql = "SELECT user_id FROM live_stream_skip_votes WHERE queue_item_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, queueId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    votes.add(rs.getInt("user_id"));
                }
            }
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
    public Integer getCurrentPlayingQueueId() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT id FROM live_stream_queue WHERE status = 'PLAYING' LIMIT 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting current playing queue ID: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public List<LiveStreamQueue> getQueueItemsLightweight() {
        List<LiveStreamQueue> items = new ArrayList<>();
        
        try (Connection conn = getConnection()) {
            // Only get queue data - NO media file joins!
            String sql = "SELECT q.id, q.media_file_id, q.added_by, q.added_at, q.position, q.status, " +
                        "m.title, m.original_filename " +
                        "FROM live_stream_queue q " +
                        "LEFT JOIN media_files m ON q.media_file_id = m.id " +
                        "ORDER BY q.position ASC";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    LiveStreamQueue item = new LiveStreamQueue();
                    item.setId(rs.getInt("id"));
                    item.setMediaFileId(rs.getInt("media_file_id"));
                    item.setAddedBy(rs.getInt("added_by"));
                    item.setAddedAt(rs.getTimestamp("added_at").toLocalDateTime());
                    item.setPosition(rs.getInt("position"));
                    item.setStatus(LiveStreamQueue.QueueStatus.valueOf(rs.getString("status")));
                    
                    // Create lightweight media file with just title/filename
                    lexicon.object.MediaFile lightMedia = new lexicon.object.MediaFile();
                    lightMedia.setId(rs.getInt("media_file_id"));
                    lightMedia.setTitle(rs.getString("title"));
                    lightMedia.setOriginalFilename(rs.getString("original_filename"));
                    item.setMediaFile(lightMedia);
                    
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting lightweight queue items: " + e.getMessage());
        }
        
        return items;
    }
}
