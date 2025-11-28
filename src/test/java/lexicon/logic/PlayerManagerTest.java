package lexicon.logic;

import lexicon.data.MockLexiconDatabase;
import lexicon.object.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;

/**
 * Unit tests for PlayerManager
 * Tests user management and authentication for the media sharing platform
 */
class PlayerManagerTest {
    
    private PlayerManager playerManager;
    private MockLexiconDatabase database;
    
    @BeforeEach
    void setUp() {
        database = new MockLexiconDatabase();
        playerManager = new PlayerManager(database);
    }
    
    // ========== Registration Tests ==========
    
    @Test
    void testRegisterPlayer() {
        Player player = playerManager.registerPlayer("john", "password123", 
                                                     "john@example.com", "John Doe");
        
        assertNotNull(player);
        assertEquals("john", player.getUsername());
        assertEquals("john@example.com", player.getEmail());
        assertEquals("John Doe", player.getDisplayName());
        assertNotNull(player.getRegistrationDate());
        assertEquals(1, player.getLevel());
    }
    
    @Test
    void testRegisterPlayerWithDefaultDisplayName() {
        Player player = playerManager.registerPlayer("jane", "pass", 
                                                     "jane@example.com", null);
        
        assertEquals("jane", player.getDisplayName()); // Should default to username
    }
    
    @Test
    void testRegisterPlayerDuplicateUsername() {
        playerManager.registerPlayer("duplicate", "pass", "user1@example.com", "User 1");
        
        assertThrows(IllegalArgumentException.class, () -> {
            playerManager.registerPlayer("duplicate", "pass", "user2@example.com", "User 2");
        });
    }
    
    @Test
    void testRegisterPlayerDuplicateEmail() {
        playerManager.registerPlayer("user1", "pass", "same@example.com", "User 1");
        
        assertThrows(IllegalArgumentException.class, () -> {
            playerManager.registerPlayer("user2", "pass", "same@example.com", "User 2");
        });
    }
    
