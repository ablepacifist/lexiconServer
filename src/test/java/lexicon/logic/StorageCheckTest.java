package lexicon.logic;

import lexicon.config.StorageProperties;
import lexicon.data.IChatFileDatabase;
import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.data.IMobileTokenDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.data.IRememberMeDatabase;
import lexicon.object.ChatFile;
import lexicon.object.MediaFile;
import lexicon.object.Permission;
import lexicon.service.OptimizedFileStorageService;
import lexicon.service.VideoTranscodingService;
import lexicon.service.YtDlpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The storage check and real deletes, against files in a temp folder (never the real drive).
 * The database side is mocked; the disk side is the real storage service.
 */
class StorageCheckTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final int ADMIN = 1;

    @TempDir Path base;
    private IMediaDatabase mediaDb;
    private IChatFileDatabase chatDb;
    private OptimizedFileStorageService storage;
    private UserAdminManager admin;
    private final List<MediaFile> rows = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties props = new StorageProperties();
        props.setBasePath(base.toString());
        storage = new OptimizedFileStorageService();
        var field = OptimizedFileStorageService.class.getDeclaredField("storageProperties");
        field.setAccessible(true);
        field.set(storage, props);

        mediaDb = mock(IMediaDatabase.class);
        chatDb = mock(IChatFileDatabase.class);
        when(mediaDb.getAllMediaFiles()).thenAnswer(inv -> rows);
        ChatFile chat = new ChatFile();
        chat.setId(1);
        chat.setStoredFilename("x.png");
        chat.setThumbnailFilename("thumb_x.jpg");
        when(chatDb.getAllChatFiles()).thenReturn(List.of(chat));

        PermissionManagerService permissions = mock(PermissionManagerService.class);
        when(permissions.hasPermission(ADMIN, Permission.MANAGE_USERS)).thenReturn(true);
        admin = new UserAdminManager(permissions, mock(PlayerManagerService.class), mock(MediaManagerService.class),
                mock(ChatFileManagerService.class), mock(ILexiconDatabase.class), mediaDb, chatDb,
                mock(IPlaylistDatabase.class), mock(IMobileTokenDatabase.class), mock(IRememberMeDatabase.class),
                storage, Clock.fixed(NOW, ZoneId.of("UTC")));

        file("audiobooks/large/20261029_new_moon.mp3", 4000, true);
        file("videos/original/20260101_film.mp4", 9000, true);
        file("music/compressed/20250101_forgotten.mp3", 3000, true);   // nobody points at it
        file("music/compressed/20261125_just_uploaded.mp3", 100, false); // nobody yet, but brand new
        file("chat-uploads/x.png", 50, true);
        file("chat-uploads/thumbnails/thumb_x.jpg", 10, true);
        file("chat-uploads/stray.png", 70, true);                    // nobody points at it
        file("temp/chunks/abc/chunk_0", 500, true);                 // never checked
        file("torrents/complete/show.mkv", 800, true);              // never checked

        rows.add(media(10, "audiobooks\\large\\20261029_new_moon.mp3"));                         // Windows separators
        rows.add(media(11, "//wsl.localhost/Ubuntu/mnt/x/lexicon-storage/videos/original/20260101_film.mp4")); // full path
        rows.add(media(12, ""));                                                                 // kept in the database
    }

    private void file(String rel, int size, boolean old) throws Exception {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[size]);
        Files.setLastModifiedTime(p, FileTime.from(old ? NOW.minusSeconds(30L * 86400) : NOW.minusSeconds(3600)));
    }

    private static MediaFile media(int id, String path) {
        return new MediaFile(id, "f", "f", "audio/mpeg", 1, path, 2, "Upload " + id, "", false);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> r, String key) {
        return (List<Map<String, Object>>) r.get(key);
    }

    @Test
    void findsOnlyFilesNoRowPointsAt() {
        Map<String, Object> r = admin.storageCheck(ADMIN);
        assertEquals(200, r.get("status"));
        assertEquals(List.of("music/compressed/20250101_forgotten.mp3", "chat-uploads/stray.png"),
                list(r, "orphans").stream().map(o -> o.get("path")).toList(), "largest first");
        assertEquals(3070L, r.get("orphanBytes"));
        assertEquals(1, r.get("recentSkipped"), "the brand-new upload is left alone");
        assertTrue(list(r, "missing").isEmpty());
        assertEquals(true, r.get("canClean"));
        assertEquals(7, r.get("scannedFiles"), "temp/ and torrents/ are not scanned");
    }

    @Test
    void cleaningDeletesOnlyStillUnusedFilesInsideTheCheckedFolders() {
        Map<String, Object> r = admin.cleanStorage(ADMIN, List.of(
                "music/compressed/20250101_forgotten.mp3",
                "audiobooks/large/20261029_new_moon.mp3",   // in use: refused
                "music/compressed/20261125_just_uploaded.mp3", // too new: refused
                "temp/chunks/abc/chunk_0",                  // not a checked folder: refused
                "../outside.txt"));
        assertEquals(1, r.get("deleted"));
        assertEquals(3000L, r.get("freedBytes"));
        assertEquals(4, r.get("skipped"));
        assertFalse(Files.exists(base.resolve("music/compressed/20250101_forgotten.mp3")));
        assertTrue(Files.exists(base.resolve("audiobooks/large/20261029_new_moon.mp3")));
        assertTrue(Files.exists(base.resolve("music/compressed/20261125_just_uploaded.mp3")));
        assertTrue(Files.exists(base.resolve("temp/chunks/abc/chunk_0")));
    }

    @Test
    void aFileThatGainsARowAfterTheScanIsNotDeleted() {
        admin.storageCheck(ADMIN);
        rows.add(media(13, "chat-uploads/stray.png"));
        Map<String, Object> r = admin.cleanStorage(ADMIN, List.of("chat-uploads/stray.png"));
        assertEquals(0, r.get("deleted"));
        assertTrue(Files.exists(base.resolve("chat-uploads/stray.png")));
    }

    @Test
    void manyRowsWithoutFilesBlocksAnyDeleting() {
        for (int i = 0; i < 5; i++) rows.add(media(100 + i, "videos/original/gone_" + i + ".mp4"));
        Map<String, Object> r = admin.storageCheck(ADMIN);
        assertEquals(5, list(r, "missing").size());
        assertEquals(false, r.get("canClean"));
        assertNotNull(r.get("cleanBlockedReason"));

        Map<String, Object> clean = admin.cleanStorage(ADMIN, List.of("music/compressed/20250101_forgotten.mp3"));
        assertEquals(409, clean.get("status"));
        assertTrue(Files.exists(base.resolve("music/compressed/20250101_forgotten.mp3")));
    }

    @Test
    void nonAdminsCannotScanOrClean() {
        assertEquals(403, admin.storageCheck(2).get("status"));
        assertEquals(403, admin.cleanStorage(2, List.of("chat-uploads/stray.png")).get("status"));
        assertTrue(Files.exists(base.resolve("chat-uploads/stray.png")));
    }

    @Test
    void pathsAreMatchedHoweverTheRowWroteThem() {
        assertEquals("audiobooks/large/a.mp3", UserAdminManager.storageRelative("audiobooks\\large\\a.mp3"));
        assertEquals("videos/original/b.mp4", UserAdminManager.storageRelative("/mnt/wsl/X/lexicon-storage/videos/original/b.mp4"));
        assertEquals("music/c.mp3", UserAdminManager.storageRelative("./music/c.mp3"));
        assertNull(UserAdminManager.storageRelative(""));
        assertNull(UserAdminManager.storageRelative(null));
    }

    /** A real delete through MediaManager removes the file from disk (it used to stay there). */
    @Test
    void deletingAMediaFileReallyRemovesItFromTheDrive() {
        MediaManager mediaManager = new MediaManager(mock(ILexiconDatabase.class), mediaDb, mock(YtDlpService.class),
                mock(VideoTranscodingService.class), storage);
        MediaFile book = rows.get(0);
        when(mediaDb.getMediaFile(10)).thenReturn(book);
        when(mediaDb.countMediaFilesWithPath(book.getFilePath())).thenReturn(1);

        assertEquals(4000L, mediaManager.deleteMediaFileAsAdmin(10));
        assertFalse(Files.exists(base.resolve("audiobooks/large/20261029_new_moon.mp3")));
        verify(mediaDb).deleteMediaFile(10);
    }
}
