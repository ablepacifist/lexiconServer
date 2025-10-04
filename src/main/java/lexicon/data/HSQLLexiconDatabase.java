package lexicon.data;

import lexicon.object.User;
import lexicon.object.MediaFile;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * HSQLDB implementation of the Lexicon database
 * Following the same pattern as Alchemy's HSQLDatabase
 */
@Repository
public class HSQLLexiconDatabase implements ILexiconDatabase {
    
    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9003/lexicondb";
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }
    
    // User management methods
    @Override
    public int getNextUserId() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT MAX(id) FROM users";
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
    public void addUser(User user) {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO users (id, username, password, email, display_name) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, user.getId());
                stmt.setString(2, user.getUsername());
                stmt.setString(3, user.getPassword());
                stmt.setString(4, user.getEmail());
                stmt.setString(5, user.getDisplayName());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public User getUser(int userId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("email"),
                            rs.getString("display_name")
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
    public User getUserByUsername(String username) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("email"),
                            rs.getString("display_name")
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
    public User getUserByEmail(String email) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users WHERE email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("email"),
                            rs.getString("display_name")
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
    public Collection<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users ORDER BY username";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("email"),
                        rs.getString("display_name")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
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
        }
    }
    
    @Override
    public MediaFile getMediaFile(int mediaFileId) {
        // Implementation would go here - similar pattern to getUser
        return null; // TODO: Implement
    }
    
    @Override
    public List<MediaFile> getMediaFilesByUser(int userId) {
        // Implementation would go here
        return new ArrayList<>(); // TODO: Implement
    }
    
    @Override
    public List<MediaFile> getAllPublicMediaFiles() {
        // Implementation would go here
        return new ArrayList<>(); // TODO: Implement
    }
    
    @Override
    public List<MediaFile> searchMediaFiles(String searchTerm) {
        // Implementation would go here
        return new ArrayList<>(); // TODO: Implement
    }
    
    @Override
    public void updateUser(User user) {
        // Implementation would go here
    }
    
    @Override
    public void deleteUser(int userId) {
        // Implementation would go here
    }
    
    @Override
    public void updateMediaFile(MediaFile mediaFile) {
        // Implementation would go here
    }
    
    @Override
    public void deleteMediaFile(int mediaFileId) {
        // Implementation would go here
    }
    
    @Override
    public boolean userExists(String username) {
        return getUserByUsername(username) != null;
    }
    
    @Override
    public boolean emailExists(String email) {
        return getUserByEmail(email) != null;
    }
    
    @Override
    public List<MediaFile> getRecentMediaFiles(int limit) {
        // Implementation would go here
        return new ArrayList<>(); // TODO: Implement
    }
}