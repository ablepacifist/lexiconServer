package lexicon.logic;

import lexicon.config.StorageProperties;
import lexicon.object.ChunkedUpload;
import lexicon.object.ChunkedUploadStatus;
import lexicon.object.MediaFile;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.UploadProgressService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * Tests for ChunkedUploadService using Mockito
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChunkedUploadServiceTest {
    
    @TempDir
    Path tempDir;
    
    @Mock
    private MediaManagerService mockMediaManager;
    
    @Mock
    private ChunkedUploadProgressTracker mockProgressTracker;
    
    @Mock
    private StorageProperties storageProperties;
    
    @Mock
    private OptimizedFileStorageService optimizedFileStorageService;
    
    @Mock 
    private UploadProgressService uploadProgressService;
    
    @InjectMocks
    private ChunkedUploadService service;
    
    @BeforeEach
    void setUp() {
        // Setup storage properties mock
        when(storageProperties.getTempChunksPath()).thenReturn(tempDir.toString());
        
        // Setup mock responses
        MediaFile mockMediaFile = new MediaFile();
        mockMediaFile.setId(123);
        mockMediaFile.setFilename("test_audiobook.mp3");
        mockMediaFile.setTitle("Test Audiobook");
        
        try {
            lenient().when(mockMediaManager.uploadMediaFile(
                any(MultipartFile.class), 
                anyInt(), 
                anyString(), 
                anyString(), 
                anyBoolean(), 
                anyString()
            )).thenReturn(mockMediaFile);
        } catch (Exception e) {
            fail("Failed to setup mock: " + e.getMessage());
        }
    }
    
    @AfterEach
    void tearDown() {
        // Clean up all files in temp directory to prevent TempDirectory cleanup failures
        try {
            if (tempDir != null && Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            if (!path.equals(tempDir)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException ignored) {
                            // Ignore cleanup errors
                        }
                    });
            }
        } catch (IOException ignored) {
            // Ignore cleanup errors
        }
    }
    
    @Test
    @Order(1)
    void testInitializeUpload() {
        System.out.println("\n=== Test 1: Initialize Chunked Upload ===");
        
        ChunkedUpload upload = service.initializeUpload(
            "test_audiobook.mp3", 
            "audio/mpeg", 
            500 * 1024 * 1024L, // 500MB
            10 * 1024 * 1024,   // 10MB chunks
            1, 
            "Test Audiobook", 
            "A large audiobook for testing", 
            false, 
            "AUDIOBOOK", 
            "abc123hash"
        );
        
        assertNotNull(upload, "Upload should be initialized");
        assertNotNull(upload.getUploadId(), "Upload ID should be generated");
        assertEquals("test_audiobook.mp3", upload.getOriginalFilename());
        assertEquals(500 * 1024 * 1024L, upload.getTotalSize());
        assertEquals(50, upload.getTotalChunks()); // 500MB / 10MB = 50 chunks
        assertEquals(ChunkedUploadStatus.IN_PROGRESS, upload.getStatus());
        assertEquals("abc123hash", upload.getChecksum());
        assertTrue(upload.getUploadedChunks().isEmpty());
        
        System.out.println("✓ Upload initialized: " + upload.getUploadId());
        System.out.println("✓ Total chunks: " + upload.getTotalChunks());
        System.out.println("✓ Chunk size: " + upload.getChunkSize() + " bytes");
    }
    
    @Test
    @Order(2)
    void testUploadSingleChunk() throws IOException {
        System.out.println("\n=== Test 2: Upload Single Chunk ===");
        
        // Initialize upload
        ChunkedUpload upload = service.initializeUpload(
            "test_video.mp4", "video/mp4", 100 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Test Video", "", true, "VIDEO", null
        );
        
        // Create mock chunk data
        byte[] chunkData = new byte[5 * 1024 * 1024]; // 5MB chunk
        Arrays.fill(chunkData, (byte) 0x42); // Fill with 'B'
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_0", "application/octet-stream", chunkData);
        
        // Upload chunk
        boolean success = service.uploadChunk(upload.getUploadId(), 0, chunkFile, null);
        
        assertTrue(success, "Chunk upload should succeed");
        
        // Verify upload state
        ChunkedUpload updatedUpload = service.getUploadStatus(upload.getUploadId());
        assertEquals(1, updatedUpload.getUploadedChunks().size());
        assertTrue(updatedUpload.getUploadedChunks().contains(0));
        assertEquals(10.0, updatedUpload.getProgress(), 0.01); // 1/10 chunks = 10%
        
        System.out.println("✓ Chunk 0 uploaded successfully");
        System.out.println("✓ Progress: " + updatedUpload.getProgress() + "%");
    }
    
    @Test
    @Order(3)
    void testUploadMultipleChunks() throws IOException {
        System.out.println("\n=== Test 3: Upload Multiple Chunks ===");
        
        // Initialize upload for small file (3 chunks)
        ChunkedUpload upload = service.initializeUpload(
            "small_test.txt", "text/plain", 25 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Small Test File", "", false, "OTHER", null
        );
        
        assertEquals(3, upload.getTotalChunks()); // 25MB / 10MB = 3 chunks
        
        // Upload chunks 0, 1, 2
        for (int i = 0; i < 3; i++) {
            byte[] chunkData = new byte[i == 2 ? 5 * 1024 * 1024 : 10 * 1024 * 1024]; // Last chunk smaller
            Arrays.fill(chunkData, (byte) (0x30 + i)); // Fill with '0', '1', '2'
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_" + i, "application/octet-stream", chunkData);
            
            boolean success = service.uploadChunk(upload.getUploadId(), i, chunkFile, null);
            assertTrue(success, "Chunk " + i + " upload should succeed");
            
            ChunkedUpload updatedUpload = service.getUploadStatus(upload.getUploadId());
            assertEquals(i + 1, updatedUpload.getUploadedChunks().size());
            
            double expectedProgress = ((double)(i + 1) / 3) * 100;
            assertEquals(expectedProgress, updatedUpload.getProgress(), 0.01);
            
            System.out.println("✓ Chunk " + i + " uploaded, progress: " + updatedUpload.getProgress() + "%");
        }
        
        // Verify all chunks uploaded
        ChunkedUpload finalUpload = service.getUploadStatus(upload.getUploadId());
        assertTrue(finalUpload.isComplete(), "Upload should be complete");
        assertEquals(ChunkedUploadStatus.ASSEMBLING, finalUpload.getStatus());
    }
    
    @Test
    @Order(4)
    void testResumeUpload() throws IOException {
        System.out.println("\n=== Test 4: Resume Upload ===");
        
        // Initialize upload
        ChunkedUpload upload = service.initializeUpload(
            "resume_test.bin", "application/octet-stream", 40 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Resume Test", "", false, "OTHER", null
        );
        
        assertEquals(4, upload.getTotalChunks());
        
        // Upload chunks 0 and 2 (skip chunk 1)
        int[] chunksToUpload = {0, 2};
        for (int chunkNum : chunksToUpload) {
            byte[] chunkData = new byte[10 * 1024 * 1024];
            Arrays.fill(chunkData, (byte) (0x40 + chunkNum));
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_" + chunkNum, "application/octet-stream", chunkData);
            
            service.uploadChunk(upload.getUploadId(), chunkNum, chunkFile, null);
            System.out.println("✓ Uploaded chunk " + chunkNum);
        }
        
        // Check missing chunks
        ChunkedUpload partialUpload = service.getUploadStatus(upload.getUploadId());
        var missingChunks = partialUpload.getMissingChunks();
        assertTrue(missingChunks.contains(1), "Chunk 1 should be missing");
        assertTrue(missingChunks.contains(3), "Chunk 3 should be missing");
        assertEquals(2, missingChunks.size(), "Should have 2 missing chunks");
        
        System.out.println("✓ Missing chunks identified: " + missingChunks);
        
        // Resume upload (upload missing chunks)
        for (int chunkNum : missingChunks) {
            byte[] chunkData = new byte[10 * 1024 * 1024];
            Arrays.fill(chunkData, (byte) (0x50 + chunkNum));
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_" + chunkNum, "application/octet-stream", chunkData);
            
            service.uploadChunk(upload.getUploadId(), chunkNum, chunkFile, null);
            System.out.println("✓ Resumed and uploaded chunk " + chunkNum);
        }
        
        // Verify complete
        ChunkedUpload completedUpload = service.getUploadStatus(upload.getUploadId());
        assertTrue(completedUpload.isComplete(), "Upload should be complete after resume");
        assertEquals(100.0, completedUpload.getProgress(), 0.01);
    }
    
    @Test
    @Order(5)
    void testInvalidChunkUpload() {
        System.out.println("\n=== Test 5: Invalid Chunk Upload ===");
        
        // Initialize upload
        ChunkedUpload upload = service.initializeUpload(
            "invalid_test.txt", "text/plain", 30 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Invalid Test", "", false, "OTHER", null
        );
        
        byte[] chunkData = new byte[1024];
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_invalid", "application/octet-stream", chunkData);
        
        // Test invalid chunk number (negative)
        assertThrows(IllegalArgumentException.class, () -> {
            service.uploadChunk(upload.getUploadId(), -1, chunkFile, null);
        }, "Should reject negative chunk number");
        
        // Test invalid chunk number (too high)
        assertThrows(IllegalArgumentException.class, () -> {
            service.uploadChunk(upload.getUploadId(), 10, chunkFile, null);
        }, "Should reject chunk number beyond total chunks");
        
        // Test invalid upload ID
        assertThrows(IllegalArgumentException.class, () -> {
            service.uploadChunk("invalid-upload-id", 0, chunkFile, null);
        }, "Should reject invalid upload ID");
        
        System.out.println("✓ Invalid inputs correctly rejected");
    }
    
    @Test
    @Order(6)
    void testDuplicateChunkUpload() throws IOException {
        System.out.println("\n=== Test 6: Duplicate Chunk Upload ===");
        
        // Initialize upload
        ChunkedUpload upload = service.initializeUpload(
            "duplicate_test.dat", "application/octet-stream", 20 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Duplicate Test", "", false, "OTHER", null
        );
        
        byte[] chunkData = new byte[5 * 1024 * 1024];
        Arrays.fill(chunkData, (byte) 0x99);
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_0", "application/octet-stream", chunkData);
        
        // Upload chunk 0 first time
        boolean firstUpload = service.uploadChunk(upload.getUploadId(), 0, chunkFile, null);
        assertTrue(firstUpload, "First upload should succeed");
        
        // Upload chunk 0 second time (duplicate)
        boolean secondUpload = service.uploadChunk(upload.getUploadId(), 0, chunkFile, null);
        assertTrue(secondUpload, "Duplicate upload should succeed (idempotent)");
        
        // Verify only counted once
        ChunkedUpload updatedUpload = service.getUploadStatus(upload.getUploadId());
        assertEquals(1, updatedUpload.getUploadedChunks().size());
        assertEquals(50.0, updatedUpload.getProgress(), 0.01); // 1/2 chunks
        
        System.out.println("✓ Duplicate chunk handled correctly");
    }
    
    @Test
    @Order(7)
    void testCancelUpload() throws IOException {
        System.out.println("\n=== Test 7: Cancel Upload ===");
        
        // Initialize upload
        ChunkedUpload upload = service.initializeUpload(
            "cancel_test.bin", "application/octet-stream", 50 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Cancel Test", "", false, "OTHER", null
        );
        
        // Upload one chunk
        byte[] chunkData = new byte[10 * 1024 * 1024];
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_0", "application/octet-stream", chunkData);
        service.uploadChunk(upload.getUploadId(), 0, chunkFile, null);
        
        // Verify chunk uploaded
        ChunkedUpload partialUpload = service.getUploadStatus(upload.getUploadId());
        assertEquals(1, partialUpload.getUploadedChunks().size());
        
        // Cancel upload
        boolean cancelled = service.cancelUpload(upload.getUploadId());
        assertTrue(cancelled, "Upload should be cancelled");
        
        // Verify upload session is gone
        ChunkedUpload cancelledUpload = service.getUploadStatus(upload.getUploadId());
        assertNull(cancelledUpload, "Upload session should be removed after cancellation");
        
        // Test cancelling non-existent upload
        boolean notFound = service.cancelUpload("non-existent-id");
        assertFalse(notFound, "Should return false for non-existent upload");
        
        System.out.println("✓ Upload cancelled and cleaned up");
    }
    
    @Test
    @Order(8)
    void testUploadProgressCalculation() throws IOException {
        System.out.println("\n=== Test 8: Upload Progress Calculation ===");
        
        // Initialize upload with known chunk count
        ChunkedUpload upload = service.initializeUpload(
            "progress_test.dat", "application/octet-stream", 100 * 1024 * 1024L, 
            10 * 1024 * 1024, 1, "Progress Test", "", false, "OTHER", null
        );
        
        assertEquals(10, upload.getTotalChunks());
        assertEquals(0.0, upload.getProgress(), 0.01);
        
        // Test progress at various stages
        double[] expectedProgress = {10.0, 20.0, 30.0, 40.0, 50.0};
        
        for (int i = 0; i < 5; i++) {
            byte[] chunkData = new byte[10 * 1024 * 1024];
            MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk_" + i, "application/octet-stream", chunkData);
            
            service.uploadChunk(upload.getUploadId(), i, chunkFile, null);
            
            ChunkedUpload updatedUpload = service.getUploadStatus(upload.getUploadId());
            assertEquals(expectedProgress[i], updatedUpload.getProgress(), 0.01);
            
            System.out.println("✓ Chunk " + i + " uploaded, progress: " + updatedUpload.getProgress() + "%");
        }
    }
}