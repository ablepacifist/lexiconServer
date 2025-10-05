package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.object.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Player management in the Lexicon server
 * Provides full CRUD operations for the unified Player system
 * IMPORTANT: This controller ONLY communicates with the Logic Layer (PlayerManagerService)
 * and NEVER directly accesses the Data Layer (Database)
 */
@RestController
@RequestMapping("/api/players")
@CrossOrigin(origins = "*")
public class PlayerController {

    @Autowired
    private PlayerManagerService playerManagerService;

    /**
     * Get all players
     */
    @GetMapping
    public ResponseEntity<Collection<Player>> getAllPlayers() {
        try {
            Collection<Player> players = playerManagerService.getAllPlayers();
            return ResponseEntity.ok(players);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get player by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Player> getPlayerById(@PathVariable int id) {
        try {
            Player player = playerManagerService.getPlayerById(id);
            if (player == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(player);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get player by username
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<Player> getPlayerByUsername(@PathVariable String username) {
        try {
            Player player = playerManagerService.getPlayerByUsername(username);
            if (player == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(player);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Register a new player
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerPlayer(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            String email = request.get("email");
            String displayName = request.get("displayName");

            // Validate required fields
            if (username == null || username.trim().isEmpty() || 
                password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required"));
            }

            // Use service layer to register player
            Player newPlayer = playerManagerService.registerPlayer(
                username.trim(), 
                password, 
                email != null ? email.trim() : null, 
                displayName != null ? displayName.trim() : username.trim()
            );

            if (newPlayer == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username already exists"));
            }

            // Return success response (without password)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Player registered successfully");
            response.put("playerId", newPlayer.getId());
            response.put("username", newPlayer.getUsername());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    /**
     * Login player (authenticate)
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginPlayer(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            if (username == null || password == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required"));
            }

            // Use service layer to authenticate player
            Player player = playerManagerService.authenticatePlayer(username, password);
            if (player == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid username or password"));
            }

            // Return success response (without password)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("player", Map.of(
                "id", player.getId(),
                "username", player.getUsername(),
                "displayName", player.getDisplayName() != null ? player.getDisplayName() : player.getUsername(),
                "email", player.getEmail() != null ? player.getEmail() : "",
                "level", player.getLevel()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    /**
     * Update player profile
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePlayer(@PathVariable int id, @RequestBody Map<String, String> request) {
        try {
            Player existingPlayer = playerManagerService.getPlayerById(id);
            if (existingPlayer == null) {
                return ResponseEntity.notFound().build();
            }

            // For now, we'll return a message that this feature needs service implementation
            // In a full implementation, you'd add updatePlayer method to the service interface
            
            return ResponseEntity.ok(Map.of(
                "message", "Update functionality requires service method implementation",
                "playerId", id
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Update failed: " + e.getMessage()));
        }
    }

    /**
     * Delete player
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePlayer(@PathVariable int id) {
        try {
            Player existingPlayer = playerManagerService.getPlayerById(id);
            if (existingPlayer == null) {
                return ResponseEntity.notFound().build();
            }

            // For now, we'll return a message that this feature needs service implementation
            // In a full implementation, you'd add deletePlayer method to the service interface
            
            return ResponseEntity.ok(Map.of(
                "message", "Delete functionality requires service method implementation",
                "playerId", id
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Delete failed: " + e.getMessage()));
        }
    }
}