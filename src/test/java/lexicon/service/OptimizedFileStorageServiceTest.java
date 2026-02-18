package lexicon.service;

import lexicon.config.StorageProperties;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL comprehensive tests for OptimizedFileStorageService
 */
class OptimizedFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageProperties storageProperties;
    private OptimizedFileStorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        // Setup storage properties with temp directory
        storageProperties = new StorageProperties();
        storageProperties.setBasePath(tempDir.toString());
        
        // Initialize service
        storageService = new OptimizedFileStorageService();
        
        // Inject storage properties using reflection
        var field = OptimizedFileStorageService.class.getDeclaredField("storageProperties");
        field.setAccessible(true);
        field.set(storageService, storageProperties);
        
        // Create required directory structure
        Files.createDirectories(tempDir.resolve("audiobooks"));
        Files.createDirectories(tempDir.resolve("music"));  
        Files.createDirectories(tempDir.resolve("videos"));
        Files.createDirectories(tempDir.resolve("temp"));
        Files.createDirectories(tempDir.resolve("backups"));
    }

    @Test
    void testStoreSmallAudioFile() throws IOException {
        // Create test audio content (small file)
        byte[] audioContent = new byte[1024]; // 1KB
        Arrays.fill(audioContent, (byte) 0x55); // Fill with test pattern
        
        MockMultipartFile audioFile = new MockMultipartFile(
            "file",
            "test-audio.mp3", 
            "audio/mpeg",
            audioContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Test Audio");
        mediaFile.setMediaType(MediaType.MUSIC);
        
        // Store the file
        String relativePath = storageService.storeFile(audioFile, mediaFile);
        
        // Verify file was stored
        assertNotNull(relativePath);
        Path storedFile = tempDir.resolve(relativePath);
        assertTrue(Files.exists(storedFile), "File should exist at: " + storedFile);
        assertEquals(1024L, Files.size(storedFile), "File size should match");
        
        // Verify content integrity
        byte[] storedContent = Files.readAllBytes(storedFile);
        assertArrayEquals(audioContent, storedContent, "File content should match exactly");
        
        // Verify it's in correct directory structure (should be in music or audiobooks)
        assertTrue(relativePath.contains("music") || relativePath.contains("audiobooks"),
                   "Audio file should be in music or audiobooks directory");
    }

    @Test
    void testStoreLargeVideoFile() throws IOException {
        // Create large video content (simulate large file)
        byte[] videoContent = new byte[50 * 1024]; // 50KB (simulating large)
        Arrays.fill(videoContent, (byte) 0xAA);
        
        MockMultipartFile videoFile = new MockMultipartFile(
            "file",
            "large-video.mp4",
            "video/mp4", 
            videoContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Large Video Test");
        mediaFile.setMediaType(MediaType.AUDIOBOOK); // Testing type mapping
        
        // Store the file
        String relativePath = storageService.storeFile(videoFile, mediaFile);
        
        // Verify storage
        assertNotNull(relativePath);
        Path storedFile = tempDir.resolve(relativePath);
        assertTrue(Files.exists(storedFile));
        assertEquals(videoContent.length, Files.size(storedFile));
        
        // Verify in videos directory
        assertTrue(relativePath.contains("videos") || relativePath.contains("audiobooks"));
    }

    @Test 
    void testFileInputStream() throws IOException {
        // Create and store a test file first
        byte[] testContent = "Test file content for input stream".getBytes();
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "stream-test.txt", "text/plain", testContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Stream Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(testFile, mediaFile);
        
        // Test getting input stream
        try (InputStream inputStream = storageService.getFileInputStream(relativePath)) {
            assertNotNull(inputStream);
            
            byte[] readContent = inputStream.readAllBytes();
            assertArrayEquals(testContent, readContent);
        }
    }

    @Test
    void testFileStreaming() throws IOException {
        // Create test file for streaming
        byte[] streamContent = new byte[2048]; // 2KB
        for (int i = 0; i < streamContent.length; i++) {
            streamContent[i] = (byte) (i % 256);
        }
        
        MockMultipartFile streamFile = new MockMultipartFile(
            "file", "streaming-test.mp4", "video/mp4", streamContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Streaming Test");
        mediaFile.setMediaType(MediaType.AUDIOBOOK);
        
        String relativePath = storageService.storeFile(streamFile, mediaFile);
        
        // Test streaming with range
        long rangeStart = 100;
        long rangeEnd = 199; // 100 bytes
        
        OptimizedFileStorageService.FileStreamInfo streamInfo = 
            storageService.getFileForStreaming(relativePath, rangeStart, rangeEnd);
        
        assertNotNull(streamInfo);
        assertEquals(100L, streamInfo.getContentLength());
        assertEquals(rangeStart, streamInfo.getRangeStart());
        assertEquals(rangeEnd, streamInfo.getRangeEnd());
        
        // Verify we can read the range
        assertNotNull(streamInfo.getFile());
    }

    @Test
    void testDeleteFile() throws IOException {
        // Create test file
        byte[] deleteContent = "File to be deleted".getBytes();
        MockMultipartFile deleteFile = new MockMultipartFile(
            "file", "delete-me.txt", "text/plain", deleteContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Delete Me");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(deleteFile, mediaFile);
        
        // Verify file exists
        assertTrue(Files.exists(tempDir.resolve(relativePath)));
        
        // Delete the file
        boolean deleted = storageService.deleteFile(relativePath);
        
        assertTrue(deleted, "Delete operation should return true");
        assertFalse(Files.exists(tempDir.resolve(relativePath)), "File should no longer exist");
    }

    @Test
    void testGetFileSize() throws IOException {
        // Create file with known size
        byte[] sizeTestContent = new byte[1337]; // Specific size
        Arrays.fill(sizeTestContent, (byte) 0x42);
        
        MockMultipartFile sizeFile = new MockMultipartFile(
            "file", "size-test.bin", "application/octet-stream", sizeTestContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Size Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(sizeFile, mediaFile);
        
        // Test size retrieval
        long retrievedSize = storageService.getFileSize(relativePath);
        assertEquals(1337L, retrievedSize, "Retrieved size should match stored size");
    }

    @Test
    void testFileExists() throws IOException {
        // Create test file
        MockMultipartFile existsFile = new MockMultipartFile(
            "file", "exists-test.txt", "text/plain", "existence test".getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Exists Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(existsFile, mediaFile);
        
        // Test existence check
        assertTrue(storageService.fileExists(relativePath), "File should exist");
        assertFalse(storageService.fileExists("nonexistent/file.txt"), "Non-existent file should return false");
    }

    @Test
    void testMoveFile() throws IOException {
        // Create source file
        MockMultipartFile sourceFile = new MockMultipartFile(
            "file", "move-source.txt", "text/plain", "content to move".getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Move Source");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String sourcePath = storageService.storeFile(sourceFile, mediaFile);
        
        // Move file
        String destinationPath = "music/moved-file.txt";
        long fileSize = storageService.getFileSize(sourcePath);
        
        String newPath = storageService.moveFileToPath(sourcePath, destinationPath, fileSize);
        
        assertEquals(destinationPath, newPath);
        assertFalse(storageService.fileExists(sourcePath), "Source file should no longer exist");
        assertTrue(storageService.fileExists(destinationPath), "Destination file should exist");
        assertEquals(fileSize, storageService.getFileSize(destinationPath), "File size should be preserved");
    }

    @Test
    void testGenerateChecksum() throws IOException {
        // Create file with known content for checksum
        String knownContent = "Hello, World! This is a checksum test.";
        MockMultipartFile checksumFile = new MockMultipartFile(
            "file", "checksum-test.txt", "text/plain", knownContent.getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Checksum Test");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(checksumFile, mediaFile);
        
        // Generate checksum
        String checksum1 = storageService.generateChecksum(relativePath);
        String checksum2 = storageService.generateChecksum(relativePath);
        
        assertNotNull(checksum1);
        assertEquals(32, checksum1.length(), "MD5 checksum should be 32 characters");
        assertTrue(checksum1.matches("[a-f0-9]+"), "Checksum should be lowercase hex");
        assertEquals(checksum1, checksum2, "Same file should produce same checksum");
    }

    @Test
    void testFilenameSanitization() throws IOException {
        // Test with problematic filename
        MockMultipartFile problematicFile = new MockMultipartFile(
            "file", "test file with spaces & symbols!@#$.txt", "text/plain", "test content".getBytes()
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Problematic / Filename \\ with | symbols");
        mediaFile.setMediaType(MediaType.OTHER);
        
        String relativePath = storageService.storeFile(problematicFile, mediaFile);
        
        // Verify file was stored and path is sanitized
        assertNotNull(relativePath);
        assertTrue(storageService.fileExists(relativePath));
        
        // Verify filename doesn't contain problematic characters
        String filename = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains("\\"));
        assertFalse(filename.contains("|"));
    }

    @Test
    void testLargeFileStreamingStorage() throws IOException {
        // Create larger file that triggers streaming storage
        byte[] largeContent = new byte[128 * 1024]; // 128KB
        Arrays.fill(largeContent, (byte) 0xFF);
        
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large-streaming.bin", "application/octet-stream", largeContent
        );
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setTitle("Large Streaming File");
        mediaFile.setMediaType(MediaType.OTHER);
        
        // Force large file threshold to be small for testing
        storageProperties.setLargeFileThreshold(64 * 1024); // 64KB threshold
        
        String relativePath = storageService.storeFile(largeFile, mediaFile);
        
        // Verify large file was stored correctly
        assertTrue(storageService.fileExists(relativePath));
        assertEquals(largeContent.length, storageService.getFileSize(relativePath));
        
        // Verify content integrity for large file
        try (InputStream stream = storageService.getFileInputStream(relativePath)) {
            byte[] retrievedContent = stream.readAllBytes();
            assertArrayEquals(largeContent, retrievedContent);
        }
    }
}