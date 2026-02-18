package lexicon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Enhanced upload progress tracking (WebSocket support can be added later)
 */
@Service
public class UploadProgressService {
    
    private final Map<String, UploadProgress> uploadProgress = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Update upload progress
     */
    public void updateProgress(String uploadId, long bytesUploaded, long totalBytes, String status) {
        UploadProgress progress = uploadProgress.computeIfAbsent(uploadId, k -> new UploadProgress());
        
        progress.setBytesUploaded(bytesUploaded);
        progress.setTotalBytes(totalBytes);
        progress.setStatus(status);
        
        // Handle division by zero for empty files
        if (totalBytes == 0) {
            progress.setPercentage(100);  // Empty file is complete
        } else {
            progress.setPercentage((int) ((bytesUploaded * 100) / totalBytes));
        }
        progress.setTimestamp(System.currentTimeMillis());
        
        // Calculate speed and ETA
        calculateSpeedAndETA(progress);
        
        // Log progress for now (WebSocket support can be added later)
        System.out.println(String.format("📊 Upload %s: %d%% (%d/%d bytes) - %s", 
            uploadId, progress.getPercentage(), bytesUploaded, totalBytes, status));
    }
    
    /**
     * Update progress with custom message
     */
    public void updateProgressWithMessage(String uploadId, String message, String status) {
        UploadProgress progress = uploadProgress.computeIfAbsent(uploadId, k -> new UploadProgress());
        
        progress.setMessage(message);
        progress.setStatus(status);
        progress.setTimestamp(System.currentTimeMillis());
        
        System.out.println(String.format("📊 Upload %s: %s - %s", uploadId, status, message));
    }
    
    /**
     * Mark upload as completed
     */
    public void markCompleted(String uploadId, String filename, long fileSize) {
        UploadProgress progress = uploadProgress.get(uploadId);
        if (progress == null) {
            progress = new UploadProgress();
        }
        
        progress.setBytesUploaded(fileSize);
        progress.setTotalBytes(fileSize);
        progress.setPercentage(100);
        progress.setStatus("completed");
        progress.setMessage("Upload completed: " + filename);
        progress.setTimestamp(System.currentTimeMillis());
        progress.setCompleted(true);
        
        System.out.println(String.format("✅ Upload %s completed: %s (%d bytes)", uploadId, filename, fileSize));
        
        // Clean up after a delay
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 5 second delay
                uploadProgress.remove(uploadId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Mark upload as failed
     */
    public void markFailed(String uploadId, String errorMessage) {
        UploadProgress progress = uploadProgress.computeIfAbsent(uploadId, k -> new UploadProgress());
        
        progress.setStatus("failed");
        progress.setMessage("Upload failed: " + errorMessage);
        progress.setTimestamp(System.currentTimeMillis());
        progress.setFailed(true);
        
        System.err.println(String.format("❌ Upload %s failed: %s", uploadId, errorMessage));
    }
    
    /**
     * Calculate upload speed and estimated time of arrival
     */
    private void calculateSpeedAndETA(UploadProgress progress) {
        long currentTime = System.currentTimeMillis();
        
        if (progress.getStartTime() == 0) {
            progress.setStartTime(currentTime);
        }
        
        long elapsedTime = currentTime - progress.getStartTime();
        
        if (elapsedTime > 1000) { // Only calculate after 1 second
            long bytesPerSecond = (progress.getBytesUploaded() * 1000) / elapsedTime;
            progress.setUploadSpeed(bytesPerSecond);
            
            long remainingBytes = progress.getTotalBytes() - progress.getBytesUploaded();
            if (bytesPerSecond > 0) {
                long etaSeconds = remainingBytes / bytesPerSecond;
                progress.setEtaSeconds(etaSeconds);
            }
        }
    }
    
    /**
     * Get current progress for an upload
     */
    public UploadProgress getProgress(String uploadId) {
        return uploadProgress.get(uploadId);
    }
    
    /**
     * Upload progress data class
     */
    public static class UploadProgress {
        private long bytesUploaded = 0;
        private long totalBytes = 0;
        private int percentage = 0;
        private long uploadSpeed = 0; // bytes per second
        private long etaSeconds = 0;
        private String status = "initializing";
        private String message = "";
        private long timestamp = System.currentTimeMillis();
        private long startTime = 0;
        private boolean completed = false;
        private boolean failed = false;
        
        // Getters and setters
        public long getBytesUploaded() { return bytesUploaded; }
        public void setBytesUploaded(long bytesUploaded) { this.bytesUploaded = bytesUploaded; }
        
        public long getTotalBytes() { return totalBytes; }
        public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
        
        public int getPercentage() { return percentage; }
        public void setPercentage(int percentage) { this.percentage = percentage; }
        
        public long getUploadSpeed() { return uploadSpeed; }
        public void setUploadSpeed(long uploadSpeed) { this.uploadSpeed = uploadSpeed; }
        
        public long getEtaSeconds() { return etaSeconds; }
        public void setEtaSeconds(long etaSeconds) { this.etaSeconds = etaSeconds; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        
        public boolean isFailed() { return failed; }
        public void setFailed(boolean failed) { this.failed = failed; }
    }
}