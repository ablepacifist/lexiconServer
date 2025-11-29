package lexicon.data;

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
class HSQLPlaylistDatabaseTest {

    @Autowired
    private HSQLPlaylistDatabase playlistDatabase;

    @Autowired
    private IMediaDatabase mediaDatabase;

    private static int testPlaylistId;
    private static int testMediaId1;
    private static int testMediaId2;
    private static int testMediaId3;
    private static final int TEST_USER_ID = 999;

    @BeforeEach
    void setUp() {
        // Create test media files if not already created
        if (testMediaId1 == 0) {
            MediaFile media1 = new MediaFile();
            testMediaId1 = mediaDatabase.getNextMediaFileId();
            media1.setId(testMediaId1);
            media1.setFilename("test_song_1.mp3");
            media1.setOriginalFilename("Test Song 1.mp3");
            media1.setContentType("audio/mpeg");
            media1.setFileSize(1000000L);
            media1.setFilePath("/test/path/test_song_1.mp3");
            media1.setUploadedBy(TEST_USER_ID);
            media1.setUploadDate(LocalDateTime.now());
            media1.setTitle("Test Song 1");
            media1.setDescription("Test song for playlist tests");
            media1.setPublic(true);
            media1.setMediaType(MediaType.MUSIC);
            mediaDatabase.addMediaFile(media1);

            MediaFile media2 = new MediaFile();
            testMediaId2 = mediaDatabase.getNextMediaFileId();
            media2.setId(testMediaId2);
            media2.setFilename("test_song_2.mp3");
            media2.setOriginalFilename("Test Song 2.mp3");
            media2.setContentType("audio/mpeg");
            media2.setFileSize(2000000L);
            media2.setFilePath("/test/path/test_song_2.mp3");
            media2.setUploadedBy(TEST_USER_ID);
            media2.setUploadDate(LocalDateTime.now());
            media2.setTitle("Test Song 2");
            media2.setDescription("Another test song");
            media2.setPublic(true);
            media2.setMediaType(MediaType.MUSIC);
            mediaDatabase.addMediaFile(media2);

            MediaFile media3 = new MediaFile();
            testMediaId3 = mediaDatabase.getNextMediaFileId();
            media3.setId(testMediaId3);
            media3.setFilename("test_song_3.mp3");
            media3.setOriginalFilename("Test Song 3.mp3");
            media3.setContentType("audio/mpeg");
            media3.setFileSize(1500000L);
            media3.setFilePath("/test/path/test_song_3.mp3");
            media3.setUploadedBy(TEST_USER_ID);
            media3.setUploadDate(LocalDateTime.now());
            media3.setTitle("Test Song 3");
            media3.setDescription("Third test song");
            media3.setPublic(false);
            media3.setMediaType(MediaType.MUSIC);
            mediaDatabase.addMediaFile(media3);
        }
    }

    @Test
    @Order(1)
    void testCreatePlaylist() {
        Playlist playlist = new Playlist();
        playlist.setName("Test Playlist");
        playlist.setDescription("A test playlist for unit testing");
        playlist.setPublic(true);
        playlist.setCreatedBy(TEST_USER_ID);
        playlist.setMediaType(MediaType.MUSIC);
        playlist.setCreatedDate(LocalDateTime.now());

        testPlaylistId = playlistDatabase.createPlaylist(playlist);

        assertTrue(testPlaylistId > 0, "Playlist ID should be positive");
    }

    @Test
    @Order(2)
    void testGetPlaylistById() {
        Playlist playlist = playlistDatabase.getPlaylistById(testPlaylistId);

        assertNotNull(playlist, "Playlist should not be null");
        assertEquals("Test Playlist", playlist.getName());
        assertEquals("A test playlist for unit testing", playlist.getDescription());
        assertTrue(playlist.isPublic());
        assertEquals(TEST_USER_ID, playlist.getCreatedBy());
        assertEquals(MediaType.MUSIC, playlist.getMediaType());
    }

