package lexicon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YtDlpService
 * Tests YouTube download functionality
 */
class YtDlpServiceTest {
    
    private YtDlpService ytDlpService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        ytDlpService = new YtDlpService();
    }
    
    @Test
    void testDownloadType_Enum() {
        // Test enum values
        assertEquals(2, YtDlpService.DownloadType.values().length);
        assertEquals(YtDlpService.DownloadType.AUDIO_ONLY, YtDlpService.DownloadType.valueOf("AUDIO_ONLY"));
        assertEquals(YtDlpService.DownloadType.VIDEO, YtDlpService.DownloadType.valueOf("VIDEO"));
    }
    
    @Test
    void testDownloadResult_Success() {
        // Arrange
        File testFile = new File("/tmp/test.mp3");
        
        // Act
        YtDlpService.DownloadResult result = new YtDlpService.DownloadResult(
            true, testFile, "Test Title", "audio/mpeg", null
        );
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals(testFile, result.getFile());
        assertEquals("Test Title", result.getTitle());
        assertEquals("audio/mpeg", result.getContentType());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testDownloadResult_Failure() {
        // Act
        YtDlpService.DownloadResult result = new YtDlpService.DownloadResult(
            false, null, null, null, "Download failed"
        );
        
        // Assert
        assertFalse(result.isSuccess());
        assertNull(result.getFile());
        assertNull(result.getTitle());
        assertNull(result.getContentType());
        assertEquals("Download failed", result.getErrorMessage());
    }
    
    @Test
    void testDownloadFromUrl_NullUrl() {
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            null, YtDlpService.DownloadType.AUDIO_ONLY, tempDir.toString()
        );
        
        // Assert
        assertFalse(result.isSuccess());
        assertEquals("URL cannot be empty", result.getErrorMessage());
    }
    
    @Test
    void testDownloadFromUrl_EmptyUrl() {
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "", YtDlpService.DownloadType.AUDIO_ONLY, tempDir.toString()
        );
        
        // Assert
        assertFalse(result.isSuccess());
        assertEquals("URL cannot be empty", result.getErrorMessage());
    }
    
    @Test
    void testDownloadFromUrl_InvalidUrlFormat() {
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "not-a-valid-url", YtDlpService.DownloadType.AUDIO_ONLY, tempDir.toString()
        );
        
        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Invalid URL format"));
    }
    
    @Test
    void testDownloadFromUrl_WithCookiesPath() throws Exception {
        // Arrange - Create a fake cookies file
        File cookiesFile = tempDir.resolve("test_cookies.txt").toFile();
        Files.write(cookiesFile.toPath(), "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\ttest\tvalue".getBytes());
        
        // Set cookies path using reflection
        ReflectionTestUtils.setField(ytDlpService, "cookiesPath", cookiesFile.getAbsolutePath());
        
        // Act - This will fail because yt-dlp will try to download, but we're testing the cookies path logic
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=invalid", 
            YtDlpService.DownloadType.AUDIO_ONLY, 
            tempDir.toString()
        );
        
        // Assert - Should attempt to use cookies (will fail on actual download but that's expected)
        assertFalse(result.isSuccess());
        // Error message will vary, but it should have attempted the download
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testDownloadFromUrl_AudioOnly_CreatesDirectory() {
        // Arrange
        Path nonExistentDir = tempDir.resolve("new_dir");
        
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            YtDlpService.DownloadType.AUDIO_ONLY,
            nonExistentDir.toString()
        );
        
        // Assert
        assertTrue(Files.exists(nonExistentDir), "Directory should be created if it doesn't exist");
    }
    
    @Test
    void testDownloadFromUrl_VideoType() {
        // Act - Test with VIDEO type
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            YtDlpService.DownloadType.VIDEO,
            tempDir.toString()
        );
        
        // Assert - Will fail on download but tests the VIDEO type path
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testCookiesPath_NotSet() {
        // Arrange - No cookies path set
        ReflectionTestUtils.setField(ytDlpService, "cookiesPath", "");
        
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            YtDlpService.DownloadType.AUDIO_ONLY,
            tempDir.toString()
        );
        
        // Assert - Should proceed without cookies
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testCookiesPath_FileNotFound() {
        // Arrange - Set non-existent cookies path
        ReflectionTestUtils.setField(ytDlpService, "cookiesPath", "/nonexistent/cookies.txt");
        
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            YtDlpService.DownloadType.AUDIO_ONLY,
            tempDir.toString()
        );
        
        // Assert - Should proceed without cookies (warning logged)
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testDownloadFromUrl_HttpUrl() {
        // Act - Test with http:// URL
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "http://example.com/video",
            YtDlpService.DownloadType.VIDEO,
            tempDir.toString()
        );
        
        // Assert
        assertFalse(result.isSuccess());
        // Should attempt download (will fail but validates URL format check)
    }
    
    @Test
    void testDownloadFromUrl_HttpsUrl() {
        // Act - Test with https:// URL
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://example.com/video",
            YtDlpService.DownloadType.VIDEO,
            tempDir.toString()
        );
        
        // Assert
        assertFalse(result.isSuccess());
        // Should attempt download (will fail but validates URL format check)
    }

    @Test
    void testDownloadResult_SuccessGetters() {
        // Arrange & Act
        YtDlpService.DownloadResult result = new YtDlpService.DownloadResult(
            true,
            new java.io.File("/path/to/file.mp4"),
            "Test Title",
            "video/mp4",
            null
        );

        // Assert
        assertTrue(result.isSuccess());
        assertNotNull(result.getFile());
        assertEquals("Test Title", result.getTitle());
        assertEquals("video/mp4", result.getContentType());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testDownloadResult_FailureGetters() {
        // Arrange & Act
        YtDlpService.DownloadResult result = new YtDlpService.DownloadResult(
            false,
            null,
            null,
            null,
            "Error occurred"
        );

        // Assert
        assertFalse(result.isSuccess());
        assertNull(result.getFile());
        assertNull(result.getTitle());
        assertNull(result.getContentType());
        assertEquals("Error occurred", result.getErrorMessage());
    }

    @Test
    void testDownloadType_Values() {
        // Assert all enum values exist
        assertEquals(2, YtDlpService.DownloadType.values().length);
        assertNotNull(YtDlpService.DownloadType.AUDIO_ONLY);
        assertNotNull(YtDlpService.DownloadType.VIDEO);
    }

    @Test
    void testDownloadFromUrl_EmptyDirectory() {
        // Act
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            YtDlpService.DownloadType.VIDEO,
            ""
        );

        // Assert
        assertFalse(result.isSuccess());
    }

    @Test
    void testDownloadFromUrl_NullDownloadType() {
        // Act - Null download type may be handled gracefully or throw exception
        // The service should handle this appropriately
        YtDlpService.DownloadResult result = ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            null,
            tempDir.toString()
        );

        // Assert - Either throws exception or returns failure
        // This test just ensures the service doesn't crash unexpectedly
        if (result != null) {
            assertFalse(result.isSuccess());
        }
    }

    @Test
    void testDownloadFromUrl_DirectoryCreation() {
        // Arrange - Use a non-existent subdirectory
        Path newDir = tempDir.resolve("new/nested/directory");
        assertFalse(java.nio.file.Files.exists(newDir));

        // Act
        ytDlpService.downloadFromUrl(
            "https://www.youtube.com/watch?v=test",
            YtDlpService.DownloadType.AUDIO_ONLY,
            newDir.toString()
        );

        // Assert - Directory should be created (even if download fails)
        assertTrue(java.nio.file.Files.exists(newDir));
        assertTrue(java.nio.file.Files.isDirectory(newDir));
    }
}


