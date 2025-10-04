package lexicon.data;

import lexicon.object.User;
import lexicon.object.MediaFile;
import java.util.Collection;
import java.util.List;

/**
 * Interface for Lexicon database operations
 * Following the same pattern as Alchemy's IStubDatabase
 */
public interface ILexiconDatabase {
    
    // User management
    int getNextUserId();
    void addUser(User user);
    User getUser(int userId);
    User getUserByUsername(String username);
    User getUserByEmail(String email);
    Collection<User> getAllUsers();
    void updateUser(User user);
    void deleteUser(int userId);
    
    // Media file management
    int getNextMediaFileId();
    void addMediaFile(MediaFile mediaFile);
    MediaFile getMediaFile(int mediaFileId);
    List<MediaFile> getMediaFilesByUser(int userId);
    List<MediaFile> getAllPublicMediaFiles();
    List<MediaFile> searchMediaFiles(String searchTerm);
    void updateMediaFile(MediaFile mediaFile);
    void deleteMediaFile(int mediaFileId);
    
    // Additional utility methods
    boolean userExists(String username);
    boolean emailExists(String email);
    List<MediaFile> getRecentMediaFiles(int limit);
}