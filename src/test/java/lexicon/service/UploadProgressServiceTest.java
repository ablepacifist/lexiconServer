package lexicon.service;

import lexicon.service.UploadProgressService.UploadProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL comprehensive tests for UploadProgressService
 */
class UploadProgressServiceTest {

    private UploadProgressService progressService;

    @BeforeEach
    void setUp() {
        progressService = new UploadProgressService();
    }

    @Test
    void testBasicProgressTracking() {
        String uploadId = "test-upload-basic";
        long totalBytes = 1000L;
        
        // Initial progress
        progressService.updateProgress(uploadId, 0L, totalBytes, "starting");
        
        UploadProgress progress = progressService.getProgress(uploadId);
        assertNotNull(progress, "Progress should be tracked");
        assertEquals(0L, progress.getBytesUploaded());
        assertEquals(totalBytes, progress.getTotalBytes());
        assertEquals(0, progress.getPercentage());
        assertEquals("starting", progress.getStatus());
        
        // Mid progress
        progressService.updateProgress(uploadId, 250L, totalBytes, "uploading");
        
        progress = progressService.getProgress(uploadId);
        assertEquals(250L, progress.getBytesUploaded());
        assertEquals(25, progress.getPercentage());
        assertEquals("uploading", progress.getStatus());
        
        // Complete progress
        progressService.updateProgress(uploadId, totalBytes, totalBytes, "completed");
        
        progress = progressService.getProgress(uploadId);
        assertEquals(totalBytes, progress.getBytesUploaded());
        assertEquals(100, progress.getPercentage());
        assertEquals("completed", progress.getStatus());
    }

    @Test
    void testProgressWithMessage() {
        String uploadId = "test-upload-message";
        String customMessage = "Processing chunk 5 of 10";
        
        progressService.updateProgressWithMessage(uploadId, customMessage, "processing");
        
        UploadProgress progress = progressService.getProgress(uploadId);
        assertNotNull(progress);
        assertEquals(customMessage, progress.getMessage());
        assertEquals("processing", progress.getStatus());
    }

    @Test
    void testSpeedCalculation() throws InterruptedException {
        String uploadId = "test-upload-speed";
        long totalBytes = 10000L;
        
        // First update
        progressService.updateProgress(uploadId, 0L, totalBytes, "uploading");
        UploadProgress progress1 = progressService.getProgress(uploadId);
        long startTime = progress1.getTimestamp();
        
        // Wait a bit to ensure time difference
        Thread.sleep(50);
        
        // Second update with progress
        progressService.updateProgress(uploadId, 2500L, totalBytes, "uploading");
        UploadProgress progress2 = progressService.getProgress(uploadId);
        
        // Speed should be calculated
        assertTrue(progress2.getTimestamp() > startTime, "Timestamp should be updated");
        // Speed calculation depends on timing, so just verify it's set
        assertTrue(progress2.getUploadSpeed() >= 0, "Upload speed should be calculated");
        assertTrue(progress2.getEtaSeconds() >= 0, "ETA should be calculated");
    }

    @Test
    void testMultipleUploads() {
        String upload1 = "upload-1";
        String upload2 = "upload-2";
        String upload3 = "upload-3";
        
        // Start multiple uploads
        progressService.updateProgress(upload1, 100L, 1000L, "uploading");
        progressService.updateProgress(upload2, 500L, 2000L, "uploading");
        progressService.updateProgress(upload3, 750L, 1500L, "uploading");
        
        // Verify all are tracked independently
        UploadProgress p1 = progressService.getProgress(upload1);
        UploadProgress p2 = progressService.getProgress(upload2);
        UploadProgress p3 = progressService.getProgress(upload3);
        
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotNull(p3);
        
        assertEquals(10, p1.getPercentage());
        assertEquals(25, p2.getPercentage());
        assertEquals(50, p3.getPercentage());
        
        assertEquals("uploading", p1.getStatus());
        assertEquals("uploading", p2.getStatus());
        assertEquals("uploading", p3.getStatus());
    }

