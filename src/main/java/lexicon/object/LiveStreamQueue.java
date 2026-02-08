package lexicon.object;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a queue item in the live stream
 * Each item is a media file waiting to be played or currently playing
 */
public class LiveStreamQueue {
    private int id;
    private int mediaFileId;
    private int addedBy; // User ID who added this
    private LocalDateTime addedAt;
    private int position; // Position in queue (0 = currently playing)
    private QueueStatus status;
    private List<Integer> skipVotes; // List of user IDs who voted to skip
    
    // For joining with media file info
    private MediaFile mediaFile;
    
    public enum QueueStatus {
        QUEUED,
        PLAYING,
        COMPLETED,
        SKIPPED
    }
    
    public LiveStreamQueue() {
        this.addedAt = LocalDateTime.now();
        this.status = QueueStatus.QUEUED;
        this.skipVotes = new ArrayList<>();
    }
    
    public LiveStreamQueue(int id, int mediaFileId, int addedBy, int position) {
        this.id = id;
        this.mediaFileId = mediaFileId;
        this.addedBy = addedBy;
        this.position = position;
        this.addedAt = LocalDateTime.now();
        this.status = QueueStatus.QUEUED;
        this.skipVotes = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getMediaFileId() { return mediaFileId; }
    public void setMediaFileId(int mediaFileId) { this.mediaFileId = mediaFileId; }
    
    public int getAddedBy() { return addedBy; }
    public void setAddedBy(int addedBy) { this.addedBy = addedBy; }
    
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
    
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    
    public QueueStatus getStatus() { return status; }
    public void setStatus(QueueStatus status) { this.status = status; }
    
    public List<Integer> getSkipVotes() { return skipVotes; }
    public void setSkipVotes(List<Integer> skipVotes) { this.skipVotes = skipVotes; }
    
    public MediaFile getMediaFile() { return mediaFile; }
    public void setMediaFile(MediaFile mediaFile) { this.mediaFile = mediaFile; }
    
    public void addSkipVote(int userId) {
        if (!skipVotes.contains(userId)) {
            skipVotes.add(userId);
        }
    }
    
    public int getSkipVoteCount() {
        return skipVotes.size();
    }
    
    @Override
    public String toString() {
        return "LiveStreamQueue{" +
                "id=" + id +
                ", mediaFileId=" + mediaFileId +
                ", addedBy=" + addedBy +
                ", position=" + position +
                ", status=" + status +
                ", skipVotes=" + skipVotes.size() +
                '}';
    }
}
