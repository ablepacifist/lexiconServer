package lexicon.api;

import lexicon.service.AsyncDownloadQueueService;
import lexicon.service.AsyncDownloadQueueService.DownloadJob;
import lexicon.service.UploadProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for async URL downloads
 * Returns immediately with a job ID, downloads process in background
 */
@RestController
@RequestMapping("/api/download-queue")
@CrossOrigin(origins = "*")
public class AsyncDownloadController {
    
    @Autowired
    private AsyncDownloadQueueService downloadQueue;
    
    @Autowired
    private UploadProgressService progressService;
    
    /**
     * Queue a new download job
     * POST /api/download-queue/start
     * Returns immediately with job ID
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startDownload(
            @RequestParam("url") String url,
            @RequestParam("userId") int userId,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            @RequestParam(value = "mediaType", defaultValue = "OTHER") String mediaType,
            @RequestParam(value = "downloadType", defaultValue = "AUDIO_ONLY") String downloadType) {
        
        try {
            String jobId = downloadQueue.queueDownload(url, userId, title, description, 
                                                       isPublic, mediaType, downloadType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("jobId", jobId);
            response.put("message", "Download queued successfully. Check status with /api/download-queue/status/" + jobId);
            response.put("statusUrl", "/api/download-queue/status/" + jobId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to queue download: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Check status of a download job
     * GET /api/download-queue/status/{jobId}
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String jobId) {
        DownloadJob job = downloadQueue.getJobStatus(jobId);
        
        if (job == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Job not found: " + jobId);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jobId", job.getJobId());
        response.put("url", job.getUrl());
        response.put("title", job.getTitle());
        response.put("status", job.getStatus().name());
        response.put("queuedAt", job.getQueuedAt() != null ? job.getQueuedAt().toString() : null);
        response.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        response.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
        
        if (job.getError() != null) {
            response.put("error", job.getError());
        }
        
        if (job.getMediaFileId() != null) {
            response.put("mediaFileId", job.getMediaFileId());
        }
        
        // Include progress info
        var progress = progressService.getProgress(jobId);
        if (progress != null) {
            response.put("progress", Map.of(
                "percentage", progress.getPercentage(),
                "message", progress.getMessage() != null ? progress.getMessage() : "",
                "status", progress.getStatus()
            ));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all active downloads for a user
     * GET /api/download-queue/active/{userId}
     */
    @GetMapping("/active/{userId}")
    public ResponseEntity<Map<String, Object>> getActiveDownloads(@PathVariable int userId) {
        Map<String, DownloadJob> activeJobs = downloadQueue.getActiveJobsForUser(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", activeJobs.size());
        
        Map<String, Object> jobs = new HashMap<>();
        for (Map.Entry<String, DownloadJob> entry : activeJobs.entrySet()) {
            DownloadJob job = entry.getValue();
            jobs.put(entry.getKey(), Map.of(
                "url", job.getUrl(),
                "title", job.getTitle(),
                "status", job.getStatus().name(),
                "queuedAt", job.getQueuedAt().toString()
            ));
        }
        response.put("jobs", jobs);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Cancel a download job
     * DELETE /api/download-queue/{jobId}
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> cancelDownload(@PathVariable String jobId) {
        boolean cancelled = downloadQueue.cancelJob(jobId);
        
        Map<String, Object> response = new HashMap<>();
        if (cancelled) {
            response.put("success", true);
            response.put("message", "Download cancelled");
        } else {
            response.put("success", false);
            response.put("message", "Could not cancel - job may already be in progress or completed");
        }
        
        return ResponseEntity.ok(response);
    }
}
