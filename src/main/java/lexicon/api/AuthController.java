package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.object.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication operations
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // Allow all origins (matches Alchemy controllers)
public class AuthController {
    
    @Autowired
    private PlayerManagerService playerManagerService;
    
    @PostMapping("/register")
    public ResponseEntity<?> registerPlayer(@RequestBody RegisterRequest request) {
        try {
            // Validate input
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
            }
            if (request.getPassword() == null || request.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
            }
            if (request.getEmail() == null || !request.getEmail().contains("@")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid email is required"));
            }
            
            Player player = playerManagerService.registerPlayer(
                request.getUsername().trim(),
                request.getPassword(),
                request.getEmail().trim(),
                request.getDisplayName() != null ? request.getDisplayName().trim() : null
            );
            
            // Return player without password
            PlayerResponse response = new PlayerResponse(player);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Registration failed"));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> loginPlayer(@RequestBody LoginRequest request) {
        try {
            Player player = playerManagerService.authenticatePlayer(request.getUsername(), request.getPassword());
            if (player != null) {
                PlayerResponse response = new PlayerResponse(player);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid username or password"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Login failed"));
        }
    }
    
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestParam int userId) {
        try {
            Player player = playerManagerService.getPlayerById(userId);
            if (player != null) {
                PlayerResponse response = new PlayerResponse(player);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Validation failed"));
        }
    }
    
    // Request/Response classes
    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
        private String displayName;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
    
    public static class LoginRequest {
        private String username;
        private String password;
        
        // Getters and setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class PlayerResponse {
        private int id;
        private String username;
        private String email;
        private String displayName;
        private int level;
        
        public PlayerResponse(Player player) {
            this.id = player.getId();
            this.username = player.getUsername();
            this.email = player.getEmail();
            this.displayName = player.getDisplayName();
            this.level = player.getLevel();
        }
        
        // Getters
        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getDisplayName() { return displayName; }
        public int getLevel() { return level; }
    }
}
