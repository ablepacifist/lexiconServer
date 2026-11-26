package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.object.MediaFile;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.VideoTranscodingService;
import lexicon.service.YtDlpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deleting a media file now removes the stored file too, so the disk space actually comes back. */
class MediaDeleteFreesDiskTest {

    private IMediaDatabase mediaDb;
    private OptimizedFileStorageService storage;
    private MediaManager manager;
    private MediaFile file;

    @BeforeEach
    void setUp() throws Exception {
        mediaDb = mock(IMediaDatabase.class);
        storage = mock(OptimizedFileStorageService.class);
        manager = new MediaManager(mock(ILexiconDatabase.class), mediaDb, mock(YtDlpService.class),
                mock(VideoTranscodingService.class), storage);
        file = new MediaFile(10, "f", "book.mp3", "audio/mpeg", 500, "audiobooks/book.mp3", 2, "Book", "", false);
        when(mediaDb.getMediaFile(10)).thenReturn(file);
        when(storage.getFileSize("audiobooks/book.mp3")).thenReturn(833_542_795L);
        when(storage.deleteFile("audiobooks/book.mp3")).thenReturn(true);
    }

    @Test
    void adminDeleteRemovesTheFileAndReportsItsRealSize() {
        when(mediaDb.countMediaFilesWithPath("audiobooks/book.mp3")).thenReturn(1);
        assertEquals(833_542_795L, manager.deleteMediaFileAsAdmin(10));
        verify(storage).deleteFile("audiobooks/book.mp3");
        verify(mediaDb).deleteMediaFile(10);
    }

    @Test
    void aFileSharedWithAnotherRowIsKept() {
        when(mediaDb.countMediaFilesWithPath("audiobooks/book.mp3")).thenReturn(2);
        assertEquals(0, manager.deleteMediaFileAsAdmin(10));
        verify(storage, never()).deleteFile(any());
        verify(mediaDb).deleteMediaFile(10);
    }

    @Test
    void theOwnersOwnDeleteFreesTheDiskToo() {
        when(mediaDb.countMediaFilesWithPath("audiobooks/book.mp3")).thenReturn(1);
        assertTrue(manager.deleteMediaFile(10, 2));
        verify(storage).deleteFile("audiobooks/book.mp3");
    }

    @Test
    void someoneElsesFileIsStillRefusedForAnOwnerDelete() {
        assertFalse(manager.deleteMediaFile(10, 3));
        verify(storage, never()).deleteFile(any());
        verify(mediaDb, never()).deleteMediaFile(anyInt());
    }

    @Test
    void anUnknownFileIsMinusOne() {
        assertEquals(-1, manager.deleteMediaFileAsAdmin(404));
    }
}
