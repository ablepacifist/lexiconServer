package lexicon.utils;

import lexicon.data.IMediaDatabase;
import lexicon.data.IPlaylistDatabase;
import lexicon.data.ILiveStreamDatabase;
import lexicon.object.MediaFile;
import lexicon.object.Playlist;
import java.io.File;
import java.util.List;

/**
 * Utility class for cleaning up test artifacts including database records and physical files
 */
public class TestCleanupUtil {
    
    private static final String STORAGE_PATH = "/media/alexpdyak32/7db05fe3-9f6a-46cb-82dd-8ff00d8488a0/lexicon-storage/";
    
    // All known test user IDs - add any new test user IDs here
    public static final int[] ALL_TEST_USER_IDS = {16, 32, 888, 998, 999, 9999};
    
    /**
     * Clean up ALL test data from all known test users
     * Call this in @AfterAll or @AfterEach to ensure complete cleanup
     */
    public static void cleanupAllTestData(IMediaDatabase mediaDatabase, IPlaylistDatabase playlistDatabase) {
        cleanupUserData(mediaDatabase, playlistDatabase, ALL_TEST_USER_IDS);
    }
    
    /**
     * Clean up all media files and playlists for specific user IDs
     */
    public static void cleanupUserData(IMediaDatabase mediaDatabase, IPlaylistDatabase playlistDatabase, int... userIds) {
        System.out.println("Starting comprehensive test cleanup for users: " + java.util.Arrays.toString(userIds));
        
        for (int userId : userIds) {
            // Clean up playlists
            try {
                List<Playlist> userPlaylists = playlistDatabase.getPlaylistsByUser(userId);
                System.out.println("Found " + userPlaylists.size() + " playlists to delete for user " + userId);
                for (Playlist playlist : userPlaylists) {
                    playlistDatabase.deletePlaylist(playlist.getId());
                    System.out.println("Deleted playlist: " + playlist.getName());
                }
            } catch (Exception e) {
                System.err.println("Error cleaning up playlists for user " + userId + ": " + e.getMessage());
            }
            
            // Clean up media files
            try {
                List<MediaFile> userMedia = mediaDatabase.getMediaFilesByPlayer(userId);
                System.out.println("Found " + userMedia.size() + " media files to delete for user " + userId);
                
                for (MediaFile media : userMedia) {
                    try {
                        // Delete physical file first (if exists)
                        if (media.getFilePath() != null && !media.getFilePath().isEmpty()) {
                            File physicalFile = new File(media.getFilePath());
                            if (physicalFile.exists()) {
                                boolean deleted = physicalFile.delete();
                                System.out.println("Physical file deletion: " + deleted + " for " + media.getFilePath());
                            }
                        }
                        
                        // Delete database records
                        mediaDatabase.deleteFileData(media.getId());
                        mediaDatabase.deleteMediaFile(media.getId());
                        
                        System.out.println("Deleted media: " + media.getOriginalFilename() + " (ID: " + media.getId() + ")");
                    } catch (Exception e) {
                        System.err.println("Could not delete media " + media.getId() + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("Error cleaning up media for user " + userId + ": " + e.getMessage());
            }
        }
        
        // Clean up any remaining test files in storage
        cleanupOrphanedTestFiles();
        
        System.out.println("Comprehensive test cleanup completed");
    }
    
    /**
     * Clean up orphaned test files that weren't properly linked to database records
     */
    public static void cleanupOrphanedTestFiles() {
        try {
            File storageDir = new File(STORAGE_PATH);
            if (storageDir.exists()) {
                System.out.println("Cleaning up orphaned test files in: " + STORAGE_PATH);
                cleanupTestFilesRecursively(storageDir);
            }
        } catch (Exception e) {
            System.err.println("Error during orphaned test file cleanup: " + e.getMessage());
        }
    }
    
    private static void cleanupTestFilesRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleanupTestFilesRecursively(file);
                } else if (file.isFile()) {
                    String fileName = file.getName();
                    // Delete files that match test patterns
                    if (isTestFile(fileName)) {
                        boolean deleted = file.delete();
                        System.out.println("Cleaned up orphaned test file: " + file.getPath() + " (deleted: " + deleted + ")");
                    }
                }
            }
        }
    }
    
    private static boolean isTestFile(String fileName) {
        return fileName.contains("Test_Audio") || 
               fileName.contains("Test_Video") || 
               fileName.contains("test_file") || 
               fileName.startsWith("test") ||
               fileName.contains("Large_Video_Test") || 
               fileName.contains("Streaming_Test") ||
               fileName.contains("_Test.") ||
               fileName.toLowerCase().contains("test");
    }
}