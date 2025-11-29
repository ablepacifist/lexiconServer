package lexicon.data;

import lexicon.object.Playlist;
import lexicon.object.PlaylistItem;

import java.util.List;

/**
 * Interface for playlist database operations
 */
public interface IPlaylistDatabase {
    
    /**
     * Create a new playlist
     */
    int createPlaylist(Playlist playlist);
    
    /**
     * Get a playlist by ID (without items)
     */
    Playlist getPlaylistById(int playlistId);
    
    /**
     * Get a playlist by ID with all items populated
     */
    Playlist getPlaylistWithItems(int playlistId);
    
    /**
     * Get all playlists created by a specific user
     */
    List<Playlist> getPlaylistsByUser(int userId);
    
    /**
     * Get all public playlists
     */
    List<Playlist> getPublicPlaylists();
    
    /**
     * Update playlist metadata (name, description, isPublic)
     */
    boolean updatePlaylist(Playlist playlist);
    
    /**
     * Delete a playlist and all its items
     */
    boolean deletePlaylist(int playlistId);
    
    /**
     * Add a media file to a playlist
     */
    boolean addItemToPlaylist(int playlistId, int mediaFileId, int position);
    
    /**
     * Remove a media file from a playlist
     */
    boolean removeItemFromPlaylist(int playlistId, int mediaFileId);
    
    /**
     * Get all items in a playlist (ordered by position)
     */
    List<PlaylistItem> getPlaylistItems(int playlistId);
    
    /**
     * Update the position of items in a playlist
     */
    boolean updateItemPosition(int playlistId, int mediaFileId, int newPosition);
    
    /**
     * Reorder all items in a playlist
     */
    boolean reorderPlaylist(int playlistId, List<Integer> mediaFileIds);
}
