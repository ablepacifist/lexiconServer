package lexicon.api;

import lexicon.service.LiveStreamService;
import lexicon.object.LiveStreamQueue;
import lexicon.object.LiveStreamState;
import lexicon.object.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Live Stream functionality
 * Manages the synchronized video/music stream for all users
 */
@RestController
@RequestMapping("/api/livestream")
@CrossOrigin(origins = "*")
public class LiveStreamController {

    @Autowired
    private LiveStreamService liveStreamService;

    /**
     * Get current live stream state
     * GET /api/livestream/state
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState() {
        try {
            LiveStreamState state = liveStreamService.getStreamState();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("state", state);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get stream state: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get current queue
     * GET /api/livestream/queue
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue() {
        try {
            List<LiveStreamQueue> queue = liveStreamService.getQueue();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("queue", queue);
            response.put("count", queue.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get queue: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get all media eligible for the livestream queue
     * Includes public media + private media in public playlists
     * GET /api/livestream/eligible-media
     */
    @GetMapping("/eligible-media")
    public ResponseEntity<Map<String, Object>> getEligibleMedia() {
        try {
            List<MediaFile> media = liveStreamService.getEligibleMedia();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("media", media);
            response.put("count", media.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get eligible media: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Add media to queue
     * POST /api/livestream/queue
     * Body: { "userId": 1, "mediaFileId": 123 }
     */
    @PostMapping("/queue")
    public ResponseEntity<Map<String, Object>> addToQueue(
            @RequestBody Map<String, Integer> request) {
        try {
            Integer userId = request.get("userId");
            Integer mediaFileId = request.get("mediaFileId");
            
            if (userId == null || mediaFileId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userId and mediaFileId are required");
                return ResponseEntity.badRequest().body(error);
            }
            
            LiveStreamQueue queueItem = liveStreamService.addToQueue(userId, mediaFileId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Added to queue");
            response.put("queueItem", queueItem);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to add to queue: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Remove item from queue
     * DELETE /api/livestream/queue/{queueId}?userId={userId}
     */
    @DeleteMapping("/queue/{queueId}")
    public ResponseEntity<Map<String, Object>> removeFromQueue(
            @PathVariable int queueId,
            @RequestParam int userId) {
        try {
            liveStreamService.removeFromQueue(queueId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Removed from queue");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to remove from queue: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Vote to skip current media
     * POST /api/livestream/skip
     * Body: { "userId": 1 }
     */
    @PostMapping("/skip")
    public ResponseEntity<Map<String, Object>> voteSkip(
            @RequestBody Map<String, Integer> request) {
        try {
            Integer userId = request.get("userId");
            
            if (userId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            boolean skipped = liveStreamService.voteSkip(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("skipped", skipped);
            response.put("message", skipped ? "Media skipped" : "Skip vote recorded");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to vote skip: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Server-Sent Events endpoint for real-time updates
     * GET /api/livestream/updates
     * Requires explicit @CrossOrigin since SSE responses need CORS headers for streaming
     */
    @GetMapping(value = "/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(origins = "*", allowedHeaders = "*", allowCredentials = "false", maxAge = 3600)
    public SseEmitter streamUpdates() {
        // Set reasonable timeout - 30 minutes
        SseEmitter emitter = new SseEmitter(1800000L);
        
        try {
            // Register this emitter with the service FIRST
            liveStreamService.registerEmitter(emitter);
            
            // Immediately send a heartbeat to establish connection
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("connected"));
            
            // Send initial state quickly - minimize payload for faster transmission
            LiveStreamState state = liveStreamService.getStreamState();
            List<LiveStreamQueue> fullQueue = liveStreamService.getQueue();
            
            // Only send currently playing + next 5 items to minimize initial payload
            List<LiveStreamQueue> minimalQueue = new ArrayList<>();
            if (fullQueue != null && !fullQueue.isEmpty()) {
                // Find PLAYING item and include it + next few items
                int playingIndex = -1;
                for (int i = 0; i < fullQueue.size(); i++) {
                    if (fullQueue.get(i).getStatus() == LiveStreamQueue.QueueStatus.PLAYING) {
                        playingIndex = i;
                        break;
                    }
                }
                
                // Include playing item + up to 5 queued items after it
                int startIdx = Math.max(0, playingIndex == -1 ? 0 : playingIndex);
                int endIdx = Math.min(fullQueue.size(), startIdx + 6);
                minimalQueue = fullQueue.subList(startIdx, endIdx);
            }
            
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("type", "init");
            initialData.put("state", state);
            initialData.put("queue", minimalQueue);
            initialData.put("queueSize", fullQueue != null ? fullQueue.size() : 0);
            initialData.put("timestamp", System.currentTimeMillis());
            
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(initialData));
            
            // Handle cleanup on completion or timeout
            emitter.onCompletion(() -> liveStreamService.unregisterEmitter(emitter));
            emitter.onTimeout(() -> {
                liveStreamService.unregisterEmitter(emitter);
                emitter.complete();
            });
            emitter.onError((e) -> {
                liveStreamService.unregisterEmitter(emitter);
            });
            
        } catch (Exception e) {
            liveStreamService.unregisterEmitter(emitter);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }

    /**
     * Report that the current media has ended - advances to next
     * POST /api/livestream/media-ended
     * Called by frontend when video/audio ends naturally
     */
    @PostMapping("/media-ended")
    public ResponseEntity<Map<String, Object>> mediaEnded() {
        try {
            long start = System.currentTimeMillis();
            liveStreamService.mediaEnded();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Advanced to next media");
            response.put("timeMs", System.currentTimeMillis() - start);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to advance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Manually advance to next media (admin/testing)
     * POST /api/livestream/advance
     */
    @PostMapping("/advance")
    public ResponseEntity<Map<String, Object>> advanceToNext() {
        try {
            liveStreamService.checkAndAdvanceIfNeeded();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Advanced to next media");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to advance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
