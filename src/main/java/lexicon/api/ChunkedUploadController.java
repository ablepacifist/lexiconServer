package lexicon.api;

import lexicon.logic.ChunkedUploadService;
import lexicon.logic.ChunkedUploadProgressTracker;
import lexicon.object.ChunkedUpload;
import lexicon.object.UploadChunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for chunked file uploads
 * Handles large file uploads by splitting them into chunks
 */
@RestController
@RequestMapping("/api/media/chunked")
@CrossOrigin(origins = "*")
public class ChunkedUploadController {

    @Autowired
    private ChunkedUploadService chunkedUploadService;
    
    @Autowired
    private ChunkedUploadProgressTracker progressTracker;

    /**
     * Initialize a new chunked upload session
     * POST /api/media/chunked/init
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeUpload(
            @RequestParam("filename") String filename,
            @RequestParam("contentType") String contentType,
            @RequestParam("totalSize") long totalSize,
            @RequestParam("chunkSize") int chunkSize,
            @RequestParam("userId") int userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            @RequestParam(value = "mediaType", defaultValue = "OTHER") String mediaType,
            @RequestParam(value = "checksum", required = false) String checksum) {
        
        try {
            ChunkedUpload upload = chunkedUploadService.initializeUpload(
                filename, contentType, totalSize, chunkSize, userId, 
                title, description, isPublic, mediaType, checksum
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", upload.getUploadId());
            response.put("totalChunks", upload.getTotalChunks());
            response.put("chunkSize", upload.getChunkSize());
            response.put("message", "Chunked upload session initialized");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to initialize upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Upload a single chunk
     * POST /api/media/chunked/upload/{uploadId}
     */
    @PostMapping("/upload/{uploadId}")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @PathVariable String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("chunk") MultipartFile chunkFile,
            @RequestParam(value = "checksum", required = false) String checksum) {
        
        try {
            boolean success = chunkedUploadService.uploadChunk(uploadId, chunkNumber, chunkFile, checksum);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            
            if (success) {
                ChunkedUpload upload = chunkedUploadService.getUploadStatus(uploadId);
                response.put("progress", upload.getProgress());
                response.put("uploadedChunks", upload.getUploadedChunks().size());
                response.put("totalChunks", upload.getTotalChunks());
                response.put("isComplete", upload.isComplete());
                response.put("message", "Chunk uploaded successfully");
                
                // If upload is complete, start assembly
                if (upload.isComplete()) {
                    response.put("assembling", true);
                    response.put("message", "All chunks uploaded, starting assembly...");
                }
            } else {
                response.put("message", "Failed to upload chunk");
            }
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to upload chunk: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get upload status and progress
     * GET /api/media/chunked/status/{uploadId}
     */
    @GetMapping("/status/{uploadId}")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@PathVariable String uploadId) {
        try {
            ChunkedUpload upload = chunkedUploadService.getUploadStatus(uploadId);
            
            if (upload == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Upload session not found");
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", upload.getUploadId());
            response.put("filename", upload.getOriginalFilename());
            response.put("totalSize", upload.getTotalSize());
            response.put("totalChunks", upload.getTotalChunks());
            response.put("uploadedChunks", upload.getUploadedChunks().size());
            response.put("progress", upload.getProgress());
            response.put("status", upload.getStatus().toString());
            response.put("isComplete", upload.isComplete());
            response.put("lastActivity", upload.getLastActivity());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get upload status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get missing chunks for resumable uploads
     * GET /api/media/chunked/missing/{uploadId}
     */
    @GetMapping("/missing/{uploadId}")
    public ResponseEntity<Map<String, Object>> getMissingChunks(@PathVariable String uploadId) {
        try {
            ChunkedUpload upload = chunkedUploadService.getUploadStatus(uploadId);
            
            if (upload == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Upload session not found");
                return ResponseEntity.notFound().build();
            }
            
            Set<Integer> missingChunks = upload.getMissingChunks();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", uploadId);
            response.put("missingChunks", missingChunks);
            response.put("missingCount", missingChunks.size());
            response.put("totalChunks", upload.getTotalChunks());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get missing chunks: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Finalize upload and create MediaFile
     * POST /api/media/chunked/finalize/{uploadId}
     */
    @PostMapping("/finalize/{uploadId}")
    public ResponseEntity<Map<String, Object>> finalizeUpload(@PathVariable String uploadId) {
        try {
            Map<String, Object> result = chunkedUploadService.finalizeUpload(uploadId);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to finalize upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Cancel an upload session
     * DELETE /api/media/chunked/{uploadId}
     */
    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Map<String, Object>> cancelUpload(@PathVariable String uploadId) {
        try {
            boolean cancelled = chunkedUploadService.cancelUpload(uploadId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", cancelled);
            response.put("message", cancelled ? "Upload cancelled successfully" : "Upload session not found");
            
            return cancelled ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to cancel upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get real-time progress updates via Server-Sent Events
     * GET /api/media/chunked/progress/{uploadId}
     */
    @GetMapping("/progress/{uploadId}")
    public SseEmitter getUploadProgress(@PathVariable String uploadId) {
        return progressTracker.registerEmitter(uploadId);
    }
}