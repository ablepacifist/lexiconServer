package lexicon.api;

import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for media file operations
 */
@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*") // Allow all origins (matches Alchemy controllers)
public class MediaController {
    
    @Autowired
    private MediaManagerService mediaManagerService;
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") int userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic) {
        
        try {
            MediaFile mediaFile = mediaManagerService.uploadMediaFile(file, userId, title, description, isPublic);
            return ResponseEntity.ok(mediaFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/public")
    public ResponseEntity<List<MediaFile>> getPublicMedia() {
        List<MediaFile> mediaFiles = mediaManagerService.getAllPublicMediaFiles();
        return ResponseEntity.ok(mediaFiles);
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<MediaFile>> getUserMedia(@PathVariable int userId) {
        List<MediaFile> mediaFiles = mediaManagerService.getMediaFilesByUser(userId);
        return ResponseEntity.ok(mediaFiles);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<MediaFile> getMediaFile(@PathVariable int id) {
        MediaFile mediaFile = mediaManagerService.getMediaFileById(id);
        if (mediaFile != null) {
            return ResponseEntity.ok(mediaFile);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadMediaFile(@PathVariable int id, @RequestParam(required = false) Integer userId) {
        // Check permission if userId is provided
        if (userId != null && !mediaManagerService.hasAccessPermission(id, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        MediaFile mediaFile = mediaManagerService.getMediaFileById(id);
        if (mediaFile == null) {
            return ResponseEntity.notFound().build();
        }
        
        // If no userId provided, only allow public files
        if (userId == null && !mediaFile.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        byte[] fileData = mediaManagerService.getFileData(id);
        if (fileData == null) {
            return ResponseEntity.notFound().build();
        }
        
        ByteArrayResource resource = new ByteArrayResource(fileData);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + mediaFile.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(mediaFile.getContentType()))
                .contentLength(fileData.length)
                .body(resource);
    }
    
    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> streamMediaFile(@PathVariable int id, @RequestParam(required = false) Integer userId) {
        // Check permission if userId is provided
        if (userId != null && !mediaManagerService.hasAccessPermission(id, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        MediaFile mediaFile = mediaManagerService.getMediaFileById(id);
        if (mediaFile == null) {
            return ResponseEntity.notFound().build();
        }
        
        // If no userId provided, only allow public files
        if (userId == null && !mediaFile.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        byte[] fileData = mediaManagerService.getFileData(id);
        if (fileData == null) {
            return ResponseEntity.notFound().build();
        }
        
        ByteArrayResource resource = new ByteArrayResource(fileData);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaFile.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(mediaFile.getContentType()))
                .contentLength(fileData.length)
                .body(resource);
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<MediaFile>> searchMedia(@RequestParam String q) {
        List<MediaFile> mediaFiles = mediaManagerService.searchMediaFiles(q);
        return ResponseEntity.ok(mediaFiles);
    }
    
    @GetMapping("/recent")
    public ResponseEntity<List<MediaFile>> getRecentMedia(@RequestParam(defaultValue = "10") int limit) {
        List<MediaFile> mediaFiles = mediaManagerService.getRecentMediaFiles(limit);
        return ResponseEntity.ok(mediaFiles);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMediaFile(@PathVariable int id, @RequestBody MediaFile mediaFile, @RequestParam int userId) {
        MediaFile existingFile = mediaManagerService.getMediaFileById(id);
        if (existingFile == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user owns the file
        if (existingFile.getUploadedBy() != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }
        
        // Only allow updating certain fields
        existingFile.setTitle(mediaFile.getTitle());
        existingFile.setDescription(mediaFile.getDescription());
        existingFile.setPublic(mediaFile.isPublic());
        
        boolean success = mediaManagerService.updateMediaFile(existingFile);
        if (success) {
            return ResponseEntity.ok(existingFile);
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update media file"));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMediaFile(@PathVariable int id, @RequestParam int userId) {
        boolean success = mediaManagerService.deleteMediaFile(id, userId);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Media file deleted successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete media file"));
    }
}
