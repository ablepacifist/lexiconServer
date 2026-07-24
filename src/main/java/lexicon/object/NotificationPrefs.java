package lexicon.object;

import java.time.LocalDateTime;

/**
 * Per-user notification preferences plus the unread cursor (lastReadAt).
 * Unread notifications are those created after lastReadAt.
 */
public class NotificationPrefs {
    private int userId;
    private boolean enableMessage = true;
    private boolean enableVoiceJoin = true;
    private boolean enableMention = true;
    private boolean enableMusic = false;
    private boolean enablePush = true;
    private LocalDateTime lastReadAt;

    public NotificationPrefs() {}

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public boolean isEnableMessage() { return enableMessage; }
    public void setEnableMessage(boolean enableMessage) { this.enableMessage = enableMessage; }

    public boolean isEnableVoiceJoin() { return enableVoiceJoin; }
    public void setEnableVoiceJoin(boolean enableVoiceJoin) { this.enableVoiceJoin = enableVoiceJoin; }

    public boolean isEnableMention() { return enableMention; }
    public void setEnableMention(boolean enableMention) { this.enableMention = enableMention; }

    public boolean isEnableMusic() { return enableMusic; }
    public void setEnableMusic(boolean enableMusic) { this.enableMusic = enableMusic; }

    public boolean isEnablePush() { return enablePush; }
    public void setEnablePush(boolean enablePush) { this.enablePush = enablePush; }

    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }

    /**
     * Whether the given notification type is enabled for in-app delivery.
     */
    public boolean isTypeEnabled(String type) {
        if (type == null) return false;
        switch (type) {
            case "message":    return enableMessage;
            case "voice_join": return enableVoiceJoin;
            case "mention":    return enableMention;
            case "music":      return enableMusic;
            default:           return true;
        }
    }
}
