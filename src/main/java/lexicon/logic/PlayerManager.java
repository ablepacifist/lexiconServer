package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.object.Player;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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
        
        // Verify password
        if (passwordEncoder.matches(password, player.getPassword())) {
            // Update last login
            database.updatePlayerLastLogin(player.getId(), LocalDateTime.now());
            return player;
        }
        
        return null;
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
