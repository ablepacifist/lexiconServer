package lexicon.api;

import lexicon.service.LiveStreamService;
import lexicon.object.LiveStreamQueue;
import lexicon.object.LiveStreamState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight Live Stream Controller - queue management only
 * 
 * This controller provides a minimal SSE stream for queue operations (add/skip)
 * without loading full media or queue data. Ideal for slow connections.
 * 
 * Endpoints:
 * - GET /api/livestream/light/state - Current media only (no queue)
 * - POST /api/livestream/light/queue - Add to queue
 * - DELETE /api/livestream/light/queue/{id} - Remove from queue
 * - POST /api/livestream/light/skip - Vote to skip
 * - GET /api/livestream/light/updates - SSE stream (state changes only)
 */
@RestController
@RequestMapping("/api/livestream/light")
@CrossOrigin(origins = "*")
public class LiveStreamLightController {

    @Autowired
    private LiveStreamService liveStreamService;

    /**
     * Get current playing media ONLY - no queue data
     * Super lightweight for status checks
     * GET /api/livestream/light/state
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getLightState() {
        try {
            LiveStreamState state = liveStreamService.getStreamState();
            
            // Strip down to bare minimum - just current media and playback state
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("currentMediaId", state.getCurrentMediaId());
            response.put("currentMedia", state.getCurrentMedia());
            response.put("currentStartTime", state.getCurrentStartTime());
            response.put("currentPositionMs", state.getCurrentPositionMs());
            response.put("requiredSkipVotes", state.getRequiredSkipVotes());
            response.put("totalSkipVotes", state.getTotalSkipVotes());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get state: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Add media to queue (same as full version)
     * POST /api/livestream/light/queue
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
            response.put("queueId", queueItem.getId());
            response.put("position", queueItem.getPosition());
            
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
     * DELETE /api/livestream/light/queue/{queueId}?userId={userId}
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
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to remove from queue: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Vote to skip current media
     * POST /api/livestream/light/skip
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
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to vote skip: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Lightweight SSE stream - only state updates (no queue data)
     * GET /api/livestream/light/updates
     * 
     * Sends minimal events:
     * - state-update: { currentMediaId, title, position, startTime }
     * - skip-update: { totalSkipVotes }
     * 
     * No full queue serialization = much faster updates
     */
    @GetMapping(value = "/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(origins = "*", allowedHeaders = "*", allowCredentials = "false", maxAge = 3600)
    public SseEmitter streamLightUpdates() {
        // Set reasonable timeout - 30 minutes
        SseEmitter emitter = new SseEmitter(1800000L);
        
        try {
            // Register with service (will receive minimal updates)
            liveStreamService.registerEmitter(emitter);
            
            // Immediately send heartbeat
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("connected"));
            
            // Send current state snapshot
            LiveStreamState state = liveStreamService.getStreamState();
            
            Map<String, Object> stateData = new HashMap<>();
            stateData.put("currentMediaId", state.getCurrentMediaId());
            stateData.put("currentMedia", state.getCurrentMedia());
            stateData.put("currentStartTime", state.getCurrentStartTime());
            stateData.put("currentPositionMs", state.getCurrentPositionMs());
            stateData.put("timestamp", System.currentTimeMillis());
            
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(stateData));
            
            // Handle cleanup
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
}
