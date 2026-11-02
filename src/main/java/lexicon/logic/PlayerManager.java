package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.object.Player;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Implementation of PlayerManagerService
 * Handles player/user management and authentication for the media sharing platform
 */
@Service
public class PlayerManager implements PlayerManagerService {
    
    private final ILexiconDatabase database;
    private final BCryptPasswordEncoder passwordEncoder;
    
    public PlayerManager(ILexiconDatabase database) {
        this.database = database;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    @Override
    public Player registerPlayer(String username, String password, String email, String displayName) {
        // Validate inputs
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        
        // Check if username already exists
        if (database.playerExists(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        // Check if email already exists
        if (database.emailExists(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Hash the password
        String hashedPassword = passwordEncoder.encode(password);
        
        // Create new player
        int playerId = database.getNextPlayerId();
        String finalDisplayName = (displayName == null || displayName.trim().isEmpty()) 
                                  ? username : displayName;
        
        Player newPlayer = new Player(
            playerId,
            username,
            hashedPassword,
            1, // Starting level
            email,
            finalDisplayName,
            LocalDateTime.now(),
            null // Last login will be set on first login
        );
        
        database.addPlayer(newPlayer);
        return newPlayer;
    }
    
    @Override
    public Player authenticatePlayer(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        
        Player player = database.getPlayerByUsername(username);
        if (player == null) {
            return null;
        }
        
        // Verify password. BCrypt is the norm; a few old rows still hold plain text. Those are
        // verified once (constant time) and immediately replaced by a BCrypt hash, so nobody is
        // locked out and nobody has to reset anything.
        String storedPassword = player.getPassword();
        if (storedPassword == null) {
            return null;
        }
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(password, storedPassword) ? recordLogin(player) : null;
        }

        boolean legacyMatch = MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
        if (!legacyMatch) {
            return null;
        }

        String upgraded = passwordEncoder.encode(password);
        database.updatePlayerPassword(player.getId(), upgraded);
        System.out.println("Upgraded stored password to a BCrypt hash for player id " + player.getId());
        return recordLogin(new Player(player.getId(), player.getUsername(), upgraded, player.getLevel(),
                player.getEmail(), player.getDisplayName(), player.getRegistrationDate(), player.getLastLoginDate()));
    }

    /** Stamps the sign-in time. Best effort: failing to record it must never stop someone logging in. */
    private Player recordLogin(Player player) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        try {
            database.updatePlayerLastLogin(player.getId(), now);
        } catch (Exception e) {
            System.err.println("Could not record last login for player id " + player.getId() + ": " + e.getMessage());
        }
        return new Player(player.getId(), player.getUsername(), player.getPassword(), player.getLevel(),
                player.getEmail(), player.getDisplayName(), player.getRegistrationDate(), now);
    }
    
    private static boolean isBcryptHash(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }

    @Override
    public Player getPlayerById(int playerId) {
        return database.getPlayer(playerId);
    }
    
    @Override
    public Player getPlayerByUsername(String username) {
        if (username == null) {
            return null;
        }
        return database.getPlayerByUsername(username);
    }
    
    @Override
    public Collection<Player> getAllPlayers() {
        return database.getAllPlayers();
    }
    
    @Override
    public boolean updatePlayer(Player player) {
        if (player == null) {
            return false;
        }
        
        Player existing = database.getPlayer(player.getId());
        if (existing == null) {
            return false;
        }
        
        database.updatePlayer(player);
        return true;
    }
    
    @Override
    public boolean deletePlayer(int playerId) {
        Player player = database.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        
        database.deletePlayer(playerId);
        return true;
    }
    
    @Override
    public boolean isUsernameAvailable(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return !database.playerExists(username);
    }
    
    @Override
    public boolean isEmailAvailable(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return !database.emailExists(email);
    }
}
