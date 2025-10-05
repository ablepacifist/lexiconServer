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
    public List<MediaFile> getMediaFilesByPlayer(int playerId) {
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
        // Implementation would go here
    }
    
    @Override
    public void deleteMediaFile(int mediaFileId) {
        // Implementation would go here
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
        // Implementation would go here
        return new ArrayList<>(); // TODO: Implement
    }
}