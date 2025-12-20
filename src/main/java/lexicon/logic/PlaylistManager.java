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
    private MediaManagerService mediaManager;
    
    @Autowired
    private YoutubeImportService youtubeImportService;
    
    /**
     * Callback interface for playlist import progress
     */
    @FunctionalInterface
    public interface ImportProgressCallback {
        void onProgress(String message, int total, int successful, int failed);
    }
    
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
        return deletePlaylist(playlistId, userId, false);
    }
    
    public boolean deletePlaylist(int playlistId, int userId, boolean deleteMediaFiles) {
        Playlist existing = playlistDatabase.getPlaylistById(playlistId);
        if (existing == null) {
            return false;
        }
        
        // Only allow owner to delete
        if (existing.getCreatedBy() != userId) {
            throw new SecurityException("Not authorized to delete this playlist");
        }
        
        // If user wants to delete media files, get the playlist items first
        if (deleteMediaFiles) {
            List<PlaylistItem> items = playlistDatabase.getPlaylistItems(playlistId);
            for (PlaylistItem item : items) {
                try {
                    // Delete each media file (this includes security checks)
                    mediaManager.deleteMediaFile(item.getMediaFileId(), userId);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete media file " + item.getMediaFileId() + ": " + e.getMessage());
                    // Continue with other files even if one fails
                }
            }
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
     * Import a YouTube playlist (without progress callback)
     */
    public ImportResult importYoutubePlaylist(String playlistUrl, int userId, String customPlaylistName,
                                               boolean isPublic, boolean mediaIsPublic) {
        return importYoutubePlaylist(playlistUrl, userId, customPlaylistName, isPublic, mediaIsPublic, null);
    }
    
    /**
     * Import a YouTube playlist with progress tracking
     * This is a long-running operation that should be called in a background thread
     */
    public ImportResult importYoutubePlaylist(String playlistUrl, int userId, String customPlaylistName,
                                               boolean isPublic, boolean mediaIsPublic, ImportProgressCallback callback) {
        ImportResult result = new ImportResult();
        
        try {
            System.out.println("Starting YouTube playlist import for URL: " + playlistUrl);
            
            // Step 1: Fetch playlist metadata and count total entries
            YoutubeImportService.PlaylistMetadata metadata = youtubeImportService.fetchPlaylistMetadata(playlistUrl);
            
            String playlistName = (customPlaylistName != null && !customPlaylistName.trim().isEmpty()) 
                ? customPlaylistName : metadata.name;
            
            if (callback != null) {
                callback.onProgress("Counting playlist entries...", 0, 0, 0);
            }
            
            // Count total entries first
            java.util.List<JsonNode> allEntries = new java.util.ArrayList<>();
            allEntries.add(metadata.firstEntry); // Add the first entry
            
            String line;
            while ((line = metadata.reader.readLine()) != null) {
                try {
                    JsonNode entry = metadata.mapper.readTree(line);
                    allEntries.add(entry);
                } catch (Exception e) {
                    System.err.println("Error parsing playlist entry: " + e.getMessage());
                }
            }
            
            metadata.process.waitFor();
            metadata.reader.close();
            
            result.totalTracks = allEntries.size();
            System.out.println("Found " + result.totalTracks + " tracks in playlist");
            
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
            
            if (callback != null) {
                callback.onProgress("Created playlist: " + playlistName + ". Processing " + result.totalTracks + " tracks...", 
                                  result.totalTracks, 0, 0);
            }
            
            // Step 3: Process all entries with accurate progress
            int processed = 0;
            for (JsonNode entry : allEntries) {
                processed++;
                try {
                    if (processPlaylistEntry(entry, playlistId, userId, playlistName, mediaIsPublic)) {
                        result.successfulTracks++;
                    } else {
                        result.failedTracks++;
                    }
                    
                    // Progress callback every track
                    if (callback != null) {
                        callback.onProgress(
                            String.format("Processing track %d/%d...", processed, result.totalTracks), 
                            result.totalTracks, result.successfulTracks, result.failedTracks);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing playlist entry: " + e.getMessage());
                    result.failedTracks++;
                    
                    if (callback != null) {
                        callback.onProgress(
                            String.format("Error on track %d/%d: %s", processed, result.totalTracks, e.getMessage()), 
                            result.totalTracks, result.successfulTracks, result.failedTracks);
                    }
                }
                
                // Small delay to avoid overwhelming the server
                Thread.sleep(2000);
            }
            
            result.success = true;
            System.out.println("Playlist import complete: " + result.successfulTracks + "/" + result.totalTracks + " tracks successfully added");
            
            // Final progress update
            if (callback != null) {
                callback.onProgress(
                    String.format("Import completed: %d successful, %d failed", result.successfulTracks, result.failedTracks),
                    result.totalTracks, result.successfulTracks, result.failedTracks);
            }
            
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            System.err.println("Error importing playlist: " + e.getMessage());
            e.printStackTrace();
            
            if (callback != null) {
                callback.onProgress("Import failed: " + e.getMessage(), result.totalTracks, result.successfulTracks, result.failedTracks);
            }
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
