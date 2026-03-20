package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import lexicon.object.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String COOKIE_NAME = "remember-me";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days

    @Autowired
    private PlayerManagerService playerManagerService;

    @Autowired
    private RememberMeManagerService rememberMeManagerService;

    /**
     * Login endpoint - creates a session
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> payload, HttpServletRequest request, HttpServletResponse response) {
        String username = (String) payload.get("username");
        String password = (String) payload.get("password");
        boolean rememberMe = Boolean.TRUE.equals(payload.get("rememberMe"));

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
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("playerId", player.getId());
            resp.put("id", player.getId());
            resp.put("username", player.getUsername());
            resp.put("displayName", player.getDisplayName() != null ? 
                player.getDisplayName() : player.getUsername());
            resp.put("email", player.getEmail() != null ? player.getEmail() : "");
            resp.put("level", player.getLevel());

            // If "remember me" was checked, issue a persistent cookie
            if (rememberMe) {
                String rawToken = rememberMeManagerService.createToken(player.getId());
                addRememberMeCookie(response, rawToken);
            }

            return ResponseEntity.ok(resp);

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
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpSession session = request.getSession(false);
            Integer userId = null;

            if (session != null) {
                userId = (Integer) session.getAttribute("userId");
            }

            // No valid session — try remember-me cookie
            if (userId == null) {
                String rawToken = getCookieValue(request, COOKIE_NAME);
                RememberMeManagerService.RememberMeResult result = 
                    rememberMeManagerService.validateAndRotate(rawToken);
                if (result != null) {
                    userId = result.userId();
                    addRememberMeCookie(response, result.newRawToken());
                    // Re-create the session from the remember-me token
                    session = request.getSession(true);
                    Player remembered = playerManagerService.getPlayerById(userId);
                    if (remembered != null) {
                        session.setAttribute("userId", remembered.getId());
                        session.setAttribute("username", remembered.getUsername());
                        session.setMaxInactiveInterval(30 * 24 * 60 * 60);
                    } else {
                        // User was deleted — clear the token
                        rememberMeManagerService.clearTokens(userId);
                        clearRememberMeCookie(response);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                    }
                } else {
                    clearRememberMeCookie(response);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                }
            }

            // Get full player details
            Player player = playerManagerService.getPlayerById(userId);
            if (player == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", player.getId());
            userInfo.put("username", player.getUsername());
            userInfo.put("displayName", player.getDisplayName() != null ? 
                player.getDisplayName() : player.getUsername());
            userInfo.put("email", player.getEmail() != null ? player.getEmail() : "");
            userInfo.put("level", player.getLevel());

            return ResponseEntity.ok(userInfo);

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
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Integer userId = (Integer) session.getAttribute("userId");
                if (userId != null) {
                    rememberMeManagerService.clearTokens(userId);
                }
                session.invalidate();
            }
            clearRememberMeCookie(response);
            return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Logout failed: " + e.getMessage());
        }
    }

    private void addRememberMeCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }

    private void clearRememberMeCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