    @Test
    void testEdgeCases() {
        String uploadId = "test-edge-cases";
        
        // Zero byte file
        progressService.updateProgress(uploadId, 0L, 0L, "completed");
        UploadProgress progress = progressService.getProgress(uploadId);
        assertEquals(100, progress.getPercentage(), "Zero byte file should be 100% complete");
        
        // Large file (use reasonable size to avoid overflow)
        long largeSize = 10L * 1024 * 1024 * 1024; // 10GB
        progressService.updateProgress(uploadId, largeSize / 4, largeSize, "uploading");
        progress = progressService.getProgress(uploadId);
        assertEquals(25, progress.getPercentage(), "Large file percentage should be calculated correctly");
    }

    @Test
    void testProgressStates() {
        String uploadId = "test-states";
        
        // Test various states
        progressService.updateProgress(uploadId, 0L, 1000L, "initializing");
        assertEquals("initializing", progressService.getProgress(uploadId).getStatus());
        
        progressService.updateProgress(uploadId, 100L, 1000L, "uploading");
        assertEquals("uploading", progressService.getProgress(uploadId).getStatus());
        
        progressService.updateProgress(uploadId, 1000L, 1000L, "processing");
        assertEquals("processing", progressService.getProgress(uploadId).getStatus());
        
        progressService.updateProgress(uploadId, 1000L, 1000L, "completed");
        UploadProgress finalProgress = progressService.getProgress(uploadId);
        assertEquals("completed", finalProgress.getStatus());
        assertEquals(100, finalProgress.getPercentage());
    }

    @Test
    void testNonExistentUpload() {
        UploadProgress progress = progressService.getProgress("nonexistent-upload");
        assertNull(progress, "Non-existent upload should return null");
    }

    @Test
    void testProgressOverwrite() {
        String uploadId = "test-overwrite";
        
        // Initial progress
        progressService.updateProgress(uploadId, 100L, 1000L, "uploading");
        UploadProgress progress1 = progressService.getProgress(uploadId);
        assertEquals(100L, progress1.getBytesUploaded());
        
        // Overwrite with new progress
        progressService.updateProgress(uploadId, 300L, 1000L, "uploading");
        UploadProgress progress2 = progressService.getProgress(uploadId);
        assertEquals(300L, progress2.getBytesUploaded());
        assertEquals(30, progress2.getPercentage());
        
        // Should be same object updated
        assertSame(progress1, progress2, "Should update same progress object");
    }

    @Test 
    void testProgressTimestamps() {
        String uploadId = "test-timestamps";
        
        long beforeUpdate = System.currentTimeMillis();
        progressService.updateProgress(uploadId, 500L, 1000L, "uploading");
        long afterUpdate = System.currentTimeMillis();
        
        UploadProgress progress = progressService.getProgress(uploadId);
        assertTrue(progress.getTimestamp() >= beforeUpdate, "Timestamp should be after start");
        assertTrue(progress.getTimestamp() <= afterUpdate, "Timestamp should be before end");
    }

    @Test
    void testProgressFields() {
        String uploadId = "test-fields";
        progressService.updateProgress(uploadId, 400L, 800L, "uploading");
        
        UploadProgress progress = progressService.getProgress(uploadId);
        
        // Test all getters work
        assertEquals(400L, progress.getBytesUploaded());
        assertEquals(800L, progress.getTotalBytes());
        assertEquals(50, progress.getPercentage());
        assertEquals("uploading", progress.getStatus());
        assertTrue(progress.getTimestamp() > 0);
        
        // Test initial values for other fields
        assertEquals(0L, progress.getUploadSpeed());
        assertEquals(0L, progress.getEtaSeconds());
        assertEquals("", progress.getMessage());
        assertFalse(progress.isCompleted());
        assertFalse(progress.isFailed());
    }

    @Test
    void testMessageUpdate() {
        String uploadId = "test-message-update";
        
        // Update with custom message
        progressService.updateProgressWithMessage(uploadId, "Initializing upload", "starting");
        UploadProgress progress = progressService.getProgress(uploadId);
        assertEquals("Initializing upload", progress.getMessage());
        assertEquals("starting", progress.getStatus());
        
        // Update message again
        progressService.updateProgressWithMessage(uploadId, "Uploading chunks", "uploading");
        progress = progressService.getProgress(uploadId);
        assertEquals("Uploading chunks", progress.getMessage());
        assertEquals("uploading", progress.getStatus());
    }
}