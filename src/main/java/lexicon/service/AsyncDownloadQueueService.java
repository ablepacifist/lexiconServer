package lexicon.service;

import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.UUID;

/**
 * Async download queue for YouTube and other URL downloads
 * Prevents HTTP timeouts by returning immediately and processing in background
 */
@Service
public class AsyncDownloadQueueService {
    
    @Autowired
    private YtDlpService ytDlpService;
    
    @Autowired
    private MediaManagerService mediaManager;
    
    @Autowired
    private UploadProgressService progressService;
    
    // Thread pool for concurrent downloads (limit to 2 to avoid bandwidth issues)
    private ExecutorService downloadExecutor;
    
    // Track active and completed downloads
    private final Map<String, DownloadJob> downloadJobs = new ConcurrentHashMap<>();
    
    // Cleanup old jobs after 1 hour
    private ScheduledExecutorService cleanupScheduler;
    
    @PostConstruct
    public void init() {
        downloadExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "download-worker");
            t.setDaemon(true);
            return t;
        });
        
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupOldJobs, 30, 30, TimeUnit.MINUTES);
    }
    
    @PreDestroy
    public void shutdown() {
        if (downloadExecutor != null) {
            downloadExecutor.shutdown();
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
    }
    
    /**
     * Queue a download job - returns immediately with a job ID
     */
    public String queueDownload(String url, int userId, String title, String description,
                                boolean isPublic, String mediaType, String downloadType) {
        
        String jobId = UUID.randomUUID().toString();
        
        DownloadJob job = new DownloadJob(jobId, url, userId, title, description, 
                                          isPublic, mediaType, downloadType);
        downloadJobs.put(jobId, job);
        
        // Update progress to "queued"
        progressService.updateProgressWithMessage(jobId, "Download queued: " + url, "queued");
        
        // Submit to executor
        downloadExecutor.submit(() -> processDownload(job));
        
        System.out.println("📥 Queued download job " + jobId + " for URL: " + url);
        
        return jobId;
    }
    
    /**
     * Process the download in background
     */
    private void processDownload(DownloadJob job) {
        try {
            job.setStatus(DownloadStatus.DOWNLOADING);
            job.setStartedAt(LocalDateTime.now());
            
            progressService.updateProgressWithMessage(job.getJobId(), 
                "Starting download from: " + job.getUrl(), "downloading");
            
            // Convert download type string to enum
            YtDlpService.DownloadType dlType;
            try {
                dlType = YtDlpService.DownloadType.valueOf(job.getDownloadType().toUpperCase());
            } catch (IllegalArgumentException e) {
                dlType = YtDlpService.DownloadType.AUDIO_ONLY;
            }
            
            // Get temp directory for downloads
            String tempDir = System.getProperty("java.io.tmpdir") + "/lexicon-downloads";
            new File(tempDir).mkdirs();
            
            // Execute download (this is the long-running part)
            YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
                job.getUrl(), dlType, tempDir);
            
            if (!result.isSuccess()) {
                job.setStatus(DownloadStatus.FAILED);
                job.setError(result.getErrorMessage());
                job.setCompletedAt(LocalDateTime.now());
                
                progressService.updateProgressWithMessage(job.getJobId(),
                    "Download failed: " + result.getErrorMessage(), "failed");
                
                System.err.println("❌ Download failed for job " + job.getJobId() + ": " + result.getErrorMessage());
                return;
            }
            
            progressService.updateProgressWithMessage(job.getJobId(),
                "Download complete, saving to library...", "processing");
            
            // Upload the downloaded file to our media system
            File downloadedFile = result.getFile();
            
            MediaFile mediaFile = mediaManager.uploadMediaFromFile(
                downloadedFile,
                job.getUserId(),
                job.getTitle(),
                job.getDescription(),
                job.isPublic(),
                job.getMediaType(),
                job.getUrl()
            );
            
            // Cleanup temp file
            if (downloadedFile != null && downloadedFile.exists()) {
                downloadedFile.delete();
            }
            
            job.setStatus(DownloadStatus.COMPLETED);
            job.setMediaFileId(mediaFile.getId());
            job.setCompletedAt(LocalDateTime.now());
            
            progressService.markCompleted(job.getJobId(), mediaFile.getTitle(), mediaFile.getFileSize());
            
            System.out.println("✅ Download completed for job " + job.getJobId() + 
                             " -> MediaFile ID: " + mediaFile.getId());
            
        } catch (Exception e) {
            job.setStatus(DownloadStatus.FAILED);
            job.setError("Internal error: " + e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            
            progressService.updateProgressWithMessage(job.getJobId(),
                "Internal error: " + e.getMessage(), "failed");
            
            System.err.println("❌ Exception processing download job " + job.getJobId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get status of a download job
     */
    public DownloadJob getJobStatus(String jobId) {
        return downloadJobs.get(jobId);
    }
    
    /**
     * Get all active (queued or downloading) jobs for a user
     */
    public Map<String, DownloadJob> getActiveJobsForUser(int userId) {
        Map<String, DownloadJob> userJobs = new ConcurrentHashMap<>();
        for (Map.Entry<String, DownloadJob> entry : downloadJobs.entrySet()) {
            if (entry.getValue().getUserId() == userId && 
                (entry.getValue().getStatus() == DownloadStatus.QUEUED || 
                 entry.getValue().getStatus() == DownloadStatus.DOWNLOADING)) {
                userJobs.put(entry.getKey(), entry.getValue());
            }
        }
        return userJobs;
    }
    
    /**
     * Cancel a download job (if still queued)
     */
    public boolean cancelJob(String jobId) {
        DownloadJob job = downloadJobs.get(jobId);
        if (job != null && job.getStatus() == DownloadStatus.QUEUED) {
            job.setStatus(DownloadStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
            return true;
        }
        return false;
    }
    
    /**
     * Cleanup old completed/failed jobs
     */
    private void cleanupOldJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        downloadJobs.entrySet().removeIf(entry -> {
            DownloadJob job = entry.getValue();
            return job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff);
        });
    }
    
    // ========== Inner Classes ==========
    
    public enum DownloadStatus {
        QUEUED, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }
    
    public static class DownloadJob {
        private final String jobId;
        private final String url;
        private final int userId;
        private final String title;
        private final String description;
        private final boolean isPublic;
        private final String mediaType;
        private final String downloadType;
        private final LocalDateTime queuedAt;
        
        private DownloadStatus status = DownloadStatus.QUEUED;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String error;
        private Integer mediaFileId;
        
        public DownloadJob(String jobId, String url, int userId, String title, 
                          String description, boolean isPublic, String mediaType, String downloadType) {
            this.jobId = jobId;
            this.url = url;
            this.userId = userId;
            this.title = title;
            this.description = description;
            this.isPublic = isPublic;
            this.mediaType = mediaType;
            this.downloadType = downloadType;
            this.queuedAt = LocalDateTime.now();
        }
        
        // Getters
        public String getJobId() { return jobId; }
        public String getUrl() { return url; }
        public int getUserId() { return userId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public boolean isPublic() { return isPublic; }
        public String getMediaType() { return mediaType; }
        public String getDownloadType() { return downloadType; }
        public LocalDateTime getQueuedAt() { return queuedAt; }
        public DownloadStatus getStatus() { return status; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public String getError() { return error; }
        public Integer getMediaFileId() { return mediaFileId; }
        
        // Setters
        public void setStatus(DownloadStatus status) { this.status = status; }
        public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        public void setError(String error) { this.error = error; }
        public void setMediaFileId(Integer mediaFileId) { this.mediaFileId = mediaFileId; }
    }
}
