package lexicon.api;

import lexicon.logic.ChatFileManagerService;
import lexicon.logic.ChatFileManagerService.ChatFileServingInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
        try {
            Map<String, Object> response = chatFileManagerService.uploadChatFile(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    userId,
                    channelId
            );
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
     * GET /api/chat/files/{fileId} — Serve the original uploaded file
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> serveChatFile(@PathVariable long fileId) {
        ChatFileServingInfo info = chatFileManagerService.getFileForServing(fileId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, info.getContentType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(new FileSystemResource(info.getPath()));
    }

    /**
     * GET /api/chat/files/{fileId}/thumb — Serve the thumbnail
     */
    @GetMapping("/files/{fileId}/thumb")
    public ResponseEntity<Resource> serveChatFileThumbnail(@PathVariable long fileId) {
        ChatFileServingInfo info = chatFileManagerService.getThumbnailForServing(fileId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, info.getContentType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(new FileSystemResource(info.getPath()));
    }
}
