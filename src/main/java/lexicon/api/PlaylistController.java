package lexicon.api;

import lexicon.logic.PlaylistManager;
import lexicon.object.Playlist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
@CrossOrigin(origins = "*")
public class PlaylistController {
    
    @Autowired
    private PlaylistManager playlistManager;
    
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
            @RequestParam("userId") Integer userId) {
        
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User ID required");
        }
        
        try {
            boolean success = playlistManager.deletePlaylist(id, userId);
            
            if (success) {
                return ResponseEntity.ok("Playlist deleted");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting playlist");
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
}
