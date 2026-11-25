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
    /**
     * Delete an account for good, in one transaction: the Alchemy rows that reference it by
     * foreign key (inventory, knowledge book, potions; without this the player row cannot be
     * deleted at all once they have played), then the player. True when the player row went.
     */
    boolean deletePlayerAccount(int playerId);
    void updatePlayerLastLogin(int playerId, java.time.LocalDateTime lastLoginDate);
    /** Replaces only the stored password (used to upgrade a legacy plaintext value to a BCrypt hash). */
    void updatePlayerPassword(int playerId, String hashedPassword);
    
    // Roles (PLAYERS.ROLE). Only these touch the column; updatePlayer() deliberately leaves it alone.
    /** The stored role value, or {@code null} when the player does not exist. */
    String getPlayerRole(int playerId);
    void setPlayerRole(int playerId, String role);
    int countPlayersWithRole(String role);

    // Activity, for the admin user list
    /** Stamps the last time this player made a signed-in request. */
    void updatePlayerLastActive(int playerId, java.time.LocalDateTime lastActiveDate);
    /** Every account with its role and dates, ordered by id. No passwords. */
    java.util.List<lexicon.object.PlayerActivity> getPlayerActivity();

    // Additional utility methods
    boolean playerExists(String username);
    boolean emailExists(String email);
}
