package lexicon.data;

import lexicon.object.LiveStreamQueue;
import lexicon.object.LiveStreamState;
import java.util.List;

/**
 * Interface for live stream database operations
 * All methods that deal with stream state or queue take a channel parameter ("music" or "video")
 */
public interface ILiveStreamDatabase {
    
    // Stream State Management
    LiveStreamState getStreamState(String channel);
    void initializeStreamState(String channel);
    void setCurrentMedia(String channel, int mediaId, long positionMs);
    
    // Queue Management
    int getNextQueueId();
    int addToQueue(String channel, int mediaFileId, int userId);
    boolean removeFromQueue(String channel, int queueId);
    void updateQueueStatus(int queueId, LiveStreamQueue.QueueStatus status);
    void reorderQueue(String channel);
    
    // Skip Vote Management
    boolean addSkipVote(int queueId, int userId);
    List<Integer> getSkipVotes(int queueId);
    int getSkipVoteCount(int queueId);
    void clearSkipVotesForItem(int queueId);
    boolean hasUserVotedSkip(int queueId, int userId);
    
    // Channel-aware queries
    Integer getCurrentPlayingQueueId(String channel);
    List<LiveStreamQueue> getQueueItemsLightweight(String channel);
}
