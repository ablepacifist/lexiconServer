package lexicon.object;

/**
 * Represents an item in a playlist
 * Links a media file to a playlist with ordering
 */
public class PlaylistItem {
    private int playlistId;
    private int mediaFileId;
    private int position; // Order in the playlist
    private MediaFile mediaFile; // Optional: populated when fetching full playlist
    
    public PlaylistItem() {}
    
    public PlaylistItem(int playlistId, int mediaFileId, int position) {
        this.playlistId = playlistId;
        this.mediaFileId = mediaFileId;
        this.position = position;
    }
    
    // Getters and Setters
    public int getPlaylistId() { return playlistId; }
    public void setPlaylistId(int playlistId) { this.playlistId = playlistId; }
    
    public int getMediaFileId() { return mediaFileId; }
    public void setMediaFileId(int mediaFileId) { this.mediaFileId = mediaFileId; }
    
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    
    public MediaFile getMediaFile() { return mediaFile; }
    public void setMediaFile(MediaFile mediaFile) { this.mediaFile = mediaFile; }
    
    @Override
    public String toString() {
        return "PlaylistItem{playlistId=" + playlistId + ", mediaFileId=" + mediaFileId + ", position=" + position + "}";
    }
}