    @Test
    void testRegisterPlayerEmptyUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerManager.registerPlayer("", "pass", "test@example.com", "Test");
        });
    }
    
    @Test
    void testRegisterPlayerNullUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerManager.registerPlayer(null, "pass", "test@example.com", "Test");
        });
    }
    
    @Test
    void testRegisterPlayerEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerManager.registerPlayer("user", "", "test@example.com", "Test");
        });
    }
    
    @Test
    void testRegisterPlayerEmptyEmail() {
        assertThrows(IllegalArgumentException.class, () -> {
            playerManager.registerPlayer("user", "pass", "", "Test");
        });
    }
    
    @Test
    void testPasswordIsHashed() {
        Player player = playerManager.registerPlayer("testuser", "plainpassword", 
                                                     "test@example.com", "Test");
        
        assertNotEquals("plainpassword", player.getPassword());
        assertTrue(player.getPassword().startsWith("$2a$")); // BCrypt hash format
    }
    
    // ========== Authentication Tests ==========
    
    @Test
    void testAuthenticatePlayerSuccess() {
        playerManager.registerPlayer("authuser", "correctpass", "auth@example.com", "Auth User");
        
        Player authenticated = playerManager.authenticatePlayer("authuser", "correctpass");
        
        assertNotNull(authenticated);
        assertEquals("authuser", authenticated.getUsername());
        assertNotNull(authenticated.getLastLoginDate()); // Should update last login
    }
    
    @Test
    void testAuthenticatePlayerWrongPassword() {
        playerManager.registerPlayer("user", "rightpass", "user@example.com", "User");
        
        Player authenticated = playerManager.authenticatePlayer("user", "wrongpass");
        
        assertNull(authenticated);
    }
    
    @Test
    void testAuthenticatePlayerNonexistentUser() {
        Player authenticated = playerManager.authenticatePlayer("nonexistent", "pass");
        
        assertNull(authenticated);
    }
    
    @Test
    void testAuthenticatePlayerNullUsername() {
        Player authenticated = playerManager.authenticatePlayer(null, "pass");
        
        assertNull(authenticated);
    }
    
    @Test
    void testAuthenticatePlayerNullPassword() {
        playerManager.registerPlayer("user", "pass", "user@example.com", "User");
        
        Player authenticated = playerManager.authenticatePlayer("user", null);
        
        assertNull(authenticated);
    }
    
    // ========== Get Player Tests ==========
    
    @Test
    void testGetPlayerById() {
        Player registered = playerManager.registerPlayer("getuser", "pass", 
                                                         "get@example.com", "Get User");
        
        Player retrieved = playerManager.getPlayerById(registered.getId());
        
        assertNotNull(retrieved);
        assertEquals(registered.getId(), retrieved.getId());
        assertEquals("getuser", retrieved.getUsername());
    }
    
    @Test
    void testGetPlayerByIdNonexistent() {
        Player retrieved = playerManager.getPlayerById(9999);
        
        assertNull(retrieved);
    }
    
    @Test
    void testGetPlayerByUsername() {
        playerManager.registerPlayer("searchuser", "pass", "search@example.com", "Search");
        
        Player found = playerManager.getPlayerByUsername("searchuser");
        
        assertNotNull(found);
        assertEquals("searchuser", found.getUsername());
    }
    
    @Test
    void testGetPlayerByUsernameNonexistent() {
        Player found = playerManager.getPlayerByUsername("doesnotexist");
        
        assertNull(found);
    }
    
    @Test
    void testGetPlayerByUsernameNull() {
        Player found = playerManager.getPlayerByUsername(null);
        
        assertNull(found);
    }
    
    @Test
    void testGetAllPlayers() {
        playerManager.registerPlayer("user1", "pass", "u1@example.com", "User 1");
        playerManager.registerPlayer("user2", "pass", "u2@example.com", "User 2");
        playerManager.registerPlayer("user3", "pass", "u3@example.com", "User 3");
        
        Collection<Player> all = playerManager.getAllPlayers();
        
        assertEquals(3, all.size());
    }
    
    // ========== Update/Delete Tests ==========
    
    @Test
    void testUpdatePlayer() {
        Player player = playerManager.registerPlayer("update", "pass", 
                                                     "update@example.com", "Original");
        
        player.setDisplayName("Updated Name");
        boolean updated = playerManager.updatePlayer(player);
        
        assertTrue(updated);
        Player retrieved = playerManager.getPlayerById(player.getId());
        assertEquals("Updated Name", retrieved.getDisplayName());
    }
    
    @Test
    void testUpdatePlayerNonexistent() {
        Player fakePlayer = new Player(9999, "fake", "pass", 1);
        
        boolean updated = playerManager.updatePlayer(fakePlayer);
        
        assertFalse(updated);
    }
    
    @Test
    void testUpdatePlayerNull() {
        boolean updated = playerManager.updatePlayer(null);
        
        assertFalse(updated);
    }
    
    @Test
    void testDeletePlayer() {
        Player player = playerManager.registerPlayer("deleteme", "pass", 
                                                     "delete@example.com", "Delete");
        
        boolean deleted = playerManager.deletePlayer(player.getId());
        
        assertTrue(deleted);
        assertNull(playerManager.getPlayerById(player.getId()));
    }
    
    @Test
    void testDeletePlayerNonexistent() {
        boolean deleted = playerManager.deletePlayer(9999);
        
        assertFalse(deleted);
    }
    
    // ========== Username/Email Availability Tests ==========
    
    @Test
    void testIsUsernameAvailable() {
        playerManager.registerPlayer("taken", "pass", "taken@example.com", "Taken");
        
        assertFalse(playerManager.isUsernameAvailable("taken"));
        assertTrue(playerManager.isUsernameAvailable("available"));
    }
    
    @Test
    void testIsUsernameAvailableEmpty() {
        assertFalse(playerManager.isUsernameAvailable(""));
        assertFalse(playerManager.isUsernameAvailable("   "));
    }
    
    @Test
    void testIsUsernameAvailableNull() {
        assertFalse(playerManager.isUsernameAvailable(null));
    }
    
    @Test
    void testIsEmailAvailable() {
        playerManager.registerPlayer("user", "pass", "taken@example.com", "User");
        
        assertFalse(playerManager.isEmailAvailable("taken@example.com"));
        assertTrue(playerManager.isEmailAvailable("available@example.com"));
    }
    
    @Test
    void testIsEmailAvailableEmpty() {
        assertFalse(playerManager.isEmailAvailable(""));
        assertFalse(playerManager.isEmailAvailable("   "));
    }
    
    @Test
    void testIsEmailAvailableNull() {
        assertFalse(playerManager.isEmailAvailable(null));
    }
}
