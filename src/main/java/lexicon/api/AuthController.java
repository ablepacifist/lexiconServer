package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.object.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller for Lexicon
 * Handles login, registration, and session management
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private PlayerManagerService playerManagerService;

    /**
     * Login endpoint - creates a session
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Username and password required");
        }

        try {
            // Authenticate user
            Player player = playerManagerService.authenticatePlayer(username, password);
            
            if (player == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password");
            }

            // Create session
            HttpSession session = request.getSession(true);
            session.setAttribute("userId", player.getId());
            session.setAttribute("username", player.getUsername());
            session.setMaxInactiveInterval(30 * 24 * 60 * 60); // 30 days

            // Return user info
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("playerId", player.getId());
            response.put("id", player.getId());
            response.put("username", player.getUsername());
            response.put("displayName", player.getDisplayName() != null ? 
                player.getDisplayName() : player.getUsername());
            response.put("email", player.getEmail() != null ? player.getEmail() : "");
            response.put("level", player.getLevel());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Login error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Login failed: " + e.getMessage());
        }
    }

    /**
     * Register new user
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String confirmPassword = payload.get("confirmPassword");
        String email = payload.get("email");
        String displayName = payload.get("displayName");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                .body("Username and password are required");
        }

        // Validate password confirmation if provided
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                .body("Passwords do not match");
        }

        // Use username as email if not provided (for backwards compatibility)
        if (email == null || email.trim().isEmpty()) {
            email = username + "@lexicon.local";
        }

        try {
            Player newPlayer = playerManagerService.registerPlayer(
                username, password, email, displayName
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("playerId", newPlayer.getId());
            response.put("username", newPlayer.getUsername());
            response.put("message", "Registration successful");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Registration failed: " + e.getMessage());
        }
    }

    /**
     * Get current logged-in user from session
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Integer userId = (Integer) session.getAttribute("userId");
            String username = (String) session.getAttribute("username");

            if (userId == null || username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get full player details
            Player player = playerManagerService.getPlayerById(userId);
            if (player == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", player.getId());
            response.put("username", player.getUsername());
            response.put("displayName", player.getDisplayName() != null ? 
                player.getDisplayName() : player.getUsername());
            response.put("email", player.getEmail() != null ? player.getEmail() : "");
            response.put("level", player.getLevel());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error in getCurrentUser: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error retrieving current user: " + e.getMessage());
        }
    }

    /**
     * Logout - invalidate session
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Logout failed: " + e.getMessage());
        }
    }
}
