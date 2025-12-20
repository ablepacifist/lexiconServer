package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.YtDlpService;
import lexicon.service.VideoTranscodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaManager
 * Tests all media file operations and business logic
 */
class MediaManagerTest {
    
    @Mock
    private ILexiconDatabase playerDatabase;
    
    @Mock
    private IMediaDatabase mediaDatabase;
    
    @Mock
    private YtDlpService ytDlpService;
    
    @Mock
    private VideoTranscodingService videoTranscodingService;
    
    @Mock
    private OptimizedFileStorageService fileStorageService;
    
    @Mock
    private MultipartFile mockFile;
    
    private MediaManager mediaManager;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mediaManager = new MediaManager(playerDatabase, mediaDatabase, ytDlpService, videoTranscodingService, fileStorageService);
    }
    
    @Test
    void testUploadMediaFile_Success() throws IOException {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("test.mp3");
        when(mockFile.getContentType()).thenReturn("audio/mpeg");
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getBytes()).thenReturn(new byte[]{1, 2, 3, 4});
        when(mediaDatabase.getNextMediaFileId()).thenReturn(1);
        when(fileStorageService.storeFile(any(MultipartFile.class), any(MediaFile.class)))
            .thenReturn("/storage/music/test.mp3");
        
        // Act
        MediaFile result = mediaManager.uploadMediaFile(mockFile, 16, "Test Song", "A test", true, "MUSIC");
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("Test Song", result.getTitle());
        assertEquals(MediaType.MUSIC, result.getMediaType());
        assertEquals("/storage/music/test.mp3", result.getFilePath());
        verify(mediaDatabase).addMediaFile(any(MediaFile.class));
        verify(fileStorageService).storeFile(eq(mockFile), any(MediaFile.class));
    }
    
    @Test
    void testUploadMediaFile_NullFile() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mediaManager.uploadMediaFile(null, 16, "Test", "Desc", true, "MUSIC");
        });
    }
    
    @Test
    void testUploadMediaFile_EmptyFile() {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(true);
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mediaManager.uploadMediaFile(mockFile, 16, "Test", "Desc", true, "MUSIC");
        });
    }
    
    @Test
    void testUploadMediaFile_EmptyTitle() throws IOException {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getBytes()).thenReturn(new byte[]{1, 2, 3});
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mediaManager.uploadMediaFile(mockFile, 16, "", "Desc", true, "MUSIC");
        });
    }
    
    @Test
    void testUploadMediaFromUrl_Success() throws IOException {
        // Arrange
        String testUrl = "https://youtube.com/watch?v=test";
        
        // Create a real temp file for testing
        File tempFile = File.createTempFile("test_download", ".mp3");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), new byte[]{1, 2, 3, 4});
        
        YtDlpService.DownloadResult mockResult = new YtDlpService.DownloadResult(
            true, tempFile, "Downloaded Song", "audio/mpeg", null
        );
        
        when(ytDlpService.downloadFromUrl(anyString(), any(), anyString())).thenReturn(mockResult);
        when(mediaDatabase.getNextMediaFileId()).thenReturn(5);
        
        // Act
        MediaFile result = mediaManager.uploadMediaFromUrl(
            testUrl, 16, "My Title", "Description", true, "MUSIC", "AUDIO_ONLY"
        );
        
        // Assert
        assertNotNull(result);
        assertEquals(5, result.getId());
        assertEquals("My Title", result.getTitle());
        assertEquals(testUrl, result.getSourceUrl());
        assertEquals(MediaType.MUSIC, result.getMediaType());
        verify(ytDlpService).downloadFromUrl(eq(testUrl), any(), anyString());
        verify(mediaDatabase).addMediaFile(any(MediaFile.class));
        verify(mediaDatabase).storeFileData(eq(5), any(byte[].class));
    }
    
    @Test
    void testUploadMediaFromUrl_EmptyUrl() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mediaManager.uploadMediaFromUrl("", 16, "Title", "Desc", true, "MUSIC", "AUDIO_ONLY");
        });
    }
    
    @Test
    void testUploadMediaFromUrl_EmptyTitle() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            mediaManager.uploadMediaFromUrl("https://youtube.com/test", 16, "", "Desc", true, "MUSIC", "AUDIO_ONLY");
        });
    }
    
    @Test
    void testUploadMediaFromUrl_DownloadFails() {
        // Arrange
        YtDlpService.DownloadResult mockResult = new YtDlpService.DownloadResult(
            false, null, null, null, "Download failed: Invalid URL"
        );
        when(ytDlpService.downloadFromUrl(anyString(), any(), anyString())).thenReturn(mockResult);
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            mediaManager.uploadMediaFromUrl("https://bad-url.com", 16, "Title", "Desc", true, "VIDEO", "VIDEO");
        });
        assertTrue(exception.getMessage().contains("Download failed"));
    }
    
    @Test
    void testGetMediaFileById() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(10);
        mockFile.setTitle("Test Media");
        when(mediaDatabase.getMediaFile(10)).thenReturn(mockFile);
        
        // Act
        MediaFile result = mediaManager.getMediaFileById(10);
        
        // Assert
        assertNotNull(result);
        assertEquals(10, result.getId());
        assertEquals("Test Media", result.getTitle());
        verify(mediaDatabase).getMediaFile(10);
    }
    
    @Test
    void testGetMediaFilesByUser() {
        // Arrange
        List<MediaFile> mockFiles = new ArrayList<>();
        MediaFile file1 = new MediaFile();
        file1.setId(1);
        file1.setTitle("File 1");
        mockFiles.add(file1);
        
        when(mediaDatabase.getMediaFilesByPlayer(16)).thenReturn(mockFiles);
        
        // Act
        List<MediaFile> result = mediaManager.getMediaFilesByUser(16);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("File 1", result.get(0).getTitle());
        verify(mediaDatabase).getMediaFilesByPlayer(16);
    }
    
    @Test
    void testGetAllPublicMediaFiles() {
        // Arrange
        List<MediaFile> mockFiles = new ArrayList<>();
        MediaFile file1 = new MediaFile();
        file1.setId(1);
        file1.setPublic(true);
        mockFiles.add(file1);
        
        when(mediaDatabase.getAllPublicMediaFiles()).thenReturn(mockFiles);
        
        // Act
        List<MediaFile> result = mediaManager.getAllPublicMediaFiles();
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isPublic());
        verify(mediaDatabase).getAllPublicMediaFiles();
    }
    
    @Test
    void testDeleteMediaFile_Success() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(5);
        mockFile.setUploadedBy(16);
        when(mediaDatabase.getMediaFile(5)).thenReturn(mockFile);
        
        // Act
        boolean result = mediaManager.deleteMediaFile(5, 16);
        
        // Assert
        assertTrue(result);
        verify(mediaDatabase).deleteMediaFile(5);
        verify(mediaDatabase, never()).deleteFileData(anyInt()); // deleteFileData is not called by deleteMediaFile
    }
    
    @Test
    void testDeleteMediaFile_Unauthorized() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(5);
        mockFile.setUploadedBy(16);
        when(mediaDatabase.getMediaFile(5)).thenReturn(mockFile);
        
        // Act
        boolean result = mediaManager.deleteMediaFile(5, 99); // Different user
        
        // Assert
        assertFalse(result);
        verify(mediaDatabase, never()).deleteMediaFile(anyInt());
    }
    
    @Test
    void testHasAccessPermission_Owner() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(5);
        mockFile.setUploadedBy(16);
        mockFile.setPublic(false);
        when(mediaDatabase.getMediaFile(5)).thenReturn(mockFile);
        
        // Act
        boolean result = mediaManager.hasAccessPermission(5, 16);
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void testHasAccessPermission_Public() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(5);
        mockFile.setUploadedBy(16);
        mockFile.setPublic(true);
        when(mediaDatabase.getMediaFile(5)).thenReturn(mockFile);
        
        // Act
        boolean result = mediaManager.hasAccessPermission(5, 99); // Different user
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void testHasAccessPermission_PrivateNotOwner() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(5);
        mockFile.setUploadedBy(16);
        mockFile.setPublic(false);
        when(mediaDatabase.getMediaFile(5)).thenReturn(mockFile);
        
        // Act
        boolean result = mediaManager.hasAccessPermission(5, 99); // Different user
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    void testGetFileData() {
        // Arrange
        byte[] mockData = new byte[]{1, 2, 3, 4, 5};
        when(mediaDatabase.getFileData(10)).thenReturn(mockData);
        
        // Act
        byte[] result = mediaManager.getFileData(10);
        
        // Assert
        assertNotNull(result);
        assertEquals(5, result.length);
        assertArrayEquals(mockData, result);
        verify(mediaDatabase).getFileData(10);
    }
}
