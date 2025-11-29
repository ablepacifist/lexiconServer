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
        System.out.println("Starting PlaylistManagerTest cleanup...");
        
        // Clean up all playlists created by TEST_USER_ID and OTHER_USER_ID
        List<Playlist> testUserPlaylists = playlistDatabase.getPlaylistsByUser(TEST_USER_ID);
        System.out.println("Found " + testUserPlaylists.size() + " playlists to delete for user " + TEST_USER_ID);
        for (Playlist playlist : testUserPlaylists) {
            playlistDatabase.deletePlaylist(playlist.getId());
        }
        
        List<Playlist> otherUserPlaylists = playlistDatabase.getPlaylistsByUser(OTHER_USER_ID);
        System.out.println("Found " + otherUserPlaylists.size() + " playlists to delete for user " + OTHER_USER_ID);
        for (Playlist playlist : otherUserPlaylists) {
            playlistDatabase.deletePlaylist(playlist.getId());
        }
        
        // Clean up ALL media files uploaded by TEST_USER_ID and OTHER_USER_ID
        try {
            List<MediaFile> testUserMedia = mediaDatabase.getMediaFilesByPlayer(TEST_USER_ID);
            List<MediaFile> otherUserMedia = mediaDatabase.getMediaFilesByPlayer(OTHER_USER_ID);
            
            int deletedCount = 0;
            for (MediaFile media : testUserMedia) {
                try {
                    mediaDatabase.deleteMediaFile(media.getId());
                    deletedCount++;
                    System.out.println("Deleted test media: " + media.getOriginalFilename());
                } catch (Exception e) {
                    System.err.println("Could not delete media " + media.getId() + ": " + e.getMessage());
                }
            }
            
            for (MediaFile media : otherUserMedia) {
                try {
                    mediaDatabase.deleteMediaFile(media.getId());
                    deletedCount++;
                    System.out.println("Deleted test media: " + media.getOriginalFilename());
                } catch (Exception e) {
                    System.err.println("Could not delete media " + media.getId() + ": " + e.getMessage());
                }
            }
            
            System.out.println("Deleted " + deletedCount + " test media files");
        } catch (Exception e) {
            System.err.println("Error during media cleanup: " + e.getMessage());
        }
        
        System.out.println("PlaylistManagerTest cleanup completed");
    }
}
