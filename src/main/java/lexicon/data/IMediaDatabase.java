package lexicon.data;

import lexicon.object.MediaFile;
import java.util.List;

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
    List<MediaFile> searchMediaFiles(String searchTerm);
    void updateMediaFile(MediaFile mediaFile);
    void deleteMediaFile(int mediaFileId);
    List<MediaFile> getRecentMediaFiles(int limit);
    
    // File data storage (for actual file bytes)
    void storeFileData(int mediaFileId, byte[] fileData);
    byte[] getFileData(int mediaFileId);
    void deleteFileData(int mediaFileId);
}
