package lexicon.api;

import lexicon.logic.ChatFileManagerService;
import lexicon.object.ChatFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatFileManagerService chatFileManagerService;

    /**
     * POST /api/chat/upload — Upload an image/GIF for chat
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadChatFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") int userId,
            @RequestParam("channelId") int channelId) {
        Map<String, Object> response = new HashMap<>();
        try {
            ChatFile chatFile = chatFileManagerService.uploadChatFile(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    userId,
                    channelId
            );

            response.put("id", chatFile.getId());
            response.put("url", "/api/chat/files/" + chatFile.getId());
            response.put("thumbnailUrl", "/api/chat/files/" + chatFile.getId() + "/thumb");
            response.put("originalFilename", chatFile.getOriginalFilename());
            response.put("mimeType", chatFile.getMimeType());
            response.put("width", chatFile.getWidth());
            response.put("height", chatFile.getHeight());
            response.put("fileSize", chatFile.getFileSize());
            response.put("uploadedBy", chatFile.getUploadedBy());
            response.put("createdAt", chatFile.getCreatedAt() != null ? chatFile.getCreatedAt().toString() : null);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/chat/files/{fileId} — Serve the original uploaded file
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> serveChatFile(@PathVariable long fileId) {
        ChatFile chatFile = chatFileManagerService.getChatFile(fileId);
        if (chatFile == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = chatFileManagerService.getFilePath(chatFile);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, chatFile.getMimeType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(resource);
    }

    /**
     * GET /api/chat/files/{fileId}/thumb — Serve the thumbnail
     */
    @GetMapping("/files/{fileId}/thumb")
    public ResponseEntity<Resource> serveChatFileThumbnail(@PathVariable long fileId) {
        ChatFile chatFile = chatFileManagerService.getChatFile(fileId);
        if (chatFile == null) {
            return ResponseEntity.notFound().build();
        }

        Path thumbPath = chatFileManagerService.getThumbnailPath(chatFile);
        if (!Files.exists(thumbPath)) {
            return ResponseEntity.notFound().build();
        }

        // Thumbnail is JPEG unless it fell back to original
        String contentType = chatFile.getThumbnailFilename() != null ? "image/jpeg" : chatFile.getMimeType();

        Resource resource = new FileSystemResource(thumbPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(resource);
    }
}