    @Test
    @Order(3)
    void testAddItemToPlaylist() {
        // Add first item
        boolean result1 = playlistDatabase.addItemToPlaylist(testPlaylistId, testMediaId1, 0);
        assertTrue(result1, "Should successfully add first item");

        // Add second item
        boolean result2 = playlistDatabase.addItemToPlaylist(testPlaylistId, testMediaId2, 1);
        assertTrue(result2, "Should successfully add second item");

        // Add third item
        boolean result3 = playlistDatabase.addItemToPlaylist(testPlaylistId, testMediaId3, 2);
        assertTrue(result3, "Should successfully add third item");
    }

    @Test
    @Order(4)
    void testGetPlaylistItems() {
        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(testPlaylistId);

        assertNotNull(items, "Items list should not be null");
        assertEquals(3, items.size(), "Should have 3 items");
        assertEquals(testMediaId1, items.get(0).getMediaFileId());
        assertEquals(testMediaId2, items.get(1).getMediaFileId());
        assertEquals(testMediaId3, items.get(2).getMediaFileId());
        assertEquals(0, items.get(0).getPosition());
        assertEquals(1, items.get(1).getPosition());
        assertEquals(2, items.get(2).getPosition());
    }

    @Test
    @Order(5)
    void testGetPlaylistWithItems() {
        Playlist playlist = playlistDatabase.getPlaylistWithItems(testPlaylistId);

        assertNotNull(playlist, "Playlist should not be null");
        assertNotNull(playlist.getItems(), "Items should not be null");
        assertEquals(3, playlist.getItems().size(), "Should have 3 items");

        // Verify items have media file data populated
        PlaylistItem firstItem = playlist.getItems().get(0);
        assertNotNull(firstItem.getMediaFile(), "Media file should be populated");
        assertEquals("Test Song 1", firstItem.getMediaFile().getTitle());
    }

    @Test
    @Order(6)
    void testRemoveItemFromPlaylist() {
        // Remove middle item (testMediaId2 at position 1)
        boolean result = playlistDatabase.removeItemFromPlaylist(testPlaylistId, testMediaId2);
        assertTrue(result, "Should successfully remove item");

        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(testPlaylistId);
        assertEquals(2, items.size(), "Should have 2 items after removal");

        // Verify the correct item was removed
        boolean hasMediaId2 = items.stream().anyMatch(item -> item.getMediaFileId() == testMediaId2);
        assertFalse(hasMediaId2, "testMediaId2 should be removed");
    }

    @Test
    @Order(7)
    void testUpdateItemPosition() {
        // Move item to new position
        boolean result = playlistDatabase.updateItemPosition(testPlaylistId, testMediaId1, 5);
        assertTrue(result, "Should successfully update position");

        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(testPlaylistId);
        PlaylistItem movedItem = items.stream()
                .filter(item -> item.getMediaFileId() == testMediaId1)
                .findFirst()
                .orElse(null);

        assertNotNull(movedItem, "Item should still exist");
        assertEquals(5, movedItem.getPosition(), "Position should be updated to 5");
    }

    @Test
    @Order(8)
    void testReorderPlaylist() {
        // Re-add testMediaId2 for reordering test
        playlistDatabase.addItemToPlaylist(testPlaylistId, testMediaId2, 10);

        // Reorder: testMediaId3, testMediaId2, testMediaId1
        List<Integer> newOrder = Arrays.asList(testMediaId3, testMediaId2, testMediaId1);
        boolean result = playlistDatabase.reorderPlaylist(testPlaylistId, newOrder);
        assertTrue(result, "Should successfully reorder playlist");

        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(testPlaylistId);
        assertEquals(3, items.size(), "Should have 3 items");
        assertEquals(testMediaId3, items.get(0).getMediaFileId(), "First should be testMediaId3");
        assertEquals(testMediaId2, items.get(1).getMediaFileId(), "Second should be testMediaId2");
        assertEquals(testMediaId1, items.get(2).getMediaFileId(), "Third should be testMediaId1");
        assertEquals(0, items.get(0).getPosition(), "First position should be 0");
        assertEquals(1, items.get(1).getPosition(), "Second position should be 1");
        assertEquals(2, items.get(2).getPosition(), "Third position should be 2");
    }

