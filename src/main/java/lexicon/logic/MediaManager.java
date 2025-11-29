package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.service.YtDlpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of MediaManagerService
 * Handles all media file operations for the file sharing platform
 * Uses separate media database for media files
 */
@Service
public class MediaManager implements MediaManagerService {
    
    private final ILexiconDatabase playerDatabase;
    private final IMediaDatabase mediaDatabase;
    private final YtDlpService ytDlpService;
    
    @Autowired
    public MediaManager(ILexiconDatabase playerDatabase, IMediaDatabase mediaDatabase, YtDlpService ytDlpService) {
        this.playerDatabase = playerDatabase;
        this.mediaDatabase = mediaDatabase;
        this.ytDlpService = ytDlpService;
    }
    
    @Override
    public MediaFile uploadMediaFile(MultipartFile file, int userId, String title, 
                                     String description, boolean isPublic, String mediaType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }
        
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        
        try {
            int id = mediaDatabase.getNextMediaFileId();
            
            // Create MediaFile object with correct constructor
            MediaFile mediaFile = new MediaFile(
                id,
                file.getOriginalFilename(), // filename
                file.getOriginalFilename(), // originalFilename
                file.getContentType(),
                file.getSize(),
                "", // filePath - will be set by storage layer
                userId, // uploadedBy
                title.trim(),
                description != null ? description.trim() : "",
                isPublic
            );
            
            mediaFile.setUploadDate(LocalDateTime.now());
            mediaFile.setMediaType(MediaType.fromString(mediaType));
            
            // Save metadata to database
            mediaDatabase.addMediaFile(mediaFile);
            
            // Store the actual file data
            byte[] fileData = file.getBytes();
            mediaDatabase.storeFileData(id, fileData);
            
            return mediaFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }
    
    @Override
    public MediaFile uploadMediaFromUrl(String url, int userId, String title, 
                                        String description, boolean isPublic, String mediaType, String downloadType) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        
        // Convert downloadType string to enum
        YtDlpService.DownloadType dlType;
        try {
            dlType = YtDlpService.DownloadType.valueOf(downloadType.toUpperCase());
        } catch (IllegalArgumentException e) {
            dlType = YtDlpService.DownloadType.AUDIO_ONLY;
        }
        
        // Download using yt-dlp
        String outputDir = System.getProperty("java.io.tmpdir");
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(url, dlType, outputDir);
        
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getErrorMessage());
        }
        
        try {
            File downloadedFile = result.getFile();
            byte[] fileData = Files.readAllBytes(downloadedFile.toPath());
            
            int id = mediaDatabase.getNextMediaFileId();
            
            // Create MediaFile object
            MediaFile mediaFile = new MediaFile(
                id,
                downloadedFile.getName(), // filename
                downloadedFile.getName(), // originalFilename
                result.getContentType(),
                downloadedFile.length(),
                "", // filePath
                userId,
                title.trim(),
                description != null ? description.trim() : "",
                isPublic
            );
            
            mediaFile.setUploadDate(LocalDateTime.now());
            mediaFile.setMediaType(MediaType.fromString(mediaType));
            mediaFile.setSourceUrl(url); // Save the source URL
            
            // Save metadata to database
            mediaDatabase.addMediaFile(mediaFile);
            
            // Store the actual file data
            mediaDatabase.storeFileData(id, fileData);
            
            // Clean up temporary file
            if (!downloadedFile.delete()) {
                System.err.println("Warning: Failed to delete temporary file: " + downloadedFile.getAbsolutePath());
            }
            
            return mediaFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process downloaded file: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload from URL: " + e.getMessage(), e);
        }
    }
    
    @Override
    public MediaFile getMediaFileById(int mediaFileId) {
        return mediaDatabase.getMediaFile(mediaFileId);
    }
    
    @Override
    public List<MediaFile> getMediaFilesByUser(int userId) {
        return mediaDatabase.getMediaFilesByPlayer(userId);
    }
    
    @Override
    public List<MediaFile> getAllPublicMediaFiles() {
        return mediaDatabase.getAllPublicMediaFiles();
    }
    
    @Override
    public List<MediaFile> searchMediaFiles(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            // Return all media files by getting all players' files
            return playerDatabase.getAllPlayers().stream()
                    .flatMap(player -> mediaDatabase.getMediaFilesByPlayer(player.getId()).stream())
                    .collect(Collectors.toList());
        }
        
        return mediaDatabase.searchMediaFiles(searchTerm.trim());
    }
    
    @Override
    public List<MediaFile> getRecentMediaFiles(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        
        return mediaDatabase.getRecentMediaFiles(limit);
    }
    
    @Override
    public boolean updateMediaFile(MediaFile mediaFile) {
        if (mediaFile == null) {
            return false;
        }
        
        // Verify file exists
        MediaFile existing = mediaDatabase.getMediaFile(mediaFile.getId());
        if (existing == null) {
            return false;
        }
        
        mediaDatabase.updateMediaFile(mediaFile);
        return true;
    }
    
    @Override
    public boolean deleteMediaFile(int mediaFileId, int userId) {
        // Verify file exists and belongs to user
        MediaFile mediaFile = mediaDatabase.getMediaFile(mediaFileId);
        if (mediaFile == null) {
            return false;
        }
        
        // Only owner can delete
        if (mediaFile.getUploadedBy() != userId) {
            return false;
        }
        
        mediaDatabase.deleteMediaFile(mediaFileId);
        return true;
    }
    
    @Override
    public boolean hasAccessPermission(int mediaFileId, int userId) {
        MediaFile mediaFile = mediaDatabase.getMediaFile(mediaFileId);
        
        if (mediaFile == null) {
            return false;
        }
        
        // Public files are accessible to everyone
        if (mediaFile.isPublic()) {
            return true;
        }
        
        // Private files only accessible to owner
        return mediaFile.getUploadedBy() == userId;
    }
    
    @Override
    public byte[] getFileData(int mediaFileId) {
        return mediaDatabase.getFileData(mediaFileId);
    }
}
