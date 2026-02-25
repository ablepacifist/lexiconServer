package lexicon.api;

import lexicon.logic.MessageManagerService;
import lexicon.object.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for text message operations
 * Used by Mumble bridge for chat message storage and retrieval
 */
@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    @Autowired
    private MessageManagerService messageManagerService;

    /**
     * POST /api/messages — Store a new text message
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addMessage(@RequestBody TextMessage message) {
        Map<String, Object> response = new HashMap<>();
        try {
            long messageId = messageManagerService.addMessage(message);
            response.put("success", true);
            response.put("messageId", messageId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to store message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/messages/channel/{channelId} — Get message history for a channel
     * Query params: limit (default 50), before (ISO timestamp for pagination)
     */
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<?> getMessagesByChannel(
            @PathVariable int channelId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before) {
        try {
            List<TextMessage> messages = messageManagerService.getMessagesByChannel(channelId, limit, before);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve messages: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/messages/{id} — Get a single message by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getMessage(@PathVariable long id) {
        try {
            TextMessage message = messageManagerService.getMessageById(id);
            if (message == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Message not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * PUT /api/messages/{id}?userId={userId} — Edit a message (owner only)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateMessage(
            @PathVariable long id,
            @RequestParam int userId,
            @RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String content = body.get("content");
            boolean updated = messageManagerService.updateMessage(id, userId, content);
            if (updated) {
                response.put("success", true);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Message not found or you don't have permission to edit it");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to update message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * DELETE /api/messages/{id}?userId={userId} — Soft-delete a message (owner only)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @PathVariable long id,
            @RequestParam int userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean deleted = messageManagerService.deleteMessage(id, userId);
            if (deleted) {
                response.put("success", true);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Message not found or you don't have permission to delete it");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to delete message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/messages/search?q={term}&channelId={channelId} — Search messages
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchMessages(
            @RequestParam String q,
            @RequestParam(defaultValue = "-1") int channelId) {
        try {
            List<TextMessage> messages = messageManagerService.searchMessages(q, channelId);
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to search messages: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
