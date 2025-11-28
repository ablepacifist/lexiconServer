package lexicon.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

/**
 * Unit tests for MediaFile domain object
 */
class MediaFileTest {
    
    private MediaFile mediaFile;
    
    @BeforeEach
    void setUp() {
        mediaFile = new MediaFile(
            1,
            "video123.mp4",
            "my-vacation.mp4",
            "video/mp4",
            1024000L,
            "/uploads/video123.mp4",
            100,
            "My Vacation",
            "Summer vacation 2025",
            true
        );
    }
    
    @Test
    void testMediaFileCreation() {
        assertNotNull(mediaFile);
        assertEquals(1, mediaFile.getId());
        assertEquals("video123.mp4", mediaFile.getFilename());
        assertEquals("my-vacation.mp4", mediaFile.getOriginalFilename());
        assertEquals("video/mp4", mediaFile.getContentType());
        assertEquals(1024000L, mediaFile.getFileSize());
        assertEquals("/uploads/video123.mp4", mediaFile.getFilePath());
        assertEquals(100, mediaFile.getUploadedBy());
        assertEquals("My Vacation", mediaFile.getTitle());
        assertEquals("Summer vacation 2025", mediaFile.getDescription());
        assertTrue(mediaFile.isPublic());
        assertNotNull(mediaFile.getUploadDate());
    }
    
    @Test
    void testSetters() {
        mediaFile.setId(2);
        mediaFile.setFilename("newfile.mp4");
        mediaFile.setOriginalFilename("original.mp4");
        mediaFile.setContentType("video/mpeg");
        mediaFile.setFileSize(2048000L);
        mediaFile.setFilePath("/new/path.mp4");
        mediaFile.setUploadedBy(200);
        mediaFile.setTitle("New Title");
        mediaFile.setDescription("New Description");
        mediaFile.setPublic(false);
        
        assertEquals(2, mediaFile.getId());
        assertEquals("newfile.mp4", mediaFile.getFilename());
        assertEquals("original.mp4", mediaFile.getOriginalFilename());
        assertEquals("video/mpeg", mediaFile.getContentType());
        assertEquals(2048000L, mediaFile.getFileSize());
        assertEquals("/new/path.mp4", mediaFile.getFilePath());
        assertEquals(200, mediaFile.getUploadedBy());
        assertEquals("New Title", mediaFile.getTitle());
        assertEquals("New Description", mediaFile.getDescription());
        assertFalse(mediaFile.isPublic());
    }
    
    @Test
    void testEmptyConstructor() {
        MediaFile emptyFile = new MediaFile();
        assertNotNull(emptyFile);
        assertEquals(0, emptyFile.getId());
    }
    
    @Test
    void testUploadDateIsSet() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        MediaFile newFile = new MediaFile(
            5, "test.mp4", "test.mp4", "video/mp4",
            1000L, "/test", 1, "Test", "Test", true
        );
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        
        assertNotNull(newFile.getUploadDate());
        assertTrue(newFile.getUploadDate().isAfter(before));
        assertTrue(newFile.getUploadDate().isBefore(after));
    }
    
    @Test
    void testVideoContentType() {
        MediaFile video = new MediaFile(
            1, "vid.mp4", "vid.mp4", "video/mp4",
            1000L, "/path", 1, "Video", "Desc", true
        );
        assertTrue(video.getContentType().startsWith("video/"));
    }
    
    @Test
    void testAudioContentType() {
        MediaFile audio = new MediaFile(
            2, "song.mp3", "song.mp3", "audio/mpeg",
            500000L, "/path", 1, "Song", "Desc", true
        );
        assertTrue(audio.getContentType().startsWith("audio/"));
    }
    
    @Test
    void testPrivateMediaFile() {
        MediaFile privateFile = new MediaFile(
            3, "private.mp4", "private.mp4", "video/mp4",
            1000L, "/path", 1, "Private", "Private content", false
        );
        assertFalse(privateFile.isPublic());
    }
    
    @Test
    void testNullDescription() {
        MediaFile fileWithoutDesc = new MediaFile(
            4, "test.mp4", "test.mp4", "video/mp4",
            1000L, "/path", 1, "Title", null, true
        );
        assertNull(fileWithoutDesc.getDescription());
    }
}
