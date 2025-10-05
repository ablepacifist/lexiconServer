package lexicon.object;

import java.time.LocalDateTime;

public class MediaFile {
    private int id;
    private String filename;
    private String originalFilename;
    private String contentType;
    private long fileSize;
    private String filePath;
    private int uploadedBy; // User ID
    private LocalDateTime uploadDate;
    private String title;
    private String description;
    private boolean isPublic;
    private String fileHash; // SHA-256 hash for deduplication
    
    public MediaFile() {}
    
    public MediaFile(int id, String filename, String originalFilename, String contentType, 
                    long fileSize, String filePath, int uploadedBy, String title, String description, boolean isPublic) {
        this.id = id;
        this.filename = filename;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.filePath = filePath;
        this.uploadedBy = uploadedBy;
        this.uploadDate = LocalDateTime.now();
        this.title = title;
        this.description = description;
        this.isPublic = isPublic;
    }
    
    // Constructor with file hash
    public MediaFile(int id, String filename, String originalFilename, String contentType, 
                    long fileSize, String filePath, int uploadedBy, String title, String description, boolean isPublic, String fileHash) {
        this(id, filename, originalFilename, contentType, fileSize, filePath, uploadedBy, title, description, isPublic);
        this.fileHash = fileHash;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public int getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(int uploadedBy) { this.uploadedBy = uploadedBy; }
    
    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    @Override
    public String toString() {
        return "MediaFile{id=" + id + ", title='" + title + "', filename='" + filename + "', uploadedBy=" + uploadedBy + ", fileHash='" + fileHash + "'}";
    }
}