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
    
    // Additional utility methods
    boolean playerExists(String username);
    boolean emailExists(String email);
}
