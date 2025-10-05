package lexicon.data;

import lexicon.object.Player;
import lexicon.object.MediaFile;
import java.util.Collection;
import java.util.List;

/**
 * Interface for Lexicon database operations
 * Now using unified Player class instead of User
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
    
    // Media file management
    int getNextMediaFileId();
    void addMediaFile(MediaFile mediaFile);
    MediaFile getMediaFile(int mediaFileId);
    List<MediaFile> getMediaFilesByPlayer(int playerId);
    List<MediaFile> getAllPublicMediaFiles();
    List<MediaFile> searchMediaFiles(String searchTerm);
    void updateMediaFile(MediaFile mediaFile);
    void deleteMediaFile(int mediaFileId);
    
    // Additional utility methods
    boolean playerExists(String username);
    boolean emailExists(String email);
    List<MediaFile> getRecentMediaFiles(int limit);
}