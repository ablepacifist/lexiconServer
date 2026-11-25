package lexicon.data;

import lexicon.object.MediaFile;
import lexicon.object.PlaybackPosition;
import java.util.List;
import java.io.InputStream;

/**
 * Interface for media file database operations
 * Separated from player database for better organization
 */
public interface IMediaDatabase {
    
    // Media file management
    int getNextMediaFileId();
    void addMediaFile(MediaFile mediaFile);
    MediaFile getMediaFile(int mediaFileId);
    List<MediaFile> getMediaFilesByPlayer(int playerId);
    List<MediaFile> getAllPublicMediaFiles();
    /** Every media file, public or private, newest first (admin storage view). */
    List<MediaFile> getAllMediaFiles();
    /** How many media rows point at this stored file path (a file shared by two rows must stay). */
    int countMediaFilesWithPath(String filePath);
    List<MediaFile> searchMediaFiles(String searchTerm);
    void updateMediaFile(MediaFile mediaFile);
    void deleteMediaFile(int mediaFileId);
    List<MediaFile> getRecentMediaFiles(int limit);
    
    // File data storage (for actual file bytes)
    void storeFileData(int mediaFileId, byte[] fileData);
    void storeFileDataStreaming(int mediaFileId, InputStream inputStream, long fileSize);
    byte[] getFileData(int mediaFileId);
    void deleteFileData(int mediaFileId);
    
    // Playback position tracking (for audiobooks)
    void savePlaybackPosition(PlaybackPosition position);
    PlaybackPosition getPlaybackPosition(int userId, int mediaFileId);
    List<PlaybackPosition> getUserPlaybackPositions(int userId);
    void deletePlaybackPosition(int userId, int mediaFileId);
}
