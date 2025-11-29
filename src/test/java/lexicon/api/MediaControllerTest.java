package lexicon.api;

import lexicon.logic.MediaManagerService;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaController
 * Tests REST API endpoints for media management
 */
class MediaControllerTest {
    
    @Mock
    private MediaManagerService mediaManager;
    
    @Mock
    private MultipartFile mockFile;
    
    private MediaController mediaController;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mediaController = new MediaController();
        // Inject mock via reflection
        try {
            var field = MediaController.class.getDeclaredField("mediaManager");
            field.setAccessible(true);
            field.set(mediaController, mediaManager);
        } catch (Exception e) {
            fail("Failed to inject mock: " + e.getMessage());
        }
    }
    
    @Test
    void testUploadFile_Success() {
        // Arrange
        MediaFile mockMediaFile = new MediaFile();
        mockMediaFile.setId(1);
        mockMediaFile.setTitle("Test Song");
        mockMediaFile.setMediaType(MediaType.MUSIC);
        
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), anyBoolean(), anyString()))
            .thenReturn(mockMediaFile);
        
        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFile(
            mockFile, 16, "Test Song", "A test", true, "MUSIC"
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("File uploaded successfully", response.getBody().get("message"));
        assertEquals(mockMediaFile, response.getBody().get("mediaFile"));
        
        verify(mediaManager).uploadMediaFile(mockFile, 16, "Test Song", "A test", true, "MUSIC");
    }
    
    @Test
    void testUploadFile_IllegalArgument() {
        // Arrange
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), anyBoolean(), anyString()))
            .thenThrow(new IllegalArgumentException("Title cannot be empty"));
        
        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFile(
            mockFile, 16, "", "Desc", true, "MUSIC"
        );
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("Title cannot be empty"));
    }
    
    @Test
    void testUploadFile_ServerError() {
        // Arrange
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), anyBoolean(), anyString()))
            .thenThrow(new RuntimeException("Database error"));
        
        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFile(
            mockFile, 16, "Title", "Desc", true, "MUSIC"
        );
        
        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("Failed to upload file"));
    }
    
    @Test
    void testUploadFromUrl_Success() {
        // Arrange
        MediaFile mockMediaFile = new MediaFile();
        mockMediaFile.setId(2);
        mockMediaFile.setTitle("YouTube Video");
        mockMediaFile.setSourceUrl("https://youtube.com/test");
        
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), anyString(), anyString(), anyBoolean(), anyString(), anyString()))
            .thenReturn(mockMediaFile);
        
        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "https://youtube.com/test", 16, "YouTube Video", "A video", true, "VIDEO", "VIDEO"
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("Media downloaded and uploaded successfully", response.getBody().get("message"));
        assertEquals(mockMediaFile, response.getBody().get("mediaFile"));
        
        verify(mediaManager).uploadMediaFromUrl("https://youtube.com/test", 16, "YouTube Video", "A video", true, "VIDEO", "VIDEO");
    }
    
    @Test
    void testUploadFromUrl_InvalidUrl() {
        // Arrange
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), anyString(), anyString(), anyBoolean(), anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid URL"));
        
        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "invalid-url", 16, "Title", "Desc", true, "VIDEO", "VIDEO"
        );
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("Invalid URL"));
    }
    
    @Test
    void testUploadFromUrl_DownloadFails() {
        // Arrange
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), anyString(), anyString(), anyBoolean(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Download failed: YouTube error"));
        
        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "https://youtube.com/test", 16, "Title", "Desc", true, "VIDEO", "VIDEO"
        );
        
        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("Failed to download from URL"));
    }
    
    @Test
    void testUploadFromUrl_DefaultDownloadType() {
        // Arrange
        MediaFile mockMediaFile = new MediaFile();
        mockMediaFile.setId(3);
        
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), anyString(), anyString(), anyBoolean(), anyString(), anyString()))
            .thenReturn(mockMediaFile);
        
        // Act - Not providing downloadType should default to AUDIO_ONLY
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "https://youtube.com/test", 16, "Title", null, false, "MUSIC", "AUDIO_ONLY"
        );
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mediaManager).uploadMediaFromUrl(
            eq("https://youtube.com/test"), eq(16), eq("Title"), isNull(), eq(false), eq("MUSIC"), eq("AUDIO_ONLY")
        );
    }
    
    @Test
    void testGetMediaFile_Found() {
        // Arrange
        MediaFile mockFile = new MediaFile();
        mockFile.setId(10);
        mockFile.setTitle("Found File");
        when(mediaManager.getMediaFileById(10)).thenReturn(mockFile);
        
        // Act
        ResponseEntity<MediaFile> response = mediaController.getMediaFile(10);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().getId());
        assertEquals("Found File", response.getBody().getTitle());
        verify(mediaManager).getMediaFileById(10);
    }
    
    @Test
    void testGetMediaFile_NotFound() {
        // Arrange
        when(mediaManager.getMediaFileById(999)).thenReturn(null);
        
        // Act
        ResponseEntity<MediaFile> response = mediaController.getMediaFile(999);
        
        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(mediaManager).getMediaFileById(999);
    }
    
    @Test
    void testGetMediaFilesByUser() {
        // Arrange
        List<MediaFile> mockFiles = new ArrayList<>();
        MediaFile file1 = new MediaFile();
        file1.setId(1);
        mockFiles.add(file1);
        
        when(mediaManager.getMediaFilesByUser(16)).thenReturn(mockFiles);
        
        // Act
        ResponseEntity<List<MediaFile>> response = mediaController.getMediaFilesByUser(16);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(mediaManager).getMediaFilesByUser(16);
    }
    
    @Test
    void testGetMediaFilesByUser_EmptyList() {
        // Arrange
        when(mediaManager.getMediaFilesByUser(999)).thenReturn(new ArrayList<>());
        
        // Act
        ResponseEntity<List<MediaFile>> response = mediaController.getMediaFilesByUser(999);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
    
    @Test
    void testGetPublicMediaFiles() {
        // Arrange
        List<MediaFile> mockFiles = new ArrayList<>();
        MediaFile file1 = new MediaFile();
        file1.setId(1);
        file1.setPublic(true);
        mockFiles.add(file1);
        
        MediaFile file2 = new MediaFile();
        file2.setId(2);
        file2.setPublic(true);
        mockFiles.add(file2);
        
        when(mediaManager.getAllPublicMediaFiles()).thenReturn(mockFiles);
        
        // Act
        ResponseEntity<List<MediaFile>> response = mediaController.getPublicMediaFiles();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().get(0).isPublic());
        assertTrue(response.getBody().get(1).isPublic());
        verify(mediaManager).getAllPublicMediaFiles();
    }
    
    @Test
    void testGetPublicMediaFiles_Empty() {
        // Arrange
        when(mediaManager.getAllPublicMediaFiles()).thenReturn(new ArrayList<>());
        
        // Act
        ResponseEntity<List<MediaFile>> response = mediaController.getPublicMediaFiles();
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
    
    @Test
    void testUploadFile_WithDifferentMediaTypes() {
        // Test MUSIC
        MediaFile musicFile = new MediaFile();
        musicFile.setMediaType(MediaType.MUSIC);
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), anyBoolean(), eq("MUSIC")))
            .thenReturn(musicFile);
        
        ResponseEntity<Map<String, Object>> musicResponse = mediaController.uploadFile(
            mockFile, 16, "Song", "Desc", true, "MUSIC"
        );
        assertEquals(HttpStatus.OK, musicResponse.getStatusCode());
        
        // Test VIDEO
        MediaFile videoFile = new MediaFile();
        videoFile.setMediaType(MediaType.VIDEO);
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), anyBoolean(), eq("VIDEO")))
            .thenReturn(videoFile);
        
        ResponseEntity<Map<String, Object>> videoResponse = mediaController.uploadFile(
            mockFile, 16, "Video", "Desc", true, "VIDEO"
        );
        assertEquals(HttpStatus.OK, videoResponse.getStatusCode());
        
        // Test AUDIOBOOK
        MediaFile audiobookFile = new MediaFile();
        audiobookFile.setMediaType(MediaType.AUDIOBOOK);
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), anyBoolean(), eq("AUDIOBOOK")))
            .thenReturn(audiobookFile);
        
        ResponseEntity<Map<String, Object>> audiobookResponse = mediaController.uploadFile(
            mockFile, 16, "Book", "Desc", true, "AUDIOBOOK"
        );
        assertEquals(HttpStatus.OK, audiobookResponse.getStatusCode());
    }

    @Test
    void testUploadFile_WithNullTitle() {
        // Arrange
        MediaFile mediaFile = new MediaFile();
        when(mediaManager.uploadMediaFile(any(), anyInt(), isNull(), anyString(), anyBoolean(), anyString()))
            .thenReturn(mediaFile);

        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFile(
            mockFile, 16, null, "Description", true, "MUSIC"
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUploadFile_WithNullDescription() {
        // Arrange
        MediaFile mediaFile = new MediaFile();
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), isNull(), anyBoolean(), anyString()))
            .thenReturn(mediaFile);

        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFile(
            mockFile, 16, "Title", null, true, "MUSIC"
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUploadFile_PrivateFile() {
        // Arrange
        MediaFile mediaFile = new MediaFile();
        when(mediaManager.uploadMediaFile(any(), anyInt(), anyString(), anyString(), eq(false), anyString()))
            .thenReturn(mediaFile);

        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFile(
            mockFile, 16, "Title", "Description", false, "MUSIC"
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUploadFromUrl_WithNullTitle() {
        // Arrange
        MediaFile mediaFile = new MediaFile();
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), isNull(), anyString(), anyBoolean(), anyString(), anyString()))
            .thenReturn(mediaFile);

        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "https://example.com/video", 16, null, "Description", true, "VIDEO", "VIDEO"
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUploadFromUrl_WithNullDescription() {
        // Arrange
        MediaFile mediaFile = new MediaFile();
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), anyString(), isNull(), anyBoolean(), anyString(), anyString()))
            .thenReturn(mediaFile);

        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "https://example.com/video", 16, "Title", null, true, "VIDEO", "VIDEO"
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUploadFromUrl_AudioDownloadType() {
        // Arrange
        MediaFile mediaFile = new MediaFile();
        when(mediaManager.uploadMediaFromUrl(anyString(), anyInt(), anyString(), anyString(), anyBoolean(), anyString(), eq("AUDIO_ONLY")))
            .thenReturn(mediaFile);

        // Act
        ResponseEntity<Map<String, Object>> response = mediaController.uploadFromUrl(
            "https://example.com/audio", 16, "Title", "Description", true, "MUSIC", "AUDIO_ONLY"
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mediaManager).uploadMediaFromUrl(
            eq("https://example.com/audio"), eq(16), eq("Title"), eq("Description"), 
            eq(true), eq("MUSIC"), eq("AUDIO_ONLY")
        );
    }

    @Test
    void testGetMediaFilesByUser_MultipleFiles() {
        // Arrange
        MediaFile file1 = new MediaFile();
        file1.setId(1);
        MediaFile file2 = new MediaFile();
        file2.setId(2);
        MediaFile file3 = new MediaFile();
        file3.setId(3);
        
        when(mediaManager.getMediaFilesByUser(16))
            .thenReturn(Arrays.asList(file1, file2, file3));

        // Act
        ResponseEntity<List<MediaFile>> response = mediaController.getMediaFilesByUser(16);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
    }
}

