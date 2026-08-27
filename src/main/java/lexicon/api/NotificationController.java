package lexicon.api;

import lexicon.logic.NotificationManagerService;
import lexicon.object.Notification;
import lexicon.object.NotificationPrefs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for notifications. Thin delegator only — each method calls a
 * single NotificationManagerService method and wraps the result. No SQL, no SSE
 * emitter handling, and no push logic live here.
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private NotificationManagerService notificationService;

    /** POST /api/notifications — create a notification (called by the Mumble bridge). */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Notification notification) {
        Map<String, Object> response = new HashMap<>();
        try {
            long id = notificationService.create(notification);
            response.put("success", true);
            response.put("id", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to create notification: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** GET /api/notifications?userId=&limit=&before= — history list. */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam int userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        try {
            List<Notification> items = notificationService.list(userId, limit, before);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return errorBody("Failed to list notifications: " + e.getMessage());
        }
    }

    /** GET /api/notifications/unread-count?userId= */
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@RequestParam int userId) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("count", notificationService.unreadCount(userId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorBody("Failed to get unread count: " + e.getMessage());
        }
    }

    /** POST /api/notifications/read-all?userId= */
    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(@RequestParam int userId) {
        try {
            notificationService.markAllRead(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorBody("Failed to mark read: " + e.getMessage());
        }
    }

    /** GET /api/notifications/prefs?userId= */
    @GetMapping("/prefs")
    public ResponseEntity<?> getPrefs(@RequestParam int userId) {
        try {
            return ResponseEntity.ok(notificationService.getPrefs(userId));
        } catch (Exception e) {
            return errorBody("Failed to get preferences: " + e.getMessage());
        }
    }

    /** PUT /api/notifications/prefs?userId= — body is the updated preferences. */
    @PutMapping("/prefs")
    public ResponseEntity<?> updatePrefs(
            @RequestParam int userId,
            @RequestBody NotificationPrefs prefs) {
        try {
            prefs.setUserId(userId);
            notificationService.updatePrefs(prefs);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorBody("Failed to update preferences: " + e.getMessage());
        }
    }

    /** GET /api/notifications/stream?userId= — SSE live stream. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(origins = "*", allowedHeaders = "*", allowCredentials = "false", maxAge = 3600)
    public SseEmitter stream(@RequestParam int userId) {
        return notificationService.subscribe(userId);
    }

    private ResponseEntity<Map<String, Object>> errorBody(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
