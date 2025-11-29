package lexicon.data;

import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for HSQLMediaDatabase
 * Tests actual database operations
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HSQLMediaDatabaseTest {
    
    private HSQLMediaDatabase mediaDatabase;
    private static final int TEST_USER_ID = 16;
    
    @BeforeAll
    void setupDatabase() {
        mediaDatabase = new HSQLMediaDatabase();
    }
    
    @AfterAll
    void cleanup() {
        System.out.println("Starting HSQLMediaDatabaseTest cleanup...");
        
        // Clean up ALL media files uploaded by TEST_USER_ID (16)
        try {
            List<MediaFile> userMedia = mediaDatabase.getMediaFilesByPlayer(TEST_USER_ID);
            int deletedCount = 0;
            for (MediaFile media : userMedia) {
                try {
                    // Delete file data first
                    mediaDatabase.deleteFileData(media.getId());
                    // Then delete the media record
                    mediaDatabase.deleteMediaFile(media.getId());
                    deletedCount++;
                    System.out.println("Deleted test media: " + media.getOriginalFilename() + " (ID: " + media.getId() + ")");
                } catch (Exception e) {
                    System.err.println("Could not delete media " + media.getId() + ": " + e.getMessage());
                }
            }
            System.out.println("Deleted " + deletedCount + " test media files for user " + TEST_USER_ID);
        } catch (Exception e) {
            System.err.println("Error during media cleanup: " + e.getMessage());
        }
        
        System.out.println("HSQLMediaDatabaseTest cleanup completed");
    }
    
    @Test
    void testGetNextMediaFileId() {
        // Act
        int id1 = mediaDatabase.getNextMediaFileId();
        int id2 = mediaDatabase.getNextMediaFileId();
        
        // Assert
        assertTrue(id1 > 0);
        assertTrue(id2 >= id1); // Should be same or higher (depends on DB state)
    }
    
    @Test
    void testAddAndGetMediaFile() {
        // Arrange
        int testMediaId = mediaDatabase.getNextMediaFileId();
        MediaFile mediaFile = new MediaFile(
            testMediaId,
            "test_file.mp3",
            "test_file.mp3",
            "audio/mpeg",
            1024L,
            "",
            16, // userId
            "Test Audio File",
            "A test audio file",
            true
        );
        mediaFile.setUploadDate(LocalDateTime.now());
        mediaFile.setMediaType(MediaType.MUSIC);
        mediaFile.setSourceUrl("https://youtube.com/test");
        
        // Act
        mediaDatabase.addMediaFile(mediaFile);
        MediaFile retrieved = mediaDatabase.getMediaFile(testMediaId);
        
        // Assert
        assertNotNull(retrieved);
        assertEquals(testMediaId, retrieved.getId());
        assertEquals("Test Audio File", retrieved.getTitle());
        assertEquals("test_file.mp3", retrieved.getFilename());
        assertEquals("audio/mpeg", retrieved.getContentType());
        assertEquals(1024L, retrieved.getFileSize());
        assertEquals(16, retrieved.getUploadedBy());
        assertTrue(retrieved.isPublic());
        assertEquals(MediaType.MUSIC, retrieved.getMediaType());
        assertEquals("https://youtube.com/test", retrieved.getSourceUrl());
        assertNotNull(retrieved.getUploadDate());
    }
    
    @Test
    void testGetMediaFile_NotFound() {
        // Act
        MediaFile result = mediaDatabase.getMediaFile(999999);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void testStoreAndGetFileData() {
        // Arrange
        int testMediaId = mediaDatabase.getNextMediaFileId();
        byte[] testData = {1, 2, 3, 4, 5, 6, 7, 8};
        
        MediaFile mediaFile = new MediaFile(
            testMediaId,
            "data_test.txt",
            "data_test.txt",
            "text/plain",
            8L,
            "",
            16,
            "File Data Test",
            "Testing file data storage",
            false
        );
        mediaFile.setUploadDate(LocalDateTime.now());
        mediaFile.setMediaType(MediaType.OTHER);
        
        // Act
        mediaDatabase.addMediaFile(mediaFile);
        mediaDatabase.storeFileData(testMediaId, testData);
        byte[] retrieved = mediaDatabase.getFileData(testMediaId);
        
        // Assert
        assertNotNull(retrieved);
        assertArrayEquals(testData, retrieved);
    }
    
    @Test
    void testGetMediaFilesByPlayer() {
        // Arrange - Create two files for user 16
        int baseId = mediaDatabase.getNextMediaFileId();
        int id1 = baseId + 1;
        MediaFile file1 = new MediaFile(
            id1, "file1.mp3", "file1.mp3", "audio/mpeg", 512L, "", 16,
            "User File 1", "First file", true
        );
        file1.setUploadDate(LocalDateTime.now());
        file1.setMediaType(MediaType.MUSIC);
        
        int id2 = id1 + 1;
        MediaFile file2 = new MediaFile(
            id2, "file2.mp3", "file2.mp3", "audio/mpeg", 1024L, "", 16,
            "User File 2", "Second file", false
        );
        file2.setUploadDate(LocalDateTime.now());
        file2.setMediaType(MediaType.AUDIOBOOK);
        
        mediaDatabase.addMediaFile(file1);
        mediaDatabase.addMediaFile(file2);
        
        // Act
        List<MediaFile> files = mediaDatabase.getMediaFilesByPlayer(16);
        
        // Assert
        assertNotNull(files);
        assertTrue(files.size() >= 2); // At least our two test files
        
        // Find our test files
        boolean found1 = false, found2 = false;
        for (MediaFile file : files) {
            if (file.getId() == id1) {
                found1 = true;
                assertEquals("User File 1", file.getTitle());
                assertEquals(MediaType.MUSIC, file.getMediaType());
            }
            if (file.getId() == id2) {
                found2 = true;
                assertEquals("User File 2", file.getTitle());
                assertEquals(MediaType.AUDIOBOOK, file.getMediaType());
            }
        }
        assertTrue(found1 && found2, "Both test files should be in results");
    }
    
    @Test
    void testGetAllPublicMediaFiles() {
        // Arrange - Create a public file
        int publicId = mediaDatabase.getNextMediaFileId();
        MediaFile publicFile = new MediaFile(
            publicId, "public.mp4", "public.mp4", "video/mp4", 2048L, "", 16,
            "Public Video", "A public video file", true
        );
        publicFile.setUploadDate(LocalDateTime.now());
        publicFile.setMediaType(MediaType.VIDEO);
        
        mediaDatabase.addMediaFile(publicFile);
        
        // Act
        List<MediaFile> files = mediaDatabase.getAllPublicMediaFiles();
        
        // Assert
        assertNotNull(files);
        assertTrue(files.size() > 0);
        
        // All returned files should be public
        for (MediaFile file : files) {
            assertTrue(file.isPublic(), "All files should be public");
        }
        
        // Find our test file
        boolean foundPublic = false;
        for (MediaFile file : files) {
            if (file.getId() == publicId) {
                foundPublic = true;
                assertEquals("Public Video", file.getTitle());
                assertEquals(MediaType.VIDEO, file.getMediaType());
            }
        }
        assertTrue(foundPublic, "Our public test file should be in results");
    }
    
    @Test
    void testSearchMediaFiles() {
        // Arrange - Create files with searchable content
        int searchId = mediaDatabase.getNextMediaFileId();
        MediaFile searchable = new MediaFile(
            searchId, "searchable.mp3", "searchable.mp3", "audio/mpeg", 512L, "", 16,
            "Searchable Title UniqueKeyword", "Contains searchable text", true
        );
        searchable.setUploadDate(LocalDateTime.now());
        searchable.setMediaType(MediaType.MUSIC);
        
        mediaDatabase.addMediaFile(searchable);
        
        // Act
        List<MediaFile> results = mediaDatabase.searchMediaFiles("UniqueKeyword");
        
        // Assert
        assertNotNull(results);
        assertTrue(results.size() > 0);
        
        boolean found = false;
        for (MediaFile file : results) {
            if (file.getId() == searchId) {
                found = true;
                assertTrue(file.getTitle().contains("UniqueKeyword"));
            }
        }
        assertTrue(found, "Should find file with UniqueKeyword in title");
    }
    
    @Test
    void testUpdateMediaFile() {
        // Arrange
        int testMediaId = mediaDatabase.getNextMediaFileId();
        MediaFile original = new MediaFile(
            testMediaId, "original.mp3", "original.mp3", "audio/mpeg", 1024L, "", 16,
            "Original Title", "Original description", false
        );
        original.setUploadDate(LocalDateTime.now());
        original.setMediaType(MediaType.OTHER);
        
        mediaDatabase.addMediaFile(original);
        
        // Modify the file
        MediaFile updated = mediaDatabase.getMediaFile(testMediaId);
        updated.setTitle("Updated Title");
        updated.setDescription("Updated description");
        updated.setPublic(true);
        updated.setMediaType(MediaType.MUSIC);
        
        // Act
        mediaDatabase.updateMediaFile(updated);
        MediaFile retrieved = mediaDatabase.getMediaFile(testMediaId);
        
        // Assert
        assertNotNull(retrieved);
        assertEquals("Updated Title", retrieved.getTitle());
        assertEquals("Updated description", retrieved.getDescription());
        assertTrue(retrieved.isPublic());
        assertEquals(MediaType.MUSIC, retrieved.getMediaType());
    }
    
    @Test
    void testDeleteMediaFile() {
        // Arrange
        int testMediaId = mediaDatabase.getNextMediaFileId();
        MediaFile toDelete = new MediaFile(
            testMediaId, "delete_me.mp3", "delete_me.mp3", "audio/mpeg", 512L, "", 16,
            "Delete This", "Will be deleted", false
        );
        toDelete.setUploadDate(LocalDateTime.now());
        toDelete.setMediaType(MediaType.OTHER);
        
        mediaDatabase.addMediaFile(toDelete);
        byte[] fileData = {1, 2, 3};
        mediaDatabase.storeFileData(testMediaId, fileData);
        
        // Verify it exists
        assertNotNull(mediaDatabase.getMediaFile(testMediaId));
        assertNotNull(mediaDatabase.getFileData(testMediaId));
        
        // Act
        mediaDatabase.deleteFileData(testMediaId);
        mediaDatabase.deleteMediaFile(testMediaId);
        
        // Assert
        assertNull(mediaDatabase.getMediaFile(testMediaId));
        assertNull(mediaDatabase.getFileData(testMediaId));
    }
    
    @Test
    void testGetRecentMediaFiles() {
        // Arrange - Create some files
        int recent1 = mediaDatabase.getNextMediaFileId();
        MediaFile file1 = new MediaFile(
            recent1, "recent1.mp3", "recent1.mp3", "audio/mpeg", 512L, "", 16,
            "Recent File 1", "First recent", true
        );
        file1.setUploadDate(LocalDateTime.now());
        file1.setMediaType(MediaType.MUSIC);
        
        mediaDatabase.addMediaFile(file1);
        
        // Act
        List<MediaFile> recent = mediaDatabase.getRecentMediaFiles(10);
        
        // Assert
        assertNotNull(recent);
        assertTrue(recent.size() > 0);
        assertTrue(recent.size() <= 10);
        
        // Should be ordered by upload date DESC (most recent first)
        // Verify our file is in there
        boolean foundRecent = false;
        for (MediaFile file : recent) {
            if (file.getId() == recent1) {
                foundRecent = true;
                break;
            }
        }
        assertTrue(foundRecent, "Recently added file should appear in recent files");
    }
    
    @Test
    void testMediaTypeEnum() {
        // Test MediaType enum conversion
        assertEquals(MediaType.MUSIC, MediaType.fromString("MUSIC"));
        assertEquals(MediaType.MUSIC, MediaType.fromString("music"));
        assertEquals(MediaType.VIDEO, MediaType.fromString("VIDEO"));
        assertEquals(MediaType.AUDIOBOOK, MediaType.fromString("AUDIOBOOK"));
        assertEquals(MediaType.OTHER, MediaType.fromString("OTHER"));
        assertEquals(MediaType.OTHER, MediaType.fromString("invalid"));
        assertEquals(MediaType.OTHER, MediaType.fromString(null));
    }
}
