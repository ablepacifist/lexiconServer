package lexicon.data;

import lexicon.object.MediaFile;
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
            String sql = "INSERT INTO media_files (id, filename, original_filename, content_type, file_size, file_path, uploaded_by, upload_date, title, description, is_public) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                        
                        return mediaFile;
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
                        
                        files.add(mediaFile);
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
                    
                    files.add(mediaFile);
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
                        
                        files.add(mediaFile);
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
            String sql = "UPDATE media_files SET filename = ?, original_filename = ?, content_type = ?, file_size = ?, file_path = ?, title = ?, description = ?, is_public = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mediaFile.getFilename());
                stmt.setString(2, mediaFile.getOriginalFilename());
                stmt.setString(3, mediaFile.getContentType());
                stmt.setLong(4, mediaFile.getFileSize());
                stmt.setString(5, mediaFile.getFilePath());
                stmt.setString(6, mediaFile.getTitle());
                stmt.setString(7, mediaFile.getDescription());
                stmt.setBoolean(8, mediaFile.isPublic());
                stmt.setInt(9, mediaFile.getId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void deleteMediaFile(int mediaFileId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM media_files WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
                        
                        files.add(mediaFile);
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
}
