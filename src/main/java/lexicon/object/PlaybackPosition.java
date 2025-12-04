package lexicon.object;

import java.time.LocalDateTime;

/**
 * Represents a user's playback position in an audiobook or media file
 */
public class PlaybackPosition {
    private int id;
    private int userId;
    private int mediaFileId;
    private double position; // Current playback position in seconds
    private double duration; // Total duration in seconds
    private LocalDateTime lastUpdated;
    private boolean completed;
    
    public PlaybackPosition() {}
    
    public PlaybackPosition(int id, int userId, int mediaFileId, double position, double duration, LocalDateTime lastUpdated, boolean completed) {
        this.id = id;
        this.userId = userId;
        this.mediaFileId = mediaFileId;
        this.position = position;
        this.duration = duration;
        this.lastUpdated = lastUpdated;
        this.completed = completed;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    
    public int getMediaFileId() { return mediaFileId; }
    public void setMediaFileId(int mediaFileId) { this.mediaFileId = mediaFileId; }
    
    public double getPosition() { return position; }
    public void setPosition(double position) { this.position = position; }
    
    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }
    
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    
    public double getProgressPercentage() {
        if (duration <= 0) return 0;
        return (position / duration) * 100;
    }
    
    @Override
    public String toString() {
        return "PlaybackPosition{" +
                "id=" + id +
                ", userId=" + userId +
                ", mediaFileId=" + mediaFileId +
                ", position=" + position +
                ", duration=" + duration +
                ", completed=" + completed +
                '}';
    }
}
