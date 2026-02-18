package lexicon.object;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.HashSet;

/**
 * Represents a chunked upload session for large files
 */
public class ChunkedUpload {
    private String uploadId;
    private String originalFilename;
    private String contentType;
    private long totalSize;
    private int totalChunks;
    private int chunkSize;
    private int uploadedBy;
    private LocalDateTime startTime;
    private LocalDateTime lastActivity;
    private ChunkedUploadStatus status;
    private Set<Integer> uploadedChunks;
    private String title;
    private String description;
    private boolean isPublic;
    private String mediaType;
    private String checksum; // For file integrity verification
    
    public ChunkedUpload() {
        this.uploadedChunks = new HashSet<>();
        this.startTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.status = ChunkedUploadStatus.IN_PROGRESS;
    }
    
    public ChunkedUpload(String uploadId, String originalFilename, String contentType, 
                        long totalSize, int totalChunks, int chunkSize, int uploadedBy,
                        String title, String description, boolean isPublic, String mediaType) {
        this();
        this.uploadId = uploadId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.uploadedBy = uploadedBy;
        this.title = title;
        this.description = description;
        this.isPublic = isPublic;
        this.mediaType = mediaType;
    }
    
    public boolean isComplete() {
        return uploadedChunks.size() == totalChunks;
    }
    
    public double getProgress() {
        if (totalChunks == 0) return 0.0;
        return ((double) uploadedChunks.size() / totalChunks) * 100.0;
    }
    
    public void addUploadedChunk(int chunkNumber) {
        uploadedChunks.add(chunkNumber);
        lastActivity = LocalDateTime.now();
    }
    
    public Set<Integer> getMissingChunks() {
        Set<Integer> missing = new HashSet<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!uploadedChunks.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }
    
    // Getters and Setters
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    
    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    
    public int getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(int uploadedBy) { this.uploadedBy = uploadedBy; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }
    
    public ChunkedUploadStatus getStatus() { return status; }
    public void setStatus(ChunkedUploadStatus status) { this.status = status; }
    
    public Set<Integer> getUploadedChunks() { return uploadedChunks; }
    public void setUploadedChunks(Set<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}