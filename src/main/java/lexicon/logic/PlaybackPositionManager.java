package lexicon.logic;

import lexicon.data.IMediaDatabase;
import lexicon.object.PlaybackPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Business logic for managing playback positions
 * Handles saving, retrieving, and managing user progress in audiobooks/media
 */
@Component
public class PlaybackPositionManager {
    
    private final IMediaDatabase mediaDatabase;
    
    @Autowired
    public PlaybackPositionManager(IMediaDatabase mediaDatabase) {
        this.mediaDatabase = mediaDatabase;
    }
    
    /**
     * Save or update a user's playback position
     * @param userId The user ID
     * @param mediaFileId The media file ID
     * @param position Current position in seconds
     * @param duration Total duration in seconds
     * @param completed Whether the media has been completed
     * @return true if saved successfully
     */
    public boolean savePosition(int userId, int mediaFileId, double position, double duration, boolean completed) {
        try {
            PlaybackPosition playbackPosition = new PlaybackPosition();
            playbackPosition.setUserId(userId);
            playbackPosition.setMediaFileId(mediaFileId);
            playbackPosition.setPosition(position);
            playbackPosition.setDuration(duration);
            playbackPosition.setCompleted(completed);
            
            mediaDatabase.savePlaybackPosition(playbackPosition);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get a user's playback position for a specific media file
     * @param userId The user ID
     * @param mediaFileId The media file ID
     * @return PlaybackPosition or null if not found
     */
    public PlaybackPosition getPosition(int userId, int mediaFileId) {
        try {
            return mediaDatabase.getPlaybackPosition(userId, mediaFileId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get all playback positions for a user
     * @param userId The user ID
     * @return List of PlaybackPosition objects, ordered by last updated
     */
    public List<PlaybackPosition> getUserPositions(int userId) {
        try {
            return mediaDatabase.getUserPlaybackPositions(userId);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
    
    /**
     * Delete a user's playback position for a specific media file
     * @param userId The user ID
     * @param mediaFileId The media file ID
     * @return true if deleted successfully
     */
    public boolean deletePosition(int userId, int mediaFileId) {
        try {
            mediaDatabase.deletePlaybackPosition(userId, mediaFileId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Check if a position should be marked as completed
     * Position is considered completed if within 30 seconds of the end
     * @param position Current position in seconds
     * @param duration Total duration in seconds
     * @return true if should be marked completed
     */
    public boolean shouldMarkCompleted(double position, double duration) {
        return duration - position <= 30.0;
    }
}
