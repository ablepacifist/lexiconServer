package lexicon.api;

import lexicon.logic.PlaybackPositionManager;
import lexicon.object.PlaybackPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for playback position tracking
 * Delegates all business logic to PlaybackPositionManager
 */
@RestController
@RequestMapping("/api/playback")
@CrossOrigin(origins = "*")
public class PlaybackPositionController {

    @Autowired
    private PlaybackPositionManager playbackPositionManager;

    /**
     * Save or update playback position
     * POST /api/playback/position
     */
    @PostMapping("/position")
    public ResponseEntity<Map<String, Object>> savePosition(@RequestBody Map<String, Object> request) {
        try {
            int userId = (int) request.get("userId");
            int mediaFileId = (int) request.get("mediaFileId");
            double position = ((Number) request.get("position")).doubleValue();
            double duration = ((Number) request.get("duration")).doubleValue();
            boolean completed = request.containsKey("completed") ? (boolean) request.get("completed") : false;
            
            boolean success = playbackPositionManager.savePosition(userId, mediaFileId, position, duration, completed);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Playback position saved" : "Failed to save position");
            return success ? ResponseEntity.ok(response) : 
                           ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to save position: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get playback position for a specific media file
     * GET /api/playback/position/{userId}/{mediaFileId}
     */
    @GetMapping("/position/{userId}/{mediaFileId}")
    public ResponseEntity<?> getPosition(
            @PathVariable int userId,
            @PathVariable int mediaFileId) {
        try {
            PlaybackPosition position = playbackPositionManager.getPosition(userId, mediaFileId);
            
            if (position == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("found", false);
                return ResponseEntity.ok(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("found", true);
            response.put("position", position.getPosition());
            response.put("duration", position.getDuration());
            response.put("completed", position.isCompleted());
            response.put("progressPercentage", position.getProgressPercentage());
            response.put("lastUpdated", position.getLastUpdated());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get position: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get all playback positions for a user
     * GET /api/playback/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPositions(@PathVariable int userId) {
        try {
            List<PlaybackPosition> positions = playbackPositionManager.getUserPositions(userId);
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get positions: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete playback position
     * DELETE /api/playback/position/{userId}/{mediaFileId}
     */
    @DeleteMapping("/position/{userId}/{mediaFileId}")
    public ResponseEntity<Map<String, Object>> deletePosition(
            @PathVariable int userId,
            @PathVariable int mediaFileId) {
        try {
            boolean success = playbackPositionManager.deletePosition(userId, mediaFileId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Playback position deleted" : "Failed to delete position");
            return success ? ResponseEntity.ok(response) : 
                           ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete position: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
