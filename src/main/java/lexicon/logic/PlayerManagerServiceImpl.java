package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.object.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Implementation of PlayerManagerService for unified player/user management
 */
@Service
public class PlayerManagerServiceImpl implements PlayerManagerService {
    
    @Autowired
    private ILexiconDatabase database;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public Player registerPlayer(String username, String password, String email, String displayName) {
        // Check if username or email already exists
        if (!isUsernameAvailable(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (!isEmailAvailable(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Create new player
        int newId = database.getNextPlayerId();
        String hashedPassword = passwordEncoder.encode(password);
        
        Player player = new Player(
            newId, 
            username, 
            hashedPassword, 
            1, // default level
            email,
            displayName != null ? displayName : username,
            LocalDateTime.now(),
            null // lastLoginDate will be set on first login
        );
        
        database.addPlayer(player);
        return player;
    }
    
    @Override
    public Player authenticatePlayer(String username, String password) {
        Player player = database.getPlayerByUsername(username);
        if (player != null && passwordEncoder.matches(password, player.getPassword())) {
            // Update last login date
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
        return database.getPlayerByUsername(username);
    }
    
    @Override
    public Collection<Player> getAllPlayers() {
        return database.getAllPlayers();
    }
    
    @Override
    public boolean updatePlayer(Player player) {
        try {
            database.updatePlayer(player);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean deletePlayer(int playerId) {
        try {
            database.deletePlayer(playerId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean isUsernameAvailable(String username) {
        return !database.playerExists(username);
    }
    
    @Override
    public boolean isEmailAvailable(String email) {
        return !database.emailExists(email);
    }
}
