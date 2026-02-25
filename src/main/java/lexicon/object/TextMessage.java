package lexicon.object;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * Text message entity for Mumble bridge chat integration
 */
public class TextMessage {
    private long id;
    private int channelId;
    private String channelName;
    private int userId;
    private String username;
    private String content;
    private String messageType; // TEXT, MEDIA_SHARE, SYSTEM, BOT_COMMAND
    private Long mediaFileId; // nullable - linked media file
    private Long replyToId; // nullable - threaded replies
    private boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;
    private LocalDateTime deletedAt;

    public TextMessage() {}

    public TextMessage(int channelId, String channelName, int userId, String username, String content, String messageType) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.userId = userId;
        this.username = username;
        this.content = content;
        this.messageType = messageType;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getChannelId() { return channelId; }
    public void setChannelId(int channelId) { this.channelId = channelId; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public Long getMediaFileId() { return mediaFileId; }
    public void setMediaFileId(Long mediaFileId) { this.mediaFileId = mediaFileId; }

    public Long getReplyToId() { return replyToId; }
    public void setReplyToId(Long replyToId) { this.replyToId = replyToId; }

    @JsonProperty("isPinned")
    public boolean isPinned() { return isPinned; }

    @JsonProperty("isPinned")
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime editedAt) { this.editedAt = editedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public String toString() {
        return "TextMessage{id=" + id + ", channelId=" + channelId + ", userId=" + userId + 
               ", username='" + username + "', type='" + messageType + "'}";
    }
}
