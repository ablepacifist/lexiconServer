package lexicon.data;

import lexicon.object.LiveStreamQueue;
import lexicon.object.LiveStreamState;
import java.util.List;

/**
 * Interface for live stream database operations
 */
public interface ILiveStreamDatabase {
    
    // Stream State Management
    /**
     * Get the current stream state (singleton)
     */
    LiveStreamState getStreamState();
    
    /**
     * Initialize stream state if it doesn't exist
     */
    void initializeStreamState();
    
    /**
     * Update the current playing media
     */
    void setCurrentMedia(int mediaId, long positionMs);
    
    /**
     * Clear all skip votes for current media
     */
    void clearSkipVotes();
    
    // Queue Management - Fast Methods
    /**
     * Get next queue ID
     */
    int getNextQueueId();
    
    /**
     * Add item to queue
     */
    int addToQueue(int mediaFileId, int userId);
    
    /**
     * Remove item from queue
     */
    boolean removeFromQueue(int queueId);
    
    /**
     * Update queue item status
     */
    void updateQueueStatus(int queueId, LiveStreamQueue.QueueStatus status);
    
    /**
     * Reorder queue positions after removal
     */
    void reorderQueue();
    
    // Skip Vote Management
    /**
     * Add skip vote for current media
     */
    boolean addSkipVote(int queueId, int userId);
    
    /**
     * Get skip votes for a queue item
     */
    List<Integer> getSkipVotes(int queueId);
    
    /**
     * Get count of skip votes for a queue item
     */
    int getSkipVoteCount(int queueId);
    
    /**
     * Clear skip votes for a queue item
     */
    void clearSkipVotesForItem(int queueId);
    
    /**
     * Check if user has already voted to skip
     */
    boolean hasUserVotedSkip(int queueId, int userId);
    
    /**
     * Get the currently playing queue item ID (fast - no joins)
     */
    Integer getCurrentPlayingQueueId();
    
    /**
     * Get queue items without loading full media files (fast)
     */
    List<LiveStreamQueue> getQueueItemsLightweight();
}
