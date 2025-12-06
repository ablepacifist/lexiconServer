package lexicon.object;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a playlist of media files (music, videos, audiobooks)
 */
public class Playlist {
    private int id;
    private String name;
    private String description;
    private boolean isPublic;
    private int createdBy; // User ID
    private LocalDateTime createdDate;
    private MediaType mediaType; // MUSIC, VIDEO, AUDIOBOOK
    private List<PlaylistItem> items;
    private List<Integer> mediaFileIds; // Transient field for bulk creation
    
    public Playlist() {
        this.items = new ArrayList<>();
        this.createdDate = LocalDateTime.now();
    }
    
    public Playlist(int id, String name, String description, boolean isPublic, int createdBy, MediaType mediaType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isPublic = isPublic;
        this.createdBy = createdBy;
        this.createdDate = LocalDateTime.now();
        this.mediaType = mediaType;
        this.items = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    @JsonProperty("isPublic")
    public boolean isPublic() { return isPublic; }
    @JsonProperty("isPublic")
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    
    public MediaType getMediaType() { return mediaType; }
    public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }
    
    public List<PlaylistItem> getItems() { return items; }
    public void setItems(List<PlaylistItem> items) { this.items = items; }
    
    public List<Integer> getMediaFileIds() { return mediaFileIds; }
    public void setMediaFileIds(List<Integer> mediaFileIds) { this.mediaFileIds = mediaFileIds; }
    
    public void addItem(PlaylistItem item) {
        this.items.add(item);
    }
    
    @Override
    public String toString() {
        return "Playlist{id=" + id + ", name='" + name + "', mediaType=" + mediaType + ", items=" + items.size() + "}";
    }
}
