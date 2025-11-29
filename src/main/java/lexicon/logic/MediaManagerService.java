package lexicon.logic;

import lexicon.object.MediaFile;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * Service interface for media file management
 * Following the same pattern as Alchemy's service layer
 */
public interface MediaManagerService {
    
    /**
     * Upload a new media file
     */
    MediaFile uploadMediaFile(MultipartFile file, int userId, String title, String description, boolean isPublic, String mediaType);
    
    /**
     * Upload media from a URL using yt-dlp
     */
    MediaFile uploadMediaFromUrl(String url, int userId, String title, String description, boolean isPublic, String mediaType, String downloadType);
    
    /**
     * Get media file by ID
     */
    MediaFile getMediaFileById(int mediaFileId);
    
    /**
     * Get all media files uploaded by a specific user
     */
    List<MediaFile> getMediaFilesByUser(int userId);
    
    /**
     * Get all public media files
     */
    List<MediaFile> getAllPublicMediaFiles();
    
    /**
     * Search media files by title or description
     */
    List<MediaFile> searchMediaFiles(String searchTerm);
    
    /**
     * Get recent media files
     */
    List<MediaFile> getRecentMediaFiles(int limit);
    
    /**
     * Update media file information
     */
    boolean updateMediaFile(MediaFile mediaFile);
    
    /**
     * Delete a media file
     */
    boolean deleteMediaFile(int mediaFileId, int userId);
    
    /**
     * Check if user has permission to access media file
     */
    boolean hasAccessPermission(int mediaFileId, int userId);
    
    /**
     * Get file data for download
     */
    byte[] getFileData(int mediaFileId);
}
