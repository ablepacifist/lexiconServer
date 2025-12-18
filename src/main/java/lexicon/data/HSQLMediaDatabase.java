package lexicon.data;

import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.object.PlaybackPosition;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

/**
 * HSQLDB implementation for media file storage
 * Uses same mydb database as players, but separate tables for organization
 */
@Repository
public class HSQLMediaDatabase implements IMediaDatabase {
    
    // Use same database as players (port 9002) but different tables
    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }
    
    @Override
    public int getNextMediaFileId() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT MAX(id) FROM media_files";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
                return 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 1;
        }
    }
    
    @Override
    public void addMediaFile(MediaFile mediaFile) {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO media_files (id, filename, original_filename, content_type, file_size, file_path, uploaded_by, upload_date, title, description, is_public, media_type, source_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mediaFile.getId());
                stmt.setString(2, mediaFile.getFilename());
                stmt.setString(3, mediaFile.getOriginalFilename());
                stmt.setString(4, mediaFile.getContentType());
                stmt.setLong(5, mediaFile.getFileSize());
                stmt.setString(6, mediaFile.getFilePath());
                stmt.setInt(7, mediaFile.getUploadedBy());
                stmt.setTimestamp(8, Timestamp.valueOf(mediaFile.getUploadDate()));
                stmt.setString(9, mediaFile.getTitle());
                stmt.setString(10, mediaFile.getDescription());
                stmt.setBoolean(11, mediaFile.isPublic());
                stmt.setString(12, mediaFile.getMediaType() != null ? mediaFile.getMediaType().name() : "OTHER");
                stmt.setString(13, mediaFile.getSourceUrl());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to add media file: " + e.getMessage(), e);
        }
    }
    
    @Override
    public MediaFile getMediaFile(int mediaFileId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mediaFileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToMediaFile(rs);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public List<MediaFile> getMediaFilesByPlayer(int playerId) {
        List<MediaFile> files = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE uploaded_by = ? ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        files.add(mapResultSetToMediaFile(rs));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return files;
    }
    
    @Override
    public List<MediaFile> getAllPublicMediaFiles() {
        List<MediaFile> files = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE is_public = TRUE ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSetToMediaFile(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return files;
    }
    
    @Override
    public List<MediaFile> searchMediaFiles(String searchTerm) {
        List<MediaFile> files = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE LOWER(title) LIKE ? OR LOWER(description) LIKE ? ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                String searchPattern = "%" + searchTerm.toLowerCase() + "%";
                stmt.setString(1, searchPattern);
                stmt.setString(2, searchPattern);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        files.add(mapResultSetToMediaFile(rs));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return files;
    }
    
    @Override
    public void updateMediaFile(MediaFile mediaFile) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE media_files SET filename = ?, original_filename = ?, content_type = ?, file_size = ?, file_path = ?, title = ?, description = ?, is_public = ?, media_type = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mediaFile.getFilename());
                stmt.setString(2, mediaFile.getOriginalFilename());
                stmt.setString(3, mediaFile.getContentType());
                stmt.setLong(4, mediaFile.getFileSize());
                stmt.setString(5, mediaFile.getFilePath());
                stmt.setString(6, mediaFile.getTitle());
                stmt.setString(7, mediaFile.getDescription());
                stmt.setBoolean(8, mediaFile.isPublic());
                stmt.setString(9, mediaFile.getMediaType() != null ? mediaFile.getMediaType().name() : MediaType.OTHER.name());
                stmt.setInt(10, mediaFile.getId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void deleteMediaFile(int mediaFileId) {
        try (Connection conn = getConnection()) {
            // Delete file data first (foreign key constraint)
            String deleteDataSql = "DELETE FROM file_data WHERE media_file_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteDataSql)) {
                stmt.setInt(1, mediaFileId);
                stmt.executeUpdate();
            }
            
            // Then delete the media file record
            String deleteFileSql = "DELETE FROM media_files WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteFileSql)) {
                stmt.setInt(1, mediaFileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public List<MediaFile> getRecentMediaFiles(int limit) {
        List<MediaFile> files = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files ORDER BY upload_date DESC LIMIT ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        files.add(mapResultSetToMediaFile(rs));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return files;
    }
    
    @Override
    public void storeFileData(int mediaFileId, byte[] fileData) {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO file_data (media_file_id, data) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mediaFileId);
                stmt.setBytes(2, fileData);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to store file data: " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[] getFileData(int mediaFileId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT data FROM file_data WHERE media_file_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mediaFileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBytes("data");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public void deleteFileData(int mediaFileId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM file_data WHERE media_file_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mediaFileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to map ResultSet to MediaFile object with new fields
     */
    private MediaFile mapResultSetToMediaFile(ResultSet rs) throws SQLException {
        MediaFile mediaFile = new MediaFile(
            rs.getInt("id"),
            rs.getString("filename"),
            rs.getString("original_filename"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getString("file_path"),
            rs.getInt("uploaded_by"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getBoolean("is_public")
        );
        
        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
        if (uploadTimestamp != null) {
            mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
        }
        
        // Set new fields
        String mediaTypeStr = rs.getString("media_type");
        if (mediaTypeStr != null) {
            mediaFile.setMediaType(lexicon.object.MediaType.fromString(mediaTypeStr));
        }
        
        mediaFile.setSourceUrl(rs.getString("source_url"));
        
        return mediaFile;
    }
    
    // ==================== Playback Position Tracking ====================
    
    @Override
    public void savePlaybackPosition(PlaybackPosition position) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);  // Start transaction
            
            // Check if position already exists
            String checkSql = "SELECT id FROM playback_positions WHERE user_id = ? AND media_file_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, position.getUserId());
                checkStmt.setInt(2, position.getMediaFileId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Update existing position
                        String updateSql = "UPDATE playback_positions SET position = ?, duration = ?, last_updated = ?, completed = ? WHERE user_id = ? AND media_file_id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setDouble(1, position.getPosition());
                            updateStmt.setDouble(2, position.getDuration());
                            updateStmt.setTimestamp(3, Timestamp.valueOf(java.time.LocalDateTime.now()));
                            updateStmt.setBoolean(4, position.isCompleted());
                            updateStmt.setInt(5, position.getUserId());
                            updateStmt.setInt(6, position.getMediaFileId());
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // Insert new position
                        String insertSql = "INSERT INTO playback_positions (user_id, media_file_id, position, duration, last_updated, completed) VALUES (?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setInt(1, position.getUserId());
                            insertStmt.setInt(2, position.getMediaFileId());
                            insertStmt.setDouble(3, position.getPosition());
                            insertStmt.setDouble(4, position.getDuration());
                            insertStmt.setTimestamp(5, Timestamp.valueOf(java.time.LocalDateTime.now()));
                            insertStmt.setBoolean(6, position.isCompleted());
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
            
            conn.commit();  // Commit transaction
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();  // Rollback on error
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }
    
    @Override
    public PlaybackPosition getPlaybackPosition(int userId, int mediaFileId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM playback_positions WHERE user_id = ? AND media_file_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, mediaFileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlaybackPosition position = new PlaybackPosition();
                        position.setId(rs.getInt("id"));
                        position.setUserId(rs.getInt("user_id"));
                        position.setMediaFileId(rs.getInt("media_file_id"));
                        position.setPosition(rs.getDouble("position"));
                        position.setDuration(rs.getDouble("duration"));
                        
                        Timestamp lastUpdated = rs.getTimestamp("last_updated");
                        if (lastUpdated != null) {
                            position.setLastUpdated(lastUpdated.toLocalDateTime());
                        }
                        
                        position.setCompleted(rs.getBoolean("completed"));
                        return position;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public List<PlaybackPosition> getUserPlaybackPositions(int userId) {
        List<PlaybackPosition> positions = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM playback_positions WHERE user_id = ? ORDER BY last_updated DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        PlaybackPosition position = new PlaybackPosition();
                        position.setId(rs.getInt("id"));
                        position.setUserId(rs.getInt("user_id"));
                        position.setMediaFileId(rs.getInt("media_file_id"));
                        position.setPosition(rs.getDouble("position"));
                        position.setDuration(rs.getDouble("duration"));
                        
                        Timestamp lastUpdated = rs.getTimestamp("last_updated");
                        if (lastUpdated != null) {
                            position.setLastUpdated(lastUpdated.toLocalDateTime());
                        }
                        
                        position.setCompleted(rs.getBoolean("completed"));
                        positions.add(position);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return positions;
    }
    
    @Override
    public void deletePlaybackPosition(int userId, int mediaFileId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM playback_positions WHERE user_id = ? AND media_file_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, mediaFileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
