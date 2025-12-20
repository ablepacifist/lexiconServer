package lexicon.api;

import lexicon.logic.PlaylistManager;
import lexicon.object.Playlist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/playlists")
@CrossOrigin(origins = "*")
public class PlaylistController {
    
    @Autowired
    private PlaylistManager playlistManager;
    
    // Store SSE emitters for playlist import progress
    private final Map<String, SseEmitter> playlistImportEmitters = new ConcurrentHashMap<>();
    
    /**
     * Create a new playlist
     */
    @PostMapping
    public ResponseEntity<?> createPlaylist(
            @RequestBody Playlist playlist,
            @RequestParam("userId") Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        try {
            playlist.setCreatedBy(userId);
            int playlistId = playlistManager.createPlaylist(playlist);
            
            if (playlistId > 0) {
                playlist.setId(playlistId);
                
                // Add media files if provided
                if (playlist.getMediaFileIds() != null && !playlist.getMediaFileIds().isEmpty()) {
                    System.out.println("Adding " + playlist.getMediaFileIds().size() + " items to playlist " + playlistId);
                    for (Integer mediaFileId : playlist.getMediaFileIds()) {
                        System.out.println("  Adding media file " + mediaFileId);
                        boolean added = playlistManager.addItemToPlaylist(playlistId, mediaFileId, userId);
                        System.out.println("  Result: " + added);
                    }
                } else {
                    System.out.println("No mediaFileIds provided for playlist " + playlistId);
                }
                
                return ResponseEntity.ok(playlist);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create playlist");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Playlist creation validation error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Playlist creation error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error creating playlist: " + e.getMessage());
        }
    }
    
    /**
     * Get playlists by query parameter (alternative endpoint)
     * Supports GET /api/playlists?userId=X
     */
    @GetMapping
    public ResponseEntity<?> getPlaylistsByUserId(@RequestParam(value = "userId", required = false) Integer userId) {
        if (userId == null) {
            // If no userId provided, return public playlists
            try {
                List<Playlist> playlists = playlistManager.getPublicPlaylists();
                return ResponseEntity.ok(playlists);
            } catch (Exception e) {
                System.err.println("Error fetching public playlists: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching public playlists: " + e.getMessage());
            }
        }
        
        // Return user's playlists
        try {
            List<Playlist> playlists = playlistManager.getPlaylistsByUser(userId);
            return ResponseEntity.ok(playlists);
        } catch (Exception e) {
            System.err.println("Error fetching user playlists: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error fetching playlists: " + e.getMessage());
        }
    }
    
    /**
     * Get playlists created by the current user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPlaylists(@PathVariable Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        try {
            List<Playlist> playlists = playlistManager.getPlaylistsByUser(userId);
            return ResponseEntity.ok(playlists);
        } catch (Exception e) {
            System.err.println("Error fetching user playlists: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error fetching playlists: " + e.getMessage());
        }
    }
    
    /**
     * Get all public playlists
     */
    @GetMapping("/public")
    public ResponseEntity<?> getPublicPlaylists() {
        try {
            List<Playlist> playlists = playlistManager.getPublicPlaylists();
            return ResponseEntity.ok(playlists);
        } catch (Exception e) {
            System.err.println("Error fetching public playlists: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error fetching public playlists: " + e.getMessage());
        }
    }
    
