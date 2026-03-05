package lexicon.object;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current state of the live stream
 * Only one instance exists at a time (singleton in database)
 */
public class LiveStreamState {
    private int id;
    private String channel; // "music" or "video"
    private int currentMediaId;
    private LocalDateTime currentStartTime;
    private long currentPositionMs; // Position in milliseconds when paused/stored
    private int totalSkipVotes;
    private int requiredSkipVotes; // Threshold for skipping
    
    // Transient - not stored in DB, populated from queries
    private MediaFile currentMedia;
    private List<LiveStreamQueue> queuedItems;
    
    public LiveStreamState() {
        this.requiredSkipVotes = 1; // Default: 1 vote to skip
        this.queuedItems = new ArrayList<>();
    }
    
    public LiveStreamState(String channel, int currentMediaId, LocalDateTime currentStartTime) {
        this.channel = channel;
        this.currentMediaId = currentMediaId;
        this.currentStartTime = currentStartTime;
        this.currentPositionMs = 0;
        this.totalSkipVotes = 0;
        this.requiredSkipVotes = 1;
        this.queuedItems = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    
    public int getCurrentMediaId() { return currentMediaId; }
    public void setCurrentMediaId(int currentMediaId) { this.currentMediaId = currentMediaId; }
    
    public LocalDateTime getCurrentStartTime() { return currentStartTime; }
    public void setCurrentStartTime(LocalDateTime currentStartTime) { this.currentStartTime = currentStartTime; }
    
    public long getCurrentPositionMs() { return currentPositionMs; }
    public void setCurrentPositionMs(long currentPositionMs) { this.currentPositionMs = currentPositionMs; }
    
    public int getTotalSkipVotes() { return totalSkipVotes; }
    public void setTotalSkipVotes(int totalSkipVotes) { this.totalSkipVotes = totalSkipVotes; }
    
    public int getRequiredSkipVotes() { return requiredSkipVotes; }
    public void setRequiredSkipVotes(int requiredSkipVotes) { this.requiredSkipVotes = requiredSkipVotes; }
    
    public MediaFile getCurrentMedia() { return currentMedia; }
    public void setCurrentMedia(MediaFile currentMedia) { this.currentMedia = currentMedia; }
    
    public List<LiveStreamQueue> getQueuedItems() { return queuedItems; }
    public void setQueuedItems(List<LiveStreamQueue> queuedItems) { this.queuedItems = queuedItems; }
    
    /**
     * Calculate current playback position based on start time
     */
    public long calculateCurrentPosition() {
        if (currentStartTime == null) {
            return currentPositionMs;
        }
        
        long elapsedMs = java.time.Duration.between(currentStartTime, LocalDateTime.now()).toMillis();
        return currentPositionMs + elapsedMs;
    }
    
    @Override
    public String toString() {
        return "LiveStreamState{" +
                "channel=" + channel +
                ", currentMediaId=" + currentMediaId +
                ", currentStartTime=" + currentStartTime +
                ", currentPositionMs=" + currentPositionMs +
                ", totalSkipVotes=" + totalSkipVotes +
                ", requiredSkipVotes=" + requiredSkipVotes +
                ", queueSize=" + queuedItems.size() +
                '}';
    }
}
