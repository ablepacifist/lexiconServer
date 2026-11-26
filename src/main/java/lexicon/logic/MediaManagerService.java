package lexicon.logic;

import lexicon.object.MediaFile;
import lexicon.object.StreamResult;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

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
     * Update media file metadata (title, description, isPublic)
     */
    boolean updateMediaFile(int mediaFileId, int userId, Map<String, Object> updates);
    
    /**
     * Delete a media file
     */
    boolean deleteMediaFile(int mediaFileId, int userId);

    /**
     * Admin delete: removes the file whatever its owner, from disk as well as the database.
     * Returns the bytes freed, or -1 when there is no such file.
     */
    long deleteMediaFileAsAdmin(int mediaFileId);
    
    /**
     * Check if user has permission to access media file
     */
    boolean hasAccessPermission(int mediaFileId, int userId);
    
    /**
     * Get file data for download
     */
    byte[] getFileData(int mediaFileId);
    
    /**
     * Get file data for streaming with Range request support
     */
    StreamResult getStreamData(int mediaFileId, String rangeHeader);
    
    /**
     * Upload media from a local file (used by async download queue)
     */
    MediaFile uploadMediaFromFile(java.io.File file, int userId, String title, String description, 
                                  boolean isPublic, String mediaType, String sourceUrl);

    /**
     * Register a file that is ALREADY on the storage volume, by hard-linking it into the
     * media layout instead of copying it (used by the torrent downloader, where qBittorrent
     * keeps its own name for the same bytes so it can go on seeding).
     */
    ImportedMedia importLinkedFile(java.nio.file.Path source, int userId, String title, String description,
                                   boolean isPublic, String mediaType, String sourceUrl);

    /** The registered media file, and whether the hard link was made (false = it was moved). */
    record ImportedMedia(MediaFile mediaFile, boolean hardLinked) {
    }
}
