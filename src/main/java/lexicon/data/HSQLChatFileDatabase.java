package lexicon.data;

import lexicon.object.ChatFile;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;

@Repository
public class HSQLChatFileDatabase implements IChatFileDatabase {

    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }

    @Override
    public long addChatFile(ChatFile chatFile) {
        String sql = "INSERT INTO chat_files (original_filename, stored_filename, mime_type, file_size, width, height, thumbnail_filename, uploaded_by, channel_id, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, chatFile.getOriginalFilename());
            stmt.setString(2, chatFile.getStoredFilename());
            stmt.setString(3, chatFile.getMimeType());
            stmt.setLong(4, chatFile.getFileSize());
            if (chatFile.getWidth() != null) {
                stmt.setInt(5, chatFile.getWidth());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            if (chatFile.getHeight() != null) {
                stmt.setInt(6, chatFile.getHeight());
            } else {
                stmt.setNull(6, Types.INTEGER);
            }
            stmt.setString(7, chatFile.getThumbnailFilename());
            stmt.setInt(8, chatFile.getUploadedBy());
            if (chatFile.getChannelId() != null) {
                stmt.setInt(9, chatFile.getChannelId());
            } else {
                stmt.setNull(9, Types.INTEGER);
            }
            stmt.setTimestamp(10, Timestamp.valueOf(
                chatFile.getCreatedAt() != null ? chatFile.getCreatedAt() : LocalDateTime.now()));
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add chat file: " + e.getMessage(), e);
        }
        return -1;
    }

    @Override
    public ChatFile getChatFile(long id) {
        String sql = "SELECT * FROM chat_files WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get chat file: " + e.getMessage(), e);
        }
        return null;
    }

    private ChatFile mapResultSet(ResultSet rs) throws SQLException {
        ChatFile cf = new ChatFile();
        cf.setId(rs.getLong("id"));
        cf.setOriginalFilename(rs.getString("original_filename"));
        cf.setStoredFilename(rs.getString("stored_filename"));
        cf.setMimeType(rs.getString("mime_type"));
        cf.setFileSize(rs.getLong("file_size"));
        int w = rs.getInt("width");
        cf.setWidth(rs.wasNull() ? null : w);
        int h = rs.getInt("height");
        cf.setHeight(rs.wasNull() ? null : h);
        cf.setThumbnailFilename(rs.getString("thumbnail_filename"));
        cf.setUploadedBy(rs.getInt("uploaded_by"));
        int ch = rs.getInt("channel_id");
        cf.setChannelId(rs.wasNull() ? null : ch);
        Timestamp ts = rs.getTimestamp("created_at");
        cf.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        return cf;
    }
}
