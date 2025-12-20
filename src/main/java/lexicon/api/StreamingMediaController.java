package lexicon.api;

import lexicon.service.OptimizedFileStorageService;
import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

/**
 * Enhanced media streaming controller with HTTP range request support
 * Optimized for video streaming and large file delivery
 */
@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*")
public class StreamingMediaController {
    
    @Autowired
    private OptimizedFileStorageService fileStorageService;
    
    @Autowired
    private MediaManagerService mediaManager;
    
    /**
     * Stream media file with HTTP range support for video seeking
     */
    @GetMapping("/{mediaFileId}")
    public ResponseEntity<InputStreamResource> streamMedia(
            @PathVariable int mediaFileId,
            HttpServletRequest request) {
        
        try {
            // Get media file info
            MediaFile mediaFile = mediaManager.getMediaFileById(mediaFileId);
            if (mediaFile == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Check if file is stored on file system
            if (mediaFile.getFilePath() == null || mediaFile.getFilePath().isEmpty()) {
                // Fall back to database streaming (for small files)
                return streamFromDatabase(mediaFileId);
            }
            
            // Parse Range header for seeking support
            String rangeHeader = request.getHeader("Range");
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(mediaFile, rangeHeader);
            } else {
                return handleFullFileRequest(mediaFile);
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Handle HTTP range requests for video seeking
     */
    private ResponseEntity<InputStreamResource> handleRangeRequest(MediaFile mediaFile, String rangeHeader) {
        try {
            // Parse range: "bytes=start-end"
            String range = rangeHeader.substring(6); // Remove "bytes="
            String[] ranges = range.split("-");
            
            long fileSize = fileStorageService.getFileSize(mediaFile.getFilePath());
            long start = 0;
            long end = fileSize - 1;
            
            if (ranges.length >= 1 && !ranges[0].isEmpty()) {
                start = Long.parseLong(ranges[0]);
            }
            if (ranges.length >= 2 && !ranges[1].isEmpty()) {
                end = Long.parseLong(ranges[1]);
            }
            
            // Validate range
            if (start >= fileSize || end >= fileSize || start > end) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileSize)
                    .build();
            }
            
            // Get file stream for range
            OptimizedFileStorageService.FileStreamInfo streamInfo = 
                fileStorageService.getFileForStreaming(mediaFile.getFilePath(), start, end);
            
            // Create input stream resource
            InputStreamResource resource = new InputStreamResource(
                new BufferedInputStream(new FileInputStream(streamInfo.getFile().getFD()))
            ) {
                @Override
                public long contentLength() {
                    return streamInfo.getContentLength();
                }
            };
            
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize))
                .header("Content-Length", String.valueOf(streamInfo.getContentLength()))
                .contentType(MediaType.parseMediaType(getContentType(mediaFile)))
                .body(resource);
                
        } catch (Exception e) {
            System.err.println("Error handling range request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Handle full file requests
     */
    private ResponseEntity<InputStreamResource> handleFullFileRequest(MediaFile mediaFile) {
        try {
            InputStream inputStream = fileStorageService.getFileInputStream(mediaFile.getFilePath());
            long fileSize = fileStorageService.getFileSize(mediaFile.getFilePath());
            
            InputStreamResource resource = new InputStreamResource(inputStream) {
                @Override
                public long contentLength() {
                    return fileSize;
                }
            };
            
            return ResponseEntity.ok()
                .header("Accept-Ranges", "bytes")
                .header("Content-Length", String.valueOf(fileSize))
                .contentType(MediaType.parseMediaType(getContentType(mediaFile)))
                .body(resource);
                
        } catch (IOException e) {
            System.err.println("Error streaming full file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get content type for media file, with fallback based on media type or file extension
     */
    private String getContentType(MediaFile mediaFile) {
        // Return actual content type if available
        if (mediaFile.getContentType() != null && !mediaFile.getContentType().isEmpty()) {
            return mediaFile.getContentType();
        }
        
        // Fallback based on media type
        if (mediaFile.getMediaType() != null) {
            switch (mediaFile.getMediaType()) {
                case VIDEO:
                    return "video/mp4";
                case MUSIC:
                case AUDIOBOOK:
                    return "audio/mpeg";
                default:
                    break;
            }
        }
        
        // Fallback based on filename extension
        String filename = mediaFile.getFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv")) {
                return "video/mp4";
            } else if (lower.endsWith(".mp3")) {
                return "audio/mpeg";
            } else if (lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                return "audio/aac";
            } else if (lower.endsWith(".wav")) {
                return "audio/wav";
            } else if (lower.endsWith(".ogg")) {
                return "audio/ogg";
            } else if (lower.endsWith(".flac")) {
                return "audio/flac";
            }
        }
        
        // Default fallback
        return "application/octet-stream";
    }
    
    /**
     * Fall back to database streaming for small files
     */
    private ResponseEntity<InputStreamResource> streamFromDatabase(int mediaFileId) {
        try {
            // Use existing media streaming endpoint
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/api/media/" + mediaFileId + "/stream")
                .build();
                
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get media file info for client-side streaming setup
     */
    @GetMapping("/{mediaFileId}/info")
    public ResponseEntity<MediaStreamInfo> getStreamInfo(@PathVariable int mediaFileId) {
        try {
            MediaFile mediaFile = mediaManager.getMediaFileById(mediaFileId);
            if (mediaFile == null) {
                return ResponseEntity.notFound().build();
            }
            
            long fileSize = 0;
            boolean supportsRanges = false;
            
            if (mediaFile.getFilePath() != null && !mediaFile.getFilePath().isEmpty()) {
                fileSize = fileStorageService.getFileSize(mediaFile.getFilePath());
                supportsRanges = true;
            } else {
                fileSize = mediaFile.getFileSize();
            }
            
            MediaStreamInfo info = new MediaStreamInfo(
                mediaFile.getId(),
                mediaFile.getTitle(),
                mediaFile.getContentType(),
                fileSize,
                supportsRanges,
                mediaFile.getMediaType().name()
            );
            
            return ResponseEntity.ok(info);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Media stream information class
     */
    public static class MediaStreamInfo {
        private int id;
        private String title;
        private String contentType;
        private long fileSize;
        private boolean supportsRanges;
        private String mediaType;
        
        public MediaStreamInfo(int id, String title, String contentType, long fileSize, 
                              boolean supportsRanges, String mediaType) {
            this.id = id;
            this.title = title;
            this.contentType = contentType;
            this.fileSize = fileSize;
            this.supportsRanges = supportsRanges;
            this.mediaType = mediaType;
        }
        
        // Getters
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getContentType() { return contentType; }
        public long getFileSize() { return fileSize; }
        public boolean isSupportsRanges() { return supportsRanges; }
        public String getMediaType() { return mediaType; }
    }
}