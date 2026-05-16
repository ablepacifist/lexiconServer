package lexicon.api;

import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import lexicon.object.StreamResult;
import lexicon.service.OptimizedFileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Media file management in the Lexicon server
 * Handles file uploads, downloads, and media file operations
 */
@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*")
public class MediaController {

    @Autowired
    private MediaManagerService mediaManager;

    @Autowired
    private OptimizedFileStorageService fileStorageService;

    /**
     * Upload a media file
     * POST /api/media/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") int userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            @RequestParam(value = "mediaType", defaultValue = "OTHER") String mediaType) {
        
        try {
            MediaFile mediaFile = mediaManager.uploadMediaFile(file, userId, title, description, isPublic, mediaType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("mediaFile", mediaFile);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Upload from a URL using yt-dlp
     * POST /api/media/upload-from-url
     * User provides URL and download type, then fills out title/description/mediaType
     */
    @PostMapping("/upload-from-url")
    public ResponseEntity<Map<String, Object>> uploadFromUrl(
            @RequestParam("url") String url,
            @RequestParam("userId") int userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            @RequestParam(value = "mediaType", defaultValue = "OTHER") String mediaType,
            @RequestParam(value = "downloadType", defaultValue = "AUDIO_ONLY") String downloadType) {
        
        try {
            MediaFile mediaFile = mediaManager.uploadMediaFromUrl(url, userId, title, description, isPublic, mediaType, downloadType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Media downloaded and uploaded successfully");
            response.put("mediaFile", mediaFile);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to download from URL: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get media file by ID
     * GET /api/media/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<MediaFile> getMediaFile(@PathVariable int id) {
        try {
            MediaFile mediaFile = mediaManager.getMediaFileById(id);
            if (mediaFile == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(mediaFile);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all media files by user
     * GET /api/media/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<MediaFile>> getMediaFilesByUser(@PathVariable int userId) {
        try {
            List<MediaFile> files = mediaManager.getMediaFilesByUser(userId);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all public media files
     * GET /api/media/public
     */
    @GetMapping("/public")
    public ResponseEntity<List<MediaFile>> getPublicMediaFiles() {
        try {
            List<MediaFile> files = mediaManager.getAllPublicMediaFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search media files
     * GET /api/media/search?q=searchTerm
     */
    @GetMapping("/search")
    public ResponseEntity<List<MediaFile>> searchMediaFiles(@RequestParam("q") String searchTerm) {
        try {
            List<MediaFile> files = mediaManager.searchMediaFiles(searchTerm);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get recent media files
     * GET /api/media/recent?limit=10
     */
    @GetMapping("/recent")
    public ResponseEntity<List<MediaFile>> getRecentMediaFiles(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        try {
            List<MediaFile> files = mediaManager.getRecentMediaFiles(limit);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update media file metadata
     * PUT /api/media/{id}?userId=123
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateMediaFile(
            @PathVariable int id,
            @RequestParam int userId,
            @RequestBody Map<String, Object> updates) {
        try {
            boolean updated = mediaManager.updateMediaFile(id, userId, updates);
            
            Map<String, Object> response = new HashMap<>();
            if (updated) {
                response.put("success", true);
                response.put("message", "Media file updated successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Media file not found or permission denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to update: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete media file
     * DELETE /api/media/{id}?userId=123
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMediaFile(
            @PathVariable int id,
            @RequestParam("userId") int userId) {
        try {
            boolean deleted = mediaManager.deleteMediaFile(id, userId);
            
            Map<String, Object> response = new HashMap<>();
            if (deleted) {
                response.put("success", true);
                response.put("message", "Media file deleted successfully");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Media file not found or permission denied");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Check access permission
     * GET /api/media/{id}/access?userId=123
     */
    @GetMapping("/{id}/access")
    public ResponseEntity<Map<String, Object>> checkAccess(
            @PathVariable int id,
            @RequestParam("userId") int userId) {
        try {
            boolean hasAccess = mediaManager.hasAccessPermission(id, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("hasAccess", hasAccess);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download file data
     * GET /api/media/{id}/download
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable int id) {
        try {
            MediaFile mediaFile = mediaManager.getMediaFileById(id);
            if (mediaFile == null) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] fileData = mediaManager.getFileData(id);
            if (fileData == null || fileData.length == 0) {
                return ResponseEntity.noContent().build();
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mediaFile.getContentType()))
                    .header("Content-Disposition", "attachment; filename=\"" + mediaFile.getOriginalFilename() + "\"")
                    .body(fileData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Stream video/audio with range request support for smooth playback
     * GET /api/media/stream/{id}
     *
     * No Range header: HTTP 200 + stream full file via InputStreamResource.
     * With Range header: HTTP 206 + 2MB byte[] chunks.
     */
    @GetMapping("/stream/{id}")
    public ResponseEntity<?> streamFile(
            @PathVariable int id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {
        try {
            // No Range header — return HTTP 200 with full file streamed.
            // Mobile browsers REJECT 206 when they didn't send a Range header.
            if (rangeHeader == null || rangeHeader.isEmpty()) {
                MediaFile mediaFile = mediaManager.getMediaFileById(id);
                if (mediaFile == null) {
                    return ResponseEntity.notFound().build();
                }
                String filePath = mediaFile.getFilePath();
                if (filePath != null && !filePath.isEmpty()) {
                    long fileSize = fileStorageService.getFileSize(filePath);
                    InputStream inputStream = fileStorageService.getFileInputStream(filePath);
                    InputStreamResource resource = new InputStreamResource(inputStream);
                    String contentType = mediaFile.getContentType() != null ? mediaFile.getContentType() : "application/octet-stream";
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header("Accept-Ranges", "bytes")
                            .header("Content-Length", String.valueOf(fileSize))
                            .body(resource);
                }
                // DB-stored file fallback
                StreamResult result = mediaManager.getStreamData(id, null);
                if (result == null) return ResponseEntity.notFound().build();
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(result.getContentType()))
                        .header("Accept-Ranges", "bytes")
                        .header("Content-Length", String.valueOf(result.getTotalSize()))
                        .body(result.getData());
            }

            // Range header present — chunked 206 response
            StreamResult result = mediaManager.getStreamData(id, rangeHeader);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            if (result.getStart() < 0) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + result.getTotalSize())
                        .build();
            }
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.parseMediaType(result.getContentType()))
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Range", "bytes " + result.getStart() + "-" + result.getEnd() + "/" + result.getTotalSize())
                    .header("Content-Length", String.valueOf(result.getContentLength()))
                    .body(result.getData());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