    @Test
    @Order(9)
    void testGetPlaylistsByUser() {
        // Create another playlist for the same user
        Playlist playlist2 = new Playlist();
        playlist2.setName("Second Test Playlist");
        playlist2.setDescription("Another playlist");
        playlist2.setPublic(false);
        playlist2.setCreatedBy(TEST_USER_ID);
        playlist2.setMediaType(MediaType.VIDEO);
        playlist2.setCreatedDate(LocalDateTime.now());
        playlistDatabase.createPlaylist(playlist2);

        List<Playlist> userPlaylists = playlistDatabase.getPlaylistsByUser(TEST_USER_ID);

        assertNotNull(userPlaylists, "User playlists should not be null");
        assertTrue(userPlaylists.size() >= 2, "Should have at least 2 playlists");

        boolean hasTestPlaylist = userPlaylists.stream()
                .anyMatch(p -> p.getName().equals("Test Playlist"));
        assertTrue(hasTestPlaylist, "Should contain test playlist");
    }

    @Test
    @Order(10)
    void testGetPublicPlaylists() {
        List<Playlist> publicPlaylists = playlistDatabase.getPublicPlaylists();

        assertNotNull(publicPlaylists, "Public playlists should not be null");
        assertTrue(publicPlaylists.size() >= 1, "Should have at least 1 public playlist");

        // All playlists should be public
        for (Playlist playlist : publicPlaylists) {
            assertTrue(playlist.isPublic(), "All playlists should be public");
        }
    }

    @Test
    @Order(11)
    void testUpdatePlaylist() {
        Playlist playlist = playlistDatabase.getPlaylistById(testPlaylistId);
        playlist.setName("Updated Playlist Name");
        playlist.setDescription("Updated description");
        playlist.setPublic(false);

        boolean result = playlistDatabase.updatePlaylist(playlist);
        assertTrue(result, "Should successfully update playlist");

        Playlist updated = playlistDatabase.getPlaylistById(testPlaylistId);
        assertEquals("Updated Playlist Name", updated.getName());
        assertEquals("Updated description", updated.getDescription());
        assertFalse(updated.isPublic());
    }

    @Test
    @Order(12)
    void testDeletePlaylist() {
        // Create a temporary playlist to delete
        Playlist tempPlaylist = new Playlist();
        tempPlaylist.setName("Temp Playlist");
        tempPlaylist.setDescription("To be deleted");
        tempPlaylist.setPublic(true);
        tempPlaylist.setCreatedBy(TEST_USER_ID);
        tempPlaylist.setMediaType(MediaType.MUSIC);
        tempPlaylist.setCreatedDate(LocalDateTime.now());
        int tempId = playlistDatabase.createPlaylist(tempPlaylist);

        // Add an item to it
        playlistDatabase.addItemToPlaylist(tempId, testMediaId1, 0);

        // Delete the playlist
        boolean result = playlistDatabase.deletePlaylist(tempId);
        assertTrue(result, "Should successfully delete playlist");

        // Verify it's deleted
        Playlist deleted = playlistDatabase.getPlaylistById(tempId);
        assertNull(deleted, "Playlist should be null after deletion");

        // Verify items are also deleted (CASCADE)
        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(tempId);
        assertTrue(items.isEmpty(), "Items should be empty after playlist deletion");
    }

