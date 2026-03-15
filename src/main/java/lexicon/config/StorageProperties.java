package lexicon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Storage configuration properties for file system storage
 */
@Component
@ConfigurationProperties(prefix = "lexicon.storage")
public class StorageProperties {
    
    private String basePath = "/media/alex/lexicon-hdd/lexicon-storage";
    private String tempPath;
    private String audiobooksPath;
    private String musicPath;
    private String videosPath;
    private String backupsPath;
    
    // File size thresholds
    private long smallFileThreshold = 10 * 1024 * 1024;  // 10MB
    private long largeFileThreshold = 100 * 1024 * 1024; // 100MB
    
    // Upload settings
    private int chunkSize = 10 * 1024 * 1024; // 10MB chunks
    private int streamingBufferSize = 1024 * 1024; // 1MB buffer
    private int transcodingJobs = 2; // Parallel transcoding jobs
    private long cacheSize = 512 * 1024 * 1024; // 512MB cache
    
    // Getters and setters
    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    
    public String getTempPath() { 
        return tempPath != null ? tempPath : basePath + "/temp"; 
    }
    public void setTempPath(String tempPath) { this.tempPath = tempPath; }
    
    public String getAudiobooksPath() { 
        return audiobooksPath != null ? audiobooksPath : basePath + "/audiobooks"; 
    }
    public void setAudiobooksPath(String audiobooksPath) { this.audiobooksPath = audiobooksPath; }
    
    public String getMusicPath() { 
        return musicPath != null ? musicPath : basePath + "/music"; 
    }
    public void setMusicPath(String musicPath) { this.musicPath = musicPath; }
    
    public String getVideosPath() { 
        return videosPath != null ? videosPath : basePath + "/videos"; 
    }
    public void setVideosPath(String videosPath) { this.videosPath = videosPath; }
    
    public String getBackupsPath() { 
        return backupsPath != null ? backupsPath : basePath + "/backups"; 
    }
    public void setBackupsPath(String backupsPath) { this.backupsPath = backupsPath; }
    
    public long getSmallFileThreshold() { return smallFileThreshold; }
    public void setSmallFileThreshold(long smallFileThreshold) { this.smallFileThreshold = smallFileThreshold; }
    
    public long getLargeFileThreshold() { return largeFileThreshold; }
    public void setLargeFileThreshold(long largeFileThreshold) { this.largeFileThreshold = largeFileThreshold; }
    
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    
    public int getStreamingBufferSize() { return streamingBufferSize; }
    public void setStreamingBufferSize(int streamingBufferSize) { this.streamingBufferSize = streamingBufferSize; }
    
    public int getTranscodingJobs() { return transcodingJobs; }
    public void setTranscodingJobs(int transcodingJobs) { this.transcodingJobs = transcodingJobs; }
    
    public long getCacheSize() { return cacheSize; }
    public void setCacheSize(long cacheSize) { this.cacheSize = cacheSize; }
    
    // Path getters for convenience
    public String getSmallFilesPath() { return basePath + "/small-files"; }
    public String getLargeFilesPath() { return basePath + "/large-files"; }  
    public String getMassiveFilesPath() { return basePath + "/massive-files"; }
    
    // Convenience method for size-only storage path determination
    public String getStoragePathForMedia(long fileSize) {
        if (fileSize < smallFileThreshold) {
            return getSmallFilesPath();
        } else if (fileSize < largeFileThreshold) {
            return getLargeFilesPath();
        } else {
            return getMassiveFilesPath();
        }
    }
    
    /**
     * Get storage path for a media type and file size
     */
    public String getStoragePathForMedia(String mediaType, long fileSize) {
        String basePath;
        String sizePath;
        
        switch (mediaType.toUpperCase()) {
            case "AUDIOBOOK":
                basePath = getAudiobooksPath();
                sizePath = (fileSize > largeFileThreshold) ? "/large" : "/standard";
                break;
            case "MUSIC":
                basePath = getMusicPath();
                sizePath = (fileSize > smallFileThreshold) ? "/lossless" : "/compressed";
                break;
            case "VIDEO":
                basePath = getVideosPath();
                sizePath = "/original";
                break;
            default:
                basePath = this.basePath + "/other";
                sizePath = "";
        }
        
        return basePath + sizePath;
    }
    
    /**
     * Get thumbnails path for videos
     */
    public String getVideoThumbnailsPath() {
        return getVideosPath() + "/thumbnails";
    }
    
    /**
     * Get transcoded videos path
     */
    public String getVideoTranscodedPath() {
        return getVideosPath() + "/transcoded";
    }
    
    /**
     * Get temp chunks path
     */
    public String getTempChunksPath() {
        return getTempPath() + "/chunks";
    }
    
    /**
     * Get processing temp path
     */
    public String getTempProcessingPath() {
        return getTempPath() + "/processing";
    }
    
    /**
     * Get cache path
     */
    public String getCachePath() {
        return getTempPath() + "/cache";
    }
}