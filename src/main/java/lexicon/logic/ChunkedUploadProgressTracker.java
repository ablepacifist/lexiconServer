package lexicon.logic;

import lexicon.object.ChunkedUpload;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks progress of chunked uploads and streams updates to clients via SSE
 */
@Component
public class ChunkedUploadProgressTracker {
    
    private final Map<String, ChunkedUploadProgress> progressMap = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    public static class ChunkedUploadProgress {
        public String uploadId;
        public String filename;
        public long totalSize;
        public int totalChunks;
        public int uploadedChunks;
        public double progress; // 0-100
        public String status; // "uploading", "assembling", "completed", "failed", "paused"
        public String currentChunk;
        public String errorMessage;
        
        public ChunkedUploadProgress() {}
        
        public ChunkedUploadProgress(ChunkedUpload upload) {
            this.uploadId = upload.getUploadId();
            this.filename = upload.getOriginalFilename();
            this.totalSize = upload.getTotalSize();
            this.totalChunks = upload.getTotalChunks();
            this.uploadedChunks = upload.getUploadedChunks().size();
            this.progress = upload.getProgress();
            this.status = upload.getStatus().toString().toLowerCase();
        }
    }
    
    /**
     * Register an SSE emitter for chunked upload progress
     */
    public SseEmitter registerEmitter(String uploadId) {
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L); // 24 hour timeout - effectively no timeout
        emitters.put(uploadId, emitter);
        
        emitter.onCompletion(() -> emitters.remove(uploadId));
        emitter.onTimeout(() -> emitters.remove(uploadId));
        emitter.onError((e) -> emitters.remove(uploadId));
        
        return emitter;
    }
    
    /**
     * Update progress for an upload
     */
    public void updateProgress(ChunkedUpload upload, String currentAction) {
        ChunkedUploadProgress progress = new ChunkedUploadProgress(upload);
        progress.currentChunk = currentAction;
        
        progressMap.put(upload.getUploadId(), progress);
        sendUpdate(upload.getUploadId());
    }
    
    /**
     * Mark upload as failed
     */
    public void markFailed(String uploadId, String error) {
        ChunkedUploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.status = "failed";
            progress.errorMessage = error;
            sendUpdate(uploadId);
        }
    }
    
    /**
     * Mark upload as completed
     */
    public void markCompleted(String uploadId) {
        ChunkedUploadProgress progress = progressMap.get(uploadId);
        if (progress != null) {
            progress.status = "completed";
            progress.progress = 100.0;
            sendUpdate(uploadId);
            
            // Clean up after completion
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    progressMap.remove(uploadId);
                    SseEmitter emitter = emitters.remove(uploadId);
                    if (emitter != null) {
                        emitter.complete();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    
    /**
     * Get progress for an upload
     */
    public ChunkedUploadProgress getProgress(String uploadId) {
        return progressMap.get(uploadId);
    }
    
    /**
     * Send progress update via SSE
     */
    private void sendUpdate(String uploadId) {
        SseEmitter emitter = emitters.get(uploadId);
        ChunkedUploadProgress progress = progressMap.get(uploadId);
        
        if (emitter != null && progress != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("chunked-upload-progress")
                    .data(progress));
            } catch (IOException e) {
                System.err.println("Error sending chunked upload SSE update: " + e.getMessage());
                emitters.remove(uploadId);
            }
        }
    }
}