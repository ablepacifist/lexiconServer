package lexicon.logic;

import lexicon.data.IPlaylistDatabase;
import lexicon.object.Playlist;
import lexicon.object.PlaylistItem;
import lexicon.object.MediaType;
import lexicon.service.YoutubeImportService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaylistManager {
    
    @Autowired
    private IPlaylistDatabase playlistDatabase;
    
    @Autowired
    private YoutubeImportService youtubeImportService;
    
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
    
    /**
     * Import a YouTube playlist
     * This is a long-running operation that should be called in a background thread
     */
    public ImportResult importYoutubePlaylist(String playlistUrl, int userId, String customPlaylistName,
                                               boolean isPublic, boolean mediaIsPublic) {
        ImportResult result = new ImportResult();
        
        try {
            System.out.println("Starting YouTube playlist import for URL: " + playlistUrl);
            
            // Step 1: Fetch playlist metadata
            YoutubeImportService.PlaylistMetadata metadata = youtubeImportService.fetchPlaylistMetadata(playlistUrl);
            
            String playlistName = (customPlaylistName != null && !customPlaylistName.trim().isEmpty()) 
                ? customPlaylistName : metadata.name;
            
            // Step 2: Create the playlist
            Playlist playlist = new Playlist();
            playlist.setName(playlistName);
            playlist.setDescription("Imported from YouTube");
            playlist.setMediaType(MediaType.MUSIC);
            playlist.setCreatedBy(userId);
            playlist.setPublic(isPublic);
            
            int playlistId = createPlaylist(playlist);
            result.playlistId = playlistId;
            System.out.println("Created playlist ID: " + playlistId);
            
            // Step 3: Process first entry
            result.totalTracks++;
            if (processPlaylistEntry(metadata.firstEntry, playlistId, userId, playlistName, mediaIsPublic)) {
                result.successfulTracks++;
            } else {
                result.failedTracks++;
            }
            
            // Step 4: Process remaining entries
            String line;
            while ((line = metadata.reader.readLine()) != null) {
                result.totalTracks++;
                try {
                    JsonNode entry = metadata.mapper.readTree(line);
                    if (processPlaylistEntry(entry, playlistId, userId, playlistName, mediaIsPublic)) {
                        result.successfulTracks++;
                    } else {
                        result.failedTracks++;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing playlist entry: " + e.getMessage());
                    result.failedTracks++;
                }
                
                // Small delay to avoid overwhelming the server
                Thread.sleep(2000);
            }
            
            metadata.process.waitFor();
            metadata.reader.close();
            
            result.success = true;
            System.out.println("Playlist import complete: " + result.successfulTracks + "/" + result.totalTracks + " tracks successfully added");
            
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            System.err.println("Error importing playlist: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Process a single playlist entry
     */
    private boolean processPlaylistEntry(JsonNode entry, int playlistId, int userId, 
                                          String playlistName, boolean mediaIsPublic) {
        try {
            String videoId = entry.get("id").asText();
            String title = entry.get("title").asText();
            
            System.out.println("Processing: " + title);
            
            // Download and upload the media
            int mediaId = youtubeImportService.downloadAndUploadMedia(
                videoId, title, playlistName, userId, mediaIsPublic);
            
            if (mediaId > 0) {
                // Add to playlist - no authorization check needed since we're the creator
                List<PlaylistItem> items = playlistDatabase.getPlaylistItems(playlistId);
                int position = items.size();
                playlistDatabase.addItemToPlaylist(playlistId, mediaId, position);
                
                System.out.println("✓ Added: " + title);
                return true;
            } else {
                System.err.println("✗ Failed to upload: " + title);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error processing entry: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Result object for import operation
     */
    public static class ImportResult {
        public boolean success = false;
        public int playlistId = -1;
        public int totalTracks = 0;
        public int successfulTracks = 0;
        public int failedTracks = 0;
        public String errorMessage = null;
    }
}
