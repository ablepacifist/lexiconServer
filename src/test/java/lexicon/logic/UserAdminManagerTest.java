package lexicon.logic;

import lexicon.data.IChatFileDatabase;
import lexicon.data.ILexiconDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.data.IMobileTokenDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.data.IRememberMeDatabase;
import lexicon.object.ChatFile;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.object.Permission;
import lexicon.object.PlaybackPosition;
import lexicon.object.PlayerActivity;
import lexicon.object.Playlist;
import lexicon.service.OptimizedFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The admin user/storage page logic, with every store mocked: who may use it, what each row
 * says, sort orders, and what deleting an account removes (and refuses to).
 */
class UserAdminManagerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 12, 0);
    private static final int ADMIN = 1, BOB = 2, CAROL = 3, OTHER_ADMIN = 4;

    private PermissionManagerService permissions;
    private PlayerManagerService players;
    private MediaManagerService mediaManager;
    private ChatFileManagerService chatFiles;
    private ILexiconDatabase playerDb;
    private IMediaDatabase mediaDb;
    private IChatFileDatabase chatDb;
    private IPlaylistDatabase playlistDb;
    private IMobileTokenDatabase mobileTokens;
    private IRememberMeDatabase rememberMe;
    private OptimizedFileStorageService storage;
    private UserAdminManager admin;

    @BeforeEach
    void setUp() {
        permissions = mock(PermissionManagerService.class);
        players = mock(PlayerManagerService.class);
        mediaManager = mock(MediaManagerService.class);
        chatFiles = mock(ChatFileManagerService.class);
        playerDb = mock(ILexiconDatabase.class);
        mediaDb = mock(IMediaDatabase.class);
        chatDb = mock(IChatFileDatabase.class);
        playlistDb = mock(IPlaylistDatabase.class);
        mobileTokens = mock(IMobileTokenDatabase.class);
        rememberMe = mock(IRememberMeDatabase.class);
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        storage = mock(OptimizedFileStorageService.class);
        admin = new UserAdminManager(permissions, players, mediaManager, chatFiles, playerDb, mediaDb, chatDb,
                playlistDb, mobileTokens, rememberMe, storage, clock);

        when(permissions.hasPermission(ADMIN, Permission.MANAGE_USERS)).thenReturn(true);
        when(playerDb.getPlayerActivity()).thenReturn(List.of(
                new PlayerActivity(ADMIN, "zx", "ADMIN", NOW.minusDays(400), NOW.minusDays(2), NOW),
                new PlayerActivity(BOB, "bob", "USER", NOW.minusDays(300), NOW.minusDays(200), null),
                new PlayerActivity(CAROL, "carol", "USER", NOW.minusDays(10), null, NOW.minusDays(50)),
                new PlayerActivity(OTHER_ADMIN, "dm2", "ADMIN", null, null, null)));
        when(mediaDb.getAllMediaFiles()).thenReturn(List.of(
                media(10, BOB, 3L * 1024 * 1024 * 1024, NOW.minusDays(250), MediaType.AUDIOBOOK, "Twilight"),
                media(11, CAROL, 5L * 1024 * 1024, NOW.minusDays(5), MediaType.MUSIC, "Song"),
                media(12, 99, 700L * 1024 * 1024, NOW.minusDays(500), MediaType.VIDEO, "")));
        when(chatDb.getAllChatFiles()).thenReturn(List.of(chat(20, BOB, 2048, NOW.minusDays(100))));
    }

    private static MediaFile media(int id, int owner, long size, LocalDateTime at, MediaType type, String title) {
        MediaFile m = new MediaFile(id, "f" + id, "file" + id + ".mp3", "audio/mpeg", size, "path/" + id, owner, title, "", false);
        m.setUploadDate(at);
        m.setMediaType(type);
        return m;
    }

    private static ChatFile chat(long id, int owner, long size, LocalDateTime at) {
        ChatFile c = new ChatFile();
        c.setId(id);
        c.setUploadedBy(owner);
        c.setFileSize(size);
        c.setCreatedAt(at);
        c.setOriginalFilename("pic.png");
        return c;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result, String key) {
        return (List<Map<String, Object>>) result.get(key);
    }

    // ── access ────────────────────────────────────────────────

    @Test
    void nonAdminsGet403EverywhereAndNothingIsTouched() {
        assertEquals(403, admin.listUsers(BOB, null).get("status"));
        assertEquals(403, admin.listUploads(BOB, null, null).get("status"));
        assertEquals(403, admin.deleteUpload(BOB, "media", 10).get("status"));
        assertEquals(403, admin.deleteUser(BOB, CAROL, "carol").get("status"));
        assertEquals(403, admin.setPassword(BOB, CAROL, "longenough").get("status"));
        verifyNoInteractions(mediaManager, chatFiles, players, mobileTokens, rememberMe, playlistDb);
        verify(playerDb, never()).deletePlayerAccount(anyInt());
    }

    // ── account list ──────────────────────────────────────────

    @Test
    void accountsAreListedLongestUnusedFirstWithReadyToShowLabels() {
        Map<String, Object> result = admin.listUsers(ADMIN, null);
        assertEquals(200, result.get("status"));
        assertEquals("inactive", result.get("sort"));
        List<Map<String, Object>> users = rows(result, "users");
        assertEquals(List.of("dm2", "bob", "carol", "zx"), users.stream().map(u -> u.get("username")).toList(),
                "never recorded first, then oldest activity");

        Map<String, Object> bob = users.get(1);
        assertEquals("2026-05-09", bob.get("lastSeen"), "last login, as there is no activity stamp");
        assertEquals("6 months ago", bob.get("lastSeenAgo"));
        assertEquals("danger", bob.get("inactiveLevel"));
        assertEquals(2, bob.get("uploadCount"), "one audiobook and one chat image");
        assertEquals("3.0 GB", bob.get("storage"));
        assertEquals(true, bob.get("canDelete"));

        Map<String, Object> carol = users.get(2);
        assertEquals("warn", carol.get("inactiveLevel"), "50 days");
        assertEquals("50 days ago", carol.get("lastSeenAgo"));

        Map<String, Object> me = users.get(3);
        assertEquals(true, me.get("isYou"));
        assertEquals("ok", me.get("inactiveLevel"));
        assertEquals(false, me.get("canDelete"));
        assertEquals("This is your own account.", me.get("deleteBlockedReason"));

        Map<String, Object> dm2 = users.get(0);
        assertEquals("Never recorded", dm2.get("lastSeenAgo"));
        assertEquals(false, dm2.get("canDelete"), "admins can't be deleted");

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) result.get("totals");
        assertEquals(4, totals.get("accounts"));
        assertEquals(3, totals.get("inactive"));
    }

    @Test
    void accountsCanBeSortedByStorage() {
        List<Map<String, Object>> users = rows(admin.listUsers(ADMIN, "storage"), "users");
        assertEquals("bob", users.get(0).get("username"));
        assertEquals("carol", users.get(1).get("username"));
    }

    @Test
    void anUnknownSortFallsBackToTheDefault() {
        assertEquals("inactive", admin.listUsers(ADMIN, "DROP TABLE").get("sort"));
    }

    // ── uploads ───────────────────────────────────────────────

    @Test
    void uploadsAreListedOldestFirstAndOrphansAreFlagged() {
        Map<String, Object> result = admin.listUploads(ADMIN, null, null);
        List<Map<String, Object>> uploads = rows(result, "uploads");
        assertEquals(List.of(12L, 10L, 20L, 11L), uploads.stream().map(u -> ((Number) u.get("id")).longValue()).toList());

        Map<String, Object> orphan = uploads.get(0);
        assertEquals(true, orphan.get("ownerMissing"));
        assertEquals("Deleted account #99", orphan.get("owner"));
        assertEquals("file12.mp3", orphan.get("title"), "falls back to the file name when untitled");
        assertEquals("Video", orphan.get("type"));
        assertEquals("700 MB", orphan.get("size"));
        assertEquals("16 months ago", orphan.get("uploadedAgo"));

        Map<String, Object> chat = uploads.get(2);
        assertEquals("chat", chat.get("kind"));
        assertEquals("Chat image", chat.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) result.get("totals");
        assertEquals(1, totals.get("orphans"));
    }

    @Test
    void uploadsCanBeNarrowedToOneOwnerAndSortedBySize() {
        Map<String, Object> result = admin.listUploads(ADMIN, "largest", BOB);
        List<Map<String, Object>> uploads = rows(result, "uploads");
        assertEquals(2, uploads.size());
        assertEquals("Twilight", uploads.get(0).get("title"));
        assertEquals("bob", result.get("ownerName"));
    }

    @Test
    void deletingAnUploadReportsWhatWasFreed() {
        when(mediaManager.deleteMediaFileAsAdmin(10)).thenReturn(1024L * 1024 * 1024);
        Map<String, Object> result = admin.deleteUpload(ADMIN, "media", 10);
        assertEquals(200, result.get("status"));
        assertEquals("Deleted. Freed 1.0 GB.", result.get("message"));

        when(chatFiles.deleteChatFile(20)).thenReturn(-1L);
        assertEquals(404, admin.deleteUpload(ADMIN, "chat", 20).get("status"));
        assertEquals(400, admin.deleteUpload(ADMIN, "exe", 1).get("status"));
    }

    // ── deleting accounts ─────────────────────────────────────

    @Test
    void deletingAnAccountRemovesItsUploadsPlaylistsSignInsAndThenTheAccount() {
        when(mediaDb.getMediaFilesByPlayer(BOB)).thenReturn(List.of(media(10, BOB, 0, NOW, MediaType.AUDIOBOOK, "T")));
        when(mediaManager.deleteMediaFileAsAdmin(10)).thenReturn(3L * 1024 * 1024 * 1024);
        when(chatFiles.deleteChatFile(20)).thenReturn(2048L);
        Playlist list = new Playlist();
        list.setId(7);
        when(playlistDb.getPlaylistsByUser(BOB)).thenReturn(List.of(list));
        when(playlistDb.deletePlaylist(7)).thenReturn(true);
        when(mediaDb.getUserPlaybackPositions(BOB)).thenReturn(List.of(new PlaybackPosition(1, BOB, 10, 5.0, 10.0, NOW, false)));
        when(playerDb.deletePlayerAccount(BOB)).thenReturn(true);

        Map<String, Object> result = admin.deleteUser(ADMIN, BOB, " bob ");

        assertEquals(200, result.get("status"));
        assertEquals("Deleted bob: 1 upload, 1 chat image, 1 playlist. Freed 3.0 GB.", result.get("message"));
        verify(chatFiles, never()).deleteChatFile(longThat(id -> id != 20)); // only Bob's chat images
        verify(mediaDb).deletePlaybackPosition(BOB, 10);
        verify(mobileTokens).deleteByUserId(BOB);
        verify(rememberMe).deleteByUserId(BOB);
        verify(playerDb).deletePlayerAccount(BOB);
    }

    @Test
    void deletingRequiresTheExactUsername() {
        Map<String, Object> result = admin.deleteUser(ADMIN, BOB, "Bob");
        assertEquals(400, result.get("status"));
        verifyNoInteractions(mediaManager);
        verify(playerDb, never()).deletePlayerAccount(anyInt());
    }

    @Test
    void yourOwnAccountAndOtherAdminsCannotBeDeleted() {
        assertEquals(409, admin.deleteUser(ADMIN, ADMIN, "zx").get("status"));
        assertEquals(409, admin.deleteUser(ADMIN, OTHER_ADMIN, "dm2").get("status"));
        assertEquals(404, admin.deleteUser(ADMIN, 555, "ghost").get("status"));
        verify(playerDb, never()).deletePlayerAccount(anyInt());
    }

    // ── passwords ─────────────────────────────────────────────

    @Test
    void settingAPasswordSignsTheAccountOutElsewhere() {
        Map<String, Object> result = admin.setPassword(ADMIN, CAROL, "new-secret-1");
        assertEquals(200, result.get("status"));
        verify(players).setPassword(CAROL, "new-secret-1");
        verify(mobileTokens).deleteByUserId(CAROL);
        verify(rememberMe).deleteByUserId(CAROL);
    }

    @Test
    void aTooShortPasswordIsRefusedWithTheReason() {
        doThrow(new IllegalArgumentException("The password must be at least 8 characters."))
                .when(players).setPassword(CAROL, "short");
        Map<String, Object> result = admin.setPassword(ADMIN, CAROL, "short");
        assertEquals(400, result.get("status"));
        assertEquals("The password must be at least 8 characters.", result.get("message"));
        verify(mobileTokens, never()).deleteByUserId(anyInt());
    }

    @Test
    void sizesAndAgesReadNaturally() {
        assertEquals("0 B", UserAdminManager.humanBytes(0));
        assertEquals("1.5 KB", UserAdminManager.humanBytes(1536));
        assertEquals("120 MB", UserAdminManager.humanBytes(120L * 1024 * 1024));
        assertEquals("Today", UserAdminManager.ago(0));
        assertEquals("Yesterday", UserAdminManager.ago(1));
        assertEquals("3 months ago", UserAdminManager.ago(95));
    }
}
