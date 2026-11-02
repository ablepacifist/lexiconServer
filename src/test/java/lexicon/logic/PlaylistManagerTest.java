package lexicon.logic;

import lexicon.data.IMediaDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.object.MediaFile;
import lexicon.object.MediaType;
import lexicon.object.Playlist;
import lexicon.object.PlaylistItem;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaylistManagerTest {

    @Autowired
    private PlaylistManager playlistManager;

    @Autowired
    private IPlaylistDatabase playlistDatabase;

    @Autowired
    private IMediaDatabase mediaDatabase;

    private static int testPlaylistId;
    private static int testMediaId1;
    private static int testMediaId2;
    private static final int TEST_USER_ID = 888;
    private static final int OTHER_USER_ID = 777;

    @BeforeEach
    void setUp() {
        // Create test media files if not already created
        if (testMediaId1 == 0) {
            MediaFile media1 = new MediaFile();
            testMediaId1 = mediaDatabase.getNextMediaFileId();
            media1.setId(testMediaId1);
            media1.setFilename("manager_test_1.mp3");
            media1.setOriginalFilename("Manager Test 1.mp3");
            media1.setContentType("audio/mpeg");
            media1.setFileSize(1000000L);
            media1.setFilePath("/test/path/manager_test_1.mp3");
            media1.setUploadedBy(TEST_USER_ID);
            media1.setUploadDate(LocalDateTime.now());
            media1.setTitle("Manager Test Song 1");
            media1.setPublic(true);
            media1.setMediaType(MediaType.MUSIC);
            mediaDatabase.addMediaFile(media1);

            MediaFile media2 = new MediaFile();
            testMediaId2 = mediaDatabase.getNextMediaFileId();
            media2.setId(testMediaId2);
            media2.setFilename("manager_test_2.mp3");
            media2.setOriginalFilename("Manager Test 2.mp3");
            media2.setContentType("audio/mpeg");
            media2.setFileSize(2000000L);
            media2.setFilePath("/test/path/manager_test_2.mp3");
            media2.setUploadedBy(TEST_USER_ID);
            media2.setUploadDate(LocalDateTime.now());
            media2.setTitle("Manager Test Song 2");
            media2.setPublic(true);
            media2.setMediaType(MediaType.MUSIC);
            mediaDatabase.addMediaFile(media2);
        }
    }

    @Test
    @Order(1)
    void testCreatePlaylist() {
        Playlist playlist = new Playlist();
        playlist.setName("Manager Test Playlist");
        playlist.setDescription("Testing playlist manager");
        playlist.setPublic(true);
        playlist.setCreatedBy(TEST_USER_ID);
        playlist.setMediaType(MediaType.MUSIC);
        playlist.setCreatedDate(LocalDateTime.now());

        testPlaylistId = playlistManager.createPlaylist(playlist);

        assertTrue(testPlaylistId > 0, "Should create playlist and return ID");
    }

    @Test
    @Order(2)
    void testCreatePlaylistWithEmptyName() {
        Playlist playlist = new Playlist();
        playlist.setName("");
        playlist.setDescription("Invalid playlist");
        playlist.setPublic(true);
        playlist.setCreatedBy(TEST_USER_ID);
        playlist.setMediaType(MediaType.MUSIC);
        playlist.setCreatedDate(LocalDateTime.now());

        assertThrows(IllegalArgumentException.class, () -> {
            playlistManager.createPlaylist(playlist);
        }, "Should throw exception for empty name");
    }

    @Test
    @Order(3)
    void testGetPlaylistById() {
        Playlist playlist = playlistManager.getPlaylistById(testPlaylistId);

        assertNotNull(playlist);
        assertEquals("Manager Test Playlist", playlist.getName());
    }

    @Test
    @Order(4)
    void testAddItemToPlaylist() {
        boolean result = playlistManager.addItemToPlaylist(testPlaylistId, testMediaId1, TEST_USER_ID);
        assertTrue(result, "Should successfully add item");

        Playlist playlist = playlistManager.getPlaylistWithItems(testPlaylistId);
        assertEquals(1, playlist.getItems().size());
    }

    @Test
    @Order(5)
    void testAddItemToPlaylistUnauthorized() {
        assertThrows(SecurityException.class, () -> {
            playlistManager.addItemToPlaylist(testPlaylistId, testMediaId2, OTHER_USER_ID);
        }, "Should throw SecurityException for unauthorized user");
    }

    @Test
    @Order(6)
    void testRemoveItemFromPlaylist() {
        // Add second item first
        playlistManager.addItemToPlaylist(testPlaylistId, testMediaId2, TEST_USER_ID);

        Playlist before = playlistManager.getPlaylistWithItems(testPlaylistId);
        assertEquals(2, before.getItems().size());

        // Remove first item
        boolean result = playlistManager.removeItemFromPlaylist(testPlaylistId, testMediaId1, TEST_USER_ID);
        assertTrue(result);

        Playlist after = playlistManager.getPlaylistWithItems(testPlaylistId);
        assertEquals(1, after.getItems().size());
        assertEquals(testMediaId2, after.getItems().get(0).getMediaFileId());
    }

    @Test
    @Order(7)
    void testRemoveItemFromPlaylistUnauthorized() {
        assertThrows(SecurityException.class, () -> {
            playlistManager.removeItemFromPlaylist(testPlaylistId, testMediaId2, OTHER_USER_ID);
        }, "Should throw SecurityException for unauthorized user");
    }

    @Test
    @Order(8)
    void testReorderPlaylist() {
        // Add first item back
        playlistManager.addItemToPlaylist(testPlaylistId, testMediaId1, TEST_USER_ID);

        List<Integer> newOrder = Arrays.asList(testMediaId1, testMediaId2);
        boolean result = playlistManager.reorderPlaylist(testPlaylistId, newOrder, TEST_USER_ID);
        assertTrue(result);

        Playlist playlist = playlistManager.getPlaylistWithItems(testPlaylistId);
        assertEquals(testMediaId1, playlist.getItems().get(0).getMediaFileId());
        assertEquals(testMediaId2, playlist.getItems().get(1).getMediaFileId());
    }

    @Test
    @Order(9)
    void testReorderPlaylistUnauthorized() {
        List<Integer> newOrder = Arrays.asList(testMediaId2, testMediaId1);
        assertThrows(SecurityException.class, () -> {
            playlistManager.reorderPlaylist(testPlaylistId, newOrder, OTHER_USER_ID);
        }, "Should throw SecurityException for unauthorized user");
    }

    @Test
    @Order(10)
    void testUpdatePlaylist() {
        Playlist playlist = playlistManager.getPlaylistById(testPlaylistId);
        playlist.setName("Updated Manager Playlist");
        playlist.setDescription("Updated by manager");

        boolean result = playlistManager.updatePlaylist(playlist, TEST_USER_ID);
        assertTrue(result);

        Playlist updated = playlistManager.getPlaylistById(testPlaylistId);
        assertEquals("Updated Manager Playlist", updated.getName());
        assertEquals("Updated by manager", updated.getDescription());
    }

    @Test
    @Order(11)
    void testUpdatePlaylistUnauthorized() {
        Playlist playlist = playlistManager.getPlaylistById(testPlaylistId);
        playlist.setName("Unauthorized Update");

        assertThrows(SecurityException.class, () -> {
            playlistManager.updatePlaylist(playlist, OTHER_USER_ID);
        }, "Should throw SecurityException for unauthorized user");
    }

    @Test
    @Order(12)
    void testGetPlaylistsByUser() {
        List<Playlist> playlists = playlistManager.getPlaylistsByUser(TEST_USER_ID);

        assertNotNull(playlists);
        assertTrue(playlists.size() >= 1);
        assertTrue(playlists.stream().anyMatch(p -> p.getName().equals("Updated Manager Playlist")));
    }

    @Test
    @Order(13)
    void testGetPublicPlaylists() {
        List<Playlist> publicPlaylists = playlistManager.getPublicPlaylists();

        assertNotNull(publicPlaylists);
        assertTrue(publicPlaylists.stream().allMatch(Playlist::isPublic));
    }

    @Test
    @Order(14)
    void testDeletePlaylist() {
        // Create a temp playlist to delete
        Playlist tempPlaylist = new Playlist();
        tempPlaylist.setName("Temp for Deletion");
        tempPlaylist.setDescription("Will be deleted");
        tempPlaylist.setPublic(true);
        tempPlaylist.setCreatedBy(TEST_USER_ID);
        tempPlaylist.setMediaType(MediaType.MUSIC);
        tempPlaylist.setCreatedDate(LocalDateTime.now());
        int tempId = playlistManager.createPlaylist(tempPlaylist);

        boolean result = playlistManager.deletePlaylist(tempId, TEST_USER_ID);
        assertTrue(result);

        Playlist deleted = playlistManager.getPlaylistById(tempId);
        assertNull(deleted);
    }

    @Test
    @Order(15)
    void testDeletePlaylistUnauthorized() {
        assertThrows(SecurityException.class, () -> {
            playlistManager.deletePlaylist(testPlaylistId, OTHER_USER_ID);
        }, "Should throw SecurityException for unauthorized user");
    }

    @AfterAll
    static void cleanup(@Autowired IPlaylistDatabase playlistDatabase,
                        @Autowired IMediaDatabase mediaDatabase) {
        lexicon.utils.TestCleanupUtil.cleanupUserData(mediaDatabase, playlistDatabase, TEST_USER_ID, OTHER_USER_ID);
    }
    
    // ============ YouTube Import Tests ============
    
    // The importer shells out to yt-dlp and talks to YouTube; these tests replace it so they need neither.
    @org.springframework.boot.test.mock.mockito.MockBean
    private lexicon.service.YoutubeImportService youtubeImportService;

    /** A process that has already finished successfully, standing in for the yt-dlp listing process. */
    private static class FinishedProcess extends Process {
        @Override public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
        @Override public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }
        @Override public java.io.InputStream getErrorStream() { return java.io.InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { }
    }

    /** What the importer returns for a playlist listing: the first entry, then one JSON line per further entry. */
    private static lexicon.service.YoutubeImportService.PlaylistMetadata playlistListing(String name, String... laterEntries)
            throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        lexicon.service.YoutubeImportService.PlaylistMetadata metadata = new lexicon.service.YoutubeImportService.PlaylistMetadata();
        metadata.name = name;
        metadata.mapper = mapper;
        metadata.firstEntry = mapper.readTree("{\"id\":\"vid1\",\"title\":\"Track One\"}");
        metadata.reader = new java.io.BufferedReader(new java.io.StringReader(String.join("\n", laterEntries)));
        metadata.process = new FinishedProcess();
        return metadata;
    }

    @Test
    @Order(20)
    void testImportYoutubePlaylist_invalidUrl() throws Exception {
        String invalidUrl = "https://music.youtube.com/playlist?list=INVALID";
        org.mockito.Mockito.when(youtubeImportService.fetchPlaylistMetadata(invalidUrl))
                .thenThrow(new IllegalStateException("Playlist does not exist"));

        PlaylistManager.ImportResult result = playlistManager.importYoutubePlaylist(
            invalidUrl, TEST_USER_ID, null, true, false);

        assertFalse(result.success, "Import should fail for invalid URL");
        assertEquals("Playlist does not exist", result.errorMessage);
        assertEquals(-1, result.playlistId, "Should not create playlist");
        assertEquals(0, result.totalTracks, "Should have 0 total tracks");
    }

    @Test
    @Order(21)
    void testImportYoutubePlaylist_validUrlWithCustomName() throws Exception {
        String url = "https://music.youtube.com/playlist?list=PLcustom";
        org.mockito.Mockito.when(youtubeImportService.fetchPlaylistMetadata(url))
                .thenReturn(playlistListing("Detected Name", "{\"id\":\"vid2\",\"title\":\"Track Two\"}"));
        org.mockito.Mockito.when(youtubeImportService.downloadAndUploadMedia(
                org.mockito.ArgumentMatchers.eq("vid1"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(testMediaId1);
        org.mockito.Mockito.when(youtubeImportService.downloadAndUploadMedia(
                org.mockito.ArgumentMatchers.eq("vid2"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(testMediaId2);

        PlaylistManager.ImportResult result = playlistManager.importYoutubePlaylist(url, TEST_USER_ID, "Test Import Playlist", true, false);

        assertTrue(result.success, "Import should succeed: " + result.errorMessage);
        assertTrue(result.playlistId > 0, "Should create playlist");
        assertEquals(2, result.totalTracks);
        assertEquals(2, result.successfulTracks);
        assertEquals(0, result.failedTracks);

        Playlist imported = playlistManager.getPlaylistById(result.playlistId);
        assertNotNull(imported, "Playlist should exist");
        assertEquals("Test Import Playlist", imported.getName(), "Should use custom name, not the detected one");
        assertEquals(MediaType.MUSIC, imported.getMediaType());
        assertEquals(TEST_USER_ID, imported.getCreatedBy());
        assertTrue(imported.isPublic());
        assertEquals(2, playlistDatabase.getPlaylistItems(result.playlistId).size());

        playlistManager.deletePlaylist(result.playlistId, TEST_USER_ID);
    }

    @Test
    @Order(22)
    void testImportYoutubePlaylist_defaultName() throws Exception {
        String url = "https://music.youtube.com/playlist?list=PLdefault";
        org.mockito.Mockito.when(youtubeImportService.fetchPlaylistMetadata(url))
                .thenReturn(playlistListing("Detected Name", "{\"id\":\"vid2\",\"title\":\"Track Two\"}"));
        org.mockito.Mockito.when(youtubeImportService.downloadAndUploadMedia(
                org.mockito.ArgumentMatchers.eq("vid1"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(testMediaId1);
        // the second track cannot be downloaded
        org.mockito.Mockito.when(youtubeImportService.downloadAndUploadMedia(
                org.mockito.ArgumentMatchers.eq("vid2"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(-1);

        // null name: use the detected one; private playlist, public media
        PlaylistManager.ImportResult result = playlistManager.importYoutubePlaylist(url, TEST_USER_ID, null, false, true);

        assertTrue(result.success, "A failed track must not fail the whole import: " + result.errorMessage);
        assertEquals(2, result.totalTracks);
        assertEquals(1, result.successfulTracks);
        assertEquals(1, result.failedTracks);

        Playlist imported = playlistManager.getPlaylistById(result.playlistId);
        assertEquals("Detected Name", imported.getName());
        assertFalse(imported.isPublic(), "Should be private (as specified)");
        assertEquals(1, playlistDatabase.getPlaylistItems(result.playlistId).size());
        org.mockito.Mockito.verify(youtubeImportService).downloadAndUploadMedia(
                "vid1", "Track One", "Detected Name", TEST_USER_ID, true, "MUSIC", "AUDIO_ONLY");

        playlistManager.deletePlaylist(result.playlistId, TEST_USER_ID);
    }

    @Test
    @Order(23)
    void testImportResult_defaultValues() {
        System.out.println("\n=== Test: ImportResult Default Values ===");
        
        PlaylistManager.ImportResult result = new PlaylistManager.ImportResult();
        
        assertFalse(result.success, "Default success should be false");
        assertEquals(-1, result.playlistId, "Default playlist ID should be -1");
        assertEquals(0, result.totalTracks, "Default total tracks should be 0");
        assertEquals(0, result.successfulTracks, "Default successful tracks should be 0");
        assertEquals(0, result.failedTracks, "Default failed tracks should be 0");
        assertNull(result.errorMessage, "Default error message should be null");
        
        System.out.println("✓ ImportResult has correct default values");
    }
}
