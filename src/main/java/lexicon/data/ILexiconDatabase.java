package lexicon.data;

import lexicon.object.Player;
import java.util.Collection;

/**
 * Interface for Lexicon database operations
 * Player management only - media files moved to IMediaDatabase
 */
public interface ILexiconDatabase {
    
    // Player/User management
    int getNextPlayerId();
    void addPlayer(Player player);
    Player getPlayer(int playerId);
    Player getPlayerByUsername(String username);
    Player getPlayerByEmail(String email);
    Collection<Player> getAllPlayers();
    void updatePlayer(Player player);
    void deletePlayer(int playerId);
    void updatePlayerLastLogin(int playerId, java.time.LocalDateTime lastLoginDate);
    /** Replaces only the stored password (used to upgrade a legacy plaintext value to a BCrypt hash). */
    void updatePlayerPassword(int playerId, String hashedPassword);
    
    // Additional utility methods
    boolean playerExists(String username);
    boolean emailExists(String email);
}
