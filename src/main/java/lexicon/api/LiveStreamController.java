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
 * REST Controller for Live Stream functionality with channel support.
 * All endpoints accept a ?channel=music or ?channel=video parameter.
 */
@RestController
@RequestMapping("/api/livestream")
@CrossOrigin(origins = "*")
public class LiveStreamController {

    @Autowired
    private LiveStreamService liveStreamService;

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState(
            @RequestParam(defaultValue = "video") String channel) {
        try {
            LiveStreamState state = liveStreamService.getStreamState(channel);
            
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

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue(
            @RequestParam(defaultValue = "video") String channel) {
        try {
            List<LiveStreamQueue> queue = liveStreamService.getQueue(channel);
            
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

    @GetMapping("/eligible-media")
    public ResponseEntity<Map<String, Object>> getEligibleMedia(
            @RequestParam(defaultValue = "video") String channel) {
        try {
            List<MediaFile> media = liveStreamService.getEligibleMedia(channel);
            
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

    @PostMapping("/queue/playlist")
    public ResponseEntity<Map<String, Object>> addPlaylistToQueue(
            @RequestParam(defaultValue = "video") String channel,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer userId = request.get("userId");
            Integer playlistId = request.get("playlistId");
            
            if (userId == null || playlistId == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "userId and playlistId are required");
                return ResponseEntity.badRequest().body(error);
            }
            
            int added = liveStreamService.addPlaylistToQueue(channel, userId, playlistId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Added " + added + " tracks to queue");
            response.put("addedCount", added);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (SecurityException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to add playlist to queue: " + e.getMessage());
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
    public SseEmitter streamUpdates(
            @RequestParam(defaultValue = "video") String channel) {
        SseEmitter emitter = new SseEmitter(1800000L);
        
        try {
            liveStreamService.registerEmitter(channel, emitter);
            
            // Heartbeat
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data("connected"));
            
            // Initial state
            LiveStreamState state = liveStreamService.getStreamState(channel);
            List<LiveStreamQueue> fullQueue = liveStreamService.getQueue(channel);
            
            List<LiveStreamQueue> minimalQueue = new ArrayList<>();
            if (fullQueue != null && !fullQueue.isEmpty()) {
                int playingIndex = -1;
                for (int i = 0; i < fullQueue.size(); i++) {
                    if (fullQueue.get(i).getStatus() == LiveStreamQueue.QueueStatus.PLAYING) {
                        playingIndex = i;
                        break;
                    }
                }
                int startIdx = Math.max(0, playingIndex == -1 ? 0 : playingIndex);
                int endIdx = Math.min(fullQueue.size(), startIdx + 6);
                minimalQueue = fullQueue.subList(startIdx, endIdx);
            }
            
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("type", "init");
            initialData.put("channel", channel);
            initialData.put("state", state);
            initialData.put("queue", minimalQueue);
            initialData.put("queueSize", fullQueue != null ? fullQueue.size() : 0);
            initialData.put("timestamp", System.currentTimeMillis());
            
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(initialData));
            
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

    @PostMapping("/media-ended")
    public ResponseEntity<Map<String, Object>> mediaEnded(
            @RequestParam(defaultValue = "video") String channel) {
        try {
            long start = System.currentTimeMillis();
            liveStreamService.mediaEnded(channel);
            
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

    @PostMapping("/advance")
    public ResponseEntity<Map<String, Object>> advanceToNext(
            @RequestParam(defaultValue = "video") String channel) {
        try {
            liveStreamService.checkAndAdvanceIfNeeded(channel);
            
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
