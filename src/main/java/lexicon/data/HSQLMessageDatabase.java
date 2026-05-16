package lexicon.data;

import lexicon.object.TextMessage;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HSQLDB implementation for text message storage
 * Used by Mumble bridge for chat message persistence
 * Follows same pattern as HSQLMediaDatabase
 */
@Repository
public class HSQLMessageDatabase implements IMessageDatabase {

    private final String DATABASE_URL = "jdbc:hsqldb:hsql://localhost:9002/mydb";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, "SA", "");
    }

    @Override
    public long addMessage(TextMessage message) {
        String sql = "INSERT INTO text_messages (channel_id, channel_name, user_id, username, content, message_type, media_file_id, reply_to_id, is_pinned, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, message.getChannelId());
            stmt.setString(2, message.getChannelName());
            stmt.setInt(3, message.getUserId());
            stmt.setString(4, message.getUsername());
            stmt.setString(5, message.getContent());
            stmt.setString(6, message.getMessageType() != null ? message.getMessageType() : "TEXT");
            if (message.getMediaFileId() != null) {
                stmt.setLong(7, message.getMediaFileId());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }
            if (message.getReplyToId() != null) {
                stmt.setLong(8, message.getReplyToId());
            } else {
                stmt.setNull(8, Types.BIGINT);
            }
            stmt.setBoolean(9, message.isPinned());
            stmt.setTimestamp(10, Timestamp.valueOf(
                message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now()));
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to add message: " + e.getMessage(), e);
        }
        return -1;
    }

    @Override
    public TextMessage getMessage(long messageId) {
        String sql = "SELECT * FROM text_messages WHERE id = ? AND deleted_at IS NULL";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMessage(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<TextMessage> getMessagesByChannel(int channelId, int limit, String beforeTimestamp) {
        List<TextMessage> messages = new ArrayList<>();
        String sql;
        if (beforeTimestamp != null && !beforeTimestamp.isEmpty()) {
            sql = "SELECT * FROM text_messages WHERE channel_id = ? AND deleted_at IS NULL AND created_at < ? ORDER BY created_at DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM text_messages WHERE channel_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT ?";
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, channelId);
            if (beforeTimestamp != null && !beforeTimestamp.isEmpty()) {
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.parse(beforeTimestamp)));
                stmt.setInt(3, limit);
            } else {
                stmt.setInt(2, limit);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    @Override
    public boolean updateMessage(long messageId, int userId, String newContent) {
        String sql = "UPDATE text_messages SET content = ?, edited_at = ? WHERE id = ? AND user_id = ? AND deleted_at IS NULL";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newContent);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, messageId);
            stmt.setInt(4, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean deleteMessage(long messageId, int userId) {
        String sql = "UPDATE text_messages SET deleted_at = ? WHERE id = ? AND user_id = ? AND deleted_at IS NULL";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, messageId);
            stmt.setInt(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<TextMessage> searchMessages(String searchTerm, int channelId) {
        List<TextMessage> messages = new ArrayList<>();
        String sql;
        if (channelId >= 0) {
            sql = "SELECT * FROM text_messages WHERE channel_id = ? AND deleted_at IS NULL AND LOWER(content) LIKE LOWER(?) ORDER BY created_at DESC LIMIT 50";
        } else {
            sql = "SELECT * FROM text_messages WHERE deleted_at IS NULL AND LOWER(content) LIKE LOWER(?) ORDER BY created_at DESC LIMIT 50";
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (channelId >= 0) {
                stmt.setInt(1, channelId);
                stmt.setString(2, "%" + searchTerm + "%");
            } else {
                stmt.setString(1, "%" + searchTerm + "%");
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    /**
     * Map a ResultSet row to a TextMessage object
     */
    private TextMessage mapResultSetToMessage(ResultSet rs) throws SQLException {
        TextMessage msg = new TextMessage();
        msg.setId(rs.getLong("id"));
        msg.setChannelId(rs.getInt("channel_id"));
        msg.setChannelName(rs.getString("channel_name"));
        msg.setUserId(rs.getInt("user_id"));
        msg.setUsername(rs.getString("username"));
        msg.setContent(rs.getString("content"));
        msg.setMessageType(rs.getString("message_type"));
        
        long mediaFileId = rs.getLong("media_file_id");
        msg.setMediaFileId(rs.wasNull() ? null : mediaFileId);
        
        long replyToId = rs.getLong("reply_to_id");
        msg.setReplyToId(rs.wasNull() ? null : replyToId);
        
        msg.setPinned(rs.getBoolean("is_pinned"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        msg.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        
        Timestamp editedAt = rs.getTimestamp("edited_at");
        msg.setEditedAt(editedAt != null ? editedAt.toLocalDateTime() : null);
        
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        msg.setDeletedAt(deletedAt != null ? deletedAt.toLocalDateTime() : null);
        
        return msg;
    }
}
