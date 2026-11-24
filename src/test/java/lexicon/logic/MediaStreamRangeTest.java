package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.object.MediaFile;
import lexicon.object.StreamResult;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.VideoTranscodingService;
import lexicon.service.YtDlpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Range requests for files on disk must return the whole requested range.
 *
 * They used to be cut to 2 MB. ExoPlayer (the Android app) takes the end of a response as
 * the end of the media, so an audiobook resumed mid-way was "finished" after ~4 minutes and
 * the playlist skipped to the next book; songs and videos did the same after a seek.
 */
class MediaStreamRangeTest {

    private static final int SIZE = 5 * 1024 * 1024 + 123; // well over the old 2 MB cap
    private static final String PATH = "audiobooks/large/book.mp3";

    private final IMediaDatabase mediaDatabase = mock(IMediaDatabase.class);
    private final OptimizedFileStorageService storage = mock(OptimizedFileStorageService.class);
    private final byte[] file = new byte[SIZE];
    private final AtomicBoolean closed = new AtomicBoolean();
    private MediaManager mediaManager;

    @BeforeEach
    void setUp() throws IOException {
        for (int i = 0; i < SIZE; i++) file[i] = (byte) (i * 31 + 7);
        MediaFile media = new MediaFile(826, "book.mp3", "book.mp3", "audio/mpeg", SIZE, PATH, 1, "Book", "", true);
        when(mediaDatabase.getMediaFile(826)).thenReturn(media);
        when(storage.getFileSize(PATH)).thenReturn((long) SIZE);
        when(storage.getFileInputStream(PATH)).thenAnswer(inv -> new ByteArrayInputStream(file) {
            @Override public void close() throws IOException { closed.set(true); super.close(); }
        });
        mediaManager = new MediaManager(mock(ILexiconDatabase.class), mediaDatabase,
                mock(YtDlpService.class), mock(VideoTranscodingService.class), storage);
    }

    @Test
    void openEndedRangeFromTheMiddleStreamsToTheEndOfTheFile() throws IOException {
        long start = 2_097_153; // a resume point, like "bytes=208733001-"
        StreamResult result = mediaManager.getStreamData(826, "bytes=" + start + "-");

        assertEquals(start, result.getStart());
        assertEquals(SIZE - 1, result.getEnd(), "must run to the last byte, not stop 2 MB later");
        assertEquals(SIZE - start, result.getContentLength());
        assertTrue(result.isPartialContent());
        assertNull(result.getData(), "large ranges are streamed, not held in memory");

        byte[] sent;
        try (InputStream in = result.getStream()) {
            sent = in.readAllBytes();
        }
        assertArrayEquals(Arrays.copyOfRange(file, (int) start, SIZE), sent);
        assertTrue(closed.get(), "closing the response stream closes the file");
    }

    @Test
    void boundedRangeReturnsExactlyThoseBytes() throws IOException {
        StreamResult result = mediaManager.getStreamData(826, "bytes=100-199");

        assertEquals(100, result.getContentLength());
        try (InputStream in = result.getStream()) {
            assertArrayEquals(Arrays.copyOfRange(file, 100, 200), in.readAllBytes());
        }
    }

    @Test
    void rangeEndPastTheFileIsClampedToTheLastByte() throws IOException {
        StreamResult result = mediaManager.getStreamData(826, "bytes=10-999999999");

        assertEquals(SIZE - 1, result.getEnd());
        try (InputStream in = result.getStream()) {
            assertEquals(SIZE - 10, in.readAllBytes().length);
        }
    }

    @Test
    void startPastTheEndIsNotSatisfiable() {
        StreamResult result = mediaManager.getStreamData(826, "bytes=" + SIZE + "-");

        assertTrue(result.getStart() < 0, "controller answers 416 for a negative start");
        assertEquals(SIZE, result.getTotalSize());
    }

    @Test
    void wholeFileRangeStreamsEveryByte() throws IOException {
        StreamResult result = mediaManager.getStreamData(826, "bytes=0-");

        assertEquals(SIZE, result.getContentLength());
        try (InputStream in = result.getStream()) {
            assertEquals(SIZE, in.readAllBytes().length);
        }
    }
}
