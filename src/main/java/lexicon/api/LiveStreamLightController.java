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
 * Lightweight Live Stream Controller with channel support.
 */
@RestController
@RequestMapping("/api/livestream/light")
@CrossOrigin(origins = "*")
public class LiveStreamLightController {

    @Autowired
    private LiveStreamService liveStreamService;

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getLightState(
            @RequestParam(defaultValue = "video") String channel) {
        try {
            LiveStreamState state = liveStreamService.getStreamState(channel);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("channel", channel);
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

    @PostMapping("/queue")
    public ResponseEntity<Map<String, Object>> addToQueue(
            @RequestParam(defaultValue = "video") String channel,
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
            
            LiveStreamQueue queueItem = liveStreamService.addToQueue(channel, userId, mediaFileId);
            
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

    @DeleteMapping("/queue/{queueId}")
    public ResponseEntity<Map<String, Object>> removeFromQueue(
            @PathVariable int queueId,
            @RequestParam int userId,
            @RequestParam(defaultValue = "video") String channel) {
        try {
            liveStreamService.removeFromQueue(channel, queueId, userId);
            
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

    @PostMapping("/skip")
    public ResponseEntity<Map<String, Object>> voteSkip(
            @RequestParam(defaultValue = "video") String channel,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer userId = request.get("userId");
            
            if (userId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            boolean skipped = liveStreamService.voteSkip(channel, userId);
            
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

    @GetMapping(value = "/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(origins = "*", allowedHeaders = "*", allowCredentials = "false", maxAge = 3600)
    public SseEmitter streamLightUpdates(
            @RequestParam(defaultValue = "video") String channel) {
        SseEmitter emitter = new SseEmitter(1800000L);
        
        try {
            liveStreamService.registerEmitter(channel, emitter);
            
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("connected"));
            
            LiveStreamState state = liveStreamService.getStreamState(channel);
            
            Map<String, Object> stateData = new HashMap<>();
            stateData.put("channel", channel);
            stateData.put("currentMediaId", state.getCurrentMediaId());
            stateData.put("currentMedia", state.getCurrentMedia());
            stateData.put("currentStartTime", state.getCurrentStartTime());
            stateData.put("currentPositionMs", state.getCurrentPositionMs());
            stateData.put("timestamp", System.currentTimeMillis());
            
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(stateData));
            
            emitter.onCompletion(() -> liveStreamService.unregisterEmitter(channel, emitter));
            emitter.onTimeout(() -> {
                liveStreamService.unregisterEmitter(channel, emitter);
                emitter.complete();
            });
            emitter.onError((e) -> liveStreamService.unregisterEmitter(channel, emitter));
            
        } catch (Exception e) {
            liveStreamService.unregisterEmitter(channel, emitter);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
}
