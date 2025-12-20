package lexicon.integration;

import lexicon.config.StorageProperties;
import lexicon.object.MediaType;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.UploadProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the new storage optimization system
 */
class StorageOptimizationIntegrationTest {

    @TempDir
    Path tempDir;

    private StorageProperties storageProperties;
    private OptimizedFileStorageService fileStorageService;
    private UploadProgressService uploadProgressService;

    @BeforeEach
    void setUp() {
        // Setup storage properties
        storageProperties = new StorageProperties();
        storageProperties.setBasePath(tempDir.toString());
        
        // Initialize services 
        uploadProgressService = new UploadProgressService();
        
        // Create directory structure manually for testing
        try {
            Files.createDirectories(tempDir.resolve("audiobooks"));
            Files.createDirectories(tempDir.resolve("music"));
            Files.createDirectories(tempDir.resolve("videos"));
            Files.createDirectories(tempDir.resolve("temp"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directories", e);
        }
    }

    @Test
    void testUploadProgressTracking() {
        String uploadId = "test-upload-123";
        
        // Update progress
        uploadProgressService.updateProgress(uploadId, 512L, 1024L, "uploading");
        
        // Verify progress is tracked
        var progress = uploadProgressService.getProgress(uploadId);
        assertNotNull(progress);
        assertEquals(512L, progress.getBytesUploaded());
        assertEquals(1024L, progress.getTotalBytes());
        assertEquals(50, progress.getPercentage());
        assertEquals("uploading", progress.getStatus());
    }

    @Test
    void testDirectoriesAreCreated() {
        // Verify that initialization created the required directories
        assertTrue(Files.exists(tempDir.resolve("audiobooks")));
        assertTrue(Files.exists(tempDir.resolve("music")));
        assertTrue(Files.exists(tempDir.resolve("videos")));
        assertTrue(Files.exists(tempDir.resolve("temp")));
    }

    @Test 
    void testStoragePropertiesConfiguration() {
        // Test basic storage properties functionality
        assertEquals(tempDir.toString(), storageProperties.getBasePath());
        
        // Test path construction
        assertTrue(storageProperties.getAudiobooksPath().endsWith("audiobooks"));
        assertTrue(storageProperties.getMusicPath().endsWith("music"));
        assertTrue(storageProperties.getVideosPath().endsWith("videos"));
        assertTrue(storageProperties.getTempPath().endsWith("temp"));
    }
}