package lexicon.logic;

import lexicon.data.IPlaylistDatabase;
import lexicon.object.Playlist;
import lexicon.object.PlaylistItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaylistManager {
    
    @Autowired
    private IPlaylistDatabase playlistDatabase;
    
    public int createPlaylist(Playlist playlist) {
        if (playlist.getName() == null || playlist.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Playlist name cannot be empty");
        }
        return playlistDatabase.createPlaylist(playlist);
    }
    
    public Playlist getPlaylistById(int playlistId) {
        return playlistDatabase.getPlaylistById(playlistId);
    }
    
    public Playlist getPlaylistWithItems(int playlistId) {
        return playlistDatabase.getPlaylistWithItems(playlistId);
    }
    
    public List<Playlist> getPlaylistsByUser(int userId) {
        return playlistDatabase.getPlaylistsByUser(userId);
    }
    
    public List<Playlist> getPublicPlaylists() {
        return playlistDatabase.getPublicPlaylists();
    }
    
    public boolean updatePlaylist(Playlist playlist, int userId) {
        Playlist existing = playlistDatabase.getPlaylistById(playlist.getId());
        if (existing == null) {
            return false;
        }
        
        // Only allow owner to update
        if (existing.getCreatedBy() != userId) {
            throw new SecurityException("Not authorized to update this playlist");
        }
        
        return playlistDatabase.updatePlaylist(playlist);
    }
    
    public boolean deletePlaylist(int playlistId, int userId) {
        Playlist existing = playlistDatabase.getPlaylistById(playlistId);
        if (existing == null) {
            return false;
        }
        
        // Only allow owner to delete
        if (existing.getCreatedBy() != userId) {
            throw new SecurityException("Not authorized to delete this playlist");
        }
        
        return playlistDatabase.deletePlaylist(playlistId);
    }
    
    public boolean addItemToPlaylist(int playlistId, int mediaFileId, int userId) {
        Playlist playlist = playlistDatabase.getPlaylistById(playlistId);
        if (playlist == null) {
            return false;
        }
        
        // Only allow owner to modify
        if (playlist.getCreatedBy() != userId) {
            throw new SecurityException("Not authorized to modify this playlist");
        }
        
        // Get current item count to determine position
        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(playlistId);
        int position = items.size();
        
        return playlistDatabase.addItemToPlaylist(playlistId, mediaFileId, position);
    }
    
    public boolean removeItemFromPlaylist(int playlistId, int mediaFileId, int userId) {
        Playlist playlist = playlistDatabase.getPlaylistById(playlistId);
        if (playlist == null) {
            return false;
        }
        
        // Only allow owner to modify
        if (playlist.getCreatedBy() != userId) {
            throw new SecurityException("Not authorized to modify this playlist");
        }
        
        return playlistDatabase.removeItemFromPlaylist(playlistId, mediaFileId);
    }
    
    public boolean reorderPlaylist(int playlistId, List<Integer> mediaFileIds, int userId) {
        Playlist playlist = playlistDatabase.getPlaylistById(playlistId);
        if (playlist == null) {
            return false;
        }
        
        // Only allow owner to modify
        if (playlist.getCreatedBy() != userId) {
            throw new SecurityException("Not authorized to modify this playlist");
        }
        
        return playlistDatabase.reorderPlaylist(playlistId, mediaFileIds);
    }
}
