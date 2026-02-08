package lexicon.utils;

import lexicon.data.HSQLMediaDatabase;
import lexicon.data.HSQLPlaylistDatabase;
import lexicon.data.IMediaDatabase;
import lexicon.data.IPlaylistDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Base test class that provides automatic cleanup of test data.
 * All integration tests that use the real database should extend this class
 * to ensure test data is properly cleaned up after each test run.
 * 
 * Usage:
 * 1. Extend this class instead of writing your own @AfterAll cleanup
 * 2. Use TEST_USER_ID or any ID from TestCleanupUtil.ALL_TEST_USER_IDS for test data
 * 3. Override getTestUserIds() if you need custom test user IDs
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {
    
    protected IMediaDatabase mediaDatabase;
    protected IPlaylistDatabase playlistDatabase;
    
    /**
     * Override this to provide the test user IDs your test uses.
     * Default returns all known test user IDs.
     */
    protected int[] getTestUserIds() {
        return TestCleanupUtil.ALL_TEST_USER_IDS;
    }
    
    /**
     * Called before all tests to initialize database connections.
     * Override this in subclasses to set up test-specific databases.
     */
    @BeforeAll
    protected void initializeDatabases() {
        // Subclasses should override to set up their database connections
        // Example:
        // mediaDatabase = new HSQLMediaDatabase();
        // playlistDatabase = new HSQLPlaylistDatabase();
    }
    
    /**
     * Cleanup after each test to prevent test data accumulation.
     * This ensures that even if a test fails, cleanup still happens.
     */
    @AfterEach
    protected void cleanupAfterEachTest() {
        // Optional: Override if you want per-test cleanup
    }
    
    /**
     * Final cleanup after all tests complete.
     * This is the main cleanup that removes all test data.
     */
    @AfterAll
    protected void cleanupAfterAllTests() {
        System.out.println("=== Running automatic test cleanup ===");
        
        if (mediaDatabase != null || playlistDatabase != null) {
            TestCleanupUtil.cleanupUserData(mediaDatabase, playlistDatabase, getTestUserIds());
        }
        
        // Also clean up orphaned test files in storage
        TestCleanupUtil.cleanupOrphanedTestFiles();
        
        System.out.println("=== Test cleanup complete ===");
    }
}
