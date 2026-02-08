package lexicon.utils;

import java.sql.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone utility to clean ALL test data from the database
 * Can be run directly: java lexicon.utils.DatabaseCleanupRunner
 */
public class DatabaseCleanupRunner {
    
    private static final String DB_URL = "jdbc:hsqldb:file:../alchemyServer/alchemydb";
    private static final String DB_USER = "SA";
    private static final String DB_PASSWORD = "";
    
    // All known test user IDs
    private static final int[] TEST_USER_IDS = {16, 32, 888, 998, 999, 9999};
    
    public static void main(String[] args) {
        System.out.println("=== Database Test Data Cleanup ===");
        cleanupAllTestData();
        System.out.println("=== Cleanup Complete ===");
    }
    
    public static void cleanupAllTestData() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Connected to database");
            
            // 1. Clean up media files for test users
            cleanupTestUserMediaFiles(conn);
            
            // 2. Clean up any media files with "test" in the filename
            cleanupTestNamedMediaFiles(conn);
            
            // 3. Clean up test playlists
            cleanupTestPlaylists(conn);
            
            // 4. Clean up live stream queue entries from test users
            cleanupTestLiveStreamData(conn);
            
            // 5. Clean up playback positions for test users
            cleanupTestPlaybackPositions(conn);
            
            conn.commit();
            System.out.println("All test data cleanup committed successfully");
            
        } catch (SQLException e) {
            System.err.println("Database cleanup error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void cleanupTestUserMediaFiles(Connection conn) throws SQLException {
        System.out.println("\n--- Cleaning up test user media files ---");
        
        for (int userId : TEST_USER_IDS) {
            // First, get the file paths so we can delete physical files
            List<String> filePaths = new ArrayList<>();
            List<Integer> mediaIds = new ArrayList<>();
            
            String selectSql = "SELECT id, file_path FROM media_files WHERE uploaded_by = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    mediaIds.add(rs.getInt("id"));
                    String path = rs.getString("file_path");
                    if (path != null) {
                        filePaths.add(path);
                    }
                }
            }
            
            if (!mediaIds.isEmpty()) {
                System.out.println("Found " + mediaIds.size() + " media files for test user " + userId);
                
                // Delete file data first (foreign key constraint)
                for (int mediaId : mediaIds) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM file_data WHERE media_file_id = ?")) {
                        ps.setInt(1, mediaId);
                        ps.executeUpdate();
                    }
                }
                
                // Delete media files
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM media_files WHERE uploaded_by = ?")) {
                    ps.setInt(1, userId);
                    int deleted = ps.executeUpdate();
                    System.out.println("Deleted " + deleted + " media files for user " + userId);
                }
                
                // Delete physical files
                for (String path : filePaths) {
                    File file = new File(path);
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        System.out.println("Physical file " + path + " deleted: " + deleted);
                    }
                }
            }
        }
    }
    
    private static void cleanupTestNamedMediaFiles(Connection conn) throws SQLException {
        System.out.println("\n--- Cleaning up media files with 'test' in filename ---");
        
        List<Integer> mediaIds = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        
        String selectSql = "SELECT id, file_path FROM media_files WHERE LOWER(original_filename) LIKE '%test%'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {
            while (rs.next()) {
                mediaIds.add(rs.getInt("id"));
                String path = rs.getString("file_path");
                if (path != null) {
                    filePaths.add(path);
                }
            }
        }
        
        if (!mediaIds.isEmpty()) {
            System.out.println("Found " + mediaIds.size() + " media files with 'test' in filename");
            
            // Delete file data first
            for (int mediaId : mediaIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM file_data WHERE media_file_id = ?")) {
                    ps.setInt(1, mediaId);
                    ps.executeUpdate();
                }
            }
            
            // Delete media files
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate("DELETE FROM media_files WHERE LOWER(original_filename) LIKE '%test%'");
                System.out.println("Deleted " + deleted + " test-named media files");
            }
            
            // Delete physical files
            for (String path : filePaths) {
                File file = new File(path);
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }
    
    private static void cleanupTestPlaylists(Connection conn) throws SQLException {
        System.out.println("\n--- Cleaning up test playlists ---");
        
        for (int userId : TEST_USER_IDS) {
            // Delete playlist items first
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM playlist_items WHERE playlist_id IN (SELECT id FROM playlists WHERE owner_id = ?)")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                // Table might not exist
            }
            
            // Delete playlists
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM playlists WHERE owner_id = ?")) {
                ps.setInt(1, userId);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    System.out.println("Deleted " + deleted + " playlists for user " + userId);
                }
            } catch (SQLException e) {
                // Table might not exist
            }
        }
        
        // Also delete playlists with "test" in name
        try (Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate("DELETE FROM playlists WHERE LOWER(name) LIKE '%test%'");
            if (deleted > 0) {
                System.out.println("Deleted " + deleted + " test-named playlists");
            }
        } catch (SQLException e) {
            // Table might not exist
        }
    }
    
    private static void cleanupTestLiveStreamData(Connection conn) throws SQLException {
        System.out.println("\n--- Cleaning up test live stream data ---");
        
        for (int userId : TEST_USER_IDS) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM live_stream_queue WHERE added_by_user_id = ?")) {
                ps.setInt(1, userId);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    System.out.println("Deleted " + deleted + " queue items for user " + userId);
                }
            } catch (SQLException e) {
                // Table might not exist
            }
            
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM live_stream_skip_votes WHERE user_id = ?")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                // Table might not exist
            }
        }
    }
    
    private static void cleanupTestPlaybackPositions(Connection conn) throws SQLException {
        System.out.println("\n--- Cleaning up test playback positions ---");
        
        for (int userId : TEST_USER_IDS) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM playback_positions WHERE user_id = ?")) {
                ps.setInt(1, userId);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    System.out.println("Deleted " + deleted + " playback positions for user " + userId);
                }
            } catch (SQLException e) {
                // Table might not exist
            }
        }
    }
}
