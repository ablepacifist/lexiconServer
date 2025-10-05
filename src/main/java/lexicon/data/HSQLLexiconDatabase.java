package lexicon.data;

import lexicon.object.Player;
import lexicon.object.MediaFile;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * HSQLDB implementation of the Lexicon database
 * Now using unified Player class instead of User
 */
@Repository
public class HSQLLexiconDatabase implements ILexiconDatabase {
    
    // Connect to the same database as Alchemy server for unified user system
    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }
    
    // Player management methods
    @Override
    public int getNextPlayerId() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT MAX(id) FROM players";
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
    public void addPlayer(Player player) {
        try (Connection conn = getConnection()) {
            // Use only the columns that exist in the current schema
            String sql = "INSERT INTO players (id, username, password, level) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, player.getId());
                stmt.setString(2, player.getUsername());
                stmt.setString(3, player.getPassword());
                stmt.setInt(4, player.getLevel());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to add player: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Player getPlayer(int playerId) {
        try (Connection conn = getConnection()) {
            // Use only the columns that exist in the current schema
            String sql = "SELECT id, username, password, level FROM players WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new Player(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getInt("level"),
                            null, // email
                            rs.getString("username"), // displayName defaults to username
                            null, // registrationDate
                            null  // lastLoginDate
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public Player getPlayerByUsername(String username) {
        try (Connection conn = getConnection()) {
            // Use only the columns that definitely exist in the old schema
            String sql = "SELECT id, username, password, level FROM players WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new Player(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getInt("level"),
                            null, // email
                            rs.getString("username"), // displayName defaults to username
                            null, // registrationDate  
                            null  // lastLoginDate
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public Player getPlayerByEmail(String email) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM players WHERE email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp regTimestamp = rs.getTimestamp("registration_date");
                        Timestamp lastLoginTimestamp = rs.getTimestamp("last_login_date");
                        return new Player(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getInt("level"),
                            rs.getString("email"),
                            rs.getString("display_name"),
                            regTimestamp != null ? regTimestamp.toLocalDateTime() : null,
                            lastLoginTimestamp != null ? lastLoginTimestamp.toLocalDateTime() : null
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public Collection<Player> getAllPlayers() {
        List<Player> players = new ArrayList<>();
        try (Connection conn = getConnection()) {
            // Use only the columns that definitely exist in the old schema
            String sql = "SELECT id, username, password, level FROM players ORDER BY username";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // Create Player with just the basic fields, null for new fields
                    players.add(new Player(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getInt("level"),
                        null, // email
                        rs.getString("username"), // displayName defaults to username  
                        null, // registrationDate
                        null  // lastLoginDate
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }
    
    // Media file management methods (simplified for now)
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
            // Try to add with file_hash column, fall back if it doesn't exist
            String sql = "INSERT INTO media_files (id, filename, original_filename, content_type, file_size, file_path, uploaded_by, upload_date, title, description, is_public, file_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                stmt.setString(12, mediaFile.getFileHash());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            // If file_hash column doesn't exist, try without it
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
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
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
                        MediaFile mediaFile = new MediaFile();
                        mediaFile.setId(rs.getInt("id"));
                        mediaFile.setFilename(rs.getString("filename"));
                        mediaFile.setOriginalFilename(rs.getString("original_filename"));
                        mediaFile.setContentType(rs.getString("content_type"));
                        mediaFile.setFileSize(rs.getLong("file_size"));
                        mediaFile.setFilePath(rs.getString("file_path"));
                        mediaFile.setUploadedBy(rs.getInt("uploaded_by"));
                        
                        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
                        if (uploadTimestamp != null) {
                            mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
                        }
                        
                        mediaFile.setTitle(rs.getString("title"));
                        mediaFile.setDescription(rs.getString("description"));
                        mediaFile.setPublic(rs.getBoolean("is_public"));
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
        List<MediaFile> mediaFiles = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE uploaded_by = ? ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MediaFile mediaFile = new MediaFile();
                        mediaFile.setId(rs.getInt("id"));
                        mediaFile.setFilename(rs.getString("filename"));
                        mediaFile.setOriginalFilename(rs.getString("original_filename"));
                        mediaFile.setContentType(rs.getString("content_type"));
                        mediaFile.setFileSize(rs.getLong("file_size"));
                        mediaFile.setFilePath(rs.getString("file_path"));
                        mediaFile.setUploadedBy(rs.getInt("uploaded_by"));
                        
                        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
                        if (uploadTimestamp != null) {
                            mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
                        }
                        
                        mediaFile.setTitle(rs.getString("title"));
                        mediaFile.setDescription(rs.getString("description"));
                        mediaFile.setPublic(rs.getBoolean("is_public"));
                        mediaFiles.add(mediaFile);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mediaFiles;
    }
    
    @Override
    public List<MediaFile> getAllPublicMediaFiles() {
        List<MediaFile> mediaFiles = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE is_public = true ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    MediaFile mediaFile = new MediaFile();
                    mediaFile.setId(rs.getInt("id"));
                    mediaFile.setFilename(rs.getString("filename"));
                    mediaFile.setOriginalFilename(rs.getString("original_filename"));
                    mediaFile.setContentType(rs.getString("content_type"));
                    mediaFile.setFileSize(rs.getLong("file_size"));
                    mediaFile.setFilePath(rs.getString("file_path"));
                    mediaFile.setUploadedBy(rs.getInt("uploaded_by"));
                    
                    Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
                    if (uploadTimestamp != null) {
                        mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
                    }
                    
                    mediaFile.setTitle(rs.getString("title"));
                    mediaFile.setDescription(rs.getString("description"));
                    mediaFile.setPublic(rs.getBoolean("is_public"));
                    mediaFiles.add(mediaFile);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mediaFiles;
    }
    
    @Override
    public List<MediaFile> searchMediaFiles(String searchTerm) {
        List<MediaFile> mediaFiles = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE (title LIKE ? OR description LIKE ?) AND is_public = true ORDER BY upload_date DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                String searchPattern = "%" + searchTerm + "%";
                stmt.setString(1, searchPattern);
                stmt.setString(2, searchPattern);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MediaFile mediaFile = new MediaFile();
                        mediaFile.setId(rs.getInt("id"));
                        mediaFile.setFilename(rs.getString("filename"));
                        mediaFile.setOriginalFilename(rs.getString("original_filename"));
                        mediaFile.setContentType(rs.getString("content_type"));
                        mediaFile.setFileSize(rs.getLong("file_size"));
                        mediaFile.setFilePath(rs.getString("file_path"));
                        mediaFile.setUploadedBy(rs.getInt("uploaded_by"));
                        
                        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
                        if (uploadTimestamp != null) {
                            mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
                        }
                        
                        mediaFile.setTitle(rs.getString("title"));
                        mediaFile.setDescription(rs.getString("description"));
                        mediaFile.setPublic(rs.getBoolean("is_public"));
                        mediaFiles.add(mediaFile);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mediaFiles;
    }
    
    @Override
    public void updatePlayer(Player player) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE players SET username = ?, password = ?, email = ?, display_name = ?, level = ?, registration_date = ?, last_login_date = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getUsername());
                stmt.setString(2, player.getPassword());
                stmt.setString(3, player.getEmail());
                stmt.setString(4, player.getDisplayName());
                stmt.setInt(5, player.getLevel());
                stmt.setTimestamp(6, player.getRegistrationDate() != null ? Timestamp.valueOf(player.getRegistrationDate()) : null);
                stmt.setTimestamp(7, player.getLastLoginDate() != null ? Timestamp.valueOf(player.getLastLoginDate()) : null);
                stmt.setInt(8, player.getId());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void deletePlayer(int playerId) {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM players WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, playerId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void updatePlayerLastLogin(int playerId, LocalDateTime lastLoginDate) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE players SET last_login_date = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, lastLoginDate != null ? Timestamp.valueOf(lastLoginDate) : null);
                stmt.setInt(2, playerId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void updateMediaFile(MediaFile mediaFile) {
        try (Connection conn = getConnection()) {
            String sql = "UPDATE media_files SET title = ?, description = ?, is_public = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mediaFile.getTitle());
                stmt.setString(2, mediaFile.getDescription());
                stmt.setBoolean(3, mediaFile.isPublic());
                stmt.setInt(4, mediaFile.getId());
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
    public boolean playerExists(String username) {
        return getPlayerByUsername(username) != null;
    }
    
    @Override
    public boolean emailExists(String email) {
        return getPlayerByEmail(email) != null;
    }
    
    @Override
    public List<MediaFile> getRecentMediaFiles(int limit) {
        List<MediaFile> mediaFiles = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE is_public = true ORDER BY upload_date DESC LIMIT ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MediaFile mediaFile = new MediaFile();
                        mediaFile.setId(rs.getInt("id"));
                        mediaFile.setFilename(rs.getString("filename"));
                        mediaFile.setOriginalFilename(rs.getString("original_filename"));
                        mediaFile.setContentType(rs.getString("content_type"));
                        mediaFile.setFileSize(rs.getLong("file_size"));
                        mediaFile.setFilePath(rs.getString("file_path"));
                        mediaFile.setUploadedBy(rs.getInt("uploaded_by"));
                        
                        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
                        if (uploadTimestamp != null) {
                            mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
                        }
                        
                        mediaFile.setTitle(rs.getString("title"));
                        mediaFile.setDescription(rs.getString("description"));
                        mediaFile.setPublic(rs.getBoolean("is_public"));
                        mediaFiles.add(mediaFile);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mediaFiles;
    }
    
    // File deduplication methods
    @Override
    public MediaFile getMediaFileByHash(String fileHash) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM media_files WHERE file_hash = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fileHash);
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
    public void addMediaFileReference(int originalMediaFileId, int userId, String title, String description) {
        // For now, we'll create a media_file_references table later
        // This method will create entries showing that a user "owns" a reference to the original file
        System.out.println("Creating file reference: User " + userId + " referencing file " + originalMediaFileId);
        // TODO: Implement references table
    }
    
    /**
     * Helper method to map ResultSet to MediaFile object
     */
    private MediaFile mapResultSetToMediaFile(ResultSet rs) throws SQLException {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(rs.getInt("id"));
        mediaFile.setFilename(rs.getString("filename"));
        mediaFile.setOriginalFilename(rs.getString("original_filename"));
        mediaFile.setContentType(rs.getString("content_type"));
        mediaFile.setFileSize(rs.getLong("file_size"));
        mediaFile.setFilePath(rs.getString("file_path"));
        mediaFile.setUploadedBy(rs.getInt("uploaded_by"));
        
        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
        if (uploadTimestamp != null) {
            mediaFile.setUploadDate(uploadTimestamp.toLocalDateTime());
        }
        
        mediaFile.setTitle(rs.getString("title"));
        mediaFile.setDescription(rs.getString("description"));
        mediaFile.setPublic(rs.getBoolean("is_public"));
        
        // Handle file_hash column - it might not exist in older schema
        try {
            mediaFile.setFileHash(rs.getString("file_hash"));
        } catch (SQLException e) {
            // Column doesn't exist, that's ok for backward compatibility
            mediaFile.setFileHash(null);
        }
        
        return mediaFile;
    }
}