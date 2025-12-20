package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.object.StreamResult;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.YtDlpService;
import lexicon.service.VideoTranscodingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of MediaManagerService
 * Handles all media file operations for the file sharing platform
 * Uses separate media database for media files
 * Integrates with OptimizedFileStorageService for 500GB volume storage
 */
@Service
public class MediaManager implements MediaManagerService {
    
    private final ILexiconDatabase playerDatabase;
    private final IMediaDatabase mediaDatabase;
    private final YtDlpService ytDlpService;
    private final VideoTranscodingService transcodingService;
    private final OptimizedFileStorageService fileStorageService;
    
    @Autowired
    public MediaManager(ILexiconDatabase playerDatabase, IMediaDatabase mediaDatabase, 
                       YtDlpService ytDlpService, VideoTranscodingService transcodingService,
                       OptimizedFileStorageService fileStorageService) {
        this.playerDatabase = playerDatabase;
        this.mediaDatabase = mediaDatabase;
        this.ytDlpService = ytDlpService;
        this.transcodingService = transcodingService;
        this.fileStorageService = fileStorageService;
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
            MediaType type = MediaType.fromString(mediaType);
            
            // Create MediaFile object first (needed for storage service)
            MediaFile mediaFile = new MediaFile(
                id,
                file.getOriginalFilename(), // filename
                file.getOriginalFilename(), // originalFilename
                file.getContentType(),
                file.getSize(),
                "", // filePath - will be set after storage
                userId, // uploadedBy
                title.trim(),
                description != null ? description.trim() : "",
                isPublic
            );
            
            mediaFile.setUploadDate(LocalDateTime.now());
            mediaFile.setMediaType(type);
            
            // Store file to 500GB volume using OptimizedFileStorageService
            String storedFilePath = null;
            if (fileStorageService != null) {
                storedFilePath = fileStorageService.storeFile(file, mediaFile);
                if (storedFilePath != null) {
                    mediaFile.setFilePath(storedFilePath);
                }
            }
            
            // Save metadata to database
            mediaDatabase.addMediaFile(mediaFile);
            
            // If file storage wasn't available, fall back to database storage
            if (storedFilePath == null) {
                if (file.getSize() > 100 * 1024 * 1024) { // 100MB threshold - use streaming
                    try (InputStream inputStream = file.getInputStream()) {
                        mediaDatabase.storeFileDataStreaming(id, inputStream, file.getSize());
                    }
                } else {
                    byte[] fileData = file.getBytes();
                    mediaDatabase.storeFileData(id, fileData);
                }
            }
            
            // Transcode video if needed (async in production, sync for now)
            if (type == MediaType.VIDEO) {
                // For large files, we need to handle transcoding differently
                // For now, skip transcoding for very large files
                if (file.getSize() <= 100 * 1024 * 1024) {
                    byte[] fileData = file.getBytes();
                    transcodeVideoIfNeeded(id, file.getOriginalFilename(), fileData);
                }
            }
            
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
    
    public boolean updateMediaFile(int mediaFileId, int userId, Map<String, Object> updates) {
        // Verify file exists
        MediaFile existing = mediaDatabase.getMediaFile(mediaFileId);
        if (existing == null) {
            return false;
        }
        
        // Only owner can edit
        if (existing.getUploadedBy() != userId) {
            return false;
        }
        
        // Update only the allowed fields
        if (updates.containsKey("title")) {
            existing.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("description")) {
            existing.setDescription((String) updates.get("description"));
        }
        if (updates.containsKey("isPublic")) {
            existing.setPublic((Boolean) updates.get("isPublic"));
        }
        
        mediaDatabase.updateMediaFile(existing);
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
    
    /**
     * Transcodes video files to optimized H.264/AAC MP4 format if FFmpeg is available
     * and the file is a video. Stores the transcoded version back to the database.
     */
    private void transcodeVideoIfNeeded(int mediaFileId, String filename, byte[] originalFileData) {
        // Skip if not a video file
        if (!transcodingService.isVideoFile(filename)) {
            return;
        }
        
        // Skip if FFmpeg not available
        if (!transcodingService.isFFmpegAvailable()) {
            System.out.println("FFmpeg not available - skipping transcoding for: " + filename);
            return;
        }
        
        // Skip if video doesn't need transcoding (already H.264 MP4)
        if (!transcodingService.needsTranscoding(filename)) {
            System.out.println("Video already optimized - skipping transcoding for: " + filename);
            return;
        }
        
        Path inputPath = null;
        Path outputPath = null;
        
        try {
            // Create temp files for transcoding
            inputPath = Files.createTempFile("video_input_", "_" + filename);
            String transcodedFilename = transcodingService.generateTranscodedFilename(filename);
            outputPath = Files.createTempFile("video_output_", "_" + transcodedFilename);
            
            // Write original video to temp file
            Files.write(inputPath, originalFileData);
            
            System.out.println("Starting video transcoding for: " + filename);
            
            // Transcode video using FFmpeg
            boolean success = transcodingService.transcodeVideo(
                inputPath.toString(),
                outputPath.toString(),
                1920 // max width
            );
            
            if (success) {
                // Read transcoded video bytes
                byte[] transcodedData = Files.readAllBytes(outputPath);
                
                // Store transcoded version back to database (replaces original)
                mediaDatabase.storeFileData(mediaFileId, transcodedData);
                
                long originalSize = originalFileData.length;
                long transcodedSize = transcodedData.length;
                double compressionRatio = (1.0 - (double) transcodedSize / originalSize) * 100;
                
                System.out.println("Video transcoding complete for: " + filename);
                System.out.println("Original size: " + originalSize + " bytes");
                System.out.println("Transcoded size: " + transcodedSize + " bytes");
                System.out.println("Compression: " + String.format("%.1f", compressionRatio) + "%");
            } else {
                System.err.println("Video transcoding failed for: " + filename);
            }
            
        } catch (IOException e) {
            System.err.println("Error during video transcoding for: " + filename);
            e.printStackTrace();
            // Don't fail the upload if transcoding fails - original is already stored
        } finally {
            // Clean up temp files
            try {
                if (inputPath != null) {
                    Files.deleteIfExists(inputPath);
                }
                if (outputPath != null) {
                    Files.deleteIfExists(outputPath);
                }
            } catch (IOException e) {
                System.err.println("Error cleaning up temp files: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get file data for streaming with Range request support
     * Handles parsing and validation of byte ranges
     * 
     * @param mediaFileId The media file ID
     * @param rangeHeader The HTTP Range header value (e.g., "bytes=0-1023") or null
     * @return StreamResult containing the requested byte range and metadata
     */
    @Override
    public StreamResult getStreamData(int mediaFileId, String rangeHeader) {
        System.out.println("=== getStreamData called for mediaFileId: " + mediaFileId + " ===");
        
        MediaFile mediaFile = mediaDatabase.getMediaFile(mediaFileId);
        System.out.println("MediaFile from DB: " + (mediaFile != null ? "FOUND" : "NULL"));
        
        if (mediaFile == null) {
            System.out.println("Returning null - mediaFile is null");
            return null;
        }
        
        byte[] fileData = mediaDatabase.getFileData(mediaFileId);
        System.out.println("File data from DB: " + (fileData != null ? fileData.length + " bytes" : "NULL"));
        
        if (fileData == null || fileData.length == 0) {
            System.out.println("Returning null - fileData is " + (fileData == null ? "null" : "empty"));
            return null;
        }
        
        long fileSize = fileData.length;
        long start = 0;
        long end = fileSize - 1;
        boolean isPartialContent = false;
        
        // Parse Range header if present (e.g., "bytes=0-1023")
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isPartialContent = true;
            String[] ranges = rangeHeader.substring(6).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                // Invalid range format - return error indicator with negative start
                return new StreamResult(null, -1, -1, fileSize, false, mediaFile.getContentType());
            }
        }
        
        // Validate range
        if (start > end || start < 0 || end >= fileSize) {
            // Invalid range - return error indicator with negative start
            return new StreamResult(null, -1, -1, fileSize, false, mediaFile.getContentType());
        }
        
        // Extract requested range
        long contentLength = end - start + 1;
        byte[] rangeData = new byte[(int) contentLength];
        System.arraycopy(fileData, (int) start, rangeData, 0, (int) contentLength);
        
        return new StreamResult(rangeData, start, end, fileSize, isPartialContent, mediaFile.getContentType());
    }
    
    @Override
    public MediaFile uploadMediaFromFile(File file, int userId, String title, String description,
                                         boolean isPublic, String mediaType, String sourceUrl) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File cannot be null or must exist");
        }
        
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        
        try {
            int id = mediaDatabase.getNextMediaFileId();
            MediaType type = MediaType.fromString(mediaType);
            
            // Determine content type from file extension
            String contentType = determineContentType(file.getName(), type);
            
            // Create MediaFile object
            MediaFile mediaFile = new MediaFile(
                id,
                file.getName(),
                file.getName(),
                contentType,
                file.length(),
                "", // Will be set by storage service
                userId,
                title.trim(),
                description != null ? description.trim() : "",
                isPublic
            );
            
            mediaFile.setUploadDate(LocalDateTime.now());
            mediaFile.setMediaType(type);
            mediaFile.setSourceUrl(sourceUrl);
            
            // Store file on 500GB volume using OptimizedFileStorageService
            String storedFilePath = null;
            if (fileStorageService != null) {
                // Create a wrapper to work with the storage service
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    storedFilePath = fileStorageService.storeFileFromStream(
                        fis, file.getName(), file.length(), type);
                    if (storedFilePath != null) {
                        mediaFile.setFilePath(storedFilePath);
                    }
                }
            }
            
            // Save metadata to database
            mediaDatabase.addMediaFile(mediaFile);
            
            // Fallback to database storage if file storage failed
            if (storedFilePath == null) {
                byte[] fileData = Files.readAllBytes(file.toPath());
                mediaDatabase.storeFileData(id, fileData);
            }
            
            return mediaFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Determine content type based on filename and media type
     */
    private String determineContentType(String filename, MediaType mediaType) {
        String lower = filename.toLowerCase();
        
        // Check by extension first
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".webm")) return mediaType == MediaType.VIDEO ? "video/webm" : "audio/webm";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        
        // Fallback by media type
        switch (mediaType) {
            case MUSIC:
            case AUDIOBOOK:
                return "audio/mpeg";
            case VIDEO:
                return "video/mp4";
            default:
                return "application/octet-stream";
        }
    }
}

