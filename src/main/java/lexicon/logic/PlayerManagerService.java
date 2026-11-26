package lexicon.logic;

import lexicon.object.Player;
import java.util.Collection;

/**
 * Service interface for player/user management
 * Now using unified Player class for authentication across both servers
 */
public interface PlayerManagerService {
    
    /**
     * Register a new player
     */
    Player registerPlayer(String username, String password, String email, String displayName);
    
    /**
     * Authenticate a player with username and password
     */
    Player authenticatePlayer(String username, String password);
    
    /**
     * Get player by ID
     */
    Player getPlayerById(int playerId);
    
    /**
     * Get player by username
     */
    Player getPlayerByUsername(String username);
    
    /**
     * Get all players
     */
    Collection<Player> getAllPlayers();
    
    /**
     * Update player information
     */
    boolean updatePlayer(Player player);

    /** Shortest password an admin may set for someone. */
    int MIN_ADMIN_PASSWORD_LENGTH = 8;

    /**
     * Replace a player's password (hashed here). Throws IllegalArgumentException when the player
     * does not exist or the password is shorter than {@link #MIN_ADMIN_PASSWORD_LENGTH}.
     */
    void setPassword(int playerId, String newPassword);
    
    /**
     * Delete a player
     */
    boolean deletePlayer(int playerId);
    
    /**
     * Check if username is available
     */
    boolean isUsernameAvailable(String username);
    
    /**
     * Check if email is available
     */
    boolean isEmailAvailable(String email);
}