    /**
     * Get a specific playlist with all items
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPlaylist(@PathVariable int id) {
        try {
            Playlist playlist = playlistManager.getPlaylistWithItems(id);
            
            if (playlist == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(playlist);
        } catch (Exception e) {
            System.err.println("Error fetching playlist " + id + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error fetching playlist: " + e.getMessage());
        }
    }
    
    /**
     * Update playlist metadata
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlaylist(
            @PathVariable int id,
            @RequestBody Playlist playlist,
            @RequestParam("userId") Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        try {
            playlist.setId(id);
            boolean success = playlistManager.updatePlaylist(playlist, userId);
            
            if (success) {
                return ResponseEntity.ok(playlist);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating playlist");
        }
    }
    
    /**
     * Delete a playlist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(
            @PathVariable int id,
            @RequestParam("userId") Integer userId,
            @RequestParam(value = "deleteMediaFiles", defaultValue = "false") boolean deleteMediaFiles) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        try {
            boolean success = playlistManager.deletePlaylist(id, userId, deleteMediaFiles);
            
            if (success) {
                String message = deleteMediaFiles 
                    ? "Playlist and associated media files deleted successfully"
                    : "Playlist deleted successfully";
                return ResponseEntity.ok(message);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting playlist: " + e.getMessage());
        }
    }
    
    /**
     * Add a media file to a playlist
     */
    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItem(
            @PathVariable int id,
            @RequestBody Map<String, Integer> body,
            @RequestParam("userId") Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        Integer mediaFileId = body.get("mediaFileId");
        if (mediaFileId == null) {
            return ResponseEntity.badRequest().body("mediaFileId is required");
        }
        
        try {
            boolean success = playlistManager.addItemToPlaylist(id, mediaFileId, userId);
            
            if (success) {
                return ResponseEntity.ok("Item added to playlist");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error adding item");
        }
    }
    
    /**
     * Remove a media file from a playlist
     */
    @DeleteMapping("/{id}/items/{mediaId}")
    public ResponseEntity<?> removeItem(
            @PathVariable int id,
            @PathVariable int mediaId,
            @RequestParam("userId") Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        try {
            boolean success = playlistManager.removeItemFromPlaylist(id, mediaId, userId);
            
            if (success) {
                return ResponseEntity.ok("Item removed from playlist");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error removing item");
        }
    }
    
    /**
     * Reorder playlist items
     */
    @PutMapping("/{id}/reorder")
    public ResponseEntity<?> reorderPlaylist(
            @PathVariable int id,
            @RequestBody Map<String, List<Integer>> body,
            @RequestParam("userId") Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        List<Integer> mediaFileIds = body.get("mediaFileIds");
        if (mediaFileIds == null || mediaFileIds.isEmpty()) {
            return ResponseEntity.badRequest().body("mediaFileIds is required");
        }
        
        try {
            boolean success = playlistManager.reorderPlaylist(id, mediaFileIds, userId);
            
            if (success) {
                return ResponseEntity.ok("Playlist reordered");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reordering playlist");
        }
    }
    
    /**
     * Import a YouTube playlist with progress tracking
     * POST /api/playlists/import-youtube
     */
    @PostMapping("/import-youtube")
    public ResponseEntity<?> importYoutubePlaylist(
            @RequestParam("url") String playlistUrl,
            @RequestParam("userId") Integer userId,
            @RequestParam(value = "playlistName", required = false) String playlistName,
            @RequestParam(value = "isPublic", defaultValue = "true") Boolean isPublic,
            @RequestParam(value = "mediaIsPublic", defaultValue = "false") Boolean mediaIsPublic) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        if (playlistUrl == null || playlistUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Playlist URL is required");
        }
        
        try {
            String importId = "import_" + System.currentTimeMillis() + "_" + userId;
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", "processing");
            response.put("message", "Playlist import started");
            response.put("importId", importId);
            
            // Start background task with progress tracking
            new Thread(() -> {
                try {
                    SseEmitter emitter = playlistImportEmitters.get(importId);
                    if (emitter != null) {
                        sendProgress(emitter, "Starting playlist import...", 0, 0, 0, 0);
                    }
                    
                    PlaylistManager.ImportResult result = playlistManager.importYoutubePlaylist(
                        playlistUrl, userId, playlistName, isPublic, mediaIsPublic, 
                        (message, total, successful, failed) -> {
                            SseEmitter progressEmitter = playlistImportEmitters.get(importId);
                            if (progressEmitter != null) {
                                sendProgress(progressEmitter, message, total, successful, failed, successful + failed);
                            } else {
                                // Continue processing even if SSE is disconnected
                                System.out.println("SSE disconnected but continuing: " + message + " (" + successful + "/" + total + ")");
                            }
                        }
                    );
                    
                    // Send completion
                    if (emitter != null) {
                        sendCompletion(emitter, result);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error in playlist import: " + e.getMessage());
                    e.printStackTrace();
                    SseEmitter emitter = playlistImportEmitters.get(importId);
                    if (emitter != null) {
                        sendError(emitter, "Import failed: " + e.getMessage());
                    }
                } finally {
                    playlistImportEmitters.remove(importId);
                }
            }).start();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error starting playlist import: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error starting playlist import: " + e.getMessage());
        }
    }
    
    /**
     * Get SSE stream for playlist import progress
     * GET /api/playlists/import-progress/{importId}
     */
    @GetMapping("/import-progress/{importId}")
    public SseEmitter getImportProgress(@PathVariable String importId) {
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L); // 24 hour timeout - effectively no timeout
        
        emitter.onCompletion(() -> playlistImportEmitters.remove(importId));
        emitter.onTimeout(() -> playlistImportEmitters.remove(importId));
        emitter.onError((ex) -> playlistImportEmitters.remove(importId));
        
        playlistImportEmitters.put(importId, emitter);
        
        // Send initial connection confirmation
        try {
            Map<String, Object> connected = new java.util.HashMap<>();
            connected.put("type", "connected");
            connected.put("message", "Connected to progress stream");
            connected.put("importId", importId);
            emitter.send(SseEmitter.event().name("connected").data(connected));
        } catch (Exception e) {
            System.err.println("Error sending connection confirmation: " + e.getMessage());
        }
        
        return emitter;
    }
    
    private void sendProgress(SseEmitter emitter, String message, int total, int successful, int failed, int processed) {
        try {
            if (emitter == null) {
                System.err.println("Cannot send progress: emitter is null");
                return;
            }
            
            Map<String, Object> progress = new java.util.HashMap<>();
            progress.put("type", "progress");
            progress.put("message", message);
            progress.put("total", total);
            progress.put("successful", successful);
            progress.put("failed", failed);
            progress.put("processed", processed);
            progress.put("percentage", total > 0 ? (processed * 100 / total) : 0);
            
            emitter.send(SseEmitter.event().name("progress").data(progress));
            System.out.println("Progress sent: " + message + " (" + successful + "/" + total + ")");
        } catch (java.io.IOException e) {
            System.err.println("SSE connection broken, removing emitter: " + e.getMessage());
            // Don't try to send error - connection is broken
        } catch (Exception e) {
            System.err.println("Error sending progress: " + e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {}
        }
    }
    
    private void sendCompletion(SseEmitter emitter, PlaylistManager.ImportResult result) {
        try {
            Map<String, Object> completion = new java.util.HashMap<>();
            completion.put("type", "completed");
            completion.put("playlistId", result.playlistId);
            completion.put("totalTracks", result.totalTracks);
            completion.put("successfulTracks", result.successfulTracks);
            completion.put("failedTracks", result.failedTracks);
            completion.put("message", String.format("Import completed: %d/%d tracks successful", 
                result.successfulTracks, result.totalTracks));
            
            emitter.send(SseEmitter.event().name("completed").data(completion));
            emitter.complete();
        } catch (Exception e) {
            System.err.println("Error sending completion: " + e.getMessage());
        }
    }
    
    private void sendError(SseEmitter emitter, String errorMessage) {
        try {
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("type", "error");
            error.put("message", errorMessage);
            
            emitter.send(SseEmitter.event().name("error").data(error));
            emitter.complete();
        } catch (Exception e) {
            System.err.println("Error sending error: " + e.getMessage());
        }
    }
}