    @Test
    @Order(13)
    void testRemoveItemFromAnyPosition() {
        // Test removing items from beginning, middle, and end
        Playlist testPlaylist = new Playlist();
        testPlaylist.setName("Position Test Playlist");
        testPlaylist.setDescription("Test removal from any position");
        testPlaylist.setPublic(true);
        testPlaylist.setCreatedBy(TEST_USER_ID);
        testPlaylist.setMediaType(MediaType.MUSIC);
        testPlaylist.setCreatedDate(LocalDateTime.now());
        int positionTestId = playlistDatabase.createPlaylist(testPlaylist);

        // Add 3 unique items
        playlistDatabase.addItemToPlaylist(positionTestId, testMediaId1, 0);
        playlistDatabase.addItemToPlaylist(positionTestId, testMediaId2, 1);
        playlistDatabase.addItemToPlaylist(positionTestId, testMediaId3, 2);
        
        List<PlaylistItem> items = playlistDatabase.getPlaylistItems(positionTestId);
        assertEquals(3, items.size(), "Should have 3 items initially");

        // Remove from middle (testMediaId2 at position 1)
        playlistDatabase.removeItemFromPlaylist(positionTestId, testMediaId2);
        items = playlistDatabase.getPlaylistItems(positionTestId);
        assertEquals(2, items.size(), "Should have 2 items after removing from middle");
        
        // Verify correct items remain
        boolean hasMediaId1 = items.stream().anyMatch(item -> item.getMediaFileId() == testMediaId1);
        boolean hasMediaId3 = items.stream().anyMatch(item -> item.getMediaFileId() == testMediaId3);
        assertTrue(hasMediaId1, "testMediaId1 should remain");
        assertTrue(hasMediaId3, "testMediaId3 should remain");

        // Remove from beginning (testMediaId1)
        playlistDatabase.removeItemFromPlaylist(positionTestId, testMediaId1);
        items = playlistDatabase.getPlaylistItems(positionTestId);
        assertEquals(1, items.size(), "Should have 1 item after removing from beginning");
        assertEquals(testMediaId3, items.get(0).getMediaFileId(), "Only testMediaId3 should remain");
        
        // Remove from end (last remaining item)
        playlistDatabase.removeItemFromPlaylist(positionTestId, testMediaId3);
        items = playlistDatabase.getPlaylistItems(positionTestId);
        assertEquals(0, items.size(), "Should have 0 items after removing all");

        // Cleanup
        playlistDatabase.deletePlaylist(positionTestId);
    }

    @AfterAll
    static void cleanup(@Autowired IPlaylistDatabase playlistDatabase, 
                        @Autowired IMediaDatabase mediaDatabase) {
        System.out.println("Starting HSQLPlaylistDatabaseTest cleanup...");
        
        // Clean up all playlists created by TEST_USER_ID
        List<Playlist> userPlaylists = playlistDatabase.getPlaylistsByUser(TEST_USER_ID);
        System.out.println("Found " + userPlaylists.size() + " playlists to delete for user " + TEST_USER_ID);
        for (Playlist playlist : userPlaylists) {
            playlistDatabase.deletePlaylist(playlist.getId());
        }
        
        // Clean up ALL media files uploaded by TEST_USER_ID
        try {
            List<MediaFile> userMedia = mediaDatabase.getMediaFilesByPlayer(TEST_USER_ID);
            int deletedCount = 0;
            for (MediaFile media : userMedia) {
                try {
                    mediaDatabase.deleteMediaFile(media.getId());
                    deletedCount++;
                    System.out.println("Deleted test media: " + media.getOriginalFilename());
                } catch (Exception e) {
                    System.err.println("Could not delete media " + media.getId() + ": " + e.getMessage());
                }
            }
            System.out.println("Deleted " + deletedCount + " test media files for user " + TEST_USER_ID);
        } catch (Exception e) {
            System.err.println("Error during media cleanup: " + e.getMessage());
        }
        
        System.out.println("HSQLPlaylistDatabaseTest cleanup completed");
    }
}

