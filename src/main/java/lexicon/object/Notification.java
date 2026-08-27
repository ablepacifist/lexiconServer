package lexicon.object;

import java.time.LocalDateTime;

/**
 * Notification entity — a voice-chat event (message, voice join, mention, music)
 * surfaced inside the Lexicon app.
 *
 * targetUserId == null means a broadcast notification (delivered to every user
 * except the actor). A non-null targetUserId is a directed notification (e.g. a
 * @mention) delivered only to that user.
 */
public class Notification {
    private long id;
    private Integer targetUserId; // nullable — null = broadcast
    private String type;          // message | voice_join | mention | music
    private String title;
    private String body;
    private String source = "mumble";
    private String fromUsername;  // actor display name
    private Integer fromUserId;   // actor Lexicon id (excluded from recipients)
    private Integer channelId;
    private String link;          // where clicking navigates
    private LocalDateTime createdAt;

    // Request-only flag (not persisted): when false, the logic layer skips
    // OS/browser push for this notification — used by producers that already
    // route push themselves (e.g. the bridge's mentions feature).
    private Boolean deliverPush;

    public Notification() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Integer getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Integer targetUserId) { this.targetUserId = targetUserId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public Integer getFromUserId() { return fromUserId; }
    public void setFromUserId(Integer fromUserId) { this.fromUserId = fromUserId; }

    public Integer getChannelId() { return channelId; }
    public void setChannelId(Integer channelId) { this.channelId = channelId; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getDeliverPush() { return deliverPush; }
    public void setDeliverPush(Boolean deliverPush) { this.deliverPush = deliverPush; }

    @Override
    public String toString() {
        return "Notification{id=" + id + ", type='" + type + "', targetUserId=" + targetUserId +
               ", from='" + fromUsername + "'}";
    }
}
